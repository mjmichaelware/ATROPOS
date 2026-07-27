import json
import tempfile
import unittest
from pathlib import Path

from specgraph_foundry.atoms import (
    AtomService,
)
from specgraph_foundry.database import (
    Database,
)
from specgraph_foundry.errors import (
    ValidationError,
)
from specgraph_foundry.exports import (
    ExportService,
)
from specgraph_foundry.ingestion import (
    IngestionService,
)
from specgraph_foundry.planning import (
    PlanningService,
)
from specgraph_foundry.services import (
    ProjectService,
)


class ExportTest(unittest.TestCase):
    def setUp(self) -> None:
        self.temp = (
            tempfile.TemporaryDirectory()
        )

        root = Path(self.temp.name)

        self.database = Database(
            root / "test.sqlite3"
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
        self.planning = (
            PlanningService(
                self.database
            )
        )
        self.exports = ExportService(
            self.database
        )

        self.output_root = (
            root / "exports"
        )

        self.project = (
            self.projects.create(
                "export-test",
                "Export Test",
            )
        )

        document = (
            self.ingestion.ingest_text(
                project_id=str(
                    self.project["id"]
                ),
                title="Authority",
                content=(
                    "The schema must exist.\n"
                    "The API must use the schema.\n"
                ),
                chunk_bytes=32,
            )
        )

        extraction = (
            self.atoms.extract_document(
                str(document["id"])
            )
        )

        self.atom_a = extraction[
            "atoms"
        ][0]

        self.atom_b = extraction[
            "atoms"
        ][1]

        self.planning.add_relation(
            project_id=str(
                self.project["id"]
            ),
            from_atom_id=str(
                self.atom_b["id"]
            ),
            to_atom_id=str(
                self.atom_a["id"]
            ),
            relation_type="REQUIRES",
            rationale=(
                "The API requires the schema."
            ),
        )

        self.plan = (
            self.planning.synthesize(
                str(self.project["id"]),
                allow_open_research=True,
            )
        )

    def tearDown(self) -> None:
        self.temp.cleanup()

    def test_deterministic_export(
        self,
    ) -> None:
        first = self.exports.export_plan(
            str(self.plan["id"]),
            self.output_root,
        )

        second = self.exports.export_plan(
            str(self.plan["id"]),
            self.output_root,
        )

        self.assertEqual(
            first["id"],
            second["id"],
        )

        self.assertEqual(
            first["bundle_fingerprint"],
            second["bundle_fingerprint"],
        )

        self.assertEqual(
            first["status"],
            "VERIFIED",
        )

        paths = {
            item["path"]
            for item in first["artifacts"]
        }

        self.assertIn(
            "manifest.json",
            paths,
        )

        self.assertIn(
            "atropos_handoff.json",
            paths,
        )

        self.assertIn(
            "traceability.json",
            paths,
        )

        self.assertIn(
            "implementation_blueprint.md",
            paths,
        )

    def test_traceability_is_complete(
        self,
    ) -> None:
        result = self.exports.export_plan(
            str(self.plan["id"]),
            self.output_root,
        )

        directory = Path(
            str(result["output_path"])
        )

        traceability = json.loads(
            (
                directory
                / "traceability.json"
            ).read_text(
                encoding="utf-8"
            )
        )

        self.assertEqual(
            len(
                traceability["items"]
            ),
            self.plan["atom_count"],
        )

        for item in traceability[
            "items"
        ]:
            self.assertEqual(
                len(item["plan_nodes"]),
                3,
            )

            self.assertTrue(
                item["source"][
                    "exact_quote"
                ]
            )

    def test_tampering_is_detected(
        self,
    ) -> None:
        result = self.exports.export_plan(
            str(self.plan["id"]),
            self.output_root,
        )

        directory = Path(
            str(result["output_path"])
        )

        blueprint = (
            directory
            / "implementation_blueprint.md"
        )

        blueprint.write_text(
            "tampered\n",
            encoding="utf-8",
        )

        verification = (
            self.exports.verify_export(
                str(result["id"])
            )
        )

        self.assertFalse(
            verification["valid"]
        )

        codes = {
            finding["code"]
            for finding
            in verification["findings"]
        }

        self.assertIn(
            "ARTIFACT_CHECKSUM_MISMATCH",
            codes,
        )

    def test_binding_rejects_secrets(
        self,
    ) -> None:
        with self.assertRaises(
            ValidationError
        ):
            self.exports.bind_integration(
                project_id=str(
                    self.project["id"]
                ),
                system_name="ATROPOS",
                binding_type="RUNTIME",
                config={
                    "api_key": "forbidden"
                },
            )

    def test_safe_binding_is_exported(
        self,
    ) -> None:
        binding = (
            self.exports.bind_integration(
                project_id=str(
                    self.project["id"]
                ),
                system_name="ATROPOS",
                binding_type="RUNTIME",
                config={
                    "repository": (
                        "mjmichaelware/ATROPOS"
                    ),
                    "mode": "local",
                },
            )
        )

        self.assertEqual(
            binding["system_name"],
            "ATROPOS",
        )

        result = self.exports.export_plan(
            str(self.plan["id"]),
            self.output_root,
        )

        directory = Path(
            str(result["output_path"])
        )

        bindings = json.loads(
            (
                directory
                / "integration_bindings.json"
            ).read_text(
                encoding="utf-8"
            )
        )

        self.assertEqual(
            len(bindings["bindings"]),
            1,
        )

        self.assertNotIn(
            "config_json",
            bindings["bindings"][0],
        )


if __name__ == "__main__":
    unittest.main()
