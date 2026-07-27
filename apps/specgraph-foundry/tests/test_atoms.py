import tempfile
import unittest
from pathlib import Path

from specgraph_foundry.atoms import (
    EXTRACTOR_VERSION,
    AtomService,
    DIMENSIONS,
)
from specgraph_foundry.database import (
    Database,
)
from specgraph_foundry.errors import (
    ConflictError,
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

    def test_concurrent_extraction_attempt_conflicts_instead_of_crashing(
        self,
    ) -> None:
        # Regression test: two extract_document_atoms operations for the
        # same document (e.g. a leftover duplicate from an earlier failed
        # click) used to race on the extraction_runs unique constraint and
        # crash with an unhandled sqlite3.IntegrityError / Postgres
        # "duplicate key value violates unique constraint" instead of
        # failing cleanly.
        document = self.ingest(
            "The service must start.\n"
        )

        with self.database.connect() as connection:
            connection.execute(
                """
                INSERT INTO extraction_runs(
                    id, project_id, document_id,
                    extractor_version, source_sha256,
                    status, created_at
                )
                VALUES(?,?,?,?,?,?,?)
                """,
                (
                    "already-running",
                    self.project["id"],
                    document["id"],
                    EXTRACTOR_VERSION,
                    document["sha256"],
                    "RUNNING",
                    "2026-01-01T00:00:00+00:00",
                ),
            )

        with self.assertRaises(ConflictError):
            self.atoms.extract_document(
                str(document["id"])
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

    def test_export_atoms_bundle_contains_every_extracted_atom(
        self,
    ) -> None:
        import base64

        content = (
            "# Contract\n"
            "The worker must extract atoms.\n"
            "The worker should retry failures.\n"
        )
        document = self.ingest(content)
        self.atoms.extract_document(str(document["id"]))

        bundle = self.atoms.export_atoms_bundle(
            str(document["id"])
        )

        self.assertEqual(bundle["document_id"], str(document["id"]))
        self.assertEqual(bundle["atom_count"], 2)

        for key in ("text", "pdf"):
            file = bundle[key]
            self.assertGreater(file["byte_length"], 0)
            self.assertEqual(
                len(base64.b64decode(file["base64"])),
                file["byte_length"],
            )

        text = base64.b64decode(bundle["text"]["base64"]).decode("utf-8")
        self.assertIn("The worker must extract atoms.", text)
        self.assertIn("The worker should retry failures.", text)
        self.assertNotIn("#", text)

        pdf_bytes = base64.b64decode(bundle["pdf"]["base64"])
        self.assertTrue(pdf_bytes.startswith(b"%PDF"))
        self.assertEqual(bundle["pdf"]["media_type"], "application/pdf")
        self.assertEqual(bundle["text"]["media_type"], "text/plain")

    def test_export_atoms_bundle_explains_a_genuine_zero_atom_result(
        self,
    ) -> None:
        import base64

        # A document that is entirely a heading produces zero atoms by
        # design (see test_headings_and_code_ignored) - the export must
        # say so instead of silently returning an empty file that looks
        # identical to a broken extraction.
        document = self.ingest("# Heading only\n")
        self.atoms.extract_document(str(document["id"]))

        bundle = self.atoms.export_atoms_bundle(
            str(document["id"])
        )

        self.assertEqual(bundle["atom_count"], 0)
        text = base64.b64decode(bundle["text"]["base64"]).decode("utf-8")
        self.assertIn("No candidate statements were found", text)

    def test_export_atoms_bundle_missing_document_raises_not_found(
        self,
    ) -> None:
        from specgraph_foundry.errors import NotFoundError

        with self.assertRaises(NotFoundError):
            self.atoms.export_atoms_bundle("does-not-exist")


if __name__ == "__main__":
    unittest.main()
