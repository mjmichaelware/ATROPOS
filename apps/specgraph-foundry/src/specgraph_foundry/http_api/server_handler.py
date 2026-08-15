"""The HTTP request handler.

563 lines of a 697-line function -- a class defined inside `serve` so it could
close over the application, database, limiter, signing keys and settings built
there.

Kept as a factory rather than a module-level class because that closure is the
design: every handler instance must see exactly the objects its own server was
built with, and hoisting them to module state would make two servers in one
process share them.
"""

from __future__ import annotations

import re
from typing import TYPE_CHECKING
from .database_artifact_storage import verify_artifact_token
from .gateway import new_request
from .models import ApiResponse
from .observability import safe_route
from .resource_limits import JsonLimitExceeded
from .resource_limits import validate_json_limits
from .security import SecurityRejection
from .security import security_headers
from .security import validate_content_type
from .security import validate_headers
from .security import validate_host
from .security import validate_request_target
import json
import sys
import time
import uuid
from http.server import BaseHTTPRequestHandler



_VERCEL_PROJECT_DEPLOYMENT_ORIGIN = re.compile(
    r"^https://specgraph-foundry-[a-z0-9]+-mjmichaelwares-projects\.vercel\.app$"
)


if TYPE_CHECKING:
    from ..database import Database

_ARTIFACT_DOWNLOAD_RE = re.compile(
    r"^/v1/artifact-downloads/[A-Za-z0-9_\-]+\.[A-Za-z0-9_\-]+$"
)


def is_origin_allowed(origin: str, allowed_origins: set[str]) -> bool:
    if origin in allowed_origins:
        return True
    return bool(_VERCEL_PROJECT_DEPLOYMENT_ORIGIN.match(origin))



def build_handler(
    allowed_origins,
    application,
    artifact_db,
    artifact_signing_key,
    db,
    limiter,
    max_request_bytes,
    observability,
    resource_settings,
    security_settings,
    semaphore,
    signing_key,
):
    """Returns a handler class bound to this server's collaborators."""
    class Handler(BaseHTTPRequestHandler):
        server_version = (
            "SpecGraphFoundry"
        )
        sys_version = ""

        def do_GET(self) -> None:
            self._handle()

        def do_POST(self) -> None:
            self._handle()

        def do_PUT(self) -> None:
            self._handle()

        def do_PATCH(self) -> None:
            self._handle()

        def do_DELETE(self) -> None:
            self._handle()

        def do_OPTIONS(self) -> None:
            origin = self.headers.get(
                "origin"
            )

            if (
                origin
                and not is_origin_allowed(origin, allowed_origins)
            ):
                self._send(
                    ApiResponse(
                        status=403,
                        body={
                            "error": (
                                "ORIGIN_NOT_ALLOWED"
                            ),
                            "message": (
                                "request origin "
                                "is not allowed"
                            ),
                        },
                    )
                )
                return

            self.send_response(204)
            self._security_headers()

            if origin:
                self._cors_headers(origin)

            self.send_header(
                "content-length",
                "0",
            )
            self.end_headers()

        def _handle(self) -> None:
            started = time.monotonic()
            preflight_request_id = str(
                uuid.uuid4()
            )
            acquired = semaphore.acquire(
                blocking=False
            )
            if not acquired:
                self._send(
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
                    self.headers.get("host"),
                    security_settings,
                )
                validate_request_target(
                    self.path,
                    resource_settings,
                )
                validate_headers(
                    list(self.headers.items()),
                    resource_settings,
                )
                retry_after = limiter.check(
                    "public-health"
                    if self.path.startswith("/health")
                    else (
                        self.headers.get("authorization")
                        or "preauth"
                    )
                )
                if retry_after is not None:
                    self._send(
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
                    semaphore.release()
                    return
                length = int(
                    self.headers.get(
                        "content-length",
                        "0",
                    )
                )
                validate_content_type(
                    self.command,
                    length,
                    self.headers.get(
                        "content-type"
                    ),
                )
            except SecurityRejection as error:
                self._send(
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
                semaphore.release()
                return
            except ValueError:
                self._send(
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
                semaphore.release()
                return

            if (
                length < 0
                or length
                > max_request_bytes
            ):
                self._send(
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
                semaphore.release()
                return

            payload: dict[str, object] = {}

            if length:
                try:
                    decoded = self.rfile.read(
                        length
                    ).decode("utf-8")

                    parsed = json.loads(
                        decoded
                    )
                    validate_json_limits(
                        parsed,
                        resource_settings,
                    )

                except (
                    UnicodeDecodeError,
                    json.JSONDecodeError,
                ):
                    self._send(
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
                    semaphore.release()
                    return
                except JsonLimitExceeded:
                    self._send(
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
                    semaphore.release()
                    return

                if not isinstance(
                    parsed,
                    dict,
                ):
                    self._send(
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
                    semaphore.release()
                    return

                payload = parsed

            raw_path = self.path.split("?", 1)[0]
            if (
                self.command == "GET"
                and artifact_db is not None
                and _ARTIFACT_DOWNLOAD_RE.match(raw_path)
            ):
                token = raw_path.rsplit("/", 1)[-1]
                self._serve_artifact_download(
                    token, artifact_db, artifact_signing_key
                )
                semaphore.release()
                return

            request = new_request(
                method=self.command,
                raw_path=self.path,
                headers={
                    key: value
                    for key, value
                    in self.headers.items()
                },
                payload=payload,
            )

            try:
                response = (
                    application.dispatch(
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

            self._send(response)
            observability.log(
                "INFO",
                "http_request_complete",
                request_id=request.request_id,
                method=self.command,
                route=safe_route(self.path),
                status_code=response.status,
                duration_ms=int(
                    (time.monotonic() - started)
                    * 1000
                ),
                response_bytes=len(
                    json.dumps(response.body).encode("utf-8")
                ),
            )
            semaphore.release()

        def _send(
            self,
            response: ApiResponse,
        ) -> None:
            encoded = json.dumps(
                response.body,
                indent=2,
                sort_keys=True,
            ).encode("utf-8")

            self.send_response(
                response.status
            )

            self._security_headers()

            origin = self.headers.get(
                "origin"
            )

            if (
                origin
                and is_origin_allowed(origin, allowed_origins)
            ):
                self._cors_headers(origin)

            self.send_header(
                "content-type",
                (
                    "application/json; "
                    "charset=utf-8"
                ),
            )

            self.send_header(
                "content-length",
                str(len(encoded)),
            )

            for key, value in (
                response.headers.items()
            ):
                self.send_header(
                    key,
                    value,
                )

            self.end_headers()

            if self.command != "HEAD":
                self.wfile.write(encoded)

        def _serve_artifact_download(
            self,
            token: str,
            db: Database,
            signing_key: str,
        ) -> None:
            object_path = verify_artifact_token(token, signing_key)
            if object_path is None:
                self._send(
                    ApiResponse(
                        status=404,
                        body={
                            "error": {
                                "code": "NOT_FOUND",
                                "message": "artifact not found or token expired",
                                "details": {},
                            }
                        },
                    )
                )
                return

            try:
                with db.connect() as connection:
                    row = connection.execute(
                        "SELECT data, media_type FROM artifact_blobs WHERE object_path = ?",
                        (object_path,),
                    ).fetchone()
            except Exception:
                self._send(
                    ApiResponse(
                        status=503,
                        body={
                            "error": {
                                "code": "SERVICE_UNAVAILABLE",
                                "message": "artifact storage unavailable",
                                "details": {},
                            }
                        },
                    )
                )
                return

            if row is None:
                self._send(
                    ApiResponse(
                        status=404,
                        body={
                            "error": {
                                "code": "NOT_FOUND",
                                "message": "artifact not found",
                                "details": {},
                            }
                        },
                    )
                )
                return

            raw = row["data"]
            data = bytes(raw) if isinstance(raw, (memoryview, bytearray)) else raw
            media_type = str(row["media_type"])
            filename = object_path.rsplit("/", 1)[-1]
            self._send_binary(data, media_type=media_type, filename=filename)

        def _send_binary(
            self,
            data: bytes,
            *,
            media_type: str,
            filename: str,
        ) -> None:
            self.send_response(200)
            self._security_headers()
            origin = self.headers.get("origin")
            if origin and is_origin_allowed(origin, allowed_origins):
                self._cors_headers(origin)
            self.send_header("content-type", media_type)
            self.send_header("content-length", str(len(data)))
            self.send_header(
                "content-disposition",
                f'attachment; filename="{filename}"',
            )
            self.send_header("cache-control", "private, no-store")
            self.end_headers()
            if self.command != "HEAD":
                self.wfile.write(data)

        def _security_headers(self) -> None:
            for key, value in security_headers().items():
                self.send_header(key, value)

        def _cors_headers(
            self,
            origin: str,
        ) -> None:
            self.send_header(
                "access-control-allow-origin",
                origin,
            )

            self.send_header(
                "vary",
                "Origin",
            )

            self.send_header(
                "access-control-allow-methods",
                (
                    "GET, POST, PUT, PATCH, "
                    "DELETE, OPTIONS"
                ),
            )

            self.send_header(
                "access-control-allow-headers",
                (
                    "Authorization, "
                    "Content-Type, "
                    "Idempotency-Key, "
                    "If-Match, "
                    "X-Request-ID"
                ),
            )

            self.send_header(
                "access-control-expose-headers",
                (
                    "ETag, "
                    "Idempotency-Replayed, "
                    "X-Request-ID, "
                    "X-Page-Limit, "
                    "X-Page-Count, "
                    "X-Has-More, "
                    "X-Next-Cursor, "
                    "Location"
                ),
            )

        def log_message(
            self,
            format_value: str,
            *arguments: object,
        ) -> None:
            return None

    return Handler
