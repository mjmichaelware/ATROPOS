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


def _derive_api_base_url() -> str:
    for host in parse_allowed_hosts():
        if host not in ("127.0.0.1", "localhost"):
            return f"https://{host}"
    port = os.environ.get("SPECGRAPH_PORT", "8080")
    return f"http://127.0.0.1:{port}"


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




def build_application() -> tuple[
    AuthenticatedApi,
    Settings,
    Database,
    str,
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
    _signing_key = os.environ.get("SPECGRAPH_CURSOR_SIGNING_KEY", "")
    durable_exports = DurableExportService(
        database,
        DatabaseArtifactStorageClient(
            database,
            ArtifactStorageSettings(
                bucket="database",
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
            api_base_url=_derive_api_base_url(),
            signing_key=_signing_key,
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

    worker_trigger: CloudRunWorkerTrigger | None = None
    _worker_project = os.environ.get("SPECGRAPH_GCP_PROJECT_ID", "")
    _worker_region = os.environ.get("SPECGRAPH_GCP_REGION", "")
    _worker_job = os.environ.get("SPECGRAPH_WORKER_JOB_NAME", "")
    if _worker_project and _worker_region and _worker_job:
        worker_trigger = CloudRunWorkerTrigger(
            project_id=_worker_project,
            region=_worker_region,
            job_name=_worker_job,
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
            worker_trigger=worker_trigger,
            enforce_mutation_guards=True,
        ),
        settings,
        database,
        _signing_key,
    )


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
