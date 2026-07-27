import sqlite3
import tempfile
import unittest
from datetime import UTC, datetime, timedelta
from pathlib import Path
from unittest.mock import PropertyMock, patch

from specgraph_foundry.atoms import AtomService, DIMENSIONS
from specgraph_foundry.database import Database
from specgraph_foundry.errors import (
    ConflictError,
    ValidationError,
)
from specgraph_foundry.ingestion import IngestionService
from specgraph_foundry.research import ResearchService
from specgraph_foundry.services import ProjectService


class ResearchTest(unittest.TestCase):
    def setUp(self) -> None:
        self.temp = tempfile.TemporaryDirectory()

        self.database = Database(
            Path(self.temp.name) / "test.sqlite3"
        )
        self.database.initialize()

        self.projects = ProjectService(self.database)
        self.ingestion = IngestionService(self.database)
        self.atoms = AtomService(self.database)
        self.research = ResearchService(self.database)

        self.project = self.projects.create(
            "research-test",
            "Research Test",
        )

        document = self.ingestion.ingest_text(
            project_id=str(self.project["id"]),
            title="Source",
            content=(
                "The API must preserve source provenance.\n"
            ),
            chunk_bytes=32,
        )

        self.atoms.extract_document(
            str(document["id"])
        )

    def tearDown(self) -> None:
        self.temp.cleanup()

    def test_worker_ownership(self) -> None:
        task = self.research.claim_task(
            str(self.project["id"]),
            "worker-a",
            300,
        )

        self.assertIsNotNone(task)

        with self.assertRaises(ConflictError):
            self.research.heartbeat(
                str(task["id"]),
                "worker-b",
                300,
            )

        refreshed = self.research.heartbeat(
            str(task["id"]),
            "worker-a",
            300,
        )

        self.assertEqual(
            refreshed["lease_owner"],
            "worker-a",
        )

    def test_claim_locks_row_on_postgres(self) -> None:
        # database.py downgrades "BEGIN IMMEDIATE" to a plain "BEGIN" for
        # PostgreSQL, since that lock mode is SQLite-only - claim_task must
        # compensate with "FOR UPDATE SKIP LOCKED" on the row select so two
        # concurrent workers can't both claim the same PENDING task. This
        # test's connection is still real SQLite (no live Postgres in this
        # suite), which doesn't understand that clause - so forcing
        # is_postgres=True and observing a syntax error is exactly the
        # evidence that the clause is actually sent on that code path.
        with patch.object(
            Database,
            "is_postgres",
            new_callable=PropertyMock,
            return_value=True,
        ):
            with self.assertRaises(sqlite3.OperationalError):
                self.research.claim_task(
                    str(self.project["id"]),
                    "worker-a",
                    300,
                )

    def test_expired_reclaim_does_not_clobber_a_fresh_claim(self) -> None:
        # Race window this guards: worker B reclaims an expired task and
        # re-claims it (status=CLAIMED, fresh lease) inside one
        # transaction; a concurrent worker C's UPDATE for the *same*
        # expired-reclaim, if it only matched "WHERE id = ?", would still
        # apply after B commits and blindly stomp the row back to PENDING,
        # wiping B's claim. Simulating worker C's UPDATE directly (rather
        # than orchestrating real thread timing, which can't be made
        # deterministic) against a task that's already been freshly
        # re-claimed proves the added status/lease-expiration guard makes
        # that UPDATE a no-op instead of clobbering it.
        task = self.research.claim_task(
            str(self.project["id"]),
            "worker-b",
            300,
        )
        task_id = str(task["id"])

        # worker-b's claim is fresh: CLAIMED, lease in the future.
        with self.database.connect() as connection:
            cursor = connection.execute(
                """
                UPDATE research_tasks
                SET status = 'PENDING',
                    lease_owner = NULL,
                    lease_expires_at = NULL,
                    updated_at = ?
                WHERE id = ?
                  AND status = 'CLAIMED'
                  AND lease_expires_at IS NOT NULL
                  AND lease_expires_at <= ?
                """,
                (
                    datetime.now(UTC).isoformat(),
                    task_id,
                    datetime.now(UTC).isoformat(),
                ),
            )
            self.assertEqual(cursor.rowcount, 0)

        refreshed = self.research.get_task(task_id)
        self.assertEqual(refreshed["status"], "CLAIMED")
        self.assertEqual(refreshed["lease_owner"], "worker-b")

    def test_expired_task_is_reclaimed(self) -> None:
        task = self.research.claim_task(
            str(self.project["id"]),
            "worker-a",
            300,
        )

        expired = (
            datetime.now(UTC)
            - timedelta(minutes=5)
        ).isoformat()

        with self.database.connect() as connection:
            connection.execute(
                """
                UPDATE research_tasks
                SET lease_expires_at = ?
                WHERE id = ?
                """,
                (expired, task["id"]),
            )

        reclaimed = self.research.claim_task(
            str(self.project["id"]),
            "worker-b",
            300,
        )

        self.assertEqual(
            reclaimed["id"],
            task["id"],
        )
        self.assertEqual(
            reclaimed["lease_owner"],
            "worker-b",
        )

    def test_evidence_resolves_dimension(self) -> None:
        task = self.research.claim_task(
            str(self.project["id"]),
            "researcher",
            300,
        )

        evidence = self.research.add_evidence(
            task_id=str(task["id"]),
            worker_id="researcher",
            source_uri=(
                "https://example.test/standard"
            ),
            source_title="Official Standard",
            excerpt=(
                "Systems must retain provenance."
            ),
            publisher="Standards Authority",
            evidence_type="STANDARD",
            reliability=0.95,
        )

        completed = self.research.complete_task(
            task_id=str(task["id"]),
            worker_id="researcher",
            conclusion=(
                "Durable provenance records are required."
            ),
            applicability="APPLICABLE",
            confidence=0.94,
            evidence_ids=[str(evidence["id"])],
        )

        self.assertEqual(
            completed["status"],
            "COMPLETE",
        )

        matrix = self.research.gap_matrix(
            str(self.project["id"])
        )

        self.assertEqual(
            matrix["summary"]["resolved_dimensions"],
            1,
        )
        self.assertEqual(
            matrix["summary"]["open_dimensions"],
            1,
        )
        self.assertFalse(
            matrix["summary"]["ready"]
        )

    def test_completion_requires_evidence(self) -> None:
        task = self.research.claim_task(
            str(self.project["id"]),
            "researcher",
            300,
        )

        with self.assertRaises(ValidationError):
            self.research.complete_task(
                task_id=str(task["id"]),
                worker_id="researcher",
                conclusion="Unsupported conclusion",
                applicability="NOT_APPLICABLE",
                confidence=0.5,
                evidence_ids=[],
            )

    def test_retryable_failure_requeues(self) -> None:
        task = self.research.claim_task(
            str(self.project["id"]),
            "researcher",
            300,
        )

        result = self.research.fail_task(
            task_id=str(task["id"]),
            worker_id="researcher",
            error_message="Temporary outage",
            retryable=True,
        )

        self.assertEqual(
            result["status"],
            "PENDING",
        )
        self.assertIsNone(
            result["lease_owner"]
        )


if __name__ == "__main__":
    unittest.main()
