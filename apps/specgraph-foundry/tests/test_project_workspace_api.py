import tempfile
import unittest
import uuid
from pathlib import Path

from specgraph_foundry.database import (
    Database,
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


class ProjectWorkspaceApiTest(
    unittest.TestCase
):
    def setUp(self) -> None:
        self.temp = (
            tempfile.TemporaryDirectory()
        )

        self.database = Database(
            Path(self.temp.name)
            / "workspace.sqlite3"
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

        created = self.request(
            "POST",
            "/v1/projects",
            {
                "slug": (
                    "workspace-project"
                ),
                "name": (
                    "Workspace Project"
                ),
                "description": (
                    "Workspace API test"
                ),
            },
        )

        self.assertEqual(
            created.status,
            201,
        )

        self.project_id = str(
            created.body["id"]
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
    ):
        return self.application.dispatch(
            new_request(
                method,
                path,
                {
                    "Authorization": (
                        "Bearer valid"
                    )
                },
                payload or {},
            )
        )

    def test_empty_project_requires_source(
        self,
    ) -> None:
        response = self.request(
            "GET",
            (
                f"/v1/projects/"
                f"{self.project_id}/workspace"
            ),
        )

        self.assertEqual(
            response.status,
            200,
        )

        self.assertEqual(
            response.body[
                "readiness"
            ]["status"],
            "SOURCE_REQUIRED",
        )

        self.assertEqual(
            response.body[
                "readiness"
            ]["next_action"],
            "INGEST_SOURCE",
        )

        self.assertEqual(
            response.body[
                "counts"
            ]["documents"],
            0,
        )

        self.assertEqual(
            response.body[
                "counts"
            ]["atoms"],
            0,
        )

    def test_document_requires_extraction(
        self,
    ) -> None:
        document = self.request(
            "POST",
            (
                f"/v1/projects/"
                f"{self.project_id}/documents"
            ),
            {
                "title": "Requirements",
                "content": (
                    "# Requirements\n\n"
                    "The system must preserve "
                    "exact source provenance.\n"
                ),
                "media_type": (
                    "text/markdown"
                ),
            },
        )

        self.assertEqual(
            document.status,
            201,
        )

        response = self.request(
            "GET",
            (
                f"/v1/projects/"
                f"{self.project_id}/readiness"
            ),
        )

        self.assertEqual(
            response.status,
            200,
        )

        self.assertEqual(
            response.body[
                "readiness"
            ]["status"],
            "EXTRACTION_REQUIRED",
        )

        self.assertEqual(
            response.body[
                "counts"
            ]["documents"],
            1,
        )

        self.assertEqual(
            response.body[
                "counts"
            ]["atoms"],
            0,
        )

    def test_extracted_atoms_require_research(
        self,
    ) -> None:
        document = self.request(
            "POST",
            (
                f"/v1/projects/"
                f"{self.project_id}/documents"
            ),
            {
                "title": "Requirements",
                "content": (
                    "# Requirements\n\n"
                    "The system must preserve "
                    "exact source provenance.\n"
                ),
                "media_type": (
                    "text/markdown"
                ),
            },
        )

        document_id = str(
            document.body["id"]
        )

        extracted = self.request(
            "POST",
            (
                f"/v1/documents/"
                f"{document_id}/extract"
            ),
        )

        self.assertEqual(
            extracted.status,
            200,
        )

        response = self.request(
            "GET",
            (
                f"/v1/projects/"
                f"{self.project_id}/workspace"
            ),
        )

        self.assertEqual(
            response.status,
            200,
        )

        self.assertGreater(
            response.body[
                "counts"
            ]["atoms"],
            0,
        )

        self.assertGreater(
            response.body[
                "counts"
            ]["open_dimensions"],
            0,
        )

        self.assertEqual(
            response.body[
                "readiness"
            ]["status"],
            "RESEARCH_REQUIRED",
        )

        self.assertEqual(
            response.body[
                "readiness"
            ]["next_action"],
            "COMPLETE_OPEN_DIMENSIONS",
        )

    def test_unknown_project_returns_404(
        self,
    ) -> None:
        response = self.request(
            "GET",
            (
                "/v1/projects/"
                f"{uuid.uuid4()}/workspace"
            ),
        )

        self.assertEqual(
            response.status,
            404,
        )

        self.assertEqual(
            response.body["error"],
            "NOT_FOUND",
        )


if __name__ == "__main__":
    unittest.main()
