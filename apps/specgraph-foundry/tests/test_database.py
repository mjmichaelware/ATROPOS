import sqlite3
import tempfile
import unittest
import warnings
from pathlib import Path

from specgraph_foundry.database import Database


class DatabaseConnectionTest(unittest.TestCase):
    def setUp(self) -> None:
        self.temp = tempfile.TemporaryDirectory()
        self.database = Database(
            Path(self.temp.name) / "test.sqlite3"
        )
        self.database.initialize()

    def tearDown(self) -> None:
        self.temp.cleanup()

    def test_context_manager_closes_connection(
        self,
    ) -> None:
        with self.database.connect() as connection:
            result = connection.execute(
                "SELECT 1"
            ).fetchone()[0]

        self.assertEqual(result, 1)

        with self.assertRaises(
            sqlite3.ProgrammingError
        ):
            connection.execute("SELECT 1")

    def test_repeated_connections_do_not_warn(
        self,
    ) -> None:
        with warnings.catch_warnings():
            warnings.simplefilter(
                "error",
                ResourceWarning,
            )

            for _ in range(100):
                with self.database.connect() as connection:
                    connection.execute(
                        "SELECT 1"
                    ).fetchone()


class OperationsFingerprintMigrationTest(unittest.TestCase):
    # A pre-existing local/offline SQLite database created before the
    # active-state-only fingerprint fix would still have the old
    # table-level UNIQUE(owner_id, operation_type, fingerprint) baked in
    # as a hidden sqlite_autoindex, since CREATE TABLE IF NOT EXISTS is a
    # no-op against a table that already exists. Without a real migration,
    # initialize() alone would leave that old constraint in place and the
    # freeze/500 this fix targets would persist for exactly the offline
    # users it matters most for. This drives Database.initialize() against
    # a hand-built pre-fix schema to prove the migration actually runs.
    OLD_SCHEMA = """
        CREATE TABLE IF NOT EXISTS projects (
            id TEXT PRIMARY KEY,
            slug TEXT NOT NULL UNIQUE,
            name TEXT NOT NULL,
            description TEXT NOT NULL DEFAULT '',
            created_at TEXT NOT NULL
        );

        CREATE TABLE IF NOT EXISTS operations (
            id TEXT PRIMARY KEY,
            owner_id TEXT NOT NULL,
            project_id TEXT NOT NULL
                REFERENCES projects(id) ON DELETE CASCADE,
            operation_type TEXT NOT NULL,
            fingerprint TEXT NOT NULL,
            state TEXT NOT NULL,
            phase TEXT NOT NULL,
            progress_current INTEGER NOT NULL DEFAULT 0,
            progress_total INTEGER NOT NULL DEFAULT 1,
            attempt_count INTEGER NOT NULL DEFAULT 0,
            max_attempts INTEGER NOT NULL,
            worker_id TEXT,
            lease_token_hash TEXT,
            lease_expires_at TEXT,
            heartbeat_at TEXT,
            next_attempt_at TEXT NOT NULL,
            cancel_requested_at TEXT,
            started_at TEXT,
            finished_at TEXT,
            timeout_at TEXT NOT NULL,
            request_json TEXT NOT NULL,
            result_json TEXT,
            error_code TEXT,
            error_message TEXT,
            created_at TEXT NOT NULL,
            updated_at TEXT NOT NULL,
            UNIQUE(owner_id, operation_type, fingerprint)
        );

        CREATE INDEX IF NOT EXISTS idx_operations_owner
            ON operations(owner_id, project_id, created_at, id);
        CREATE INDEX IF NOT EXISTS idx_operations_claim
            ON operations(state, next_attempt_at, created_at, id);
    """

    def setUp(self) -> None:
        self.temp = tempfile.TemporaryDirectory()
        self.db_path = Path(self.temp.name) / "pre-existing.sqlite3"

        connection = sqlite3.connect(self.db_path)
        connection.executescript(self.OLD_SCHEMA)
        connection.execute(
            "INSERT INTO projects VALUES ('p1','p1','P1','',datetime('now'))"
        )
        connection.execute(
            """
            INSERT INTO operations (
                id, owner_id, project_id, operation_type, fingerprint,
                state, phase, progress_current, progress_total,
                attempt_count, max_attempts, next_attempt_at, timeout_at,
                request_json, created_at, updated_at
            ) VALUES (
                'op1', 'owner1', 'p1', 'synthesize_project_plan',
                '00000000000000000000000000000000000000000000000000000000000000',
                'SUCCEEDED', 'succeeded', 1, 1, 1, 3,
                datetime('now'), datetime('now'), '{}',
                datetime('now'), datetime('now')
            )
            """
        )
        connection.commit()
        connection.close()

    def tearDown(self) -> None:
        self.temp.cleanup()

    def test_migrates_a_pre_existing_operations_table_and_preserves_rows(
        self,
    ) -> None:
        database = Database(self.db_path)
        database.initialize()

        with database.connect() as connection:
            schema = connection.execute(
                "SELECT sql FROM sqlite_master WHERE name = 'operations'"
            ).fetchone()
            self.assertNotIn(
                "UNIQUE(owner_id, operation_type, fingerprint)",
                schema["sql"],
            )
            preserved = connection.execute(
                "SELECT * FROM operations WHERE id = 'op1'"
            ).fetchone()
            self.assertIsNotNone(preserved)
            self.assertEqual(preserved["state"], "SUCCEEDED")

        from specgraph_foundry.http_api.operations import (
            OperationSettings,
            OperationStore,
        )

        store = OperationStore(
            database,
            OperationSettings(timeout_seconds=30),
            cursor_signing_key="x" * 32,
        )
        submitted = store.submit(
            owner_id="owner1",
            project_id="p1",
            operation_type="synthesize_project_plan",
            request={
                "path_params": {"project_id": "p1"},
                "payload": {},
            },
        )
        self.assertNotEqual(submitted["id"], "op1")
        self.assertEqual(submitted["state"], "QUEUED")


if __name__ == "__main__":
    unittest.main()
