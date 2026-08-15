import json
import sqlite3
import tempfile
import unittest
import uuid
from pathlib import Path
from unittest.mock import patch

from specgraph_foundry.database import Database
from specgraph_foundry.execution import (
    ExecutionService,
)
from specgraph_foundry.exports import (
    ExportService,
)
from specgraph_foundry.http_api.auth import (
    AuthenticationError,
)
from specgraph_foundry.http_api.gateway import (
    AuthenticatedApi,
    new_request,
)
from specgraph_foundry.http_api.models import (
    Principal,
)
from specgraph_foundry.planning import (
    PlanningService,
)
from specgraph_foundry.research import (
    ResearchService,
)
from specgraph_foundry.routing import (
    RoutingService,
)
from specgraph_foundry.services import (
    ProjectService,
)
from specgraph_foundry.ingestion import (
    IngestionService,
)
from specgraph_foundry.atoms import (
    AtomService,
)


class FakeAuthenticator:
    def __init__(
        self,
        principal: Principal,
    ) -> None:
        self.principal = principal

    def authenticate(
        self,
        authorization: str | None,
    ) -> Principal:
        if authorization != "Bearer valid":
            raise AuthenticationError(
                "valid bearer token required"
            )

        return self.principal


class Group04Fixture:
    def __init__(
        self,
        database: Database,
        temp_root: Path,
    ) -> None:
        self.database = database
        self.temp_root = temp_root
        self.projects = ProjectService(database)
        self.ingestion = IngestionService(database)
        self.atoms = AtomService(database)
        self.research = ResearchService(database)
        self.planning = PlanningService(database)
        self.exports = ExportService(database)
        self.execution = ExecutionService(database)
        self.routing = RoutingService(database)

    def build(self) -> dict[str, object]:
        project = self.projects.create(
            "idempotency-project",
            "Idempotency Project",
            "Test fixture",
        )
        document = self.ingestion.ingest_text(
            str(project["id"]),
            "Source",
            "# Requirement\n\nThe system must preserve provenance.\n",
            media_type="text/markdown",
            chunk_bytes=64,
        )
        extraction = self.atoms.extract_document(
            str(document["id"])
        )

        while True:
            task = self.research.claim_task(
                str(project["id"]),
                "fixture-worker",
                lease_seconds=300,
            )

            if task is None:
                break

            evidence = self.research.add_evidence(
                str(task["id"]),
                "fixture-worker",
                source_uri=(
                    "urn:specgraph:"
                    + str(task["id"])
                ),
                source_title="Fixture",
                excerpt=(
                    "This dimension is applicable."
                ),
                evidence_type="TEST_RESULT",
                reliability=1.0,
            )

            self.research.complete_task(
                str(task["id"]),
                "fixture-worker",
                conclusion="Applicable.",
                applicability="APPLICABLE",
                confidence=1.0,
                evidence_ids=[str(evidence["id"])],
            )

        plan = self.planning.synthesize(
            str(project["id"])
        )
        verified_plan = self.planning.verify_plan(
            str(plan["id"])
        )

        export_root = self.temp_root / "exports"
        export = self.exports.export_plan(
            str(plan["id"]),
            output_root=export_root,
        )

        run = self.execution.start_run(
            str(plan["id"]),
            runtime_system="ATROPOS",
            runtime_run_id=(
                "run-" + uuid.uuid4().hex
            ),
            export_id=str(export["id"]),
        )

        claim = self.execution.claim_node(
            str(run["id"]),
            "fixture-executor",
            lease_seconds=300,
        )

        provider = (
            self.routing.configure_provider(
                str(project["id"]),
                name="LOCAL",
                provider_class=(
                    "LOCAL_TOOLCHAIN"
                ),
                cost_class="LOCAL",
                territories=["CODE_PATCH"],
                priority=0,
                metadata={"runtime": "ATROPOS"},
            )
        )

        renderer = (
            self.routing.configure_renderer(
                str(project["id"]),
                name="JSON",
                renderer_type="JSON",
                territories=["EXECUTION_HANDOFF"],
                priority=0,
                metadata={"schema": "SPECGRAPH_V1"},
            )
        )

        binding = self.exports.bind_integration(
            str(project["id"]),
            system_name="ATROPOS",
            binding_type="AUTONOMOUS_RUNTIME",
            config={"repository": "example/repo"},
        )

        self.routing.set_policy(
            str(project["id"]),
            allow_offline_degraded=True,
            paid_emergency_enabled=True,
            max_paid_decisions_per_unlock=2,
        )

        return {
            "project": project,
            "document": document,
            "extraction": extraction,
            "plan": plan,
            "plan_verification": verified_plan,
            "export": export,
            "run": run,
            "claim": claim,
            "provider": provider,
            "renderer": renderer,
            "binding": binding,
        }


class IdempotencyTest(unittest.TestCase):
    def setUp(self) -> None:
        self.temp = (
            tempfile.TemporaryDirectory()
        )
        self.database = Database(
            Path(self.temp.name)
            / "idempotency.sqlite3"
        )
        self.database.initialize()
        self.principal = Principal(
            user_id=str(uuid.uuid4()),
            email="owner@example.com",
        )
        self.application = AuthenticatedApi(
            self.database,
            FakeAuthenticator(
                self.principal
            ),
            enforce_mutation_guards=True,
        )
        self.fixture = Group04Fixture(
            self.database,
            Path(self.temp.name),
        ).build()

    def tearDown(self) -> None:
        self.temp.cleanup()

    def request(
        self,
        method: str,
        path: str,
        payload: (
            dict[str, object] | None
        ) = None,
        *,
        idempotency_key: str | None = None,
        principal: Principal | None = None,
    ):
        headers = {
            "Authorization": "Bearer valid"
        }

        if idempotency_key is not None:
            headers["Idempotency-Key"] = (
                idempotency_key
            )

        application = self.application

        if principal is not None:
            application = AuthenticatedApi(
                self.database,
                FakeAuthenticator(principal),
                enforce_mutation_guards=True,
            )

        return application.dispatch(
            new_request(
                method,
                path,
                headers,
                payload or {},
            )
        )

    def test_missing_key_is_rejected_for_all_required_routes(
        self,
    ) -> None:
        project_id = str(
            self.fixture["project"]["id"]
        )
        document_id = str(
            self.fixture["document"]["id"]
        )
        task = self.research_task()
        plan_id = str(self.fixture["plan"]["id"])
        export_id = str(
            self.fixture["export"]["id"]
        )
        run_id = str(self.fixture["run"]["id"])
        run_node_id = str(
            self.fixture["claim"]["node"]["id"]
        )
        provider_id = str(
            self.fixture["provider"]["id"]
        )
        routes = [
            ("POST", f"/v1/projects/{project_id}/documents", {"title": "Retry", "content": "x"}),
            ("POST", f"/v1/projects/{project_id}/source-uploads", {"filename": "requirements.md", "media_type": "text/markdown", "byte_size": 4, "sha256": "9f86d081884c7d659a2feaa0c55ad015a3bf4f1b2b0b822cd15d6c15b0f00a08"}),
            ("POST", f"/v1/documents/{document_id}/extract", {}),
            ("POST", f"/v1/projects/{project_id}/research-tasks/claim", {"worker_id": "worker-1"}),
            ("POST", f"/v1/research-tasks/{task['id']}/evidence", {"worker_id": "worker-1", "source_uri": "urn:test:evidence", "source_title": "Evidence", "excerpt": "Proof."}),
            ("POST", f"/v1/research-tasks/{task['id']}/complete", {"worker_id": "worker-1", "conclusion": "Applicable", "applicability": "APPLICABLE", "confidence": 1.0, "evidence_ids": [str(task['evidence_id'])]}),
            ("POST", f"/v1/projects/{project_id}/plans", {}),
            ("POST", f"/v1/plans/{plan_id}/verify", {}),
            ("POST", f"/v1/projects/{project_id}/bindings", {"system_name": "ATROPOS", "binding_type": "AUTONOMOUS_RUNTIME", "config": {"repository": "example/repo"}}),
            ("POST", f"/v1/plans/{plan_id}/exports", {}),
            ("POST", f"/v1/exports/{export_id}/verify", {}),
            ("POST", f"/v1/plans/{plan_id}/execution-runs", {"runtime_system": "ATROPOS", "runtime_run_id": "run-again"}),
            ("POST", f"/v1/execution-runs/{run_id}/claim", {"worker_id": "worker-1"}),
            ("POST", f"/v1/execution-runs/{run_id}/verify", {}),
            ("POST", f"/v1/execution-nodes/{run_node_id}/receipts", {"worker_id": "fixture-executor", "actor_system": "ATROPOS", "outcome": "SUCCESS", "summary": "Done", "evidence": {"source_atom_ids": []}}),
            ("POST", f"/v1/projects/{project_id}/providers", {"name": "FREE", "provider_class": "FREE_READY_PROVIDER", "cost_class": "FREE", "territories": ["CODE_PATCH"], "priority": 1, "metadata": {"runtime": "ATROPOS"}}),
            ("POST", f"/v1/providers/{provider_id}/health", {"status": "READY"}),
            ("POST", f"/v1/projects/{project_id}/renderers", {"name": "HTML", "renderer_type": "HTML", "territories": ["BLUEPRINT"], "priority": 1, "metadata": {"schema": "SPECGRAPH_V1"}}),
            ("POST", f"/v1/projects/{project_id}/renderers/select", {"territory": "EXECUTION_HANDOFF"}),
            ("POST", f"/v1/projects/{project_id}/paid-unlocks", {"actor_id": "operator", "reason": "Emergency production unlock", "ttl_seconds": 60}),
            ("POST", f"/v1/projects/{project_id}/route-decisions", {"territory": "CODE_PATCH"}),
        ]

        for method, path, payload in routes:
            with self.subTest(path=path):
                response = self.request(
                    method,
                    path,
                    payload,
                )
                self.assertEqual(
                    response.status,
                    400,
                )
                self.assertEqual(
                    response.body["error"]["code"],
                    "IDEMPOTENCY_KEY_REQUIRED",
                )

    def test_missing_key_and_invalid_key_are_rejected(
        self,
    ) -> None:
        project_id = str(
            self.fixture["project"]["id"]
        )
        response = self.request(
            "POST",
            f"/v1/projects/{project_id}/documents",
            {
                "title": "Retry",
                "content": "body",
            },
        )
        self.assertEqual(response.status, 400)
        self.assertEqual(
            response.body["error"]["code"],
            "IDEMPOTENCY_KEY_REQUIRED",
        )

        invalid = self.request(
            "POST",
            f"/v1/projects/{project_id}/documents",
            {
                "title": "Retry",
                "content": "body",
            },
            idempotency_key="short",
        )
        self.assertEqual(invalid.status, 400)
        self.assertEqual(
            invalid.body["error"]["code"],
            "INVALID_IDEMPOTENCY_KEY",
        )

    def test_same_request_replays_successfully(
        self,
    ) -> None:
        project_id = str(
            self.fixture["project"]["id"]
        )
        key = "idem-key-12345678"
        payload = {
            "name": "FREE",
            "provider_class": "FREE_READY_PROVIDER",
            "cost_class": "FREE",
            "territories": ["CODE_PATCH"],
            "priority": 1,
            "metadata": {
                "alpha": 1,
                "beta": 2,
            },
        }

        first = self.request(
            "POST",
            f"/v1/projects/{project_id}/providers",
            payload,
            idempotency_key=key,
        )
        second = self.request(
            "POST",
            f"/v1/projects/{project_id}/providers",
            {
                "metadata": {
                    "beta": 2,
                    "alpha": 1,
                },
                "priority": 1,
                "territories": ["CODE_PATCH"],
                "provider_class": "FREE_READY_PROVIDER",
                "name": "FREE",
                "cost_class": "FREE",
            },
            idempotency_key=key,
        )

        self.assertEqual(first.status, 201)
        self.assertEqual(second.status, 201)
        self.assertEqual(
            first.body,
            second.body,
        )
        self.assertEqual(
            first.headers["idempotency-replayed"],
            "false",
        )
        self.assertEqual(
            second.headers["idempotency-replayed"],
            "true",
        )

        with self.database.connect() as connection:
            count = connection.execute(
                """
                SELECT count(*) AS count
                FROM provider_configs
                WHERE project_id = ?
                  AND name = 'FREE'
                """,
                (project_id,),
            ).fetchone()["count"]

        self.assertEqual(count, 1)

    def test_same_key_with_different_payload_conflicts(
        self,
    ) -> None:
        project_id = str(
            self.fixture["project"]["id"]
        )
        key = "idem-key-abcdefgh"

        first = self.request(
            "POST",
            f"/v1/projects/{project_id}/renderers",
            {
                "name": "HTML",
                "renderer_type": "HTML",
                "territories": ["BLUEPRINT"],
                "priority": 1,
                "metadata": {
                    "schema": "SPECGRAPH_V1"
                },
            },
            idempotency_key=key,
        )
        second = self.request(
            "POST",
            f"/v1/projects/{project_id}/renderers",
            {
                "name": "HTML",
                "renderer_type": "HTML",
                "territories": ["BLUEPRINT"],
                "priority": 2,
                "metadata": {
                    "schema": "SPECGRAPH_V1"
                },
            },
            idempotency_key=key,
        )

        self.assertEqual(first.status, 201)
        self.assertEqual(second.status, 409)
        self.assertEqual(
            second.body["error"]["code"],
            "IDEMPOTENCY_KEY_REUSED",
        )

    def test_same_key_does_not_collide_across_operations(
        self,
    ) -> None:
        project_id = str(
            self.fixture["project"]["id"]
        )
        key = "idem-key-op-shared"

        provider = self.request(
            "POST",
            f"/v1/projects/{project_id}/providers",
            {
                "name": "FREE2",
                "provider_class": "FREE_READY_PROVIDER",
                "cost_class": "FREE",
                "territories": ["CODE_PATCH"],
                "priority": 2,
                "metadata": {"runtime": "ATROPOS"},
            },
            idempotency_key=key,
        )
        renderer = self.request(
            "POST",
            f"/v1/projects/{project_id}/renderers",
            {
                "name": "TEXT",
                "renderer_type": "TEXT",
                "territories": ["BLUEPRINT"],
                "priority": 2,
                "metadata": {"schema": "SPECGRAPH_V1"},
            },
            idempotency_key=key,
        )

        self.assertEqual(provider.status, 201)
        self.assertEqual(renderer.status, 201)

    def test_same_key_does_not_collide_across_owners(
        self,
    ) -> None:
        project_id = str(
            self.fixture["project"]["id"]
        )
        outsider = Principal(
            user_id=str(uuid.uuid4()),
            email="other@example.com",
        )
        key = "idem-key-cross-owner"

        owner = self.request(
            "POST",
            f"/v1/projects/{project_id}/providers",
            {
                "name": "OWNER",
                "provider_class": "FREE_READY_PROVIDER",
                "cost_class": "FREE",
                "territories": ["CODE_PATCH"],
                "priority": 3,
                "metadata": {"runtime": "ATROPOS"},
            },
            idempotency_key=key,
        )
        outsider_response = self.request(
            "POST",
            f"/v1/projects/{project_id}/providers",
            {
                "name": "OUTSIDER",
                "provider_class": "FREE_READY_PROVIDER",
                "cost_class": "FREE",
                "territories": ["CODE_PATCH"],
                "priority": 4,
                "metadata": {"runtime": "ATROPOS"},
            },
            idempotency_key=key,
            principal=outsider,
        )

        self.assertEqual(owner.status, 201)
        self.assertIn(
            outsider_response.status,
            {404, 201},
        )
        self.assertNotEqual(
            owner.body,
            outsider_response.body,
        )

    def test_in_progress_conflicts_and_expired_claims_can_be_reclaimed(
        self,
    ) -> None:
        project_id = str(
            self.fixture["project"]["id"]
        )
        key = "idem-key-in-progress"
        request_hash = (
            "abc" * 21 + "d"
        )

        with self.database.connect() as connection:
            connection.execute(
                """
                INSERT INTO idempotency_records(
                    id,
                    owner_id,
                    operation,
                    idempotency_key_hash,
                    canonical_request_hash,
                    state,
                    created_at,
                    updated_at,
                    expires_at
                )
                VALUES(?,?,?,?,?,?,?,?,?)
                """,
                (
                    str(uuid.uuid4()),
                    self.principal.user_id,
                    "create_project_provider",
                    __import__("hashlib").sha256(
                        key.encode("utf-8")
                    ).hexdigest(),
                    request_hash,
                    "IN_PROGRESS",
                    "2026-01-01T00:00:00+00:00",
                    "2026-01-01T00:00:00+00:00",
                    "9999-01-01T00:00:00+00:00",
                ),
            )

        with patch(
            "specgraph_foundry.http_api.gateway_concurrency.canonical_request_hash",
            return_value=request_hash,
        ):
            conflict = self.request(
                "POST",
                f"/v1/projects/{project_id}/providers",
                {
                    "name": "BLOCKED",
                    "provider_class": "FREE_READY_PROVIDER",
                    "cost_class": "FREE",
                    "territories": ["CODE_PATCH"],
                    "priority": 5,
                    "metadata": {"runtime": "ATROPOS"},
                },
                idempotency_key=key,
            )

        self.assertEqual(conflict.status, 409)
        self.assertEqual(
            conflict.body["error"]["code"],
            "IDEMPOTENCY_IN_PROGRESS",
        )

        with self.database.connect() as connection:
            connection.execute(
                """
                UPDATE idempotency_records
                SET expires_at = '2000-01-01T00:00:00+00:00'
                WHERE owner_id = ?
                  AND operation = 'create_project_provider'
                """,
                (self.principal.user_id,),
            )

        with patch(
            "specgraph_foundry.http_api.gateway_concurrency.canonical_request_hash",
            return_value=request_hash,
        ):
            reclaimed = self.request(
                "POST",
                f"/v1/projects/{project_id}/providers",
                {
                    "name": "BLOCKED",
                    "provider_class": "FREE_READY_PROVIDER",
                    "cost_class": "FREE",
                    "territories": ["CODE_PATCH"],
                    "priority": 5,
                    "metadata": {"runtime": "ATROPOS"},
                },
                idempotency_key=key,
            )

        self.assertEqual(reclaimed.status, 201)

    def test_failed_records_are_retryable_and_not_successfully_replayed(
        self,
    ) -> None:
        project_id = str(
            self.fixture["project"]["id"]
        )
        key = "idem-key-failed-retry"
        payload = {
            "title": "Retry Document",
            "content": "body",
        }

        with patch(
            "specgraph_foundry.ingestion.IngestionService.ingest_text",
            side_effect=RuntimeError("boom"),
        ):
            failed = self.request(
                "POST",
                f"/v1/projects/{project_id}/documents",
                payload,
                idempotency_key=key,
            )

        self.assertEqual(failed.status, 500)
        self.assertEqual(
            failed.body["error"]["code"],
            "INTERNAL_ERROR",
        )

        retried = self.request(
            "POST",
            f"/v1/projects/{project_id}/documents",
            payload,
            idempotency_key=key,
        )

        self.assertEqual(retried.status, 201)
        self.assertEqual(
            retried.headers["idempotency-replayed"],
            "false",
        )

    def test_raw_key_is_not_persisted_and_error_details_are_safe(
        self,
    ) -> None:
        project_id = str(
            self.fixture["project"]["id"]
        )
        key = "idem-key-storage-safe"
        response = self.request(
            "POST",
            f"/v1/projects/{project_id}/documents",
            {
                "title": "Safe",
                "content": "body",
            },
            idempotency_key=key,
        )

        self.assertEqual(response.status, 201)

        with self.database.connect() as connection:
            row = connection.execute(
                """
                SELECT idempotency_key_hash
                FROM idempotency_records
                WHERE owner_id = ?
                  AND operation = 'ingest_project_document'
                ORDER BY created_at DESC
                LIMIT 1
                """,
                (self.principal.user_id,),
            ).fetchone()

        self.assertIsNotNone(row)
        self.assertNotEqual(
            row["idempotency_key_hash"],
            key,
        )
        self.assertNotIn(
            key,
            json.dumps(response.body),
        )

    def test_request_headers_are_preserved_on_success(
        self,
    ) -> None:
        project_id = str(
            self.fixture["project"]["id"]
        )
        response = self.request(
            "POST",
            f"/v1/projects/{project_id}/documents",
            {
                "title": "Header",
                "content": "body",
            },
            idempotency_key="idem-key-headers-01",
        )

        self.assertEqual(response.status, 201)
        self.assertIn(
            "x-request-id",
            response.headers,
        )
        self.assertEqual(
            response.headers["cache-control"],
            "no-store",
        )

    def research_task(self) -> dict[str, object]:
        project = self.projects().create(
            "idempotency-open-task",
            "Open Task Project",
            "",
        )
        document = self.ingestion().ingest_text(
            str(project["id"]),
            "Open",
            "# Open\n\nThe system must verify.\n",
        )
        self.atoms().extract_document(
            str(document["id"])
        )
        task = self.research().claim_task(
            str(project["id"]),
            "open-worker",
            lease_seconds=300,
        )
        evidence = self.research().add_evidence(
            str(task["id"]),
            "open-worker",
            source_uri="urn:test:task",
            source_title="Task",
            excerpt="Applicable.",
            evidence_type="TEST_RESULT",
            reliability=1.0,
        )
        return {
            "id": str(task["id"]),
            "evidence_id": str(evidence["id"]),
        }

    def projects(self) -> ProjectService:
        return ProjectService(self.database)

    def ingestion(self) -> IngestionService:
        return IngestionService(self.database)

    def atoms(self) -> AtomService:
        return AtomService(self.database)

    def research(self) -> ResearchService:
        return ResearchService(self.database)


if __name__ == "__main__":
    unittest.main()
