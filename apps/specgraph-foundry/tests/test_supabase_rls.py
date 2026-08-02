import re
import unittest
from pathlib import Path


ROOT = Path(__file__).resolve().parents[1]

DEPLOYMENT = (
    ROOT
    / "supabase"
    / "migrations"
    / "20260712000900_auth_rls.sql"
)

DEPLOYMENT_IDEMPOTENCY = (
    ROOT
    / "supabase"
    / "migrations"
    / "20260712001000_idempotency.sql"
)

DEPLOYMENT_SOURCE_UPLOADS = (
    ROOT
    / "supabase"
    / "migrations"
    / "20260712001100_source_uploads.sql"
)

DEPLOYMENT_DOCUMENT_DERIVATIONS = (
    ROOT
    / "supabase"
    / "migrations"
    / "20260712001200_document_derivations.sql"
)

DEPLOYMENT_DURABLE_ARTIFACTS = (
    ROOT
    / "supabase"
    / "migrations"
    / "20260712001300_durable_artifacts.sql"
)

DEPLOYMENT_OPERATIONS = (
    ROOT
    / "supabase"
    / "migrations"
    / "20260712001400_operations.sql"
)

EXPECTED_TABLES = ['artifact_manifests', 'atom_dimensions', 'atoms', 'authority_relations', 'document_derivations', 'execution_attempts', 'execution_events', 'execution_receipts', 'execution_run_nodes', 'execution_runs', 'execution_validation_findings', 'export_verification_findings', 'exports', 'extraction_runs', 'graph_edges', 'graph_nodes', 'graphs', 'idempotency_records', 'ingestion_runs', 'integration_bindings', 'paid_route_unlocks', 'plan_node_bindings', 'plan_verification_findings', 'plan_versions', 'project_policies', 'projects', 'provider_configs', 'provider_health_events', 'renderer_configs', 'research_claim_evidence', 'research_claims', 'research_evidence', 'research_task_events', 'research_tasks', 'route_decisions', 'source_chunks', 'source_documents', 'source_sections', 'source_uploads', 'storage_objects']


class SupabaseRlsMigrationTest(
    unittest.TestCase
):
    def setUp(self) -> None:
        self.sql = (
            DEPLOYMENT.read_text(
                encoding="utf-8"
            )
            + "\n"
            + DEPLOYMENT_IDEMPOTENCY.read_text(
                encoding="utf-8"
            )
            + "\n"
            + DEPLOYMENT_SOURCE_UPLOADS.read_text(
                encoding="utf-8"
            )
            + "\n"
            + DEPLOYMENT_DOCUMENT_DERIVATIONS.read_text(
                encoding="utf-8"
            )
            + "\n"
            + DEPLOYMENT_DURABLE_ARTIFACTS.read_text(
                encoding="utf-8"
            )
            + "\n"
            + DEPLOYMENT_OPERATIONS.read_text(
                encoding="utf-8"
            )
        )

    def test_canonical_migrations_are_present(
        self,
    ) -> None:
        for path in (
            DEPLOYMENT,
            DEPLOYMENT_IDEMPOTENCY,
            DEPLOYMENT_SOURCE_UPLOADS,
            DEPLOYMENT_DOCUMENT_DERIVATIONS,
            DEPLOYMENT_DURABLE_ARTIFACTS,
            DEPLOYMENT_OPERATIONS,
        ):
            self.assertTrue(path.is_file())
            self.assertGreater(path.stat().st_size, 0)

    def test_every_public_table_has_policy(
        self,
    ) -> None:
        found = set(
            re.findall(
                r'on public\.([a-z_]+)\s+'
                r'for all\s+'
                r'to authenticated',
                self.sql,
                flags=re.IGNORECASE,
            )
        )

        self.assertEqual(
            found,
            set(EXPECTED_TABLES),
        )
        operation_select = re.findall(
            r'on public\.operations\s+'
            r'for select\s+'
            r'to authenticated',
            self.sql,
            flags=re.IGNORECASE,
        )
        self.assertEqual(len(operation_select), 1)

    def test_projects_require_owner(
        self,
    ) -> None:
        self.assertIn(
            "alter column owner_id",
            self.sql,
        )
        self.assertIn(
            "set not null",
            self.sql,
        )
        self.assertIn(
            "references auth.users(id)",
            self.sql,
        )
        self.assertIn(
            "owner_id = (select auth.uid())",
            self.sql,
        )

    def test_anonymous_role_has_no_access(
        self,
    ) -> None:
        self.assertIn(
            "from anon",
            self.sql,
        )
        self.assertNotRegex(
            self.sql,
            r'create policy[\s\S]*?to anon',
        )

    def test_security_definer_functions_lock_search_path(
        self,
    ) -> None:
        function_count = len(
            re.findall(
                r"security definer",
                self.sql,
                flags=re.IGNORECASE,
            )
        )

        search_path_count = len(
            re.findall(
                r"set search_path = ''",
                self.sql,
                flags=re.IGNORECASE,
            )
        )

        self.assertEqual(
            function_count,
            search_path_count,
        )
        self.assertGreater(
            function_count,
            10,
        )


if __name__ == "__main__":
    unittest.main()
