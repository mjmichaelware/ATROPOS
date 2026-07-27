import tempfile
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
from specgraph_foundry.http_api.worker import run_once
from specgraph_foundry.ingestion import IngestionService
from specgraph_foundry.services import ProjectService


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


if __name__ == "__main__":
    unittest.main()
