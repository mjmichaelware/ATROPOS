import tempfile
import unittest
from pathlib import Path

from specgraph_foundry.atoms import (
    AtomService,
    DIMENSIONS,
)
from specgraph_foundry.database import (
    Database,
)
from specgraph_foundry.ingestion import (
    IngestionService,
)
from specgraph_foundry.services import (
    ProjectService,
)


class AtomExtractionTest(
    unittest.TestCase
):
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
        self.ingestion = (
            IngestionService(
                self.database
            )
        )
        self.atoms = AtomService(
            self.database
        )

        self.project = (
            self.projects.create(
                "atom-test",
                "Atom Test",
            )
        )

    def tearDown(self) -> None:
        self.temp.cleanup()

    def ingest(
        self,
        content: str,
    ) -> dict[str, object]:
        return self.ingestion.ingest_text(
            project_id=str(
                self.project["id"]
            ),
            title="Source",
            content=content,
            chunk_bytes=32,
        )

    def test_exact_utf8_coordinates(
        self,
    ) -> None:
        content = (
            "# Requirements\n"
            "The API must preserve café data.\n"
            "The client must not expose secrets.\n"
        )

        document = self.ingest(
            content
        )

        extraction = (
            self.atoms.extract_document(
                str(document["id"])
            )
        )

        self.assertEqual(
            extraction["atom_count"],
            2,
        )

        raw = content.encode("utf-8")

        for atom in extraction["atoms"]:
            exact = raw[
                int(atom["byte_start"]):
                int(atom["byte_end"])
            ].decode("utf-8")

            self.assertEqual(
                exact,
                atom["exact_quote"],
            )

        modalities = [
            atom["modality"]
            for atom
            in extraction["atoms"]
        ]

        self.assertEqual(
            modalities,
            [
                "MUST",
                "PROHIBITED",
            ],
        )

        kinds = [
            atom["kind"]
            for atom
            in extraction["atoms"]
        ]

        self.assertEqual(
            kinds,
            [
                "DATA",
                "SECURITY",
            ],
        )

    def test_idempotent_extraction(
        self,
    ) -> None:
        document = self.ingest(
            "The service must start.\n"
        )

        first = (
            self.atoms.extract_document(
                str(document["id"])
            )
        )

        second = (
            self.atoms.extract_document(
                str(document["id"])
            )
        )

        self.assertEqual(
            first["id"],
            second["id"],
        )

        self.assertEqual(
            first["dimension_count"],
            len(DIMENSIONS),
        )

        self.assertEqual(
            first["research_task_count"],
            len(DIMENSIONS),
        )

        atom = self.atoms.get_atom(
            str(first["atoms"][0]["id"])
        )

        self.assertEqual(
            len(atom["dimensions"]),
            len(DIMENSIONS),
        )

        self.assertEqual(
            len(
                atom["research_tasks"]
            ),
            len(DIMENSIONS),
        )

    def test_headings_and_code_ignored(
        self,
    ) -> None:
        content = (
            "# Heading only\n"
            "```python\n"
            "service.must_start()\n"
            "```\n"
            "- The worker should retry failures.\n"
        )

        document = self.ingest(
            content
        )

        extraction = (
            self.atoms.extract_document(
                str(document["id"])
            )
        )

        self.assertEqual(
            extraction["atom_count"],
            1,
        )

        atom = extraction["atoms"][0]

        self.assertEqual(
            atom["canonical_statement"],
            (
                "The worker should retry "
                "failures."
            ),
        )

        self.assertEqual(
            atom["modality"],
            "SHOULD",
        )


if __name__ == "__main__":
    unittest.main()
