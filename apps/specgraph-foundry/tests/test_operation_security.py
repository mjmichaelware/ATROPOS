import re
import tempfile
import unittest
from pathlib import Path

from specgraph_foundry.http_api.operations import OperationSettings, OperationStore
from specgraph_foundry.database import Database
from specgraph_foundry.services import ProjectService


ROOT = Path(__file__).resolve().parents[1]
DEPLOYMENT = ROOT / "supabase" / "migrations" / "20260712001400_operations.sql"
JOB = ROOT / "infra" / "cloud-run" / "worker" / "job.yaml"


class OperationSecurityTest(unittest.TestCase):
    def test_canonical_migration_is_owner_scoped(self) -> None:
        sql = DEPLOYMENT.read_text(encoding="utf-8")
        self.assertIn("create table if not exists public.operations", sql)
        self.assertIn("enable row level security", sql)
        self.assertIn("for select", sql)
        self.assertIn("owner_id = (select auth.uid())", sql)
        self.assertNotIn("for all", sql.lower())
        self.assertNotRegex(sql.lower(), r"create policy[\\s\\S]*?to anon")

    def test_job_template_contains_no_real_secrets_or_public_ingress(self) -> None:
        text = JOB.read_text(encoding="utf-8")
        self.assertIn("template-only-group-08", text)
        self.assertIn("secretKeyRef", text)
        self.assertIn("PLACEHOLDER", text)
        self.assertNotIn("password", text.lower())
        self.assertNotIn("ingress", text.lower())
        self.assertNotRegex(text, r"[a-z0-9-]+\\.run\\.app")

    def test_operation_settings_are_bounded(self) -> None:
        OperationSettings()
        with self.assertRaises(ValueError):
            OperationSettings(lease_seconds=10)
        with self.assertRaises(ValueError):
            OperationSettings(lease_seconds=60, heartbeat_seconds=60)
        with self.assertRaises(ValueError):
            OperationSettings(max_attempts=11)

    def test_public_operation_view_excludes_worker_and_lease(self) -> None:
        with tempfile.TemporaryDirectory() as temp:
            database = Database(Path(temp) / "operation-security.sqlite3")
            database.initialize()
            store = OperationStore(database, cursor_signing_key="x" * 32)
            project = ProjectService(database).create(
                "operation-security",
                "Operation Security",
            )
            operation = store.submit(
                owner_id="owner",
                project_id=str(project["id"]),
                operation_type="extract_document_atoms",
                request={"path_params": {"document_id": "doc"}, "payload": {}},
            )
            self.assertNotIn("worker_id", operation)
            self.assertNotIn("lease_token_hash", operation)
            self.assertNotIn("request_json", operation)


if __name__ == "__main__":
    unittest.main()
