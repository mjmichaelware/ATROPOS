import tempfile
import unittest
from pathlib import Path

from specgraph_foundry.api import Api
from specgraph_foundry.database import Database


class ResearchApiTest(unittest.TestCase):
    def setUp(self) -> None:
        self.temp = tempfile.TemporaryDirectory()
        self.database = Database(
            Path(self.temp.name) / "test.sqlite3"
        )
        self.database.initialize()
        self.api = Api(self.database)

    def tearDown(self) -> None:
        self.temp.cleanup()

    def test_full_research_api_flow(self) -> None:
        status, project = self.api.dispatch(
            "POST",
            "/v1/projects",
            {
                "slug": "api-test",
                "name": "API Test",
            },
        )
        self.assertEqual(status, 201)

        project_id = str(project["id"])

        status, document = self.api.dispatch(
            "POST",
            f"/v1/projects/{project_id}/documents",
            {
                "title": "Source",
                "content": (
                    "The API must retain provenance.\n"
                ),
                "chunk_bytes": 32,
            },
        )
        self.assertEqual(status, 201)

        document_id = str(document["id"])

        status, extraction = self.api.dispatch(
            "POST",
            f"/v1/documents/{document_id}/extract",
            {},
        )
        self.assertEqual(status, 200)
        self.assertEqual(
            extraction["atom_count"],
            1,
        )

        status, claimed = self.api.dispatch(
            "POST",
            (
                f"/v1/projects/{project_id}"
                "/research-tasks/claim"
            ),
            {
                "worker_id": "api-worker",
                "lease_seconds": 300,
            },
        )
        self.assertEqual(status, 200)

        task = claimed["task"]
        task_id = str(task["id"])

        status, evidence = self.api.dispatch(
            "POST",
            (
                f"/v1/research-tasks/{task_id}"
                "/evidence"
            ),
            {
                "worker_id": "api-worker",
                "source_uri": (
                    "https://example.test/standard"
                ),
                "source_title": "Standard",
                "excerpt": (
                    "Provenance must be retained."
                ),
                "evidence_type": "STANDARD",
                "reliability": 0.95,
            },
        )
        self.assertEqual(status, 201)

        status, completed = self.api.dispatch(
            "POST",
            (
                f"/v1/research-tasks/{task_id}"
                "/complete"
            ),
            {
                "worker_id": "api-worker",
                "conclusion": (
                    "Durable provenance is required."
                ),
                "applicability": "APPLICABLE",
                "confidence": 0.94,
                "evidence_ids": [
                    str(evidence["id"])
                ],
            },
        )
        self.assertEqual(status, 200)
        self.assertEqual(
            completed["status"],
            "COMPLETE",
        )

        status, matrix = self.api.dispatch(
            "GET",
            f"/v1/projects/{project_id}/gap-matrix",
            {},
        )
        self.assertEqual(status, 200)
        self.assertEqual(
            matrix["summary"][
                "resolved_dimensions"
            ],
            1,
        )


if __name__ == "__main__":
    unittest.main()
