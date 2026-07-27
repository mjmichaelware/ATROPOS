import tempfile
import unittest
import uuid
from pathlib import Path

from specgraph_foundry.atoms import AtomService
from specgraph_foundry.database import Database
from specgraph_foundry.http_api.auth import AuthenticationError
from specgraph_foundry.http_api.gateway import AuthenticatedApi, new_request
from specgraph_foundry.http_api.models import Principal
from specgraph_foundry.http_api.operation_handlers import OperationHandlerRegistry
from specgraph_foundry.http_api.operations import OperationSettings, OperationStore
from specgraph_foundry.http_api.worker import run_once
from specgraph_foundry.http_api.workspace import ProjectWorkspaceService
from specgraph_foundry.ingestion import IngestionService
from specgraph_foundry.planning import PlanningService
from specgraph_foundry.research import ResearchService
from specgraph_foundry.exports import ExportService
from specgraph_foundry.execution import ExecutionService
from specgraph_foundry.routing import RoutingService
from specgraph_foundry.services import ProjectService


class FakeAuthenticator:
    def __init__(self, principal: Principal) -> None:
        self.principal = principal

    def authenticate(self, authorization: str | None) -> Principal:
        if authorization != "Bearer valid":
            raise AuthenticationError("valid bearer token required")
        return self.principal


class OperationsApiTest(unittest.TestCase):
    def setUp(self) -> None:
        self.temp = tempfile.TemporaryDirectory()
        self.database = Database(Path(self.temp.name) / "operations.sqlite3")
        self.database.initialize()
        self.principal = Principal(user_id=str(uuid.uuid4()))
        self.operations = OperationStore(
            self.database,
            OperationSettings(timeout_seconds=30),
            cursor_signing_key="x" * 32,
        )
        self.registry = OperationHandlerRegistry(self.database)
        self.api = AuthenticatedApi(
            self.database,
            FakeAuthenticator(self.principal),
            operations=self.operations,
            operation_handlers=self.registry,
            enforce_mutation_guards=True,
        )
        project = ProjectService(self.database).create("ops", "Operations")
        self.project_id = str(project["id"])
        AtomService(self.database)
        ResearchService(self.database)
        PlanningService(self.database)
        ExportService(self.database)
        ExecutionService(self.database)
        RoutingService(self.database)
        document = IngestionService(self.database).ingest_text(
            self.project_id,
            "Authority",
            "The worker must extract atoms.\n",
            chunk_bytes=64,
        )
        self.document_id = str(document["id"])

    def tearDown(self) -> None:
        self.temp.cleanup()

    def request(self, method, path, payload=None, *, key=None, principal=None):
        headers = {"Authorization": "Bearer valid"}
        if key is not None:
            headers["Idempotency-Key"] = key
        api = self.api
        if principal is not None:
            api = AuthenticatedApi(
                self.database,
                FakeAuthenticator(principal),
                operations=self.operations,
                operation_handlers=self.registry,
                enforce_mutation_guards=True,
            )
        return api.dispatch(new_request(method, path, headers, payload or {}))

    def test_submission_returns_202_without_inline_mutation(self) -> None:
        response = self.request(
            "POST",
            f"/v1/documents/{self.document_id}/extract",
            {},
            key="operation-submit-key-01",
        )
        self.assertEqual(response.status, 202)
        self.assertEqual(response.headers["idempotency-replayed"], "false")
        self.assertIn("location", response.headers)
        self.assertIn("retry-after", response.headers)
        operation = response.body["operation"]
        self.assertEqual(operation["state"], "QUEUED")
        self.assertEqual(operation["operation_type"], "extract_document_atoms")
        self.assertEqual(AtomService(self.database).list_atoms(self.document_id), [])

    def test_idempotency_replays_same_operation_and_changed_payload_conflicts(self) -> None:
        key = "operation-submit-key-02"
        first = self.request(
            "POST",
            f"/v1/documents/{self.document_id}/extract",
            {},
            key=key,
        )
        replay = self.request(
            "POST",
            f"/v1/documents/{self.document_id}/extract",
            {},
            key=key,
        )
        self.assertEqual(replay.status, 202)
        self.assertEqual(replay.headers["idempotency-replayed"], "true")
        self.assertEqual(
            first.body["operation"]["id"],
            replay.body["operation"]["id"],
        )
        conflict = self.request(
            "POST",
            f"/v1/documents/{self.document_id}/extract",
            {"changed": True},
            key=key,
        )
        self.assertEqual(conflict.status, 409)

    def test_fresh_submission_after_terminal_operation_is_not_stuck_replaying_forever(self) -> None:
        # A duplicate-submission guard keyed only on (owner, type, request)
        # must not outlive the operation it originally guarded: once that
        # operation reaches a terminal state, a later request with the
        # identical shape (a different Idempotency-Key, since a real client
        # never reuses a key on purpose) has to be able to run again and
        # reflect whatever has changed server-side since - e.g.
        # synthesize_project_plan being re-run after research is resolved,
        # or verify_plan being re-run after a fix. Regression test for a
        # bug where the operations table's UNIQUE(owner_id, operation_type,
        # fingerprint) constraint applied unconditionally, so a second,
        # legitimate submission with the same request shape either replayed
        # the first (now stale) operation's result forever or crashed with
        # a database integrity error.
        first = self.request(
            "POST",
            f"/v1/documents/{self.document_id}/extract",
            {},
            key="operation-fresh-key-01",
        )
        self.assertEqual(first.status, 202)
        first_operation_id = str(first.body["operation"]["id"])
        self.assertTrue(run_once(self.operations, self.registry, "worker-a"))
        finished = self.request("GET", f"/v1/operations/{first_operation_id}")
        self.assertEqual(finished.body["operation"]["state"], "SUCCEEDED")

        second = self.request(
            "POST",
            f"/v1/documents/{self.document_id}/extract",
            {},
            key="operation-fresh-key-02",
        )
        self.assertEqual(second.status, 202)
        self.assertEqual(second.headers["idempotency-replayed"], "false")
        second_operation_id = str(second.body["operation"]["id"])
        self.assertNotEqual(first_operation_id, second_operation_id)
        self.assertEqual(second.body["operation"]["state"], "QUEUED")

    def test_get_list_and_cancel_are_owner_scoped(self) -> None:
        created = self.request(
            "POST",
            f"/v1/documents/{self.document_id}/extract",
            {},
            key="operation-submit-key-03",
        )
        operation_id = created.body["operation"]["id"]
        got = self.request("GET", f"/v1/operations/{operation_id}")
        self.assertEqual(got.status, 200)
        # Every pollOperation() caller in the frontend expects this nested
        # under "operation", matching the 202 submission response shape -
        # a regression test for the flat shape this endpoint used to return.
        self.assertEqual(got.body["operation"]["id"], operation_id)
        self.assertIn("state", got.body["operation"])
        listed = self.request(
            "GET",
            f"/v1/projects/{self.project_id}/operations?limit=1",
        )
        self.assertEqual(listed.status, 200)
        self.assertEqual(listed.headers["x-page-limit"], "1")
        self.assertEqual(len(listed.body["items"]), 1)
        outsider = Principal(user_id=str(uuid.uuid4()))
        hidden = self.request(
            "GET",
            f"/v1/operations/{operation_id}",
            principal=outsider,
        )
        self.assertEqual(hidden.status, 404)
        cancelled = self.request(
            "POST",
            f"/v1/operations/{operation_id}/cancel",
            {},
            key="operation-cancel-key-03",
        )
        self.assertEqual(cancelled.status, 200)
        self.assertEqual(cancelled.body["state"], "CANCELLED")

    def test_workspace_contains_bounded_operation_summary(self) -> None:
        for index in range(6):
            self.operations.submit(
                owner_id=self.principal.user_id,
                project_id=self.project_id,
                operation_type="extract_document_atoms",
                request={"index": index},
            )
        body = ProjectWorkspaceService(self.database).get(self.project_id)
        self.assertEqual(len(body["operations"]), 5)
        self.assertEqual(body["operations_count"], 6)
        self.assertTrue(body["operations_has_more"])
        self.assertEqual(
            body["operations_route"],
            f"/v1/projects/{self.project_id}/operations",
        )


if __name__ == "__main__":
    unittest.main()
