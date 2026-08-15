from .server_handler import build_handler
from .application import build_application  # noqa: F401
from .application import DEFAULT_MAX_ARTIFACT_BYTES, DEFAULT_MAX_SOURCE_BYTES, _derive_api_base_url
from .server_origins import (  # re-exported: server is the module
    _ARTIFACT_DOWNLOAD_RE,     # callers import these from.
    is_origin_allowed,
)
import json
import os
import re
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
    ArtifactStorageSettings,
)
from .database_artifact_storage import (
    DatabaseArtifactStorageClient,
    verify_artifact_token,
)
from .worker_trigger import CloudRunWorkerTrigger
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




# Vercel mints a brand-new per-deployment URL
# (specgraph-foundry-<hash>-mjmichaelwares-projects.vercel.app) on every
# deploy, distinct from the stable production alias. An exact-match
# allowlist would reject every deployment URl except the one alias that
# was configured, forever, regardless of how many times a client retries
# or clears cache - the browser is simply on a different origin the
# server has never been told about. Auth on this API is bearer-token
# only (no cookies are sent cross-origin), so allowing this project's own
# deployment subdomains carries the same risk as allowing the alias
# itself: an attacker would still need to have already obtained a valid
# token, which this origin check does not grant.






def serve(
    application: AuthenticatedApi,
    host: str,
    port: int,
    artifact_db: Database | None = None,
    artifact_signing_key: str = "",
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

    Handler = build_handler(
        allowed_origins=allowed_origins,
        application=application,
        artifact_db=artifact_db,
        artifact_signing_key=artifact_signing_key,
        db=db,
        limiter=limiter,
        max_request_bytes=max_request_bytes,
        observability=observability,
        resource_settings=resource_settings,
        security_settings=security_settings,
        semaphore=semaphore,
        signing_key=signing_key,
    )


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
    application, settings, artifact_db, signing_key = (
        build_application()
    )

    serve(
        application,
        settings.host,
        settings.port,
        artifact_db=artifact_db,
        artifact_signing_key=signing_key,
    )

    return 0


if __name__ == "__main__":
    raise SystemExit(main())
