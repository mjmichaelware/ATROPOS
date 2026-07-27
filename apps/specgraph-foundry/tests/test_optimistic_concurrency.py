import tempfile
import unittest
import uuid
from pathlib import Path

from specgraph_foundry.database import Database
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
from specgraph_foundry.services import (
    ProjectService,
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


class OptimisticConcurrencyTest(
    unittest.TestCase
):
    def setUp(self) -> None:
        self.temp = (
            tempfile.TemporaryDirectory()
        )
        self.database = Database(
            Path(self.temp.name)
            / "concurrency.sqlite3"
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
        self.project = ProjectService(
            self.database
        ).create(
            "concurrency-project",
            "Concurrency Project",
            "Fixture",
        )

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
        if_match: str | None = None,
        idempotency_key: str | None = None,
    ):
        headers = {
            "Authorization": "Bearer valid"
        }

        if if_match is not None:
            headers["If-Match"] = if_match

        if idempotency_key is not None:
            headers["Idempotency-Key"] = (
                idempotency_key
            )

        return self.application.dispatch(
            new_request(
                method,
                path,
                headers,
                payload or {},
            )
        )

    def test_routing_policy_emits_etag_and_requires_if_match_on_update(
        self,
    ) -> None:
        project_id = str(self.project["id"])

        first = self.request(
            "POST",
            f"/v1/projects/{project_id}/routing-policy",
            {
                "allow_offline_degraded": True,
                "paid_emergency_enabled": False,
                "max_paid_decisions_per_unlock": 1,
            },
        )

        self.assertEqual(first.status, 200)
        self.assertIn("etag", first.body)
        self.assertEqual(
            first.headers["etag"],
            first.body["etag"],
        )

        missing = self.request(
            "POST",
            f"/v1/projects/{project_id}/routing-policy",
            {
                "allow_offline_degraded": False,
                "paid_emergency_enabled": True,
                "max_paid_decisions_per_unlock": 2,
            },
        )
        self.assertEqual(missing.status, 428)
        self.assertEqual(
            missing.body["error"]["code"],
            "PRECONDITION_REQUIRED",
        )

        malformed = self.request(
            "POST",
            f"/v1/projects/{project_id}/routing-policy",
            {
                "allow_offline_degraded": False,
                "paid_emergency_enabled": True,
                "max_paid_decisions_per_unlock": 2,
            },
            if_match="weak",
        )
        self.assertEqual(malformed.status, 400)
        self.assertEqual(
            malformed.body["error"]["code"],
            "INVALID_PRECONDITION",
        )

        stale = self.request(
            "POST",
            f"/v1/projects/{project_id}/routing-policy",
            {
                "allow_offline_degraded": False,
                "paid_emergency_enabled": True,
                "max_paid_decisions_per_unlock": 2,
            },
            if_match="\"stale\"",
        )
        self.assertEqual(stale.status, 412)
        self.assertEqual(
            stale.body["error"]["code"],
            "PRECONDITION_FAILED",
        )

        current = self.request(
            "GET",
            f"/v1/projects/{project_id}/routing-policy",
        )

        updated = self.request(
            "POST",
            f"/v1/projects/{project_id}/routing-policy",
            {
                "allow_offline_degraded": False,
                "paid_emergency_enabled": True,
                "max_paid_decisions_per_unlock": 2,
            },
            if_match=current.headers["etag"],
        )

        self.assertEqual(updated.status, 200)
        self.assertNotEqual(
            updated.headers["etag"],
            current.headers["etag"],
        )

    def test_collection_items_include_editable_etags(
        self,
    ) -> None:
        project_id = str(self.project["id"])

        created = self.request(
            "POST",
            f"/v1/projects/{project_id}/providers",
            {
                "name": "FREE",
                "provider_class": "FREE_READY_PROVIDER",
                "cost_class": "FREE",
                "territories": ["CODE_PATCH"],
                "priority": 1,
                "metadata": {"runtime": "ATROPOS"},
            },
            idempotency_key="idem-provider-1234",
        )

        self.assertEqual(created.status, 201)
        self.assertIn("etag", created.body)

        listed = self.request(
            "GET",
            f"/v1/projects/{project_id}/providers",
        )

        self.assertEqual(listed.status, 200)
        self.assertTrue(listed.body["items"])
        self.assertIn(
            "etag",
            listed.body["items"][0],
        )

    def test_existing_binding_update_requires_exact_if_match(
        self,
    ) -> None:
        project_id = str(self.project["id"])

        created = self.request(
            "POST",
            f"/v1/projects/{project_id}/bindings",
            {
                "system_name": "ATROPOS",
                "binding_type": "AUTONOMOUS_RUNTIME",
                "config": {"repository": "example/repo"},
            },
            idempotency_key="idem-binding-1234",
        )

        self.assertEqual(created.status, 201)

        missing = self.request(
            "POST",
            f"/v1/projects/{project_id}/bindings",
            {
                "system_name": "ATROPOS",
                "binding_type": "AUTONOMOUS_RUNTIME",
                "config": {"repository": "example/repo-2"},
            },
            idempotency_key="idem-binding-5678",
        )
        self.assertEqual(missing.status, 428)

        updated = self.request(
            "POST",
            f"/v1/projects/{project_id}/bindings",
            {
                "system_name": "ATROPOS",
                "binding_type": "AUTONOMOUS_RUNTIME",
                "config": {"repository": "example/repo-2"},
            },
            idempotency_key="idem-binding-9012",
            if_match=created.headers["etag"],
        )
        self.assertEqual(updated.status, 201)
        self.assertNotEqual(
            updated.headers["etag"],
            created.headers["etag"],
        )

    def test_create_does_not_require_if_match(
        self,
    ) -> None:
        project_id = str(self.project["id"])
        created = self.request(
            "POST",
            f"/v1/projects/{project_id}/renderers",
            {
                "name": "JSON",
                "renderer_type": "JSON",
                "territories": ["BLUEPRINT"],
                "priority": 0,
                "metadata": {"schema": "SPECGRAPH_V1"},
            },
            idempotency_key="idem-renderer-123",
        )

        self.assertEqual(created.status, 201)
        self.assertIn("etag", created.body)


if __name__ == "__main__":
    unittest.main()
