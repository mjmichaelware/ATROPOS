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
from specgraph_foundry.ingestion import (
    IngestionService,
)
from specgraph_foundry.planning import (
    PlanningService,
)
from specgraph_foundry.services import (
    ProjectService,
)


class PlanningTest(unittest.TestCase):
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
        self.planning = (
            PlanningService(
                self.database
            )
        )

        self.project = (
            self.projects.create(
                "planning-test",
                "Planning Test",
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

    def tearDown(self) -> None:
        self.temp.cleanup()

    def test_plan_has_three_stages_per_atom(
        self,
    ) -> None:
        plan = self.planning.synthesize(
            str(self.project["id"]),
            allow_open_research=True,
        )

        self.assertEqual(
            plan["atom_count"],
            2,
        )

        self.assertEqual(
            plan["node_count"],
            6,
        )

        self.assertEqual(
            len(plan["bindings"]),
            6,
        )

        self.assertEqual(
            plan["status"],
            "VERIFIED",
        )

        self.assertEqual(
            len(plan["ready_nodes"]),
            2,
        )

    def test_open_research_blocks_plan(
        self,
    ) -> None:
        plan = self.planning.synthesize(
            str(self.project["id"]),
            allow_open_research=False,
        )

        self.assertEqual(
            plan["status"],
            "BLOCKED",
        )

        self.assertGreater(
            plan[
                "open_dimension_count"
            ],
            0,
        )

        self.assertEqual(
            plan["ready_nodes"],
            [],
        )

    def test_requires_relation_orders_atoms(
        self,
    ) -> None:
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
                "The API depends on the schema."
            ),
        )

        plan = self.planning.synthesize(
            str(self.project["id"]),
            allow_open_research=True,
        )

        bindings = {
            (
                str(binding["atom_id"]),
                str(binding["stage"]),
            ): str(
                binding["graph_node_id"]
            )
            for binding
            in plan["bindings"]
        }

        expected_source = bindings[
            (
                str(self.atom_a["id"]),
                "VERIFICATION",
            )
        ]

        expected_target = bindings[
            (
                str(self.atom_b["id"]),
                "IMPLEMENTATION",
            )
        ]

        edges = plan[
            "execution_graph"
        ]["edges"]

        self.assertTrue(
            any(
                edge["from_node_id"]
                == expected_source
                and edge["to_node_id"]
                == expected_target
                for edge in edges
            )
        )

    def test_dependency_cycle_rejected(
        self,
    ) -> None:
        project_id = str(
            self.project["id"]
        )

        self.planning.add_relation(
            project_id,
            str(self.atom_a["id"]),
            str(self.atom_b["id"]),
            "REQUIRES",
        )

        self.planning.add_relation(
            project_id,
            str(self.atom_b["id"]),
            str(self.atom_a["id"]),
            "REQUIRES",
        )

        with self.assertRaises(
            ValidationError
        ):
            self.planning.synthesize(
                project_id,
                allow_open_research=True,
            )

    def test_synthesis_is_idempotent(
        self,
    ) -> None:
        first = self.planning.synthesize(
            str(self.project["id"]),
            allow_open_research=True,
        )

        second = self.planning.synthesize(
            str(self.project["id"]),
            allow_open_research=True,
        )

        self.assertEqual(
            first["id"],
            second["id"],
        )


if __name__ == "__main__":
    unittest.main()
