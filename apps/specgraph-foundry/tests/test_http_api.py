import json
import tempfile
import unittest
import uuid
from pathlib import Path

from specgraph_foundry.database import (
    Database,
)
from specgraph_foundry.http_api.auth import (
    AuthenticationError,
    SupabaseAuthClient,
)
from specgraph_foundry.http_api.database import (
    RequestScopedDatabase,
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


class HttpApiTest(unittest.TestCase):
    def setUp(self) -> None:
        self.temp = (
            tempfile.TemporaryDirectory()
        )

        self.database = Database(
            Path(self.temp.name)
            / "api.sqlite3"
        )

        self.database.initialize()

        self.principal = Principal(
            user_id=str(uuid.uuid4()),
            email="owner@example.com",
        )

        self.application = (
            AuthenticatedApi(
                self.database,
                FakeAuthenticator(
                    self.principal
                ),
            )
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
        authenticated: bool = True,
    ):
        headers = {}

        if authenticated:
            headers[
                "Authorization"
            ] = "Bearer valid"

        return self.application.dispatch(
            new_request(
                method,
                path,
                headers,
                payload or {},
            )
        )

    def test_public_health(
        self,
    ) -> None:
        response = self.request(
            "GET",
            "/health",
            authenticated=False,
        )

        self.assertEqual(
            response.status,
            200,
        )

        self.assertEqual(
            response.body["status"],
            "ok",
        )

        self.assertNotIn(
            "tables",
            response.body,
        )

    def test_private_routes_require_auth(
        self,
    ) -> None:
        response = self.request(
            "GET",
            "/v1/projects",
            authenticated=False,
        )

        self.assertEqual(
            response.status,
            401,
        )

        self.assertEqual(
            response.body["error"],
            "UNAUTHENTICATED",
        )

    def test_current_user(
        self,
    ) -> None:
        response = self.request(
            "GET",
            "/v1/me",
        )

        self.assertEqual(
            response.status,
            200,
        )

        self.assertEqual(
            response.body["user"]["id"],
            self.principal.user_id,
        )

        self.assertEqual(
            response.body["user"]["email"],
            "owner@example.com",
        )

    def test_existing_project_api_is_wrapped(
        self,
    ) -> None:
        created = self.request(
            "POST",
            "/v1/projects",
            {
                "slug": "gateway-project",
                "name": "Gateway Project",
                "description": (
                    "Authenticated API test"
                ),
            },
        )

        self.assertEqual(
            created.status,
            201,
        )

        listed = self.request(
            "GET",
            "/v1/projects",
        )

        self.assertEqual(
            listed.status,
            200,
        )

        self.assertEqual(
            len(listed.body["items"]),
            1,
        )

        self.assertEqual(
            listed.body[
                "items"
            ][0]["slug"],
            "gateway-project",
        )

    def test_request_database_uses_user(
        self,
    ) -> None:
        database = RequestScopedDatabase(
            self.database,
            self.principal,
        )

        self.assertEqual(
            database.owner_id,
            self.principal.user_id,
        )


class SupabaseAuthClientTest(
    unittest.TestCase
):
    def test_valid_supabase_user(
        self,
    ) -> None:
        user_id = str(uuid.uuid4())
        observed = {}

        def opener(
            request,
            timeout,
        ):
            observed["url"] = (
                request.full_url
            )

            observed["authorization"] = (
                request.get_header(
                    "Authorization"
                )
            )

            observed["apikey"] = (
                request.get_header(
                    "Apikey"
                )
            )

            observed["timeout"] = timeout

            return FakeResponse(
                {
                    "id": user_id,
                    "email": (
                        "owner@example.com"
                    ),
                    "role": (
                        "authenticated"
                    ),
                }
            )

        client = SupabaseAuthClient(
            "https://example.supabase.co",
            "anon-example",
            opener=opener,
        )

        principal = client.authenticate(
            "Bearer token-value"
        )

        self.assertEqual(
            principal.user_id,
            user_id,
        )

        self.assertEqual(
            observed["url"],
            (
                "https://example."
                "supabase.co/auth/v1/user"
            ),
        )

        self.assertEqual(
            observed["authorization"],
            "Bearer token-value",
        )

        self.assertEqual(
            observed["apikey"],
            "anon-example",
        )

    def test_missing_token_rejected(
        self,
    ) -> None:
        client = SupabaseAuthClient(
            "https://example.supabase.co",
            "anon-example",
            opener=lambda *args, **kwargs: (
                None
            ),
        )

        with self.assertRaises(
            AuthenticationError
        ):
            client.authenticate(None)


if __name__ == "__main__":
    unittest.main()
