import tempfile
import unittest
from pathlib import Path

from specgraph_foundry.database import Database
from specgraph_foundry.errors import (
    ConflictError,
    ValidationError,
)
from specgraph_foundry.ingestion import (
    IngestionService,
)
from specgraph_foundry.services import (
    ProjectService,
)


class IngestionTest(unittest.TestCase):
    def setUp(self) -> None:
        self.temp = (
            tempfile.TemporaryDirectory()
        )
        self.database = Database(
            Path(self.temp.name)
            / "test.sqlite3"
        )
        self.database.initialize()

        self.projects = ProjectService(
            self.database
        )
        self.ingestion = IngestionService(
            self.database
        )

        self.project = self.projects.create(
            "ingestion-test",
            "Ingestion Test",
        )

    def tearDown(self) -> None:
        self.temp.cleanup()

    def test_utf8_coordinates_and_coverage(
        self,
    ) -> None:
        content = (
            "# Alpha\n"
            "café\n"
            "🙂 unicode line\n"
            "## Beta\n"
            "final line\n"
        )

        document = (
            self.ingestion.ingest_text(
                project_id=str(
                    self.project["id"]
                ),
                title="UTF-8 Source",
                content=content,
                chunk_bytes=14,
            )
        )

        self.assertEqual(
            document["byte_count"],
            len(content.encode("utf-8")),
        )

        self.assertEqual(
            [
                section["title"]
                for section
                in document["sections"]
            ],
            ["Alpha", "Beta"],
        )

        verification = (
            self.ingestion
            .verify_document(
                str(document["id"])
            )
        )

        self.assertTrue(
            verification["valid"]
        )

        reconstructed = (
            self.ingestion.reconstruct(
                str(document["id"])
            )
        )

        self.assertEqual(
            reconstructed,
            content.encode("utf-8"),
        )

        expected_start = 0

        for chunk in document["chunks"]:
            self.assertEqual(
                chunk["byte_start"],
                expected_start,
            )
            expected_start = int(
                chunk["byte_end"]
            )

        self.assertEqual(
            expected_start,
            len(content.encode("utf-8")),
        )

    def test_invalid_utf8_rejected(
        self,
    ) -> None:
        with self.assertRaises(
            ValidationError
        ):
            self.ingestion.ingest_bytes(
                project_id=str(
                    self.project["id"]
                ),
                title="Invalid",
                raw=b"\xff\xfe\xfa",
                media_type="text/plain",
                chunk_bytes=32,
            )

    def test_duplicate_source_rejected(
        self,
    ) -> None:
        content = "same source\n"

        self.ingestion.ingest_text(
            project_id=str(
                self.project["id"]
            ),
            title="First",
            content=content,
        )

        with self.assertRaises(
            ConflictError
        ):
            self.ingestion.ingest_text(
                project_id=str(
                    self.project["id"]
                ),
                title="Duplicate",
                content=content,
            )


if __name__ == "__main__":
    unittest.main()
