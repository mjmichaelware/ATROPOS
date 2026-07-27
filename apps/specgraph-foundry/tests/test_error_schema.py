import json
import tempfile
import unittest
import uuid
from pathlib import Path
from unittest.mock import patch
from urllib.error import HTTPError

from specgraph_foundry.database import Database
from specgraph_foundry.http_api.auth import (
    AuthenticationError,
    SupabaseAuthClient,
)
from specgraph_foundry.http_api.gateway import (
    AuthenticatedApi,
    new_request,
)
from specgraph_foundry.http_api.models import (
    Principal,
)


class FakeResponse:
    def __init__(
        self,
        payload: dict[str, object],
    ) -> None:
        self.payload = json.dumps(
            payload
        ).encode("utf-8")

    def __enter__(
        self,
    ) -> "FakeResponse":
        return self

    def __exit__(
        self,
        exception_type,
        exception,
        traceback,
    ) -> bool:
        return False

    def read(self) -> bytes:
        return self.payload


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


class ErrorSchemaTest(unittest.TestCase):
    def setUp(self) -> None:
        self.temp = (
            tempfile.TemporaryDirectory()
        )
        self.database = Database(
            Path(self.temp.name)
            / "error-schema.sqlite3"
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
        authorization: str | None = "Bearer valid",
    ):
        headers: dict[str, str] = {}

        if authorization is not None:
            headers["Authorization"] = authorization

        return self.application.dispatch(
            new_request(
                method,
                path,
                headers,
                payload or {},
            )
        )

    def test_missing_bearer_token_uses_stable_contract(
        self,
    ) -> None:
        client = SupabaseAuthClient(
            "https://example.supabase.co",
            "anon-key",
            opener=lambda *args, **kwargs: (
                FakeResponse({})
            ),
        )
        application = AuthenticatedApi(
            self.database,
            client,
            enforce_mutation_guards=True,
        )

        response = application.dispatch(
            new_request(
                "GET",
                "/v1/projects",
                {},
                {},
            )
        )

        self.assertEqual(
            response.status,
            401,
        )
        self.assertEqual(
            response.body["error"]["code"],
            "AUTHENTICATION_REQUIRED",
        )
        self.assertEqual(
            response.headers["x-request-id"],
            response.body["error"][
                "request_id"
            ],
        )
        self.assertEqual(
            response.body["error"]["details"],
            {},
        )

    def test_malformed_authorization_uses_stable_contract(
        self,
    ) -> None:
        client = SupabaseAuthClient(
            "https://example.supabase.co",
            "anon-key",
            opener=lambda *args, **kwargs: (
                FakeResponse({})
            ),
        )
        application = AuthenticatedApi(
            self.database,
            client,
            enforce_mutation_guards=True,
        )

        response = application.dispatch(
            new_request(
                "GET",
                "/v1/projects",
                {
                    "Authorization": (
                        "Token invalid"
                    )
                },
                {},
            )
        )

        self.assertEqual(
            response.status,
            401,
        )
        self.assertEqual(
            response.body["error"]["code"],
            "INVALID_AUTHORIZATION",
        )
        self.assertNotIsInstance(
            response.body["error"],
            str,
        )

    def test_invalid_bearer_token_uses_stable_contract(
        self,
    ) -> None:
        def opener(
            request,
            timeout,
        ):
            raise HTTPError(
                request.full_url,
                401,
                "unauthorized",
                {},
                None,
            )

        client = SupabaseAuthClient(
            "https://example.supabase.co",
            "anon-key",
            opener=opener,
        )
        application = AuthenticatedApi(
            self.database,
            client,
        )

        response = application.dispatch(
            new_request(
                "GET",
                "/v1/projects",
                {
                    "Authorization": (
                        "Bearer invalid"
                    )
                },
                {},
            )
        )

        self.assertEqual(
            response.status,
            401,
        )
        self.assertEqual(
            response.body["error"]["code"],
            "AUTHENTICATION_FAILED",
        )

    def test_unknown_protected_route_is_nested_error(
        self,
    ) -> None:
        response = self.request(
            "GET",
            "/v1/unknown",
        )

        self.assertEqual(
            response.status,
            404,
        )
        self.assertEqual(
            response.body["error"]["code"],
            "ROUTE_NOT_FOUND",
        )
        self.assertEqual(
            response.body["error"]["details"],
            {},
        )

    def test_unknown_workspace_resource_is_nested_error(
        self,
    ) -> None:
        response = self.request(
            "GET",
            f"/v1/projects/{uuid.uuid4()}/workspace",
        )

        self.assertEqual(
            response.status,
            404,
        )
        self.assertEqual(
            response.body["error"]["code"],
            "NOT_FOUND",
        )

    def test_domain_validation_error_is_nested_error(
        self,
    ) -> None:
        response = self.request(
            "POST",
            "/v1/projects",
            {
                "slug": "",
                "name": "Invalid Project",
            },
        )

        self.assertEqual(
            response.status,
            400,
        )
        self.assertEqual(
            response.body["error"]["code"],
            "VALIDATION_ERROR",
        )

    def test_domain_not_found_error_is_nested_error(
        self,
    ) -> None:
        response = self.request(
            "GET",
            f"/v1/projects/{uuid.uuid4()}",
        )

        self.assertEqual(
            response.status,
            404,
        )
        self.assertEqual(
            response.body["error"]["code"],
            "NOT_FOUND",
        )

    def test_domain_conflict_error_is_nested_error(
        self,
    ) -> None:
        created = self.request(
            "POST",
            "/v1/projects",
            {
                "slug": "conflict-project",
                "name": "Conflict Project",
            },
        )
        self.assertEqual(
            created.status,
            201,
        )

        response = self.request(
            "POST",
            "/v1/projects",
            {
                "slug": "conflict-project",
                "name": "Conflict Project",
            },
        )

        self.assertEqual(
            response.status,
            409,
        )
        self.assertEqual(
            response.body["error"]["code"],
            "CONFLICT",
        )

    def test_missing_idempotency_key_is_nested_error(
        self,
    ) -> None:
        project = self.request(
            "POST",
            "/v1/projects",
            {
                "slug": "error-schema-idem",
                "name": "Error Schema Idem",
            },
        )

        response = self.request(
            "POST",
            f"/v1/projects/{project.body['id']}/documents",
            {
                "title": "Requirements",
                "content": "body",
            },
        )

        self.assertEqual(
            response.status,
            400,
        )
        self.assertEqual(
            response.body["error"]["code"],
            "IDEMPOTENCY_KEY_REQUIRED",
        )

    def test_missing_if_match_is_nested_error(
        self,
    ) -> None:
        project = self.request(
            "POST",
            "/v1/projects",
            {
                "slug": "error-schema-etag",
                "name": "Error Schema ETag",
            },
        )

        initial = self.request(
            "POST",
            f"/v1/projects/{project.body['id']}/routing-policy",
            {
                "allow_offline_degraded": True,
                "paid_emergency_enabled": False,
                "max_paid_decisions_per_unlock": 1,
            },
        )

        self.assertEqual(
            initial.status,
            200,
        )

        response = self.request(
            "POST",
            f"/v1/projects/{project.body['id']}/routing-policy",
            {
                "allow_offline_degraded": False,
                "paid_emergency_enabled": True,
                "max_paid_decisions_per_unlock": 2,
            },
        )

        self.assertEqual(
            response.status,
            428,
        )
        self.assertEqual(
            response.body["error"]["code"],
            "PRECONDITION_REQUIRED",
        )

    def test_unexpected_failure_is_internal_error_without_leakage(
        self,
    ) -> None:
        with patch(
            "specgraph_foundry.http_api.gateway.Api.dispatch",
            side_effect=RuntimeError(
                "secret /tmp/path failure"
            ),
        ):
            response = self.request(
                "GET",
                "/v1/projects",
            )

        self.assertEqual(
            response.status,
            500,
        )
        self.assertEqual(
            response.body["error"]["code"],
            "INTERNAL_ERROR",
        )
        self.assertEqual(
            response.body["error"]["message"],
            "request failed",
        )
        self.assertNotIn(
            "/tmp/path",
            json.dumps(response.body),
        )

    def test_successful_responses_remain_unwrapped(
        self,
    ) -> None:
        public_response = self.request(
            "GET",
            "/health",
            authorization=None,
        )
        protected_response = self.request(
            "GET",
            "/v1/me",
        )

        self.assertEqual(
            public_response.status,
            200,
        )
        self.assertIn(
            "status",
            public_response.body,
        )
        self.assertNotIn(
            "error",
            public_response.body,
        )

        self.assertEqual(
            protected_response.status,
            200,
        )
        self.assertIn(
            "user",
            protected_response.body,
        )
        self.assertNotIn(
            "error",
            protected_response.body,
        )

    def test_authenticated_failures_use_nested_error_object(
        self,
    ) -> None:
        response = self.request(
            "GET",
            "/v1/projects",
            authorization=None,
        )

        self.assertIn(
            "error",
            response.body,
        )
        self.assertIsInstance(
            response.body["error"],
            dict,
        )
        self.assertEqual(
            set(response.body["error"]),
            {
                "code",
                "message",
                "request_id",
                "details",
            },
        )


if __name__ == "__main__":
    unittest.main()
