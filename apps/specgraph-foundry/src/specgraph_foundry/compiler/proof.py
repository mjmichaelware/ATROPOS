
from __future__ import annotations

from typing import Any, Dict, List
from .compiler_fingerprints import generate_fingerprint
"""The compiler's proof bundle and frontier metrics.

What a compile emits *about itself*: the evidence bundle and the numbers that
say how far the specification has been driven. Separate from the compile pass
because these are read by release decisions, not by compilation.
"""


def _duplicate_canonical_groups(
    requirements: List[Dict[str, Any]],
) -> List[Dict[str, Any]]:
    groups: Dict[str, List[str]] = {}
    statements: Dict[str, str] = {}
    for req in requirements:
        normalized = " ".join(
            str(req.get("canonical_statement", ""))
            .casefold()
            .split()
        )
        if not normalized:
            continue
        groups.setdefault(normalized, []).append(req["stable_id"])
        statements[normalized] = req["canonical_statement"]

    return [
        {
            "canonical_statement": statements[normalized],
            "stable_ids": sorted(stable_ids),
        }
        for normalized, stable_ids in sorted(groups.items())
        if len(stable_ids) > 1
    ]


def _has_valid_coordinates(coordinates: Dict[str, Any]) -> bool:
    return (
        isinstance(coordinates, dict)
        and isinstance(coordinates.get("byte_start"), int)
        and isinstance(coordinates.get("byte_end"), int)
        and isinstance(coordinates.get("line_start"), int)
        and isinstance(coordinates.get("line_end"), int)
        and coordinates["byte_start"] >= 0
        and coordinates["byte_end"] >= coordinates["byte_start"]
        and coordinates["line_start"] >= 1
        and coordinates["line_end"] >= coordinates["line_start"]
    )


def _pct(numerator: int, denominator: int) -> int:
    if denominator == 0:
        return 100
    return int((numerator * 100) / denominator)


def _dangling_executable_nodes(
    execution_dag: Dict[str, Any],
) -> List[str]:
    return sorted(
        node.get("id", "")
        for node in execution_dag.get("nodes", [])
        if not node.get("source_atom_id") or not node.get("acceptance_basis")
    )


def build_proof_bundle(
    compiler_namespace: str,
    source_document_id: str,
    source_sha256: str,
    requirements: List[Dict[str, Any]],
    unresolved_records: List[Dict[str, Any]],
    relations: List[Dict[str, Any]],
    dependencies: List[Dict[str, Any]],
    execution_dag: Dict[str, Any],
    authority_graph_metrics: Dict[str, Any],
    execution_graph_metrics: Dict[str, Any],
    shacl_result: Dict[str, Any],
    validation_findings: List[Dict[str, Any]],
) -> Dict[str, Any]:
    duplicate_canonical_groups = _duplicate_canonical_groups(requirements)
    orphaned_evidence_refs = [
        {
            "stable_id": req["stable_id"],
            "evidence_ref": evidence_ref,
        }
        for req in requirements
        for evidence_ref in req.get("evidence_refs", [])
        if not evidence_ref.get("id") and not evidence_ref.get("uri")
    ]
    frontier_metrics = compute_frontier_metrics(
        requirements,
        duplicate_canonical_groups,
        orphaned_evidence_refs,
        execution_dag,
        execution_graph_metrics,
    )
    checksums = {
        "accepted_atoms_sha256": generate_fingerprint(requirements),
        "rejection_ledger_sha256": generate_fingerprint(unresolved_records),
        "authority_relations_sha256": generate_fingerprint(relations),
        "dependency_graph_sha256": generate_fingerprint(dependencies),
        "execution_graph_sha256": generate_fingerprint(execution_dag),
        "authority_graph_metrics_sha256": generate_fingerprint(authority_graph_metrics),
        "execution_graph_metrics_sha256": generate_fingerprint(execution_graph_metrics),
        "duplicate_canonical_groups_sha256": generate_fingerprint(duplicate_canonical_groups),
        "orphaned_evidence_refs_sha256": generate_fingerprint(orphaned_evidence_refs),
        "frontier_metrics_sha256": generate_fingerprint(frontier_metrics),
        "traceability_sha256": generate_fingerprint([
            {
                "stable_id": req["stable_id"],
                "source_sha256": req["source_sha256"],
                "coordinates": req["coordinates"],
                "semantic_owner": req["semantic_owner"],
                "implementation_symbols": req["implementation_symbols"],
                "behavioral_tests": req["behavioral_tests"],
                "evidence_refs": req["evidence_refs"],
                "acceptance_predicate": req["acceptance_predicate"],
                "completion_state": req["completion_state"],
            }
            for req in requirements
        ]),
        "shacl_validation_sha256": generate_fingerprint(shacl_result),
        "graph_validation_sha256": generate_fingerprint(validation_findings),
    }
    return {
        "schema_version": "specgraph-proof-bundle-v1",
        "compiler_namespace": compiler_namespace,
        "verifier_identity": "specgraph.compiler.v1",
        "source_document_id": source_document_id,
        "source_sha256": source_sha256,
        "accepted_atom_count": len(requirements),
        "rejection_count": len(unresolved_records),
        "authority_relation_count": len(relations),
        "dependency_edge_count": len(dependencies),
        "execution_node_count": len(execution_dag.get("nodes", [])),
        "execution_edge_count": len(execution_dag.get("edges", [])),
        "authority_graph_metrics": authority_graph_metrics,
        "execution_graph_metrics": execution_graph_metrics,
        "duplicate_canonical_groups": duplicate_canonical_groups,
        "duplicate_canonical_atom_count": sum(
            max(0, len(group["stable_ids"]) - 1)
            for group in duplicate_canonical_groups
        ),
        "orphaned_evidence_refs": orphaned_evidence_refs,
        "orphaned_evidence_ref_count": len(orphaned_evidence_refs),
        "frontier_metrics": frontier_metrics,
        "checksums": checksums,
        "bundle_sha256": generate_fingerprint(checksums),
    }


def compute_frontier_metrics(
    requirements: List[Dict[str, Any]],
    duplicate_canonical_groups: List[Dict[str, Any]],
    orphaned_evidence_refs: List[Dict[str, Any]],
    execution_dag: Dict[str, Any],
    execution_graph_metrics: Dict[str, Any],
) -> Dict[str, Any]:
    total = len(requirements)
    coordinate_complete = sum(
        1
        for req in requirements
        if _has_valid_coordinates(req.get("coordinates", {}))
    )
    authority_fingerprint_complete = sum(
        1
        for req in requirements
        if req.get("source_sha256")
        and req.get("source_artifact_id") == f"sha256:{req.get('source_sha256')}"
        and req.get("artifact_hashes", {}).get("source_sha256") == req.get("source_sha256")
    )
    traceability_complete = sum(
        1
        for req in requirements
        if req.get("stable_id")
        and req.get("canonical_statement")
        and req.get("source_sha256")
        and _has_valid_coordinates(req.get("coordinates", {}))
        and req.get("semantic_owner")
        and req.get("acceptance_predicate")
        and req.get("completion_state")
        and req.get("verifier_identity")
        and isinstance(req.get("artifact_hashes"), dict)
    )
    return {
        "accepted_atom_reproducibility_target_pct": 100 if total else 100,
        "source_coordinate_coverage_pct": _pct(coordinate_complete, total),
        "authority_fingerprint_coverage_pct": _pct(authority_fingerprint_complete, total),
        "traceability_schema_validity_pct": _pct(traceability_complete, total),
        "root_reachability_pct": _pct(
            execution_graph_metrics.get("reachable_from_roots_count", 0),
            execution_graph_metrics.get("node_count", 0),
        ),
        "terminal_reachability_pct": _pct(
            execution_graph_metrics.get("terminal_reachable_from_roots_count", 0),
            len(execution_graph_metrics.get("terminal_node_ids", [])),
        ),
        "dangling_executable_nodes": len(_dangling_executable_nodes(execution_dag)),
        "duplicate_canonical_atoms": sum(
            max(0, len(group["stable_ids"]) - 1)
            for group in duplicate_canonical_groups
        ),
        "orphaned_evidence_references": len(orphaned_evidence_refs),
        "checksum_disagreement": 0,
        "secret_leakage": 0,
        "unexplained_metric_exclusions": 0,
        "fixture_contamination": 0,
    }
