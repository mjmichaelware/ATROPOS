"""Assembling the API application from settings.

Wires every service the gateway needs and hands back one object. Separate from
:mod:`server` because building the application and running a socket server are
different jobs -- tests build an application without ever opening a port.
"""

from __future__ import annotations

DEFAULT_MAX_SOURCE_BYTES = 10 * 1024 * 1024


DEFAULT_MAX_ARTIFACT_BYTES = 10 * 1024 * 1024


def _derive_api_base_url() -> str:
    for host in parse_allowed_hosts():
        if host not in ("127.0.0.1", "localhost"):
            return f"https://{host}"
    port = os.environ.get("SPECGRAPH_PORT", "8080")
    return f"http://127.0.0.1:{port}"

from ..database import Database
from ..config import Settings
from .artifact_storage import ArtifactStorageSettings
from .auth import SupabaseAuthClient
from .database_artifact_storage import DatabaseArtifactStorageClient
from .durable_exports import DurableExportService
from .gateway import AuthenticatedApi
from .operation_handlers import OperationHandlerRegistry
from .operations import OperationSettings
from .operations import OperationStore
from .source_upload_errors import SourceUploadSettings
from .source_uploads import SourceUploadService
from .storage import SupabaseStorageClient
from .worker_trigger import CloudRunWorkerTrigger
import os
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
