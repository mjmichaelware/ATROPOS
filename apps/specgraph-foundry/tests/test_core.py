import tempfile
import unittest
from pathlib import Path

from specgraph_foundry.database import Database
from specgraph_foundry.errors import ValidationError
from specgraph_foundry.services import (
    GraphService,
    ProjectService,
)


class CoreTest(unittest.TestCase):
    def setUp(self) -> None:
        self.temp = tempfile.TemporaryDirectory()
        self.database = Database(
            Path(self.temp.name) / "test.sqlite3"
        )
        self.database.initialize()

        self.projects = ProjectService(
            self.database
        )
        self.graphs = GraphService(
            self.database
        )

        self.project = self.projects.create(
            "test-project",
            "Test Project",
        )

    def tearDown(self) -> None:
        self.temp.cleanup()

    def test_dependency_readiness(self) -> None:
        graph = self.graphs.create(
            str(self.project["id"]),
            "Execution",
            "EXECUTION",
            True,
        )

        first = self.graphs.add_node(
            str(graph["id"]),
            "first",
            "BATCH",
            "First",
        )

        second = self.graphs.add_node(
            str(graph["id"]),
            "second",
            "BATCH",
            "Second",
        )

        self.graphs.add_edge(
            str(graph["id"]),
            str(first["id"]),
            str(second["id"]),
            "MUST_PRECEDE",
        )

        ready = self.graphs.ready_nodes(
            str(graph["id"])
        )

        self.assertEqual(
            [
                node["node_key"]
                for node in ready
            ],
            ["first"],
        )

        self.graphs.set_status(
            str(first["id"]),
            "COMPLETE",
        )

        ready = self.graphs.ready_nodes(
            str(graph["id"])
        )

        self.assertEqual(
            [
                node["node_key"]
                for node in ready
            ],
            ["second"],
        )

    def test_execution_cycle_is_rejected(
        self,
    ) -> None:
        graph = self.graphs.create(
            str(self.project["id"]),
            "Cycle Test",
            "EXECUTION",
            True,
        )

        first = self.graphs.add_node(
            str(graph["id"]),
            "first",
            "BATCH",
            "First",
        )

        second = self.graphs.add_node(
            str(graph["id"]),
            "second",
            "BATCH",
            "Second",
        )

        self.graphs.add_edge(
            str(graph["id"]),
            str(first["id"]),
            str(second["id"]),
            "MUST_PRECEDE",
        )

        with self.assertRaises(
            ValidationError
        ):
            self.graphs.add_edge(
                str(graph["id"]),
                str(second["id"]),
                str(first["id"]),
                "MUST_PRECEDE",
            )


if __name__ == "__main__":
    unittest.main()
