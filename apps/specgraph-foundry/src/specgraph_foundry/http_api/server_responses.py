"""Writing responses: JSON, binary, artifact downloads, and headers.

The security and CORS headers live here rather than beside the request checks
because they go on *every* response including error paths, and a header applied
in only some branches is the failure mode this grouping prevents.
"""

from __future__ import annotations

from .server_origins import (
    _ARTIFACT_DOWNLOAD_RE,
    is_origin_allowed,
)
from .database_artifact_storage import verify_artifact_token
from .models import ApiResponse
from .server_handler import is_origin_allowed
import json
import sys
import time
import uuid


def send(
    handler,
    response: ApiResponse,
) -> None:
    encoded = json.dumps(
        response.body,
        indent=2,
        sort_keys=True,
    ).encode("utf-8")

    handler.send_response(
        response.status
    )

    security_headers(handler, )

    origin = handler.headers.get(
        "origin"
    )

    if (
        origin
        and is_origin_allowed(origin, handler.allowed_origins_)
    ):
        cors_headers(handler, origin)

    handler.send_header(
        "content-type",
        (
            "handler.application_/json; "
            "charset=utf-8"
        ),
    )

    handler.send_header(
        "content-length",
        str(len(encoded)),
    )

    for key, value in (
        response.headers.items()
    ):
        handler.send_header(
            key,
            value,
        )

    handler.end_headers()

    if handler.command != "HEAD":
        handler.wfile.write(encoded)


def send_binary(
    handler,
    data: bytes,
    *,
    media_type: str,
    filename: str,
) -> None:
    handler.send_response(200)
    security_headers(handler, )
    origin = handler.headers.get("origin")
    if origin and is_origin_allowed(origin, handler.allowed_origins_):
        cors_headers(handler, origin)
    handler.send_header("content-type", media_type)
    handler.send_header("content-length", str(len(data)))
    handler.send_header(
        "content-disposition",
        f'attachment; filename="{filename}"',
    )
    handler.send_header("cache-control", "private, no-store")
    handler.end_headers()
    if handler.command != "HEAD":
        handler.wfile.write(data)


def serve_artifact_download(
    handler,
    token: str,
    db: Database,
    signing_key: str,
) -> None:
    object_path = verify_artifact_token(token, handler.signing_key_)
    if object_path is None:
        send(handler, 
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
        with handler.db_.connect() as connection:
            row = connection.execute(
                "SELECT data, media_type FROM artifact_blobs WHERE object_path = ?",
                (object_path,),
            ).fetchone()
    except Exception:
        send(handler, 
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
        send(handler, 
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
    send_binary(handler, data, media_type=media_type, filename=filename)


def security_headers(handler) -> None:
    for key, value in security_headers().items():
        handler.send_header(key, value)


def cors_headers(
    handler,
    origin: str,
) -> None:
    handler.send_header(
        "access-control-allow-origin",
        origin,
    )

    handler.send_header(
        "vary",
        "Origin",
    )

    handler.send_header(
        "access-control-allow-methods",
        (
            "GET, POST, PUT, PATCH, "
            "DELETE, OPTIONS"
        ),
    )

    handler.send_header(
        "access-control-allow-headers",
        (
            "Authorization, "
            "Content-Type, "
            "Idempotency-Key, "
            "If-Match, "
            "X-Request-ID"
        ),
    )

    handler.send_header(
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
