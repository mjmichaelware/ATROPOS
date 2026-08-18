import unittest
import json
import hashlib
import subprocess
import sys
import tempfile
from pathlib import Path
from specgraph_foundry.compiler import SpecGraphCompiler
from specgraph_foundry.compiler.requirement_ir import (
    CanonicalRequirementIR,
    TRUTHFUL_COMPLETION_STATES,
)
from specgraph_foundry.compiler.source_authority import (
    AuthorityRegistry,
    SourceAuthority,
    SourceAuthorityHashMismatch,
    SourceAuthorityNoMatch,
)
from specgraph_foundry.compiler.semantic_relations import evaluate_semantic_relation
from specgraph_foundry.compiler.proof_bundle import verify_proof_bundle
from specgraph_foundry.compiler.compiler_replay import (
    verify_event_log_manifest,
    verify_replay,
)
from specgraph_foundry.compiler.structural_validation import StructuralValidator, ValidationFinding
from specgraph_foundry.compiler.document_ir import DocumentNode, STRUCTURAL_ROLES
from specgraph_foundry.compiler.source_coordinates import SourceCoordinates
from specgraph_foundry.compiler.shacl_validation import validate_graph, SHACL_VERSION, RequirementShape, AuthorityRelationShape
from specgraph_foundry.compiler.unresolved_tracker import UnresolvedTracker, detect_unresolved_candidacy
from specgraph_foundry.compiler.execution_dag import build_execution_dag
from specgraph_foundry.compiler.graph_validation import compute_graph_metrics
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
        # Still admitted -- a list item is a declared unit of work whether or
        # not a modal verb reached it. What must not survive the boundary is
        # the *force*: item C states no obligation strength of its own, and
        # inheriting MUST across a paragraph would invent one.
        item_c = next(
            req for req in r["requirements"]
            if "item C (no inheritance)" in req["canonical_statement"]
        )
        self.assertNotEqual(item_c["force"], "MUST")

    def test_list_inheritance_stops_at_heading(self):
        doc = (
            "The system MUST support:\n"
            "- item A\n"
            "## New Section\n"
            "- item B (no inheritance)\n"
        )
        r = self.compiler.compile("test.md", doc.encode("utf-8"))
        # A heading resets force for the same reason a paragraph does. The item
        # remains a requirement; it simply carries no inherited obligation.
        item_b = next(
            req for req in r["requirements"]
            if "item B (no inheritance)" in req["canonical_statement"]
        )
        self.assertNotEqual(item_b["force"], "MUST")

    def test_list_item_is_executable_as_a_declared_unit(self):
        """A list item is admitted on its structure, with no force invented.

        This asserts the opposite of what it used to. Requiring a modal verb
        meant a document written as plain bullets produced no atoms at all --
        and most documents are written that way. Structure is the signal;
        obligation strength is a separate question, and the honest answer for
        an item that states none is UNSPECIFIED rather than exclusion.
        """
        doc = (
            "Example list:\n"
            "- non-normative item\n"
            "- another non-normative item\n"
        )
        r = self.compiler.compile("test.md", doc.encode("utf-8"))
        self.assertEqual(len(r["requirements"]), 2)
        for req in r["requirements"]:
            self.assertNotIn(req["force"], {"MUST", "SHALL", "MUST_NOT"})


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

    def test_event_log_manifest_is_verified(self):
        compiler = SpecGraphCompiler("test")
        r = compiler.compile("events.md", b"The system must log.\n")
        self.assertTrue(r["event_log_verification"]["valid"])
        self.assertEqual(r["event_log_manifest"]["event_count"], len(r["event_log"]))
        self.assertEqual(len(r["event_log_manifest"]["manifest_sha256"]), 64)

    def test_event_log_manifest_tamper_is_detected(self):
        compiler = SpecGraphCompiler("test")
        r = compiler.compile("events.md", b"The system must log.\n")
        manifest = dict(r["event_log_manifest"])
        manifest["event_count"] = 999
        verification = verify_event_log_manifest(r["event_log"], manifest)
        self.assertFalse(verification["valid"])
        self.assertEqual(
            verification["findings"][0]["code"],
            "EVENT_LOG_MANIFEST_MISMATCH",
        )

    def test_replay_verification_accepts_branched_pipeline_logs(self):
        compiler = SpecGraphCompiler("test")
        r = compiler.compile("events.md", b"The system must log.\n")
        final_fingerprint = r["event_log"][-1]["output_fingerprint"]
        self.assertTrue(verify_replay(r["event_log"], final_fingerprint))

    def test_replay_verification_rejects_missing_final_fingerprint(self):
        compiler = SpecGraphCompiler("test")
        r = compiler.compile("events.md", b"The system must log.\n")
        self.assertFalse(verify_replay(r["event_log"], "0" * 64))

    def test_source_identity_is_raw_sha256(self):
        compiler = SpecGraphCompiler("test")
        raw = b"The system must produce output.\n"
        r = compiler.compile("source.md", raw)
        expected = hashlib.sha256(raw).hexdigest()
        self.assertEqual(r["requirements"][0]["source_sha256"], expected)
        self.assertEqual(
            r["requirements"][0]["source_artifact_id"],
            f"sha256:{expected}",
        )

    def test_atom_proof_surface_is_complete(self):
        compiler = SpecGraphCompiler("test")
        doc = (
            "The compiler must produce manifest.\n"
            "The verifier must consume manifest.\n"
        )
        r = compiler.compile("proof.md", doc.encode("utf-8"))
        requirements = r["requirements"]
        self.assertEqual(len(requirements), 2)

        required_fields = {
            "source_document_id",
            "source_version",
            "source_sha256",
            "source_artifact_id",
            "extraction_decision",
            "extraction_rejection_reason",
            "authority_classification",
            "predecessor_ids",
            "successor_ids",
            "execution_node_id",
            "semantic_owner",
            "implementation_symbols",
            "behavioral_tests",
            "evidence_refs",
            "acceptance_predicate",
            "completion_state",
            "verifier_identity",
            "artifact_hashes",
        }

        for req in requirements:
            self.assertTrue(required_fields.issubset(req.keys()))
            self.assertEqual(req["extraction_decision"], "ACCEPTED")
            self.assertEqual(req["authority_classification"], "SOURCE_AUTHORITY")
            self.assertIn(req["completion_state"], {"READY", "NOT_STARTED"})
            self.assertIn("atom_identity_sha256", req["artifact_hashes"])
            self.assertIn("dependency_support_sha256", req["artifact_hashes"])
            self.assertIn("execution_support_sha256", req["artifact_hashes"])

        by_statement = {
            req["canonical_statement"]: req
            for req in requirements
        }
        self.assertEqual(
            by_statement["The compiler must produce manifest."]["successor_ids"],
            [by_statement["The verifier must consume manifest."]["stable_id"]],
        )
        self.assertEqual(
            by_statement["The verifier must consume manifest."]["predecessor_ids"],
            [by_statement["The compiler must produce manifest."]["stable_id"]],
        )

    def test_atom_output_ignores_temporary_absolute_paths(self):
        compiler = SpecGraphCompiler("test")
        raw = b"The system must log.\n"
        r1 = compiler.compile("/tmp/work-a/source.md", raw)
        r2 = compiler.compile("/var/tmp/work-b/source.md", raw)
        self.assertEqual(r1["requirements"], r2["requirements"])
        self.assertEqual(r1["dependencies"], r2["dependencies"])
        self.assertEqual(r1["execution_dag"], r2["execution_dag"])

    def test_proof_bundle_covers_deterministic_surfaces(self):
        compiler = SpecGraphCompiler("test")
        doc = (
            "The compiler must produce manifest.\n"
            "The verifier must consume manifest.\n"
            "Vague text without clear force.\n"
        )
        r = compiler.compile("proof.md", doc.encode("utf-8"))
        proof = r["proof_bundle"]
        checksums = proof["checksums"]

        self.assertEqual(proof["schema_version"], "specgraph-proof-bundle-v1")
        self.assertEqual(proof["accepted_atom_count"], len(r["requirements"]))
        self.assertEqual(proof["rejection_count"], len(r["unresolved_records"]))
        self.assertEqual(proof["dependency_edge_count"], len(r["dependencies"]))
        self.assertEqual(
            proof["execution_node_count"],
            len(r["execution_dag"]["nodes"]),
        )

        for key in {
            "accepted_atoms_sha256",
            "rejection_ledger_sha256",
            "authority_relations_sha256",
            "dependency_graph_sha256",
            "execution_graph_sha256",
            "authority_graph_metrics_sha256",
            "execution_graph_metrics_sha256",
            "duplicate_canonical_groups_sha256",
            "orphaned_evidence_refs_sha256",
            "frontier_metrics_sha256",
            "traceability_sha256",
            "shacl_validation_sha256",
            "graph_validation_sha256",
        }:
            self.assertIn(key, checksums)
            self.assertEqual(len(checksums[key]), 64)

        self.assertIn("authority_graph_metrics", proof)
        self.assertIn("execution_graph_metrics", proof)
        self.assertEqual(proof["duplicate_canonical_atom_count"], 0)
        self.assertEqual(proof["orphaned_evidence_ref_count"], 0)
        frontier = proof["frontier_metrics"]
        self.assertEqual(frontier["source_coordinate_coverage_pct"], 100)
        self.assertEqual(frontier["authority_fingerprint_coverage_pct"], 100)
        self.assertEqual(frontier["traceability_schema_validity_pct"], 100)
        self.assertEqual(frontier["root_reachability_pct"], 100)
        self.assertEqual(frontier["terminal_reachability_pct"], 100)
        self.assertEqual(frontier["dangling_executable_nodes"], 0)
        self.assertEqual(frontier["duplicate_canonical_atoms"], 0)
        self.assertEqual(frontier["orphaned_evidence_references"], 0)
        self.assertEqual(frontier["checksum_disagreement"], 0)
        self.assertEqual(frontier["secret_leakage"], 0)
        self.assertEqual(frontier["unexplained_metric_exclusions"], 0)
        self.assertEqual(frontier["fixture_contamination"], 0)

    def test_proof_bundle_ignores_temporary_absolute_paths(self):
        compiler = SpecGraphCompiler("test")
        raw = b"The compiler must produce manifest.\n"
        r1 = compiler.compile("/tmp/work-a/source.md", raw)
        r2 = compiler.compile("/var/tmp/work-b/source.md", raw)
        self.assertEqual(r1["proof_bundle"], r2["proof_bundle"])

    def test_proof_bundle_verification_is_valid_for_compiler_output(self):
        compiler = SpecGraphCompiler("test")
        r = compiler.compile("proof.md", b"The compiler must produce manifest.\n")
        verification = r["proof_bundle_verification"]
        self.assertTrue(verification["valid"])
        self.assertEqual(verification["finding_count"], 0)

    def test_tampered_proof_bundle_fails_verification(self):
        compiler = SpecGraphCompiler("test")
        r = compiler.compile("proof.md", b"The compiler must produce manifest.\n")
        proof = dict(r["proof_bundle"])
        proof["accepted_atom_count"] = 999
        proof["checksums"] = dict(proof["checksums"])
        proof["checksums"]["frontier_metrics_sha256"] = "bad"
        verification = verify_proof_bundle(proof)
        self.assertFalse(verification["valid"])
        codes = {finding["code"] for finding in verification["findings"]}
        self.assertIn("INVALID_CHECKSUM", codes)
        self.assertIn("BUNDLE_CHECKSUM_MISMATCH", codes)

    def test_malformed_required_checksum_fails_verification(self):
        compiler = SpecGraphCompiler("test")
        r = compiler.compile("proof.md", b"The compiler must produce manifest.\n")
        proof = dict(r["proof_bundle"])
        proof["checksums"] = dict(proof["checksums"])
        proof["checksums"]["accepted_atoms_sha256"] = "not-a-sha"
        verification = verify_proof_bundle(proof)
        self.assertFalse(verification["valid"])
        codes = {finding["code"] for finding in verification["findings"]}
        self.assertIn("INVALID_CHECKSUM", codes)

    def test_missing_required_checksum_fails_verification(self):
        compiler = SpecGraphCompiler("test")
        r = compiler.compile("proof.md", b"The compiler must produce manifest.\n")
        proof = dict(r["proof_bundle"])
        proof["checksums"] = dict(proof["checksums"])
        del proof["checksums"]["accepted_atoms_sha256"]
        verification = verify_proof_bundle(proof)
        self.assertFalse(verification["valid"])
        codes = {finding["code"] for finding in verification["findings"]}
        self.assertIn("INVALID_CHECKSUM", codes)

    def test_incomplete_frontier_coverage_fails_verification(self):
        compiler = SpecGraphCompiler("test")
        r = compiler.compile("proof.md", b"The compiler must produce manifest.\n")
        proof = dict(r["proof_bundle"])
        proof["frontier_metrics"] = dict(proof["frontier_metrics"])
        proof["frontier_metrics"]["traceability_schema_validity_pct"] = 99
        verification = verify_proof_bundle(proof)
        self.assertFalse(verification["valid"])
        codes = {finding["code"] for finding in verification["findings"]}
        self.assertIn("INCOMPLETE_FRONTIER_COVERAGE", codes)

    def test_nonzero_dangling_executable_nodes_fail_verification(self):
        compiler = SpecGraphCompiler("test")
        r = compiler.compile("proof.md", b"The compiler must produce manifest.\n")
        proof = dict(r["proof_bundle"])
        proof["frontier_metrics"] = dict(proof["frontier_metrics"])
        proof["frontier_metrics"]["dangling_executable_nodes"] = 1
        verification = verify_proof_bundle(proof)
        self.assertFalse(verification["valid"])
        codes = {finding["code"] for finding in verification["findings"]}
        self.assertIn("NONZERO_FRONTIER_FAILURE", codes)

    def test_duplicate_canonical_atoms_are_measured_not_hidden(self):
        compiler = SpecGraphCompiler("test")
        doc = (
            "The system must log events.\n"
            "The system must log events.\n"
        )
        r = compiler.compile("duplicate.md", doc.encode("utf-8"))
        proof = r["proof_bundle"]
        self.assertEqual(proof["duplicate_canonical_atom_count"], 1)
        self.assertEqual(len(proof["duplicate_canonical_groups"]), 1)


class TestGraphMetrics(unittest.TestCase):
    def test_graph_metrics_report_reachability_and_dangling_nodes(self):
        nodes = [
            {"id": "a"},
            {"id": "b"},
            {"id": "c"},
        ]
        edges = [
            {"from_node_id": "a", "to_node_id": "b", "edge_type": "REQUIRES"},
        ]
        metrics = compute_graph_metrics(nodes, edges)
        self.assertEqual(metrics["node_count"], 3)
        self.assertEqual(metrics["edge_count"], 1)
        self.assertEqual(metrics["root_node_ids"], ["a", "c"])
        self.assertEqual(metrics["terminal_node_ids"], ["b", "c"])
        self.assertEqual(metrics["reachable_from_roots_count"], 3)
        self.assertEqual(metrics["terminal_reachable_from_roots_count"], 2)
        self.assertEqual(metrics["dangling_node_ids"], ["c"])

    def test_graph_metrics_count_bad_edges_without_inflating_edges(self):
        nodes = [
            {"id": "a"},
            {"id": "b"},
        ]
        edges = [
            {"from_node_id": "a", "to_node_id": "b", "edge_type": "REQUIRES"},
            {"from_node_id": "a", "to_node_id": "b", "edge_type": "REQUIRES"},
            {"from_node_id": "a", "to_node_id": "a", "edge_type": "REQUIRES"},
            {"from_node_id": "missing", "to_node_id": "b", "edge_type": "REQUIRES"},
        ]
        metrics = compute_graph_metrics(nodes, edges)
        self.assertEqual(metrics["edge_count"], 1)
        self.assertEqual(metrics["duplicate_edge_count"], 1)
        self.assertEqual(metrics["self_loop_count"], 1)
        self.assertEqual(metrics["missing_endpoint_count"], 1)


class TestAuthorityPrecedenceRelations(unittest.TestCase):
    def _req(
        self,
        stable_id: str,
        source_document_id: str,
        statement: str = "The system must log events.",
    ) -> CanonicalRequirementIR:
        return CanonicalRequirementIR(
            stable_id=stable_id,
            coordinates=SourceCoordinates(0, len(statement), 1, 1),
            original_statement=statement,
            canonical_statement=statement,
            actor="system",
            force="MUST",
            source_document_id=source_document_id,
            source_sha256=f"{source_document_id}-sha",
        )

    def test_higher_tier_source_supersedes_equivalent_lower_tier(self):
        registry = AuthorityRegistry()
        registry.register_authority(SourceAuthority("doc-high", 1, "v1", "2026-01-01", "owner"))
        registry.register_authority(SourceAuthority("doc-low", 2, "v1", "2026-01-01", "owner"))
        rel = evaluate_semantic_relation(
            self._req("high", "doc-high"),
            self._req("low", "doc-low"),
            registry,
        )
        self.assertIsNotNone(rel)
        self.assertEqual(rel.relation_type, "SUPERSEDES")
        self.assertEqual(rel.from_req_id, "high")
        self.assertFalse(rel.inferred)

    def test_registered_supersession_wins_same_tier(self):
        registry = AuthorityRegistry()
        registry.register_authority(SourceAuthority("doc-new", 2, "v2", "2026-01-01", "owner"))
        registry.register_authority(SourceAuthority("doc-old", 2, "v1", "2026-01-01", "owner"))
        registry.register_supersession("doc-new", "doc-old")
        rel = evaluate_semantic_relation(
            self._req("old", "doc-old"),
            self._req("new", "doc-new"),
            registry,
        )
        self.assertIsNotNone(rel)
        self.assertEqual(rel.relation_type, "SUPERSEDES")
        self.assertEqual(rel.from_req_id, "new")

    def test_same_tier_newer_effective_date_supersedes(self):
        registry = AuthorityRegistry()
        registry.register_authority(SourceAuthority("doc-new", 2, "v2", "2026-02-01", "owner"))
        registry.register_authority(SourceAuthority("doc-old", 2, "v1", "2026-01-01", "owner"))
        rel = evaluate_semantic_relation(
            self._req("new", "doc-new"),
            self._req("old", "doc-old"),
            registry,
        )
        self.assertIsNotNone(rel)
        self.assertEqual(rel.relation_type, "SUPERSEDES")
        self.assertEqual(rel.from_req_id, "new")

    def test_unresolved_equal_precedence_falls_back_to_match_not_supersedes(self):
        registry = AuthorityRegistry()
        registry.register_authority(SourceAuthority("doc-a", 2, "v1", "2026-01-01", "owner"))
        registry.register_authority(SourceAuthority("doc-b", 2, "v1", "2026-01-01", "owner"))
        rel = evaluate_semantic_relation(
            self._req("a", "doc-a"),
            self._req("b", "doc-b"),
            registry,
        )
        self.assertIsNotNone(rel)
        self.assertEqual(rel.relation_type, "EXACT_MATCH")

    def test_authority_relation_has_stable_identity_and_rationale_hash(self):
        rel = evaluate_semantic_relation(
            self._req("a", "doc-a"),
            self._req("b", "doc-a"),
        )
        self.assertIsNotNone(rel)
        first = rel.to_dict()
        second = evaluate_semantic_relation(
            self._req("a", "doc-a"),
            self._req("b", "doc-a"),
        ).to_dict()
        self.assertTrue(first["id"].startswith("rel-"))
        self.assertEqual(len(first["id"]), 20)
        self.assertEqual(len(first["rationale_sha256"]), 64)
        self.assertEqual(first, second)

    def test_exact_lookup_failure_is_typed_no_match(self):
        registry = AuthorityRegistry()
        with self.assertRaises(SourceAuthorityNoMatch) as ctx:
            registry.require_authority("missing-doc")
        self.assertEqual(ctx.exception.document_id, "missing-doc")

    def test_hash_mismatch_is_typed_and_preserves_expected_observed(self):
        registry = AuthorityRegistry()
        registry.register_authority(
            SourceAuthority(
                "doc-a",
                1,
                "v1",
                "2026-01-01",
                "owner",
                artifact_sha256="expected",
            )
        )
        with self.assertRaises(SourceAuthorityHashMismatch) as ctx:
            registry.require_authority("doc-a", observed_sha256="observed")
        self.assertEqual(ctx.exception.document_id, "doc-a")
        self.assertEqual(ctx.exception.expected_sha256, "expected")
        self.assertEqual(ctx.exception.observed_sha256, "observed")

    def test_matching_hash_returns_authority(self):
        registry = AuthorityRegistry()
        registry.register_authority(
            SourceAuthority(
                "doc-a",
                1,
                "v1",
                "2026-01-01",
                "owner",
                artifact_sha256="sha",
            )
        )
        authority = registry.require_authority("doc-a", observed_sha256="sha")
        self.assertEqual(authority.document_id, "doc-a")

    def test_authority_manifest_is_stable_independent_of_registration_order(self):
        first = AuthorityRegistry()
        second = AuthorityRegistry()
        for registry, order in (
            (first, ["doc-b", "doc-a"]),
            (second, ["doc-a", "doc-b"]),
        ):
            for doc_id in order:
                registry.register_authority(
                    SourceAuthority(
                        doc_id,
                        1,
                        "v1",
                        "2026-01-01",
                        "owner",
                        artifact_sha256=f"{doc_id}-sha",
                    )
                )
            registry.register_supersession("doc-b", "doc-a")
        self.assertEqual(first.to_manifest(), second.to_manifest())
        self.assertEqual(len(first.to_manifest()["manifest_sha256"]), 64)


class TestProofBundleScript(unittest.TestCase):
    def test_compile_proof_bundle_script_is_path_stable(self):
        script = Path("scripts/compile_proof_bundle.py").resolve()
        raw = "The compiler must produce manifest.\n"
        with tempfile.TemporaryDirectory() as a, tempfile.TemporaryDirectory() as b:
            source_a = Path(a) / "source.md"
            source_b = Path(b) / "source.md"
            source_a.write_text(raw, encoding="utf-8")
            source_b.write_text(raw, encoding="utf-8")
            output_a = subprocess.check_output(
                [sys.executable, str(script), str(source_a)],
                text=True,
            )
            output_b = subprocess.check_output(
                [sys.executable, str(script), str(source_b)],
                text=True,
            )
        self.assertEqual(json.loads(output_a), json.loads(output_b))

    def test_compile_proof_bundle_script_can_include_requirements(self):
        script = Path("scripts/compile_proof_bundle.py").resolve()
        raw = "The compiler must produce manifest.\n"
        with tempfile.TemporaryDirectory() as tmp:
            source = Path(tmp) / "source.md"
            source.write_text(raw, encoding="utf-8")
            output = subprocess.check_output(
                [
                    sys.executable,
                    str(script),
                    str(source),
                    "--include-requirements",
                ],
                text=True,
            )
        payload = json.loads(output)
        self.assertIn("proof_bundle", payload)
        self.assertEqual(len(payload["requirements"]), 1)

    def test_compile_proof_bundle_script_can_verify(self):
        script = Path("scripts/compile_proof_bundle.py").resolve()
        raw = "The compiler must produce manifest.\n"
        with tempfile.TemporaryDirectory() as tmp:
            source = Path(tmp) / "source.md"
            source.write_text(raw, encoding="utf-8")
            output = subprocess.check_output(
                [sys.executable, str(script), str(source), "--verify"],
                text=True,
            )
        payload = json.loads(output)
        self.assertTrue(payload["proof_bundle_verification"]["valid"])


class TestSourceAuthorityManifestScript(unittest.TestCase):
    def test_source_authority_manifest_script_sorts_registry(self):
        script = Path("scripts/source_authority_manifest.py").resolve()
        payload_a = {
            "authorities": [
                {"document_id": "doc-b", "tier": 1, "version": "v1",
                 "effective_date": "2026-01-01", "owner": "owner",
                 "artifact_sha256": "b"},
                {"document_id": "doc-a", "tier": 1, "version": "v1",
                 "effective_date": "2026-01-01", "owner": "owner",
                 "artifact_sha256": "a"},
            ],
            "supersessions": [
                {"newer_doc_id": "doc-b", "older_doc_id": "doc-a"},
            ],
        }
        payload_b = {
            "authorities": list(reversed(payload_a["authorities"])),
            "supersessions": list(payload_a["supersessions"]),
        }
        with tempfile.TemporaryDirectory() as tmp:
            path_a = Path(tmp) / "a.json"
            path_b = Path(tmp) / "b.json"
            path_a.write_text(json.dumps(payload_a), encoding="utf-8")
            path_b.write_text(json.dumps(payload_b), encoding="utf-8")
            output_a = subprocess.check_output(
                [sys.executable, str(script), str(path_a)],
                text=True,
            )
            output_b = subprocess.check_output(
                [sys.executable, str(script), str(path_b)],
                text=True,
            )
        self.assertEqual(json.loads(output_a), json.loads(output_b))
        self.assertEqual(len(json.loads(output_a)["manifest_sha256"]), 64)


class TestTruthfulCompletionStates(unittest.TestCase):
    def test_compiler_outputs_only_truthful_completion_states(self):
        compiler = SpecGraphCompiler("test")
        r = compiler.compile("state.md", b"The system must log.\n")
        for req in r["requirements"]:
            self.assertIn(req["completion_state"], TRUTHFUL_COMPLETION_STATES)

    def test_invalid_completion_state_is_rejected(self):
        with self.assertRaises(ValueError):
            CanonicalRequirementIR(
                stable_id="bad",
                coordinates=SourceCoordinates(0, 1, 1, 1),
                original_statement="x",
                canonical_statement="x",
                actor="system",
                force="MUST",
                completion_state="DONE",
            )

    def test_invalid_extraction_decision_is_rejected(self):
        with self.assertRaises(ValueError):
            CanonicalRequirementIR(
                stable_id="bad",
                coordinates=SourceCoordinates(0, 1, 1, 1),
                original_statement="x",
                canonical_statement="x",
                actor="system",
                force="MUST",
                extraction_decision="MAYBE",
            )

    def test_rejected_extraction_requires_reason(self):
        with self.assertRaises(ValueError):
            CanonicalRequirementIR(
                stable_id="bad",
                coordinates=SourceCoordinates(0, 1, 1, 1),
                original_statement="x",
                canonical_statement="x",
                actor="system",
                force="MUST",
                extraction_decision="REJECTED",
            )

    def test_verified_requires_tests_and_evidence(self):
        with self.assertRaises(ValueError):
            CanonicalRequirementIR(
                stable_id="bad",
                coordinates=SourceCoordinates(0, 1, 1, 1),
                original_statement="x",
                canonical_statement="x",
                actor="system",
                force="MUST",
                completion_state="VERIFIED",
            )


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

    def test_atom_without_a_modal_verb_still_gets_a_node(self):
        """Contract change, deliberate.

        This test previously asserted that an atom with force UNSPECIFIED
        produced no execution node. That was the modal-verb requirement
        surviving one stage past the point where extraction stopped applying
        it: on a real obligation DAG, 361 of 390 extracted atoms recorded
        UNSPECIFIED and the execution graph held only the 29 that happened to
        contain "must" or "may". The document's work was extracted and then
        discarded here.
        """
        atoms = [{"id": "plain", "stable_id": "plain", "force": "UNSPECIFIED",
                  "canonical_statement": "Provider registry lists every provider.",
                  "produced_artifacts": [], "consumed_artifacts": []}]
        dag = build_execution_dag(atoms, [], set())

        self.assertEqual(len(dag["nodes"]), 1)
        self.assertEqual(dag["nodes"][0]["source_atom_id"], "plain")
        # Admitted, but the unstated strength is recorded rather than invented.
        self.assertEqual(dag["nodes"][0]["force"], "UNSPECIFIED")

    def test_resolved_unresolved_atoms_are_still_excluded(self):
        """Admitting unstated force did not disable the real exclusion."""
        atoms = [
            {"id": "keep", "stable_id": "keep", "force": "UNSPECIFIED",
             "canonical_statement": "Kept.", "produced_artifacts": [], "consumed_artifacts": []},
            {"id": "drop", "stable_id": "drop", "force": "MUST",
             "canonical_statement": "Dropped.", "produced_artifacts": [], "consumed_artifacts": []},
        ]
        dag = build_execution_dag(atoms, [], {"drop"})

        self.assertEqual([n["source_atom_id"] for n in dag["nodes"]], ["keep"])

    def test_dependency_compiler_edges_reach_the_execution_graph(self):
        """The rules the dependency compiler emits are execution ordering.

        They were computed on every run and passed only to validation, so the
        execution graph came back with zero edges however many dependencies
        the document stated.
        """
        atoms = [
            {"id": "a1", "stable_id": "a1", "force": "UNSPECIFIED", "canonical_statement": "A",
             "produced_artifacts": [], "consumed_artifacts": []},
            {"id": "a2", "stable_id": "a2", "force": "UNSPECIFIED", "canonical_statement": "B",
             "produced_artifacts": [], "consumed_artifacts": []},
        ]
        edges = [{"from_node_id": "a1", "to_node_id": "a2", "edge_type": "EXPLICIT_PHRASE"}]
        dag = build_execution_dag(atoms, edges, set())

        self.assertEqual(len(dag["edges"]), 1)
        self.assertEqual(dag["edges"][0]["edge_type"], "EXPLICIT_PHRASE")

    def test_refines_is_not_an_execution_edge(self):
        """One statement narrowing another says nothing about build order."""
        atoms = [
            {"id": "a1", "stable_id": "a1", "force": "MUST", "canonical_statement": "A",
             "produced_artifacts": [], "consumed_artifacts": []},
            {"id": "a2", "stable_id": "a2", "force": "MUST", "canonical_statement": "B",
             "produced_artifacts": [], "consumed_artifacts": []},
        ]
        edges = [{"from_node_id": "a1", "to_node_id": "a2", "relation_type": "REFINES"}]

        self.assertEqual(build_execution_dag(atoms, edges, set())["edges"], [])

    def test_a_node_with_a_predecessor_is_blocked(self):
        """Readiness could not return BLOCKED for any input before this."""
        atoms = [
            {"id": "first", "stable_id": "first", "force": "MUST", "canonical_statement": "A",
             "produced_artifacts": ["X"], "consumed_artifacts": []},
            {"id": "second", "stable_id": "second", "force": "MUST", "canonical_statement": "B",
             "produced_artifacts": [], "consumed_artifacts": ["X"]},
        ]
        states = build_execution_dag(atoms, [], set())["ready_states"]

        self.assertEqual(states["contract-first"], "READY")
        self.assertEqual(states["contract-second"], "BLOCKED")

    def test_a_cycle_is_broken_rather_than_carried(self):
        atoms = [
            {"id": f"a{i}", "stable_id": f"a{i}", "force": "MUST", "canonical_statement": f"A{i}",
             "produced_artifacts": [], "consumed_artifacts": []}
            for i in range(1, 4)
        ]
        edges = [
            {"from_node_id": "a1", "to_node_id": "a2", "edge_type": "REQUIRES"},
            {"from_node_id": "a2", "to_node_id": "a3", "edge_type": "REQUIRES"},
            {"from_node_id": "a3", "to_node_id": "a1", "edge_type": "REQUIRES"},
        ]
        dag = build_execution_dag(atoms, edges, set())

        # Every node still orders, which is only possible with the back edge gone.
        self.assertEqual(len(dag["execution_order"]), 3)
        self.assertEqual(len(dag["edges"]), 2)

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

    def test_fingerprint_changes_when_content_changes_with_same_counts(self):
        atoms_a = [
            {"id": "a1", "stable_id": "a1", "force": "MUST", "canonical_statement": "A",
             "produced_artifacts": [], "consumed_artifacts": []},
        ]
        atoms_b = [
            {"id": "a1", "stable_id": "a1", "force": "MUST", "canonical_statement": "B",
             "produced_artifacts": [], "consumed_artifacts": []},
        ]
        dag_a = build_execution_dag(atoms_a, [], set())
        dag_b = build_execution_dag(atoms_b, [], set())
        self.assertNotEqual(dag_a["fingerprint"], dag_b["fingerprint"])

    def test_fingerprint_is_stable_for_same_execution_graph(self):
        atoms = [
            {"id": "a1", "stable_id": "a1", "force": "MUST", "canonical_statement": "A",
             "produced_artifacts": [], "consumed_artifacts": []},
        ]
        dag_a = build_execution_dag(atoms, [], set())
        dag_b = build_execution_dag(atoms, [], set())
        self.assertEqual(dag_a["fingerprint"], dag_b["fingerprint"])


class TestDependencyEdgeIdentity(unittest.TestCase):
    def test_dependency_edges_have_stable_ids_and_evidence_hashes(self):
        compiler = SpecGraphCompiler("test")
        r = compiler.compile(
            "deps.md",
            (
                "The compiler must produce manifest.\n"
                "The verifier must consume manifest.\n"
            ).encode("utf-8"),
        )
        self.assertEqual(len(r["dependencies"]), 1)
        edge = r["dependencies"][0]
        self.assertTrue(edge["id"].startswith("dep-"))
        self.assertEqual(len(edge["id"]), 20)
        self.assertEqual(len(edge["evidence_sha256"]), 64)

    def test_dependency_edge_id_is_stable(self):
        compiler = SpecGraphCompiler("test")
        raw = (
            "The compiler must produce manifest.\n"
            "The verifier must consume manifest.\n"
        ).encode("utf-8")
        r1 = compiler.compile("deps-a.md", raw)
        r2 = compiler.compile("deps-b.md", raw)
        self.assertEqual(r1["dependencies"], r2["dependencies"])


if __name__ == "__main__":
    unittest.main()
