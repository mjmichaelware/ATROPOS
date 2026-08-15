"""Turning one HTTP request into one API response.

Reads the body under a size limit, validates host, headers and content type,
builds the request, dispatches it, and writes the response. 308 lines because it
is the whole request path in order; splitting it further would separate steps
that only make sense as a sequence.
"""

from __future__ import annotations

from .server_origins import (
    _ARTIFACT_DOWNLOAD_RE,
    is_origin_allowed,
)
from .gateway import new_request
from .models import ApiResponse
from .observability import safe_route
from .resource_limits import JsonLimitExceeded
from .resource_limits import validate_json_limits
from .security import SecurityRejection
from .security import validate_content_type
from .security import validate_headers
from .security import validate_host
from .security import validate_request_target
from .server_responses import send
from .server_responses import serve_artifact_download
import json
import sys
import time
import uuid


def handle(handler) -> None:
    started = time.monotonic()
    preflight_request_id = str(
        uuid.uuid4()
    )
    acquired = handler.semaphore_.acquire(
        blocking=False
    )
    if not acquired:
        send(handler, 
            ApiResponse(
                status=503,
                body={
                    "error": {
                        "code": "SERVER_BUSY",
                        "message": "server is busy",
                        "request_id": preflight_request_id,
                        "details": {},
                    }
                },
                headers={
                    "x-request-id": preflight_request_id,
                    "retry-after": "1"
                },
            )
        )
        return
    try:
        validate_host(
            handler.headers.get("host"),
            handler.security_settings_,
        )
        validate_request_target(
            handler.path,
            handler.resource_settings_,
        )
        validate_headers(
            list(handler.headers.items()),
            handler.resource_settings_,
        )
        retry_after = handler.limiter_.check(
            "public-health"
            if handler.path.startswith("/health")
            else (
                handler.headers.get("authorization")
                or "preauth"
            )
        )
        if retry_after is not None:
            send(handler, 
                ApiResponse(
                    status=429,
                    body={
                        "error": {
                            "code": "TOO_MANY_REQUESTS",
                            "message": "too many requests",
                            "request_id": preflight_request_id,
                            "details": {},
                        }
                    },
                    headers={
                        "x-request-id": preflight_request_id,
                        "retry-after": str(retry_after)
                    },
                )
            )
            handler.semaphore_.release()
            return
        length = int(
            handler.headers.get(
                "content-length",
                "0",
            )
        )
        validate_content_type(
            handler.command,
            length,
            handler.headers.get(
                "content-type"
            ),
        )
    except SecurityRejection as error:
        send(handler, 
            ApiResponse(
                status=error.status,
                body={
                    "error": {
                        "code": error.code,
                        "message": str(error),
                        "request_id": preflight_request_id,
                        "details": {},
                    }
                },
                headers=(
                    {
                        "x-request-id": preflight_request_id,
                        "retry-after": str(error.retry_after),
                    }
                    if error.retry_after
                    else {
                        "x-request-id": preflight_request_id
                    }
                ),
            )
        )
        handler.semaphore_.release()
        return
    except ValueError:
        send(handler, 
            ApiResponse(
                status=400,
                body={
                    "error": {
                        "code": "VALIDATION_ERROR",
                        "message": "Content-Length must be an integer",
                        "request_id": preflight_request_id,
                        "details": {},
                    },
                },
                headers={
                    "x-request-id": preflight_request_id
                },
            )
        )
        handler.semaphore_.release()
        return

    if (
        length < 0
        or length
        > handler.max_request_bytes_
    ):
        send(handler, 
            ApiResponse(
                status=413,
                body={
                    "error": {
                        "code": "PAYLOAD_TOO_LARGE",
                        "message": "request body exceeds the configured limit",
                        "request_id": preflight_request_id,
                        "details": {},
                    },
                },
                headers={
                    "x-request-id": preflight_request_id
                },
            )
        )
        handler.semaphore_.release()
        return

    payload: dict[str, object] = {}

    if length:
        try:
            decoded = handler.rfile.read(
                length
            ).decode("utf-8")

            parsed = json.loads(
                decoded
            )
            validate_json_limits(
                parsed,
                handler.resource_settings_,
            )

        except (
            UnicodeDecodeError,
            json.JSONDecodeError,
        ):
            send(handler, 
                ApiResponse(
                    status=400,
                    body={
                        "error": {
                            "code": "INVALID_JSON",
                            "message": "body must be valid UTF-8 JSON",
                            "request_id": preflight_request_id,
                            "details": {},
                        },
                    },
                    headers={
                        "x-request-id": preflight_request_id
                    },
                )
            )
            handler.semaphore_.release()
            return
        except JsonLimitExceeded:
            send(handler, 
                ApiResponse(
                    status=413,
                    body={
                        "error": {
                            "code": "JSON_LIMIT_EXCEEDED",
                            "message": "JSON exceeds configured limits",
                            "request_id": preflight_request_id,
                            "details": {},
                        }
                    },
                    headers={
                        "x-request-id": preflight_request_id
                    },
                )
            )
            handler.semaphore_.release()
            return

        if not isinstance(
            parsed,
            dict,
        ):
            send(handler, 
                ApiResponse(
                    status=400,
                    body={
                        "error": {
                            "code": "INVALID_JSON",
                            "message": "body must be a JSON object",
                            "request_id": preflight_request_id,
                            "details": {},
                        },
                    },
                    headers={
                        "x-request-id": preflight_request_id
                    },
                )
            )
            handler.semaphore_.release()
            return

        payload = parsed

    raw_path = handler.path.split("?", 1)[0]
    if (
        handler.command == "GET"
        and handler.artifact_db_ is not None
        and _ARTIFACT_DOWNLOAD_RE.match(raw_path)
    ):
        token = raw_path.rsplit("/", 1)[-1]
        serve_artifact_download(handler, 
            token, handler.artifact_db_, handler.artifact_signing_key_
        )
        handler.semaphore_.release()
        return

    request = new_request(
        method=handler.command,
        raw_path=handler.path,
        headers={
            key: value
            for key, value
            in handler.headers.items()
        },
        payload=payload,
    )

    try:
        response = (
            handler.application_.dispatch(
                request
            )
        )
    except Exception:
        print(
            (
                "Unhandled API failure "
                f"request_id="
                f"{request.request_id}"
            ),
            file=sys.stderr,
        )

        response = ApiResponse(
            status=500,
            body={
                "error": {
                    "code": "INTERNAL_ERROR",
                    "message": "request failed",
                    "request_id": request.request_id,
                    "details": {},
                }
            },
            headers={
                "x-request-id": (
                    request.request_id
                )
            },
        )

    send(handler, response)
    handler.observability_.log(
        "INFO",
        "http_request_complete",
        request_id=request.request_id,
        method=handler.command,
        route=safe_route(handler.path),
        status_code=response.status,
        duration_ms=int(
            (time.monotonic() - started)
            * 1000
        ),
        response_bytes=len(
            json.dumps(response.body).encode("utf-8")
        ),
    )
    handler.semaphore_.release()
