import tempfile
import unittest
import uuid
from pathlib import Path

from specgraph_foundry.atoms import AtomService
from specgraph_foundry.database import Database
from specgraph_foundry.exports import ExportService
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
from specgraph_foundry.routing import RoutingService
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


class BoundedWorkspaceTest(
    unittest.TestCase
):
    def setUp(self) -> None:
        self.temp = (
            tempfile.TemporaryDirectory()
        )
        self.database = Database(
            Path(self.temp.name)
            / "bounded-workspaces.sqlite3"
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
        self.exports = ExportService(
            self.database
        )
        self.routing = RoutingService(
            self.database
        )
        project = self.projects.create(
            "workspace-bounds",
            "Workspace Bounds",
            "",
        )
        self.project_id = str(project["id"])
        self.preview_document_id = (
            self._seed_documents()
        )
        self._seed_relations_and_plan()
        self._seed_handoff_config()

    def tearDown(self) -> None:
        self.temp.cleanup()

    def request(
        self,
        path: str,
    ):
        return self.application.dispatch(
            new_request(
                "GET",
                path,
                {
                    "Authorization": (
                        "Bearer valid"
                    )
                },
                {},
            )
        )

    def test_source_workspace_is_bounded(
        self,
    ) -> None:
        response = self.request(
            (
                f"/v1/projects/{self.project_id}"
                "/source-workspace"
            )
        )
        self.assertEqual(response.status, 200)
        self.assertLessEqual(
            len(response.body["documents"]),
            5,
        )
        self.assertEqual(
            response.body["documents_count"],
            7,
        )
        self.assertTrue(
            response.body["documents_has_more"]
        )
        self.assertEqual(
            response.body["documents_route"],
            (
                f"/v1/projects/{self.project_id}"
                "/documents"
            ),
        )

    def test_provenance_is_bounded(
        self,
    ) -> None:
        response = self.request(
            (
                f"/v1/documents/{self.preview_document_id}"
                "/provenance"
            )
        )
        self.assertEqual(response.status, 200)
        for key in (
            "sections",
            "chunks",
            "atoms",
            "ingestion_runs",
            "extraction_runs",
        ):
            self.assertLessEqual(
                len(response.body[key]),
                5,
            )

        self.assertGreater(
            response.body["sections_count"],
            5,
        )
        self.assertGreater(
            response.body["chunks_count"],
            5,
        )
        self.assertGreater(
            response.body["atoms_count"],
            5,
        )
        self.assertTrue(
            response.body["sections_has_more"]
        )
        self.assertTrue(
            response.body["chunks_has_more"]
        )
        self.assertTrue(
            response.body["atoms_has_more"]
        )
        self.assertEqual(
            response.body["atoms_route"],
            (
                f"/v1/documents/{self.preview_document_id}"
                "/atoms"
            ),
        )
        self.assertIn(
            "content_truncated",
            response.body["document"],
        )

    def test_research_workspace_is_bounded(
        self,
    ) -> None:
        response = self.request(
            (
                f"/v1/projects/{self.project_id}"
                "/research-workspace"
            )
        )
        self.assertEqual(response.status, 200)
        self.assertLessEqual(
            len(response.body["tasks"]),
            5,
        )
        self.assertLessEqual(
            len(
                response.body["gap_matrix"][
                    "atoms"
                ]
            ),
            5,
        )
        self.assertGreater(
            response.body["tasks_count"],
            5,
        )
        self.assertTrue(
            response.body["tasks_has_more"]
        )
        self.assertEqual(
            response.body["tasks_route"],
            (
                f"/v1/projects/{self.project_id}"
                "/research-tasks"
            ),
        )

    def test_planning_workspace_is_bounded_and_keeps_latest(
        self,
    ) -> None:
        response = self.request(
            (
                f"/v1/projects/{self.project_id}"
                "/planning-workspace"
            )
        )
        self.assertEqual(response.status, 200)
        self.assertLessEqual(
            len(response.body["relations"]),
            5,
        )
        self.assertTrue(
            response.body["relations_has_more"]
        )
        self.assertEqual(
            response.body["relations_count"],
            6,
        )
        self.assertLessEqual(
            len(response.body["plans"]),
            5,
        )
        self.assertIsNotNone(
            response.body["latest_plan"]
        )
        self.assertIn(
            "detail_route",
            response.body["latest_plan"],
        )

    def test_handoff_workspace_is_bounded(
        self,
    ) -> None:
        response = self.request(
            (
                f"/v1/projects/{self.project_id}"
                "/handoff-workspace"
            )
        )
        self.assertEqual(response.status, 200)
        for key in (
            "bindings",
            "providers",
            "renderers",
            "exports",
            "execution_runs",
        ):
            self.assertLessEqual(
                len(response.body[key]),
                5,
            )

        self.assertEqual(
            response.body["bindings_count"],
            6,
        )
        self.assertEqual(
            response.body["providers_count"],
            6,
        )
        self.assertEqual(
            response.body["renderers_count"],
            6,
        )
        self.assertTrue(
            response.body["bindings_has_more"]
        )
        self.assertTrue(
            response.body["providers_has_more"]
        )
        self.assertTrue(
            response.body["renderers_has_more"]
        )

    def test_unknown_workspace_resource_remains_hidden(
        self,
    ) -> None:
        response = self.request(
            (
                f"/v1/projects/{uuid.uuid4()}"
                "/source-workspace"
            )
        )
        self.assertEqual(response.status, 404)
        self.assertEqual(
            response.body["error"]["code"],
            "NOT_FOUND",
        )

    def _seed_documents(self) -> str:
        preview_document = self.ingestion.ingest_text(
            project_id=self.project_id,
            title="Preview Source",
            content=self._preview_content(),
            media_type="text/markdown",
            chunk_bytes=48,
        )
        self.atoms.extract_document(
            str(preview_document["id"])
        )

        for index in range(6):
            self.ingestion.ingest_text(
                project_id=self.project_id,
                title=f"Extra {index}",
                content=(
                    f"Extra document {index}.\n"
                ),
                media_type="text/plain",
                chunk_bytes=48,
            )

        return str(preview_document["id"])

    def _seed_relations_and_plan(
        self,
    ) -> None:
        atoms = self.atoms.list_atoms(
            self.preview_document_id
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

        self.planning.synthesize(
            self.project_id,
            allow_open_research=True,
        )

    def _seed_handoff_config(
        self,
    ) -> None:
        for index in range(6):
            self.exports.bind_integration(
                project_id=self.project_id,
                system_name=f"system-{index}",
                binding_type="EXPORT",
                config={"path": f"/tmp/{index}"},
            )
            self.routing.configure_provider(
                project_id=self.project_id,
                name=f"provider-{index}",
                provider_class="LOCAL_TOOLCHAIN",
                cost_class="LOCAL",
                territories=["CODE"],
                priority=index,
                metadata={},
            )
            self.routing.configure_renderer(
                project_id=self.project_id,
                name=f"renderer-{index}",
                renderer_type="MARKDOWN",
                territories=["CODE"],
                priority=index,
                metadata={},
            )

    @staticmethod
    def _preview_content() -> str:
        sections = []
        for index in range(6):
            sections.append(
                (
                    f"# Section {index}\n\n"
                    f"The system must retain source "
                    f"provenance {index}.\n"
                    + ("A" * 90)
                    + "\n"
                )
            )
        return "\n".join(sections)


if __name__ == "__main__":
    unittest.main()
