from typing import Any, Dict, List

from .compiler_fingerprints import generate_fingerprint


PROOF_BUNDLE_SCHEMA_VERSION = "specgraph-proof-bundle-v1"

REQUIRED_CHECKSUM_KEYS = {
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
}


class ProofBundleVerificationFinding:
    def __init__(
        self,
        severity: str,
        code: str,
        message: str,
        path: str,
    ):
        self.severity = severity
        self.code = code
        self.message = message
        self.path = path

    def to_dict(self) -> Dict[str, str]:
        return {
            "severity": self.severity,
            "code": self.code,
            "message": self.message,
            "path": self.path,
        }


def verify_proof_bundle(
    proof_bundle: Dict[str, Any],
) -> Dict[str, Any]:
    findings: List[ProofBundleVerificationFinding] = []

    if proof_bundle.get("schema_version") != PROOF_BUNDLE_SCHEMA_VERSION:
        findings.append(ProofBundleVerificationFinding(
            "ERROR",
            "INVALID_SCHEMA_VERSION",
            "Proof bundle schema version is not supported.",
            "schema_version",
        ))

    checksums = proof_bundle.get("checksums")
    if not isinstance(checksums, dict):
        findings.append(ProofBundleVerificationFinding(
            "ERROR",
            "MISSING_CHECKSUMS",
            "Proof bundle checksums object is missing.",
            "checksums",
        ))
        checksums = {}

    for key in sorted(REQUIRED_CHECKSUM_KEYS):
        value = checksums.get(key)
        if not isinstance(value, str) or not _is_sha256(value):
            findings.append(ProofBundleVerificationFinding(
                "ERROR",
                "INVALID_CHECKSUM",
                f"Required checksum {key} is missing or malformed.",
                f"checksums.{key}",
            ))

    expected_bundle_sha = generate_fingerprint(checksums)
    observed_bundle_sha = proof_bundle.get("bundle_sha256")
    if observed_bundle_sha != expected_bundle_sha:
        findings.append(ProofBundleVerificationFinding(
            "ERROR",
            "BUNDLE_CHECKSUM_MISMATCH",
            (
                "Proof bundle checksum mismatch: "
                f"expected {expected_bundle_sha}, observed {observed_bundle_sha}."
            ),
            "bundle_sha256",
        ))

    frontier = proof_bundle.get("frontier_metrics")
    if not isinstance(frontier, dict):
        findings.append(ProofBundleVerificationFinding(
            "ERROR",
            "MISSING_FRONTIER_METRICS",
            "Proof bundle frontier metrics are missing.",
            "frontier_metrics",
        ))
        frontier = {}

    for key in (
        "checksum_disagreement",
        "secret_leakage",
        "unexplained_metric_exclusions",
        "fixture_contamination",
        "dangling_executable_nodes",
        "duplicate_canonical_atoms",
        "orphaned_evidence_references",
    ):
        if frontier.get(key, 0) != 0:
            findings.append(ProofBundleVerificationFinding(
                "ERROR",
                "NONZERO_FRONTIER_FAILURE",
                f"Frontier metric {key} must be 0.",
                f"frontier_metrics.{key}",
            ))

    for key in (
        "source_coordinate_coverage_pct",
        "authority_fingerprint_coverage_pct",
        "traceability_schema_validity_pct",
    ):
        if frontier.get(key) != 100:
            findings.append(ProofBundleVerificationFinding(
                "ERROR",
                "INCOMPLETE_FRONTIER_COVERAGE",
                f"Frontier metric {key} must be 100.",
                f"frontier_metrics.{key}",
            ))

    return {
        "valid": not any(f.severity == "ERROR" for f in findings),
        "finding_count": len(findings),
        "findings": [finding.to_dict() for finding in findings],
        "verifier_identity": "specgraph.proof_bundle.verifier.v1",
    }


def _is_sha256(value: str) -> bool:
    if len(value) != 64:
        return False
    return all(char in "0123456789abcdef" for char in value)
