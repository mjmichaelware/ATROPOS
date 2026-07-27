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


class ApplicationWorkspacesApiTest(
    unittest.TestCase
):
    def setUp(self) -> None:
        self.temp = (
            tempfile.TemporaryDirectory()
        )
        self.database = Database(
            Path(self.temp.name)
            / "workspaces.sqlite3"
        )
        self.database.initialize()

        principal = Principal(
            user_id=str(uuid.uuid4()),
            email="owner@example.com",
        )

        self.application = AuthenticatedApi(
            self.database,
            FakeAuthenticator(principal),
        )

        created = self.request(
            "POST",
            "/v1/projects",
            {
                "slug": (
                    "application-workspaces"
                ),
                "name": (
                    "Application Workspaces"
                ),
                "description": (
                    "Aggregate API test"
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

    def test_empty_domain_workspaces(
        self,
    ) -> None:
        expected = {
            "source-workspace": (
                "summary",
                "documents",
            ),
            "research-workspace": (
                "counts",
                "tasks",
            ),
            "planning-workspace": (
                "counts",
                "plans",
            ),
            "handoff-workspace": (
                "counts",
                "execution_runs",
            ),
        }

        for route, required_keys in (
            expected.items()
        ):
            with self.subTest(route=route):
                response = self.request(
                    "GET",
                    (
                        f"/v1/projects/"
                        f"{self.project_id}/"
                        f"{route}"
                    ),
                )

                self.assertEqual(
                    response.status,
                    200,
                )

                self.assertEqual(
                    response.body[
                        "project"
                    ]["id"],
                    self.project_id,
                )

                for key in required_keys:
                    self.assertIn(
                        key,
                        response.body,
                    )

        source = self.request(
            "GET",
            (
                f"/v1/projects/"
                f"{self.project_id}/"
                "source-workspace"
            ),
        )

        self.assertEqual(
            source.body[
                "summary"
            ]["documents"],
            0,
        )

        research = self.request(
            "GET",
            (
                f"/v1/projects/"
                f"{self.project_id}/"
                "research-workspace"
            ),
        )

        self.assertEqual(
            research.body[
                "counts"
            ]["atoms"],
            0,
        )

        planning = self.request(
            "GET",
            (
                f"/v1/projects/"
                f"{self.project_id}/"
                "planning-workspace"
            ),
        )

        self.assertEqual(
            planning.body[
                "counts"
            ]["plans"],
            0,
        )

        handoff = self.request(
            "GET",
            (
                f"/v1/projects/"
                f"{self.project_id}/"
                "handoff-workspace"
            ),
        )

        self.assertEqual(
            handoff.body[
                "counts"
            ]["exports"],
            0,
        )

        self.assertEqual(
            handoff.body[
                "counts"
            ]["execution_runs"],
            0,
        )

    def test_source_and_research(
        self,
    ) -> None:
        content = (
            "# Requirements\n\n"
            "The system must preserve "
            "exact source provenance.\n"
        )

        ingested = self.request(
            "POST",
            (
                f"/v1/projects/"
                f"{self.project_id}/"
                "documents"
            ),
            {
                "title": "Requirements",
                "content": content,
                "media_type": (
                    "text/markdown"
                ),
            },
        )

        self.assertEqual(
            ingested.status,
            201,
        )

        document_id = str(
            ingested.body["id"]
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

        source = self.request(
            "GET",
            (
                f"/v1/projects/"
                f"{self.project_id}/"
                "source-workspace"
            ),
        )

        self.assertEqual(
            source.status,
            200,
        )

        self.assertEqual(
            source.body[
                "summary"
            ]["documents"],
            1,
        )

        self.assertGreater(
            source.body[
                "summary"
            ]["sections"],
            0,
        )

        self.assertGreater(
            source.body[
                "summary"
            ]["chunks"],
            0,
        )

        self.assertGreater(
            source.body[
                "summary"
            ]["atoms"],
            0,
        )

        self.assertEqual(
            source.body[
                "documents"
            ][0]["sha256"],
            ingested.body["sha256"],
        )

        provenance = self.request(
            "GET",
            (
                f"/v1/documents/"
                f"{document_id}/"
                "provenance"
            ),
        )

        self.assertEqual(
            provenance.status,
            200,
        )

        self.assertEqual(
            provenance.body[
                "document"
            ]["content"],
            content,
        )

        self.assertEqual(
            provenance.body[
                "provenance"
            ]["byte_start"],
            0,
        )

        self.assertEqual(
            provenance.body[
                "provenance"
            ]["byte_end"],
            len(
                content.encode("utf-8")
            ),
        )

        self.assertGreater(
            len(
                provenance.body[
                    "sections"
                ]
            ),
            0,
        )

        self.assertGreater(
            len(
                provenance.body[
                    "chunks"
                ]
            ),
            0,
        )

        self.assertGreater(
            len(
                provenance.body[
                    "atoms"
                ]
            ),
            0,
        )

        research = self.request(
            "GET",
            (
                f"/v1/projects/"
                f"{self.project_id}/"
                "research-workspace"
            ),
        )

        self.assertEqual(
            research.status,
            200,
        )

        self.assertGreater(
            research.body[
                "counts"
            ]["atoms"],
            0,
        )

        self.assertGreater(
            research.body[
                "counts"
            ]["dimensions"],
            0,
        )

        self.assertGreater(
            research.body[
                "counts"
            ]["tasks"],
            0,
        )

        self.assertGreater(
            len(
                research.body[
                    "gap_matrix"
                ]["atoms"]
            ),
            0,
        )

    def test_unknown_resources_return_404(
        self,
    ) -> None:
        missing_project = str(
            uuid.uuid4()
        )

        for route in (
            "source-workspace",
            "research-workspace",
            "planning-workspace",
            "handoff-workspace",
        ):
            with self.subTest(route=route):
                response = self.request(
                    "GET",
                    (
                        f"/v1/projects/"
                        f"{missing_project}/"
                        f"{route}"
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

        provenance = self.request(
            "GET",
            (
                f"/v1/documents/"
                f"{uuid.uuid4()}/"
                "provenance"
            ),
        )

        self.assertEqual(
            provenance.status,
            404,
        )

        self.assertEqual(
            provenance.body["error"],
            "NOT_FOUND",
        )


if __name__ == "__main__":
    unittest.main()
