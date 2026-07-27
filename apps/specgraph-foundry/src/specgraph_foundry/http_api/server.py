import json
import os
import sys
import time
import threading
import uuid
from http.server import (
    BaseHTTPRequestHandler,
    ThreadingHTTPServer,
)

from ..config import Settings
from ..database import Database
from .auth import SupabaseAuthClient
from .gateway import (
    AuthenticatedApi,
    new_request,
)
from .artifact_storage import (
    ArtifactStorageClient,
    ArtifactStorageSettings,
)
from .durable_exports import DurableExportService
from .models import ApiResponse
from .operation_handlers import OperationHandlerRegistry
from .operations import OperationSettings, OperationStore
from .observability import Observability, safe_route
from .resource_limits import (
    JsonLimitExceeded,
    ResourceLimitSettings,
    validate_json_limits,
)
from .security import (
    RateLimiter,
    SecurityRejection,
    SecuritySettings,
    security_headers,
    validate_content_type,
    validate_headers,
    validate_host,
    validate_request_target,
)
from .source_uploads import (
    SourceUploadService,
    SourceUploadSettings,
)
from .storage import SupabaseStorageClient


DEFAULT_MAX_REQUEST_BYTES = 2 * 1024 * 1024
DEFAULT_MAX_SOURCE_BYTES = 10 * 1024 * 1024
DEFAULT_MAX_ARTIFACT_BYTES = 10 * 1024 * 1024


def parse_allowed_hosts() -> tuple[str, ...]:
    raw = os.environ.get(
        "SPECGRAPH_ALLOWED_HOSTS",
        "127.0.0.1,localhost",
    )
    return tuple(
        item.strip()
        for item in raw.split(",")
        if item.strip()
    )


def parse_allowed_origins() -> set[str]:
    raw = os.environ.get(
        "SPECGRAPH_ALLOWED_ORIGINS",
        "",
    )

    return {
        item.strip()
        for item in raw.split(",")
        if item.strip()
    }


def build_application() -> tuple[
    AuthenticatedApi,
    Settings,
]:
    settings = Settings.from_environment()

    database = Database(
        settings.database_path,
        database_url=settings.database_url,
        owner_id=(
            settings.database_owner_id
        ),
    )

    database.initialize()

    authenticator = SupabaseAuthClient(
        os.environ.get(
            "SUPABASE_URL",
            "",
        ),
        os.environ.get(
            "SUPABASE_ANON_KEY",
            "",
        ),
        timeout_seconds=float(
            os.environ.get(
                "SPECGRAPH_AUTH_TIMEOUT_SECONDS",
                "10",
            )
        ),
    )
    storage_client = SupabaseStorageClient(
        os.environ.get(
            "SUPABASE_URL",
            "",
        ),
        os.environ.get(
            "SUPABASE_ANON_KEY",
            "",
        ),
        timeout_seconds=float(
            os.environ.get(
                "SPECGRAPH_STORAGE_TIMEOUT_SECONDS",
                "10",
            )
        ),
    )

    source_uploads = SourceUploadService(
        database,
        storage_client,
        SourceUploadSettings(
            bucket=os.environ.get(
                "SPECGRAPH_SOURCE_BUCKET",
                "source-documents",
            ),
            upload_url_ttl_seconds=int(
                os.environ.get(
                    "SPECGRAPH_UPLOAD_URL_TTL_SECONDS",
                    "900",
                )
            ),
            max_source_bytes=int(
                os.environ.get(
                    "SPECGRAPH_MAX_SOURCE_BYTES",
                    str(DEFAULT_MAX_SOURCE_BYTES),
                )
            ),
        ),
    )
    durable_exports = DurableExportService(
        database,
        ArtifactStorageClient(
            storage_client,
            ArtifactStorageSettings(
                bucket=os.environ.get(
                    "SPECGRAPH_EXPORT_BUCKET",
                    "export-artifacts",
                ),
                max_artifact_bytes=int(
                    os.environ.get(
                        "SPECGRAPH_ARTIFACT_MAX_BYTES",
                        str(DEFAULT_MAX_ARTIFACT_BYTES),
                    )
                ),
                download_ttl_seconds=int(
                    os.environ.get(
                        "SPECGRAPH_ARTIFACT_DOWNLOAD_TTL_SECONDS",
                        "300",
                    )
                ),
            ),
        ),
    )
    operation_settings = OperationSettings(
        lease_seconds=int(
            os.environ.get(
                "SPECGRAPH_OPERATION_LEASE_SECONDS",
                "60",
            )
        ),
        heartbeat_seconds=int(
            os.environ.get(
                "SPECGRAPH_OPERATION_HEARTBEAT_SECONDS",
                "15",
            )
        ),
        max_attempts=int(
            os.environ.get(
                "SPECGRAPH_OPERATION_MAX_ATTEMPTS",
                "3",
            )
        ),
        retry_base_seconds=int(
            os.environ.get(
                "SPECGRAPH_OPERATION_RETRY_BASE_SECONDS",
                "5",
            )
        ),
        timeout_seconds=int(
            os.environ.get(
                "SPECGRAPH_OPERATION_TIMEOUT_SECONDS",
                "1800",
            )
        ),
        poll_seconds=int(
            os.environ.get(
                "SPECGRAPH_OPERATION_POLL_SECONDS",
                "2",
            )
        ),
    )
    operations = OperationStore(
        database,
        operation_settings,
        cursor_signing_key=os.environ.get(
            "SPECGRAPH_CURSOR_SIGNING_KEY"
        ),
    )
    operation_handlers = OperationHandlerRegistry(
        database,
        durable_exports=durable_exports,
        source_uploads=source_uploads,
        worker_authorization=(
            "Bearer "
            + os.environ.get(
                "SPECGRAPH_WORKER_STORAGE_TOKEN",
                "worker",
            )
        ),
    )

    return (
        AuthenticatedApi(
            database,
            authenticator,
            cursor_signing_key=os.environ.get(
                "SPECGRAPH_CURSOR_SIGNING_KEY"
            ),
            source_uploads=source_uploads,
            durable_exports=durable_exports,
            operations=operations,
            operation_handlers=operation_handlers,
            enforce_mutation_guards=True,
        ),
        settings,
    )


def serve(
    application: AuthenticatedApi,
    host: str,
    port: int,
) -> None:
    allowed_origins = (
        parse_allowed_origins()
    )

    max_request_bytes = int(
        os.environ.get(
            "SPECGRAPH_MAX_REQUEST_BYTES",
            str(DEFAULT_MAX_REQUEST_BYTES),
        )
    )

    if max_request_bytes < 1:
        raise ValueError(
            "SPECGRAPH_MAX_REQUEST_BYTES "
            "must be positive"
        )
    resource_settings = ResourceLimitSettings(
        max_request_target_bytes=int(
            os.environ.get(
                "SPECGRAPH_MAX_REQUEST_TARGET_BYTES",
                "2048",
            )
        ),
        max_header_count=int(
            os.environ.get(
                "SPECGRAPH_MAX_HEADER_COUNT",
                "64",
            )
        ),
        max_header_bytes=int(
            os.environ.get(
                "SPECGRAPH_MAX_HEADER_BYTES",
                "16384",
            )
        ),
        max_json_depth=int(
            os.environ.get(
                "SPECGRAPH_MAX_JSON_DEPTH",
                "32",
            )
        ),
        max_json_items=int(
            os.environ.get(
                "SPECGRAPH_MAX_JSON_ITEMS",
                "10000",
            )
        ),
        max_json_string_bytes=int(
            os.environ.get(
                "SPECGRAPH_MAX_JSON_STRING_BYTES",
                "65536",
            )
        ),
        max_concurrent_requests=int(
            os.environ.get(
                "SPECGRAPH_MAX_CONCURRENT_REQUESTS",
                "32",
            )
        ),
        request_deadline_seconds=int(
            os.environ.get(
                "SPECGRAPH_REQUEST_DEADLINE_SECONDS",
                "25",
            )
        ),
    )
    security_settings = SecuritySettings(
        allowed_hosts=parse_allowed_hosts(),
        rate_limit_enabled=(
            os.environ.get(
                "SPECGRAPH_RATE_LIMIT_ENABLED",
                "true",
            ).casefold()
            == "true"
        ),
        rate_limit_requests=int(
            os.environ.get(
                "SPECGRAPH_RATE_LIMIT_REQUESTS",
                "120",
            )
        ),
        rate_limit_window_seconds=int(
            os.environ.get(
                "SPECGRAPH_RATE_LIMIT_WINDOW_SECONDS",
                "60",
            )
        ),
    )
    limiter = RateLimiter(security_settings)
    semaphore = threading.BoundedSemaphore(
        resource_settings.max_concurrent_requests
    )
    observability = Observability(
        service=os.environ.get(
            "SPECGRAPH_OTEL_SERVICE_NAME",
            "specgraph-foundry-api",
        ),
        enabled=(
            os.environ.get(
                "SPECGRAPH_OTEL_ENABLED",
                "false",
            ).casefold()
            == "true"
        ),
    )

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
                and origin
                not in allowed_origins
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
                and origin
                in allowed_origins
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

    server = ThreadingHTTPServer(
        (host, port),
        Handler,
    )

    server.daemon_threads = True

    print(
        "SpecGraph Foundry authenticated API "
        f"listening on http://{host}:{port}"
    )

    try:
        server.serve_forever()
    except KeyboardInterrupt:
        print(
            "\nStopping SpecGraph Foundry."
        )
    finally:
        server.server_close()


def main() -> int:
    application, settings = (
        build_application()
    )

    serve(
        application,
        settings.host,
        settings.port,
    )

    return 0


if __name__ == "__main__":
    raise SystemExit(main())
