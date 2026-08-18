"""The edges an obligation document states outright.

Every execution edge used to be inferred by matching phrases like "requires"
in prose, while the author's explicit `dependsOn: [...]` sat one line below in
the source and was read by nothing.
"""
import unittest

from specgraph_foundry.compiler.dependency_compiler import (
    compile_declared_dependencies,
    parse_declared_dependencies,
)


class TestParseDeclaredDependencies(unittest.TestCase):

    def test_a_declaration_anywhere_in_the_text_is_found(self):
        # The markdown accumulator glues an atom's title, its dependsOn line
        # and its RESEARCH/IMPL/WIRE triple into one statement with the
        # newlines collapsed, so a dependsOn never begins the text it lives in.
        # An anchored pattern found none of the eighty-one in a real document.
        text = "Primary nav spine registry dependsOn: [S-001] RESEARCH: diff the router."

        self.assertEqual([["S-001"]], parse_declared_dependencies(text))

    def test_several_declarations_in_one_statement_are_all_found(self):
        text = "A dependsOn: [S-001] IMPL: x  B dependsOn: [S-002, S-003] IMPL: y"

        self.assertEqual([["S-001"], ["S-002", "S-003"]], parse_declared_dependencies(text))

    def test_an_empty_declaration_is_an_answer_and_not_an_absence(self):
        # `dependsOn: []` says this atom is a root, which is different from a
        # line that never made a claim.
        self.assertEqual([[]], parse_declared_dependencies("Root atom dependsOn: []"))
        self.assertEqual([], parse_declared_dependencies("Atom with no declaration at all"))


class TestCompileDeclaredDependencies(unittest.TestCase):

    def statements(self, *pairs):
        return [{"statement_id": sid, "text": text} for sid, text in pairs]

    def test_a_declared_edge_points_from_the_prerequisite_to_the_dependent(self):
        edges, report = compile_declared_dependencies(
            self.statements(
                ("s1", "S-001 - Six-answers status contract dependsOn: []"),
                ("s2", "S-002 - Primary nav spine dependsOn: [S-001]"),
            ),
            {"s1": "req-1", "s2": "req-2"},
            {"S-001": "req-1", "S-002": "req-2"},
        )

        self.assertEqual(1, len(edges))
        self.assertEqual("req-1", edges[0].from_id)
        self.assertEqual("req-2", edges[0].to_id)
        self.assertEqual("DECLARED_DEPENDS_ON", edges[0].rule)
        self.assertEqual(2, report["declaration_count"])
        self.assertEqual(0, report["dangling_reference_count"])

    def test_a_reference_nobody_declared_is_reported_rather_than_guessed(self):
        # Inferring the target from position in the file would be inventing a
        # graph rather than reading one. The count is a fact about the source
        # that its author should see.
        edges, report = compile_declared_dependencies(
            self.statements(("s2", "S-002 - Nav dependsOn: [S-001]")),
            {"s2": "req-2"},
            {"S-002": "req-2"},
        )

        self.assertEqual([], edges)
        self.assertEqual(1, report["dangling_reference_count"])
        self.assertEqual(["S-001"], report["dangling_references"])

    def test_a_declaration_on_a_line_that_is_not_work_falls_to_the_atom_above(self):
        edges, _ = compile_declared_dependencies(
            self.statements(
                ("s1", "S-001 - First dependsOn: []"),
                ("s2", "S-002 - Second"),
                ("note", "Note: dependsOn: [S-001]"),
            ),
            # The note produced no requirement, so it cannot own an edge.
            {"s1": "req-1", "s2": "req-2"},
            {"S-001": "req-1", "S-002": "req-2"},
        )

        self.assertEqual([("req-1", "req-2")], [(e.from_id, e.to_id) for e in edges])

    def test_a_declaration_before_any_requirement_is_counted_not_dropped_silently(self):
        edges, report = compile_declared_dependencies(
            self.statements(("head", "ID Track dependsOn Node ids dependsOn: [S-001]")),
            {},
            {"S-001": "req-1"},
        )

        self.assertEqual([], edges)
        self.assertEqual(1, report["unowned_declaration_count"])

    def test_a_self_reference_is_not_an_ordering(self):
        # Keeping it would make the graph cyclic on a line the document meant
        # as a no-op.
        edges, _ = compile_declared_dependencies(
            self.statements(("s1", "S-001 - First dependsOn: [S-001]")),
            {"s1": "req-1"},
            {"S-001": "req-1"},
        )

        self.assertEqual([], edges)

    def test_an_edge_restated_by_a_neighbour_is_carried_once(self):
        edges, report = compile_declared_dependencies(
            self.statements(
                ("s1", "S-001 - First dependsOn: []"),
                ("s2", "S-002 - Second dependsOn: [S-001] and again dependsOn: [S-001]"),
            ),
            {"s1": "req-1", "s2": "req-2"},
            {"S-001": "req-1", "S-002": "req-2"},
        )

        self.assertEqual(1, len(edges))
        self.assertEqual(1, report["duplicate_edge_count"])


class TestDeclaredDependenciesEndToEnd(unittest.TestCase):

    def test_a_document_that_states_its_own_graph_gets_that_graph(self):
        from specgraph_foundry.compiler import SpecGraphCompiler

        document = (
            "S-001 - Provider registry lists every provider\n"
            "dependsOn: []\n"
            "IMPL: One registry in the shared module.\n"
            "\n"
            "S-002 - Provider health probe reads the registry\n"
            "dependsOn: [S-001]\n"
            "IMPL: Probe iterates the registry.\n"
        )

        result = SpecGraphCompiler("test").compile("dag.md", document.encode("utf-8"))
        declared = [
            edge for edge in result["dependencies"]
            if edge["rule"] == "DECLARED_DEPENDS_ON"
        ]

        self.assertEqual(1, len(declared), result["declared_dependencies"])
        self.assertEqual(0, result["declared_dependencies"]["dangling_reference_count"])
        # And it reaches the execution graph, which is the only place an edge
        # changes what gets built first.
        self.assertIn(
            "DECLARED_DEPENDS_ON",
            {edge["edge_type"] for edge in result["execution_dag"]["edges"]},
        )
        self.assertIn("BLOCKED", set(result["execution_dag"]["ready_states"].values()))


if __name__ == "__main__":
    unittest.main()
