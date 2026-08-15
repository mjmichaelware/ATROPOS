import importlib
import inspect
import tempfile
import unittest
import uuid
from pathlib import Path

from specgraph_foundry.atoms import AtomService
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
from specgraph_foundry.ingestion import IngestionService
from specgraph_foundry.planning import (
    PlanningService,
)
from specgraph_foundry.services import ProjectService


SIGNING_KEY = (
    "test-signing-key-00000000000000000000000000000000"
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


class PaginationApiTest(unittest.TestCase):
    def setUp(self) -> None:
        self.temp = (
            tempfile.TemporaryDirectory()
        )
        self.database = Database(
            Path(self.temp.name)
            / "pagination.sqlite3"
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
            cursor_signing_key=SIGNING_KEY,
        )
        self.other_application = (
            AuthenticatedApi(
                self.database,
                FakeAuthenticator(
                    Principal(
                        user_id=str(uuid.uuid4()),
                        email="other@example.com",
                    )
                ),
                cursor_signing_key=SIGNING_KEY,
            )
        )
        self.projects = ProjectService(
            self.database
        )
        self.ingestion = IngestionService(
            self.database
        )
        self.atoms = AtomService(
            self.database
        )
        self.planning = PlanningService(
            self.database
        )
        self.project = self.projects.create(
            "pagination-project",
            "Pagination Project",
            "",
        )
        self.project_ids = [
            str(self.project["id"])
        ]
        for index in range(6):
            created = self.projects.create(
                f"pagination-project-{index}",
                f"Pagination Project {index}",
                "",
            )
            self.project_ids.append(
                str(created["id"])
            )
        self.project_id = str(
            self.project["id"]
        )
        self.document_id = self._seed_atom_document()
        self.document_ids = self._seed_documents(6)
        self._seed_relations()

    def tearDown(self) -> None:
        self.temp.cleanup()

    def request(
        self,
        method: str,
        path: str,
        *,
        application: AuthenticatedApi | None = None,
    ):
        return (
            application or self.application
        ).dispatch(
            new_request(
                method,
                path,
                {
                    "Authorization": (
                        "Bearer valid"
                    )
                },
                {},
            )
        )

    def test_documents_default_limit_and_headers(
        self,
    ) -> None:
        response = self.request(
            "GET",
            f"/v1/projects/{self.project_id}/documents",
        )

        self.assertEqual(response.status, 200)
        self.assertEqual(
            response.headers["x-page-limit"],
            "50",
        )
        self.assertEqual(
            response.headers["x-page-count"],
            "7",
        )
        self.assertEqual(
            response.headers["x-has-more"],
            "false",
        )
        self.assertNotIn(
            "x-next-cursor",
            response.headers,
        )
        self.assertEqual(
            response.headers["cache-control"],
            "no-store",
        )
        self.assertIn(
            "x-request-id",
            response.headers,
        )

    def test_projects_default_limit_and_headers(
        self,
    ) -> None:
        response = self.request(
            "GET",
            "/v1/projects",
        )

        self.assertEqual(response.status, 200)
        self.assertEqual(
            response.headers["x-page-limit"],
            "50",
        )
        self.assertEqual(
            response.headers["x-page-count"],
            "7",
        )
        self.assertEqual(
            response.headers["x-has-more"],
            "false",
        )
        self.assertNotIn(
            "x-next-cursor",
            response.headers,
        )

    def test_projects_multiple_pages_have_no_duplicates_or_omissions(
        self,
    ) -> None:
        seen: list[str] = []
        cursor = None

        while True:
            suffix = (
                f"?limit=2&cursor={cursor}"
                if cursor is not None
                else "?limit=2"
            )
            response = self.request(
                "GET",
                f"/v1/projects{suffix}",
            )
            self.assertEqual(response.status, 200)
            seen.extend(
                str(item["id"])
                for item in response.body["items"]
            )
            cursor = response.headers.get(
                "x-next-cursor"
            )

            if cursor is None:
                self.assertEqual(
                    response.headers["x-has-more"],
                    "false",
                )
                break

        self.assertEqual(
            len(seen),
            len(set(seen)),
        )
        self.assertEqual(
            seen,
            [
                str(item["id"])
                for item in self.projects.list()
            ],
        )

    def test_project_cursor_rejects_wrong_owner(
        self,
    ) -> None:
        first = self.request(
            "GET",
            "/v1/projects?limit=2",
        )
        cursor = first.headers["x-next-cursor"]
        rejected = self.request(
            "GET",
            f"/v1/projects?limit=2&cursor={cursor}",
            application=self.other_application,
        )

        self.assertEqual(rejected.status, 400)
        self.assertEqual(
            rejected.body["error"]["code"],
            "VALIDATION_ERROR",
        )

    def test_documents_multiple_pages_have_no_duplicates_or_omissions(
        self,
    ) -> None:
        seen: list[str] = []
        cursor = None

        while True:
            suffix = (
                f"?limit=2&cursor={cursor}"
                if cursor is not None
                else "?limit=2"
            )
            response = self.request(
                "GET",
                (
                    f"/v1/projects/{self.project_id}"
                    f"/documents{suffix}"
                ),
            )
            self.assertEqual(response.status, 200)
            seen.extend(
                str(item["id"])
                for item in response.body["items"]
            )
            cursor = response.headers.get(
                "x-next-cursor"
            )

            if cursor is None:
                self.assertEqual(
                    response.headers["x-has-more"],
                    "false",
                )
                break

        self.assertEqual(
            len(seen),
            len(set(seen)),
        )
        self.assertEqual(
            seen,
            [
                str(item["id"])
                for item in self.ingestion.list_documents(
                    self.project_id
                )
            ],
        )

    def test_atoms_multiple_pages(
        self,
    ) -> None:
        first = self.request(
            "GET",
            (
                f"/v1/documents/{self.document_id}"
                "/atoms?limit=2"
            ),
        )
        self.assertEqual(first.status, 200)
        self.assertEqual(
            first.headers["x-has-more"],
            "true",
        )
        second = self.request(
            "GET",
            (
                f"/v1/documents/{self.document_id}"
                "/atoms?limit=2&cursor="
                f"{first.headers['x-next-cursor']}"
            ),
        )
        third = self.request(
            "GET",
            (
                f"/v1/documents/{self.document_id}"
                "/atoms?limit=2&cursor="
                f"{second.headers['x-next-cursor']}"
            ),
        )

        atom_ids = [
            str(item["id"])
            for response in (first, second, third)
            for item in response.body["items"]
        ]

        self.assertEqual(
            atom_ids,
            [
                str(item["id"])
                for item in self.atoms.list_atoms(
                    self.document_id
                )
            ],
        )
        self.assertNotIn(
            "x-next-cursor",
            third.headers,
        )

    def test_research_tasks_multiple_pages(
        self,
    ) -> None:
        seen: list[str] = []
        cursor = None

        for _ in range(3):
            suffix = (
                f"?limit=7&cursor={cursor}"
                if cursor is not None
                else "?limit=7"
            )
            response = self.request(
                "GET",
                (
                    f"/v1/projects/{self.project_id}"
                    f"/research-tasks{suffix}"
                ),
            )
            self.assertEqual(response.status, 200)
            seen.extend(
                str(item["id"])
                for item in response.body["items"]
            )
            cursor = response.headers.get(
                "x-next-cursor"
            )
            if cursor is None:
                break

        self.assertEqual(
            seen,
            [
                str(item["id"])
                for item in self.atoms.list_research_tasks(
                    self.project_id
                )[: len(seen)]
            ],
        )

    def test_relations_multiple_pages(
        self,
    ) -> None:
        first = self.request(
            "GET",
            (
                f"/v1/projects/{self.project_id}"
                "/relations?limit=3"
            ),
        )
        second = self.request(
            "GET",
            (
                f"/v1/projects/{self.project_id}"
                "/relations?limit=3&cursor="
                f"{first.headers['x-next-cursor']}"
            ),
        )

        relation_ids = [
            str(item["id"])
            for response in (first, second)
            for item in response.body["items"]
        ]
        self.assertEqual(
            relation_ids,
            [
                str(item["id"])
                for item in self.planning.list_relations(
                    self.project_id
                )
            ],
        )
        self.assertEqual(
            second.headers["x-has-more"],
            "false",
        )

    def test_empty_collection_uses_headers_without_cursor(
        self,
    ) -> None:
        empty_project = self.projects.create(
            "empty-pagination-project",
            "Empty Pagination Project",
            "",
        )
        response = self.request(
            "GET",
            (
                "/v1/projects/"
                f"{empty_project['id']}/documents"
            ),
        )

        self.assertEqual(response.status, 200)
        self.assertEqual(
            response.body["items"],
            [],
        )
        self.assertEqual(
            response.headers["x-page-count"],
            "0",
        )
        self.assertEqual(
            response.headers["x-has-more"],
            "false",
        )
        self.assertNotIn(
            "x-next-cursor",
            response.headers,
        )

    def test_invalid_limit_and_duplicate_parameter_rejected(
        self,
    ) -> None:
        for suffix in (
            "?limit=0",
            "?limit=-1",
            "?limit=abc",
            "?limit=101",
            "?limit=1&limit=2",
        ):
            with self.subTest(
                suffix=suffix
            ):
                response = self.request(
                    "GET",
                    (
                        f"/v1/projects/{self.project_id}"
                        f"/documents{suffix}"
                    ),
                )
                self.assertEqual(
                    response.status,
                    400,
                )
                self.assertEqual(
                    response.body["error"][
                        "code"
                    ],
                    "VALIDATION_ERROR",
                )

    def test_unsupported_query_parameter_rejected(
        self,
    ) -> None:
        response = self.request(
            "GET",
            (
                f"/v1/projects/{self.project_id}"
                "/documents?offset=1"
            ),
        )
        self.assertEqual(response.status, 400)
        self.assertEqual(
            response.body["error"]["code"],
            "VALIDATION_ERROR",
        )

    def test_invalid_cursor_shapes_are_rejected(
        self,
    ) -> None:
        for cursor in (
            "",
            "not-a-cursor",
            "x" * 513,
            "%%%bad",
        ):
            path = (
                f"/v1/projects/{self.project_id}"
                "/documents?cursor="
                f"{cursor}"
            )
            response = self.request(
                "GET",
                path,
            )
            self.assertEqual(
                response.status,
                400,
            )
            self.assertEqual(
                response.body["error"][
                    "code"
                ],
                "VALIDATION_ERROR",
            )

    def test_wrong_collection_parent_and_owner_cursor_rejected(
        self,
    ) -> None:
        documents = self.request(
            "GET",
            (
                f"/v1/projects/{self.project_id}"
                "/documents?limit=2"
            ),
        )
        cursor = documents.headers[
            "x-next-cursor"
        ]

        wrong_collection = self.request(
            "GET",
            (
                f"/v1/projects/{self.project_id}"
                "/research-tasks?cursor="
                f"{cursor}"
            ),
        )
        self.assertEqual(
            wrong_collection.status,
            400,
        )

        other_project = self.projects.create(
            "other-project",
            "Other Project",
            "",
        )
        wrong_parent = self.request(
            "GET",
            (
                f"/v1/projects/{other_project['id']}"
                "/documents?cursor="
                f"{cursor}"
            ),
        )
        self.assertEqual(
            wrong_parent.status,
            400,
        )

        wrong_owner = self.request(
            "GET",
            (
                f"/v1/projects/{self.project_id}"
                "/documents?cursor="
                f"{cursor}"
            ),
            application=self.other_application,
        )
        self.assertEqual(
            wrong_owner.status,
            400,
        )

    def test_altered_signature_rejected(
        self,
    ) -> None:
        first = self.request(
            "GET",
            (
                f"/v1/projects/{self.project_id}"
                "/documents?limit=2"
            ),
        )
        cursor = first.headers["x-next-cursor"]
        payload, signature = cursor.split(
            ".",
            1,
        )
        altered = ".".join(
            (
                (
                    "A" + payload[1:]
                    if payload[0] != "A"
                    else "B" + payload[1:]
                ),
                signature,
            )
        )
        response = self.request(
            "GET",
            (
                f"/v1/projects/{self.project_id}"
                "/documents?cursor="
                f"{altered}"
            ),
        )
        self.assertEqual(response.status, 400)
        self.assertEqual(
            response.body["error"]["code"],
            "VALIDATION_ERROR",
        )

    def test_missing_signing_key_fails_closed_without_leakage(
        self,
    ) -> None:
        application = AuthenticatedApi(
            self.database,
            FakeAuthenticator(
                self.principal
            ),
        )
        response = application.dispatch(
            new_request(
                "GET",
                (
                    f"/v1/projects/{self.project_id}"
                    "/documents?limit=2"
                ),
                {
                    "Authorization": (
                        "Bearer valid"
                    )
                },
                {},
            )
        )

        self.assertEqual(response.status, 500)
        self.assertEqual(
            response.body["error"]["code"],
            "INTERNAL_ERROR",
        )
        self.assertNotIn(
            "cursor signing key",
            str(response.body),
        )

    def test_same_created_at_uses_id_tie_breaker(
        self,
    ) -> None:
        timestamp = "2026-01-01T00:00:00+00:00"
        with self.database.connect() as connection:
            connection.execute(
                """
                UPDATE source_documents
                SET created_at = ?
                WHERE project_id = ?
                """,
                (
                    timestamp,
                    self.project_id,
                ),
            )

        response = self.request(
            "GET",
            (
                f"/v1/projects/{self.project_id}"
                "/documents?limit=20"
            ),
        )
        ids = [
            str(item["id"])
            for item in response.body["items"]
        ]
        self.assertEqual(ids, sorted(ids))

    def test_seek_queries_do_not_use_offset(
        self,
    ) -> None:
        for method in (
            IngestionService.list_documents_page,
            AtomService.list_atoms_page,
            AtomService.list_research_tasks_page,
            PlanningService.list_relations_page,
        ):
            # Follow a delegating method through to the module function that
            # holds the query. Splitting a service into focused modules moves
            # where the SQL lives; it must not move whether it seeks.
            source = inspect.getsource(method)

            if "Delegates to" in source:
                target = source.split("return ", 1)[1].split("(", 1)[0].strip()
                module = importlib.import_module(
                    "specgraph_foundry."
                    + source.split(":func:`", 1)[1].split(".", 1)[0]
                )
                source = inspect.getsource(getattr(module, target))

            self.assertNotIn("OFFSET", source)
            self.assertIn("LIMIT ?", source)

    def _seed_documents(
        self,
        count: int,
    ) -> list[str]:
        document_ids: list[str] = []

        for index in range(count):
            document = self.ingestion.ingest_text(
                project_id=self.project_id,
                title=f"Document {index}",
                content=(
                    f"Document {index} must exist.\n"
                ),
                media_type="text/plain",
                chunk_bytes=64,
            )
            document_ids.append(
                str(document["id"])
            )

        return document_ids

    def _seed_atom_document(
        self,
    ) -> str:
        document = self.ingestion.ingest_text(
            project_id=self.project_id,
            title="Atoms",
            content=(
                "# Requirements\n\n"
                "The system must preserve provenance.\n"
                "The system must bound workspace previews.\n"
                "The system must paginate collections.\n"
                "The system must protect cursors.\n"
                "The system must reject invalid limits.\n"
                "The system must preserve ownership.\n"
            ),
            media_type="text/markdown",
            chunk_bytes=64,
        )
        self.atoms.extract_document(
            str(document["id"])
        )
        return str(document["id"])

    def _seed_relations(
        self,
    ) -> None:
        atoms = self.atoms.list_atoms(
            self.document_id
        )

        for index in range(5):
            self.planning.add_relation(
                project_id=self.project_id,
                from_atom_id=str(
                    atoms[index]["id"]
                ),
                to_atom_id=str(
                    atoms[index + 1]["id"]
                ),
                relation_type="REQUIRES",
                confidence=0.9,
            )

        self.planning.add_relation(
            project_id=self.project_id,
            from_atom_id=str(atoms[0]["id"]),
            to_atom_id=str(atoms[2]["id"]),
            relation_type="RELATES_TO",
            confidence=0.8,
        )


if __name__ == "__main__":
    unittest.main()
