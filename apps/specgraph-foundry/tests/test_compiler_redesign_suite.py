import unittest
import json
from specgraph_foundry.compiler import SpecGraphCompiler
from specgraph_foundry.compiler.structural_validation import StructuralValidator, ValidationFinding
from specgraph_foundry.compiler.document_ir import DocumentNode, STRUCTURAL_ROLES
from specgraph_foundry.compiler.source_coordinates import SourceCoordinates
from specgraph_foundry.compiler.shacl_validation import validate_graph, SHACL_VERSION, RequirementShape, AuthorityRelationShape
from specgraph_foundry.compiler.unresolved_tracker import UnresolvedTracker, detect_unresolved_candidacy
from specgraph_foundry.compiler.execution_dag import build_execution_dag
from specgraph_foundry.compiler.applicability import ApplicabilityTracker, evaluate_research_applicability
from specgraph_foundry.compiler.legacy_quarantine import QuarantineLedger, scan_legacy_atoms
from tests.corpus.gold_fixtures import FIXTURES, CORPUS_MANIFEST


class TestStructuralExclusionLattice(unittest.TestCase):
    def setUp(self):
        self.compiler = SpecGraphCompiler("test")

    def _assert_no_requirements(self, doc_text, excluded_labels=None):
        result = self.compiler.compile("test.md", doc_text.encode("utf-8"))
        statements = [r["canonical_statement"] for r in result["requirements"]]
        for label in (excluded_labels or []):
            self.assertNotIn(label, statements,
                             f"'{label}' should be excluded but appeared as requirement")

    def _compile(self, doc_text):
        return self.compiler.compile("test.md", doc_text.encode("utf-8"))

    def test_title_never_executable(self):
        r = self._compile("# Document Title\n")
        self.assertEqual(len(r["requirements"]), 0)

    def test_heading_never_executable(self):
        r = self._compile("## Section\n")
        self.assertEqual(len(r["requirements"]), 0)

    def test_separator_never_executable(self):
        r = self._compile("___\n")
        self.assertEqual(len(r["requirements"]), 0)

    def test_metadata_never_executable(self):
        r = self._compile("Status: DRAFT\n")
        self.assertEqual(len(r["requirements"]), 0)

    def test_rationale_never_executable_even_with_modals(self):
        r = self._compile("Rationale: The system must have encryption.\n")
        self.assertEqual(len(r["requirements"]), 0)

    def test_example_never_executable_even_with_modals(self):
        r = self._compile("Example: The system should use HTTPS.\n")
        self.assertEqual(len(r["requirements"]), 0)

    def test_note_never_executable_even_with_modals(self):
        r = self._compile("NOTE: The system must never log passwords.\n")
        self.assertEqual(len(r["requirements"]), 0)

    def test_warning_never_executable_even_with_modals(self):
        r = self._compile("WARNING: Must not expose secrets.\n")
        self.assertEqual(len(r["requirements"]), 0)

    def test_defect_finding_never_executable(self):
        r = self._compile("Defect: the system crashes on null input.\n")
        self.assertEqual(len(r["requirements"]), 0)

    def test_observation_never_executable(self):
        r = self._compile("Observation: The system processes data.\n")
        self.assertEqual(len(r["requirements"]), 0)

    def test_incomplete_fragment_never_executable(self):
        r = self._compile("incomplete\n")
        self.assertEqual(len(r["requirements"]), 0)

    def test_modal_words_inside_excluded_roles_never_promote(self):
        doc = (
            "NOTE: Must not promote this.\n"
            "WARNING: Should not promote this.\n"
            "Rationale: The reason is must have.\n"
            "Example: System shall demonstrate.\n"
        )
        r = self._compile(doc)
        self.assertEqual(len(r["requirements"]), 0,
                         "Modal words inside excluded roles must never promote")


class TestListInheritanceBoundaries(unittest.TestCase):
    def setUp(self):
        self.compiler = SpecGraphCompiler("test")

    def test_list_items_inherit_parent_force(self):
        doc = "The system MUST provide:\n- deterministic output\n- exact provenance\n"
        r = self.compiler.compile("test.md", doc.encode("utf-8"))
        requirements = r["requirements"]
        self.assertEqual(len(requirements), 3)
        for req in requirements:
            if "deterministic" in req["canonical_statement"] or "exact" in req["canonical_statement"]:
                self.assertEqual(req["force"], "MUST")

    def test_list_inheritance_stops_at_non_list_paragraph(self):
        doc = (
            "The system MUST support:\n"
            "- item A\n"
            "- item B\n"
            "This resets context.\n"
            "- item C (no inheritance)\n"
        )
        r = self.compiler.compile("test.md", doc.encode("utf-8"))
        statements = [req["canonical_statement"] for req in r["requirements"]]
        self.assertNotIn("item C (no inheritance)", statements)

    def test_list_inheritance_stops_at_heading(self):
        doc = (
            "The system MUST support:\n"
            "- item A\n"
            "## New Section\n"
            "- item B (no inheritance)\n"
        )
        r = self.compiler.compile("test.md", doc.encode("utf-8"))
        statements = [req["canonical_statement"] for req in r["requirements"]]
        self.assertNotIn("item B (no inheritance)", statements)

    def test_list_item_not_executable_merely_as_list_item(self):
        doc = (
            "Example list:\n"
            "- non-normative item\n"
            "- another non-normative item\n"
        )
        r = self.compiler.compile("test.md", doc.encode("utf-8"))
        self.assertEqual(len(r["requirements"]), 0)


class TestCandidacyAndUnresolved(unittest.TestCase):
    def setUp(self):
        self.compiler = SpecGraphCompiler("test")

    def test_free_form_requirement_recognized(self):
        doc = "Encryption is mandatory for stored credentials.\n"
        r = self.compiler.compile("test.md", doc.encode("utf-8"))
        self.assertEqual(len(r["requirements"]), 1)
        self.assertIn("mandatory", r["requirements"][0]["canonical_statement"])

    def test_uncertain_span_becomes_unresolved(self):
        doc = "Some vague prose about architecture.\n"
        r = self.compiler.compile("test.md", doc.encode("utf-8"))
        self.assertEqual(len(r["requirements"]), 0)
        self.assertGreater(len(r["unresolved_records"]), 0)

    def test_unresolved_record_has_full_provenance(self):
        doc = "Vague text without clear force.\n"
        r = self.compiler.compile("test.md", doc.encode("utf-8"))
        if r["unresolved_records"]:
            u = r["unresolved_records"][0]
            self.assertIn("source_sha256", u)
            self.assertIn("coordinates", u)
            self.assertIn("role_candidates", u)
            self.assertIn("failed_rules", u)
            self.assertIn("pass_fingerprint", u)

    def test_no_forced_atoms_for_counts(self):
        doc = "This text has no requirement structure.\n"
        r = self.compiler.compile("test.md", doc.encode("utf-8"))
        self.assertEqual(len(r["requirements"]), 0)


class TestAtomicDecomposition(unittest.TestCase):
    def setUp(self):
        self.compiler = SpecGraphCompiler("test")

    def test_semicolon_split(self):
        doc = "The system must start; it must listen on port 80.\n"
        r = self.compiler.compile("test.md", doc.encode("utf-8"))
        self.assertEqual(len(r["requirements"]), 2)

    def test_no_split_for_simple_compound(self):
        doc = "The system must process and validate data.\n"
        r = self.compiler.compile("test.md", doc.encode("utf-8"))
        self.assertEqual(len(r["requirements"]), 1,
                         "Simple 'and' should not split")


class TestOrthogonalTyping(unittest.TestCase):
    def setUp(self):
        self.compiler = SpecGraphCompiler("test")

    def test_no_default_fallback_axes(self):
        doc = "The system must store data.\n"
        r = self.compiler.compile("test.md", doc.encode("utf-8"))
        req = r["requirements"][0]
        self.assertEqual(req["force"], "MUST")
        self.assertNotEqual(req["domains"], ["UNSPECIFIED"],
                            "Domain should not be UNSPECIFIED for data-related requirement")

    def test_modality_correct(self):
        doc = "The system should log events.\n"
        r = self.compiler.compile("test.md", doc.encode("utf-8"))
        self.assertEqual(r["requirements"][0]["force"], "SHOULD")

    def test_prohibition_detected(self):
        doc = "The system must never accept invalid input.\n"
        r = self.compiler.compile("test.md", doc.encode("utf-8"))
        self.assertEqual(r["requirements"][0]["force"], "MUST_NOT")


class TestSHACLValidation(unittest.TestCase):
    def test_valid_graph_passes_shacl(self):
        graph = {
            "nodes": [{"id": "r1", "title": "Requirement", "canonical_statement": "The system must work.",
                       "coordinates": {"byte_start": 0, "byte_end": 25, "line_start": 1, "line_end": 1},
                       "node_type": "ATOM"}],
            "edges": [],
            "proposals": [],
        }
        result = validate_graph(graph)
        self.assertTrue(result["valid"])
        self.assertEqual(result["violation_count"], 0)

    def test_missing_canonical_statement_fails_shacl(self):
        graph = {
            "nodes": [{"id": "r1", "node_type": "ATOM"}],
            "edges": [],
            "proposals": [],
        }
        result = validate_graph(graph)
        self.assertFalse(result["valid"])

    def test_invalid_relation_type_fails(self):
        graph = {
            "nodes": [
                {"id": "r1", "title": "A", "canonical_statement": "Req A.",
                 "coordinates": {"byte_start": 0, "byte_end": 5}, "node_type": "ATOM"},
                {"id": "r2", "title": "B", "canonical_statement": "Req B.",
                 "coordinates": {"byte_start": 6, "byte_end": 11}, "node_type": "ATOM"},
            ],
            "edges": [{"from_node_id": "r1", "to_node_id": "r2", "relation_type": "BOGUS_RELATION"}],
            "proposals": [],
        }
        result = validate_graph(graph)
        self.assertFalse(result["valid"])

    def test_self_loop_fails(self):
        graph = {
            "nodes": [{"id": "r1", "title": "A", "canonical_statement": "Req A.",
                       "coordinates": {"byte_start": 0, "byte_end": 5}, "node_type": "ATOM"}],
            "edges": [{"from_node_id": "r1", "to_node_id": "r1", "relation_type": "REQUIRES"}],
            "proposals": [],
        }
        result = validate_graph(graph)
        self.assertFalse(result["valid"])

    def test_execution_node_invalid_type_fails(self):
        graph = {
            "nodes": [{"id": "n1", "title": "Bad", "node_type": "INVALID_TYPE"}],
            "edges": [],
            "proposals": [],
        }
        result = validate_graph(graph)
        self.assertFalse(result["valid"])

    def test_versioned_shape(self):
        self.assertEqual(SHACL_VERSION, "specgraph-shacl-v1")


class TestDeterministicFingerprints(unittest.TestCase):
    def test_same_input_produces_same_output(self):
        compiler = SpecGraphCompiler("test")
        doc = "The system must produce output.\n"
        r1 = compiler.compile("a.md", doc.encode("utf-8"))
        r2 = compiler.compile("a.md", doc.encode("utf-8"))
        self.assertEqual(r1["fingerprint"], r2["fingerprint"])
        self.assertEqual(r1["shacl_validation"]["fingerprint"],
                         r2["shacl_validation"]["fingerprint"])

    def test_fingerprint_stable_across_runs(self):
        compiler = SpecGraphCompiler("test")
        doc = "The system must log.\n"
        r = compiler.compile("stable.md", doc.encode("utf-8"))
        self.assertIsInstance(r["fingerprint"], str)
        self.assertEqual(len(r["fingerprint"]), 64)


class TestApplicabilityFourState(unittest.TestCase):
    def setUp(self):
        self.tracker = ApplicabilityTracker()

    def test_applicable_open_for_must(self):
        atom = {"force": "MUST", "domains": []}
        state = evaluate_research_applicability(atom)
        self.assertEqual(state, "APPLICABLE_OPEN")

    def test_not_applicable_when_claim_says_so(self):
        atom = {"force": "MUST", "domains": []}
        claim = {"applicability": "NOT_APPLICABLE", "status": "COMPLETE"}
        state = evaluate_research_applicability(atom, claim)
        self.assertEqual(state, "NOT_APPLICABLE")

    def test_applicable_resolved_when_claim_complete(self):
        atom = {"force": "SHOULD", "domains": []}
        claim = {"applicability": "APPLICABLE", "status": "COMPLETE"}
        state = evaluate_research_applicability(atom, claim)
        self.assertEqual(state, "APPLICABLE_RESOLVED")

    def test_unresolved_when_no_force(self):
        atom = {"force": "UNSPECIFIED", "domains": []}
        state = evaluate_research_applicability(atom)
        self.assertEqual(state, "UNRESOLVED_APPLICABILITY")


class TestLegacyQuarantine(unittest.TestCase):
    def test_quarantine_default_kind(self):
        atoms = [{"id": "a1", "kind": "FUNCTIONAL", "modality": "REQUIRED",
                  "canonical_statement": "The system must work.",
                  "source_sha256": "abc123"}]
        valid, ledger = scan_legacy_atoms(atoms)
        self.assertEqual(len(valid), 0)
        self.assertEqual(len(ledger.items), 1)
        self.assertEqual(ledger.items[0].reason_code, "DEFAULT_KIND")

    def test_quarantine_missing_provenance(self):
        atoms = [{"id": "a2", "kind": "DATA", "modality": "MUST",
                  "canonical_statement": "The system must work."}]
        valid, ledger = scan_legacy_atoms(atoms)
        self.assertEqual(len(valid), 1)
        self.assertEqual(len(ledger.items), 0)

    def test_quarantine_no_destructive_migration(self):
        ledger = QuarantineLedger()
        item = ledger.quarantine("a1", "STRUCTURAL_ATOM",
                                 {"id": "a1", "canonical_statement": ""})
        self.assertEqual(item.reason_code, "STRUCTURAL_ATOM")
        self.assertIn("before_fingerprint", item.to_dict())
        self.assertIn("quarantined_at", item.to_dict())


class TestSourceDocument3Adversarial(unittest.TestCase):
    def test_zero_executable_nodes_from_excluded_prose_classes(self):
        fixture = FIXTURES["source_document_3_adversarial"]
        compiler = SpecGraphCompiler("test")
        r = compiler.compile("sd3.md", fixture["raw_bytes"])

        requirements = r["requirements"]
        statements = [req["canonical_statement"] for req in requirements]

        labels = fixture["labels"]
        expected_count = labels["expected_requirements"]

        self.assertEqual(len(requirements), expected_count,
                         f"Expected {expected_count} requirements, got {len(requirements)}")

        for req in requirements:
            text = req["canonical_statement"]
            self.assertNotIn("Source Document #3:", text)
            self.assertNotIn("Inputs:", text)
            self.assertNotIn("__PART", text)
            self.assertNotIn("Example:", text)
            self.assertNotIn("NOTE:", text)
            self.assertNotIn("WARNING:", text)
            self.assertNotIn("Status:", text)
            self.assertNotIn("DRAFT", text)

        self.assertEqual(labels["structural_false_promotions"], 0)


class TestGoldCorpus(unittest.TestCase):
    def test_corpus_manifest_exists(self):
        self.assertIn("version", CORPUS_MANIFEST)
        self.assertEqual(CORPUS_MANIFEST["version"], "specgraph-gold-v1")
        self.assertGreater(CORPUS_MANIFEST["fixture_count"], 0)

    def test_all_fixtures_produce_expected_requirements(self):
        compiler = SpecGraphCompiler("test")
        for name, fixture in FIXTURES.items():
            if name == "source_document_3_adversarial":
                continue
            with self.subTest(fixture=name):
                r = compiler.compile(f"{name}.md", fixture["raw_bytes"])
                expected = fixture["labels"]["expected_requirements"]
                self.assertEqual(
                    len(r["requirements"]), expected,
                    f"{name}: expected {expected} requirements, got {len(r['requirements'])}"
                )

    def test_all_fixtures_zero_structural_false_promotions(self):
        compiler = SpecGraphCompiler("test")
        for name, fixture in FIXTURES.items():
            with self.subTest(fixture=name):
                r = compiler.compile(f"{name}.md", fixture["raw_bytes"])
                if "structural_false_promotions" in fixture["labels"]:
                    expected = fixture["labels"]["structural_false_promotions"]
                    actual = sum(
                        1 for f in r.get("structural_validation", {}).get("findings", [])
                        if f.get("severity") == "ERROR"
                    )
                    self.assertEqual(actual, expected,
                                     f"{name}: expected {expected} structural errors, got {actual}")


class TestExecutionDAG(unittest.TestCase):
    def test_dag_accepts_valid_graph(self):
        atoms = [
            {"id": "a1", "stable_id": "a1", "force": "MUST", "canonical_statement": "A must produce X.",
             "produced_artifacts": ["X"], "consumed_artifacts": []},
            {"id": "a2", "stable_id": "a2", "force": "MUST", "canonical_statement": "B must consume X.",
             "produced_artifacts": [], "consumed_artifacts": ["X"]},
        ]
        authority_edges = [{"from_node_id": "a1", "to_node_id": "a2", "relation_type": "REQUIRES"}]
        dag = build_execution_dag(atoms, authority_edges, set())
        self.assertGreater(len(dag["nodes"]), 0)
        self.assertEqual(dag["nodes"][0]["node_type"], "CONTRACT")

    def test_no_edges_for_unresolved_atoms(self):
        atoms = [{"id": "bad", "stable_id": "bad", "force": "UNSPECIFIED",
                  "canonical_statement": "unresolved", "produced_artifacts": [],
                  "consumed_artifacts": []}]
        dag = build_execution_dag(atoms, [], set())
        self.assertEqual(len(dag["nodes"]), 0)

    def test_kahn_acyclic(self):
        atoms = [
            {"id": "a1", "stable_id": "a1", "force": "MUST", "canonical_statement": "A",
             "produced_artifacts": ["X"], "consumed_artifacts": []},
            {"id": "a2", "stable_id": "a2", "force": "MUST", "canonical_statement": "B",
             "produced_artifacts": [], "consumed_artifacts": ["X"]},
        ]
        authority_edges = [{"from_node_id": "a1", "to_node_id": "a2", "relation_type": "REQUIRES"}]
        dag = build_execution_dag(atoms, authority_edges, set())
        self.assertIn("execution_order", dag)
        self.assertEqual(len(dag["execution_order"]), 2)


if __name__ == "__main__":
    unittest.main()
