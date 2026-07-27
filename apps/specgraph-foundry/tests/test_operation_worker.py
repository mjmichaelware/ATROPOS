import base64
import hashlib
import json
import tempfile
import time
import unittest
import uuid
from pathlib import Path

from specgraph_foundry.database import Database
from specgraph_foundry.http_api.operation_handlers import OperationHandlerRegistry
from specgraph_foundry.http_api.operations import (
    OperationSettings,
    OperationStore,
    WorkerLeaseLost,
)
from specgraph_foundry.http_api.source_uploads import (
    SourceUploadService,
    SourceUploadSettings,
)
from specgraph_foundry.http_api.storage import SupabaseStorageClient
from specgraph_foundry.http_api.worker import run_once
from specgraph_foundry.ingestion import IngestionService
from specgraph_foundry.services import ProjectService
from tests.test_source_uploads import FakeStorageTransport


class OperationWorkerTest(unittest.TestCase):
    def setUp(self) -> None:
        self.temp = tempfile.TemporaryDirectory()
        self.database = Database(Path(self.temp.name) / "worker.sqlite3")
        self.database.initialize()
        self.owner_id = str(uuid.uuid4())
        self.operations = OperationStore(
            self.database,
            OperationSettings(lease_seconds=15, heartbeat_seconds=5, timeout_seconds=30),
            cursor_signing_key="x" * 32,
        )
        self.registry = OperationHandlerRegistry(self.database)
        project = ProjectService(self.database).create("worker", "Worker")
        self.project_id = str(project["id"])
        document = IngestionService(self.database).ingest_text(
            self.project_id,
            "Authority",
            "The worker must extract atoms.\n",
            chunk_bytes=64,
        )
        self.document_id = str(document["id"])

    def tearDown(self) -> None:
        self.temp.cleanup()

    def submit(self, request=None):
        return self.operations.submit(
            owner_id=self.owner_id,
            project_id=self.project_id,
            operation_type="extract_document_atoms",
            request=request
            or {
                "path_params": {"document_id": self.document_id},
                "payload": {},
            },
        )

    def test_atomic_claim_and_hashed_token(self) -> None:
        self.submit()
        first = self.operations.claim(worker_id="worker-a")
        second = self.operations.claim(worker_id="worker-b")
        self.assertIsNotNone(first)
        self.assertIsNone(second)
        with self.database.connect() as connection:
            row = connection.execute("SELECT * FROM operations").fetchone()
        self.assertNotEqual(row["lease_token_hash"], first.lease_token)
        self.assertEqual(len(row["lease_token_hash"]), 64)

    def test_wrong_lease_cannot_heartbeat_or_complete(self) -> None:
        self.submit()
        lease = self.operations.claim(worker_id="worker-a")
        bad = type(lease)(
            operation=lease.operation,
            worker_id="worker-b",
            lease_token=lease.lease_token,
        )
        with self.assertRaises(WorkerLeaseLost):
            self.operations.heartbeat(bad)

    def test_worker_success_executes_owner_scoped_handler_once(self) -> None:
        self.submit()
        self.assertTrue(run_once(self.operations, self.registry, "worker-a"))
        with self.database.connect() as connection:
            operation = connection.execute("SELECT * FROM operations").fetchone()
            atom_count = connection.execute("SELECT COUNT(*) AS value FROM atoms").fetchone()["value"]
        self.assertEqual(operation["state"], "SUCCEEDED")
        self.assertEqual(atom_count, 1)
        self.assertFalse(run_once(self.operations, self.registry, "worker-a"))

    def test_nonretryable_failure_is_terminal(self) -> None:
        self.submit({"path_params": {"document_id": str(uuid.uuid4())}, "payload": {}})
        self.assertTrue(run_once(self.operations, self.registry, "worker-a"))
        with self.database.connect() as connection:
            row = connection.execute("SELECT * FROM operations").fetchone()
        self.assertEqual(row["state"], "FAILED")
        self.assertEqual(row["error_code"], "NOT_FOUND")

    def test_cancelled_queued_operation_is_not_claimed(self) -> None:
        operation = self.submit()
        self.operations.cancel(owner_id=self.owner_id, operation_id=operation["id"])
        self.assertIsNone(self.operations.claim(worker_id="worker-a"))
        with self.database.connect() as connection:
            row = connection.execute("SELECT * FROM operations").fetchone()
        self.assertEqual(row["state"], "CANCELLED")

    def test_progress_renews_the_lease_past_its_original_window(self) -> None:
        # Regression test: heartbeat()/progress() previously updated only
        # heartbeat_at (an observability timestamp), never
        # lease_expires_at (the field _leased_update() actually enforces).
        # A handler whose real work outlives the original lease window -
        # e.g. export_plan uploading many artifacts to Supabase Storage -
        # would hit WorkerLeaseLost on its next checkpoint call no matter
        # how many times it had already heartbeat.
        self.submit()
        lease = self.operations.claim(worker_id="worker-a")
        self.operations.start(lease, phase="starting", total=3)
        # Shrink the lease to simulate only ~1 real second remaining,
        # well inside the configured 15s minimum - the fix must extend it
        # a fresh full window from here, not leave it capped at this
        # shrunk value.
        with self.database.connect() as connection:
            connection.execute(
                "UPDATE operations SET lease_expires_at = datetime('now', '+1 second') WHERE id = ?",
                (lease.operation["id"],),
            )
        self.operations.progress(lease, phase="working", current=1, total=3)
        time.sleep(1.5)
        # If progress() above had not renewed lease_expires_at, this call
        # would now raise WorkerLeaseLost - the shrunk 1-second window
        # from before has long since passed.
        self.operations.progress(lease, phase="working", current=2, total=3)

    def test_lease_actually_expires_without_renewal(self) -> None:
        # Companion to the test above: proves the expiry check itself
        # still works (this fix must not make leases immortal) by
        # shrinking lease_expires_at directly and confirming the very
        # next call is rejected.
        self.submit()
        lease = self.operations.claim(worker_id="worker-a")
        self.operations.start(lease, phase="starting", total=1)
        with self.database.connect() as connection:
            connection.execute(
                "UPDATE operations SET lease_expires_at = datetime('now', '-1 second') WHERE id = ?",
                (lease.operation["id"],),
            )
        with self.assertRaises(WorkerLeaseLost):
            self.operations.heartbeat(lease)


class FinalizeSourceUploadOperationTest(unittest.TestCase):
    # finalize_source_upload is registered in ASYNC_OPERATION_TYPES and is
    # dispatched through this exact operations/worker path in every real
    # deployment - this file previously had zero coverage of that, which is
    # how a worker handler that silently dropped the client-provided bytes
    # (falling back to Supabase Storage's broken authenticated download)
    # went unnoticed.
    def setUp(self) -> None:
        self.temp = tempfile.TemporaryDirectory()
        self.database = Database(Path(self.temp.name) / "finalize-op.sqlite3")
        self.database.initialize()
        self.owner_id = str(uuid.uuid4())
        self.operations = OperationStore(
            self.database,
            OperationSettings(lease_seconds=15, heartbeat_seconds=5, timeout_seconds=30),
            cursor_signing_key="x" * 32,
        )
        self.transport = FakeStorageTransport("https://example.supabase.co")
        self.source_uploads = SourceUploadService(
            self.database,
            SupabaseStorageClient(
                "https://example.supabase.co",
                "anon-key",
                timeout_seconds=5,
                opener=self.transport,
            ),
            SourceUploadSettings(
                bucket="source-documents",
                upload_url_ttl_seconds=900,
                max_source_bytes=4096,
            ),
            sleep=lambda _seconds: None,
        )
        self.registry = OperationHandlerRegistry(
            self.database,
            source_uploads=self.source_uploads,
        )
        project = ProjectService(self.database).create("finalize-op", "Finalize Op")
        self.project_id = str(project["id"])

    def tearDown(self) -> None:
        self.temp.cleanup()

    def test_finalize_operation_uses_client_provided_bytes_without_touching_storage(
        self,
    ) -> None:
        body = b"authority delivered via the worker\n"
        digest = hashlib.sha256(body).hexdigest()
        upload_id = str(uuid.uuid4())
        intent = self.source_uploads.create_intent(
            owner_id=self.owner_id,
            authorization="Bearer valid",
            project_id=self.project_id,
            upload_id=upload_id,
            filename="worker.txt",
            media_type="text/plain",
            byte_size=len(body),
            sha256=digest,
        )
        # Never store_object() and force every download to fail, proving the
        # worker's handler uses the client-provided bytes, not storage.
        self.transport.fail_downloads_before_success = 999

        self.operations.submit(
            owner_id=self.owner_id,
            project_id=self.project_id,
            operation_type="finalize_source_upload",
            request={
                "path_params": {"upload_id": intent["id"]},
                "payload": {
                    "raw_base64": base64.b64encode(body).decode("ascii"),
                },
            },
        )

        self.assertTrue(
            run_once(self.operations, self.registry, "worker-a")
        )

        with self.database.connect() as connection:
            operation = connection.execute(
                "SELECT * FROM operations"
            ).fetchone()

        self.assertEqual(operation["state"], "SUCCEEDED")
        result = json.loads(operation["result_json"])
        self.assertEqual(result["status"], "FINALIZED")

        reconstructed = IngestionService(
            self.database
        ).reconstruct(result["document_id"])
        self.assertEqual(reconstructed, body)

    def test_finalize_operation_without_client_bytes_falls_back_to_storage(
        self,
    ) -> None:
        body = b"authority via storage fallback\n"
        digest = hashlib.sha256(body).hexdigest()
        upload_id = str(uuid.uuid4())
        intent = self.source_uploads.create_intent(
            owner_id=self.owner_id,
            authorization="Bearer valid",
            project_id=self.project_id,
            upload_id=upload_id,
            filename="fallback.txt",
            media_type="text/plain",
            byte_size=len(body),
            sha256=digest,
        )
        self.transport.store_object(
            "source-documents",
            str(intent["object_path"]),
            body,
            "text/plain",
        )

        self.operations.submit(
            owner_id=self.owner_id,
            project_id=self.project_id,
            operation_type="finalize_source_upload",
            request={
                "path_params": {"upload_id": intent["id"]},
                "payload": {},
            },
        )

        self.assertTrue(
            run_once(self.operations, self.registry, "worker-a")
        )

        with self.database.connect() as connection:
            operation = connection.execute(
                "SELECT * FROM operations"
            ).fetchone()

        self.assertEqual(operation["state"], "SUCCEEDED")
        result = json.loads(operation["result_json"])
        self.assertEqual(result["status"], "FINALIZED")


if __name__ == "__main__":
    unittest.main()
