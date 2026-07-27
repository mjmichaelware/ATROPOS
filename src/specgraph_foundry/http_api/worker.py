from __future__ import annotations

import argparse
import logging
import os
import signal
import sys
import threading
import time

logger = logging.getLogger(__name__)

from ..config import Settings
from ..database import Database
from .artifact_storage import ArtifactStorageSettings
from .database_artifact_storage import DatabaseArtifactStorageClient
from .durable_exports import DurableExportService
from .operation_handlers import OperationHandlerRegistry, classify_error
from .operations import (
    OperationSettings,
    OperationStore,
    WorkerLeaseLost,
    default_worker_id,
)
from .source_uploads import SourceUploadService, SourceUploadSettings
from .storage import SupabaseStorageClient


STOP_REQUESTED = False


def _stop(_signum, _frame) -> None:
    global STOP_REQUESTED
    STOP_REQUESTED = True


def parse_args(argv: list[str]) -> argparse.Namespace:
    parser = argparse.ArgumentParser(
        description="Run the SpecGraph Foundry operation worker."
    )
    parser.add_argument("--once", action="store_true")
    parser.add_argument("--drain", action="store_true")
    parser.add_argument("--max-operations", type=int, default=100)
    return parser.parse_args(argv)


def operation_settings_from_env() -> OperationSettings:
    return OperationSettings(
        lease_seconds=int(os.environ.get("SPECGRAPH_OPERATION_LEASE_SECONDS", "60")),
        heartbeat_seconds=int(os.environ.get("SPECGRAPH_OPERATION_HEARTBEAT_SECONDS", "15")),
        max_attempts=int(os.environ.get("SPECGRAPH_OPERATION_MAX_ATTEMPTS", "3")),
        retry_base_seconds=int(os.environ.get("SPECGRAPH_OPERATION_RETRY_BASE_SECONDS", "5")),
        timeout_seconds=int(os.environ.get("SPECGRAPH_OPERATION_TIMEOUT_SECONDS", "1800")),
        poll_seconds=int(os.environ.get("SPECGRAPH_OPERATION_POLL_SECONDS", "2")),
    )


def build_worker_components() -> tuple[
    OperationStore,
    OperationHandlerRegistry,
    OperationSettings,
    str,
]:
    settings = Settings.from_environment()
    database = Database(
        settings.database_path,
        database_url=settings.database_url,
        owner_id=settings.database_owner_id,
    )
    database.initialize()
    operation_settings = operation_settings_from_env()
    worker_id = os.environ.get("SPECGRAPH_WORKER_ID") or default_worker_id()
    storage = SupabaseStorageClient(
        os.environ.get("SUPABASE_URL", ""),
        os.environ.get("SUPABASE_ANON_KEY", ""),
        timeout_seconds=float(os.environ.get("SPECGRAPH_STORAGE_TIMEOUT_SECONDS", "30")),
    )
    source_uploads = SourceUploadService(
        database,
        storage,
        SourceUploadSettings(
            bucket=os.environ.get("SPECGRAPH_SOURCE_BUCKET", "source-documents"),
            upload_url_ttl_seconds=int(os.environ.get("SPECGRAPH_UPLOAD_URL_TTL_SECONDS", "900")),
            max_source_bytes=int(os.environ.get("SPECGRAPH_MAX_SOURCE_BYTES", str(10 * 1024 * 1024))),
        ),
    )
    durable_exports = DurableExportService(
        database,
        DatabaseArtifactStorageClient(
            database,
            ArtifactStorageSettings(
                bucket="database",
                max_artifact_bytes=int(os.environ.get("SPECGRAPH_ARTIFACT_MAX_BYTES", str(10 * 1024 * 1024))),
                download_ttl_seconds=int(os.environ.get("SPECGRAPH_ARTIFACT_DOWNLOAD_TTL_SECONDS", "300")),
            ),
            api_base_url=os.environ.get("SPECGRAPH_API_BASE_URL", ""),
            signing_key=os.environ.get("SPECGRAPH_CURSOR_SIGNING_KEY", ""),
        ),
    )
    store = OperationStore(
        database,
        operation_settings,
        cursor_signing_key=os.environ.get("SPECGRAPH_CURSOR_SIGNING_KEY"),
    )
    registry = OperationHandlerRegistry(
        database,
        durable_exports=durable_exports,
        source_uploads=source_uploads,
        worker_authorization=(
            "Bearer "
            + os.environ.get("SPECGRAPH_WORKER_STORAGE_TOKEN", "worker")
        ),
    )
    return store, registry, operation_settings, worker_id


def run_once(
    store: OperationStore,
    registry: OperationHandlerRegistry,
    worker_id: str,
    settings: OperationSettings | None = None,
) -> bool:
    if settings is None:
        settings = OperationSettings()
    lease = store.claim(worker_id=worker_id)
    if lease is None:
        return False

    # Keep the lease alive in a background thread so that long-running
    # artifact uploads/downloads don't expire the lease between checkpoints.
    done = threading.Event()

    def _heartbeat() -> None:
        while not done.wait(settings.heartbeat_seconds):
            try:
                store.heartbeat(lease)
            except WorkerLeaseLost:
                # Lease definitively gone — stop trying.
                break
            except Exception:
                # Transient error (network hiccup, DB timeout, etc.).
                # Keep the loop alive so the next interval gets another try;
                # the lease window is wide enough to absorb a few failures.
                pass

    heartbeat = threading.Thread(target=_heartbeat, daemon=True)
    heartbeat.start()

    try:
        store.start(lease, phase="starting", total=1)
        result = registry.run(store, lease)
        store.succeed(lease, result=result)
    except WorkerLeaseLost:
        pass
    except Exception as error:
        code, message, retryable = classify_error(error)
        logger.error(
            "operation handler raised %s: %s (code=%s retryable=%s)",
            type(error).__name__,
            error,
            code,
            retryable,
            exc_info=True,
        )
        try:
            if code == "OPERATION_CANCELLED":
                store.mark_cancelled(lease)
            else:
                store.fail(
                    lease,
                    code=code,
                    message=message,
                    retryable=retryable,
                )
        except Exception:
            # DB unavailable when recording the failure; recover_expired
            # will reset the operation once the lease window closes.
            pass
    finally:
        done.set()

    return True


def main(argv: list[str] | None = None) -> int:
    args = parse_args(argv or sys.argv[1:])
    signal.signal(signal.SIGTERM, _stop)
    signal.signal(signal.SIGINT, _stop)
    store, registry, settings, worker_id = build_worker_components()

    processed = 0
    while not STOP_REQUESTED:
        claimed = run_once(store, registry, worker_id, settings)
        if claimed:
            processed += 1
        if args.once:
            break
        if args.drain and not claimed:
            break
        if args.drain and processed >= max(1, args.max_operations):
            break
        if not claimed:
            time.sleep(settings.poll_seconds)
    return 0


if __name__ == "__main__":
    raise SystemExit(main())
