import tempfile
import unittest
from pathlib import Path

from specgraph_foundry.database import Database
from specgraph_foundry.http_api.auth import AuthenticationError
from specgraph_foundry.http_api.gateway import AuthenticatedApi, new_request
from specgraph_foundry.http_api.health import (
    live_response,
    readiness_response,
    startup_response,
)
from specgraph_foundry.http_api.models import Principal


class FakeAuthenticator:
    def authenticate(self, authorization: str | None) -> Principal:
        raise AuthenticationError("auth should not be called for health")


class HealthApiTest(unittest.TestCase):
    def setUp(self) -> None:
        self.temp = tempfile.TemporaryDirectory()
        self.database = Database(Path(self.temp.name) / "health.sqlite3")
        self.database.initialize()
        self.api = AuthenticatedApi(self.database, FakeAuthenticator())

    def tearDown(self) -> None:
        self.temp.cleanup()

    def request(self, path: str):
        return self.api.dispatch(new_request("GET", path, {}, {}))

    def test_public_health_routes_do_not_require_auth(self) -> None:
        for path in (
            "/health",
            "/health/live",
            "/health/startup",
            "/health/ready",
            "/version",
        ):
            with self.subTest(path=path):
                response = self.request(path)
                self.assertIn(response.status, {200, 503})
                self.assertIn("x-request-id", response.headers)
                self.assertEqual(response.headers["cache-control"], "no-store")

    def test_liveness_is_process_only(self) -> None:
        self.assertEqual(
            live_response(),
            {"status": "ok", "service": "specgraph-foundry"},
        )

    def test_startup_and_readiness_are_safe(self) -> None:
        startup_status, startup_body = startup_response(self.database)
        self.assertEqual(startup_status, 200)
        self.assertEqual(startup_body["checks"]["configuration"], "ready")

        ready_status, ready_body = readiness_response(
            self.database,
            storage_ready=True,
            operations_ready=True,
        )
        self.assertEqual(ready_status, 200)
        self.assertEqual(ready_body["checks"]["database"], "ready")
        self.assertNotIn("tables", ready_body)
        self.assertNotIn("server_version", ready_body)

    def test_readiness_failure_is_generic(self) -> None:
        status, body = readiness_response(
            self.database,
            storage_ready=False,
            operations_ready=True,
        )
        self.assertEqual(status, 503)
        self.assertEqual(body["checks"]["storage"], "unavailable")
        self.assertNotIn("bucket", str(body).lower())
        self.assertNotIn("sqlite", str(body).lower())


if __name__ == "__main__":
    unittest.main()
