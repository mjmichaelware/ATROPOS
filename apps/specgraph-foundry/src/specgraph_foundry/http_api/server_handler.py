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

from .server_origins import (
    _ARTIFACT_DOWNLOAD_RE,
    is_origin_allowed,
)
from .server_request_handling import handle
from .server_responses import cors_headers, security_headers, send, send_binary, serve_artifact_download

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





if TYPE_CHECKING:
    from ..database import Database






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
        # The closure, bound onto the class so the split request and
        # response modules reach exactly this server's collaborators.
        allowed_origins_ = allowed_origins
        application_ = application
        artifact_db_ = artifact_db
        artifact_signing_key_ = artifact_signing_key
        db_ = db
        limiter_ = limiter
        max_request_bytes_ = max_request_bytes
        observability_ = observability
        resource_settings_ = resource_settings
        security_settings_ = security_settings
        semaphore_ = semaphore
        signing_key_ = signing_key

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
            """Delegates to :func:`server_request_handling.handle`."""
            return handle(
                self,
            )


        def _send(
            self,
            response: ApiResponse,
        ) -> None:
            """Delegates to :func:`server_responses.send`."""
            return send(
                self,
                response,
            )


        def _serve_artifact_download(
            self,
            token: str,
            db: Database,
            signing_key: str,
        ) -> None:
            """Delegates to :func:`server_responses.serve_artifact_download`."""
            return serve_artifact_download(
                self,
                token,
                db,
                signing_key,
            )


        def _send_binary(
            self,
            data: bytes,
            *,
            media_type: str,
            filename: str,
        ) -> None:
            """Delegates to :func:`server_responses.send_binary`."""
            return send_binary(
                self,
                data,
                media_type=media_type,
                filename=filename,
            )


        def _security_headers(self) -> None:
            """Delegates to :func:`server_responses.security_headers`."""
            return security_headers(
                self,
            )


        def _cors_headers(
            self,
            origin: str,
        ) -> None:
            """Delegates to :func:`server_responses.cors_headers`."""
            return cors_headers(
                self,
                origin,
            )


        def log_message(
            self,
            format_value: str,
            *arguments: object,
        ) -> None:
            return None

    return Handler
