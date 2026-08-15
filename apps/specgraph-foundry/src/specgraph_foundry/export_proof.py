"""The export proof summary: building it, and checking one that came back.

An export is only useful if it can be re-verified later, and these three are
what make that possible -- the checksum file parser, the summary builder, and
the check that a returned summary matches its own internal digest.

Grouped because they are the only functions here that must agree byte for byte:
the builder and the verifier are two halves of one format, and a change to
either that is not made to the other silently invalidates every past export.
"""

from __future__ import annotations

import hashlib
import json
from pathlib import Path

from .primitives import canonical_json


def canonical_json_bytes(value: object) -> bytes:
    """Canonical JSON with the trailing newline the artifacts carry."""
    return (canonical_json(value) + "\n").encode("utf-8")


def sha256_bytes(value: bytes) -> str:
    return hashlib.sha256(value).hexdigest()


def sha256_file(path: Path) -> str:
    """Digest a file without holding all of it in memory.

    Export artifacts run to hundreds of kilobytes and a bundle to several
    megabytes; chunked reads keep verification flat in memory regardless.
    """
    digest = hashlib.sha256()
    with path.open("rb") as handle:
        for chunk in iter(lambda: handle.read(65536), b""):
            digest.update(chunk)
    return digest.hexdigest()


def parse_checksums_file(
    path: Path,
) -> dict[str, str] | None:
    observed: dict[str, str] = {}
    try:
        lines = path.read_text(
            encoding="utf-8",
        ).splitlines()
    except UnicodeDecodeError:
        return None

    for line in lines:
        if not line.strip():
            continue
        parts = line.split()
        if len(parts) != 2:
            return None
        checksum, relative_path = parts
        if (
            len(checksum) != 64
            or any(
                char
                not in "0123456789abcdef"
                for char in checksum
            )
        ):
            return None
        if relative_path in observed:
            return None
        observed[relative_path] = checksum
    return observed


def build_export_proof_summary(
    project_id: str,
    plan: dict[str, object],
    artifacts: dict[str, bytes],
    traceability: list[dict[str, object]],
    authority_payload: dict[str, object],
    execution_payload: dict[str, object],
) -> dict[str, object]:
    artifact_hashes = {
        name: sha256_bytes(content)
        for name, content
        in sorted(artifacts.items())
    }
    traceability_hash = sha256_bytes(
        canonical_json_bytes(
            {"items": traceability}
        )
    )
    authority_hash = sha256_bytes(
        canonical_json_bytes(
            authority_payload
        )
    )
    execution_hash = sha256_bytes(
        canonical_json_bytes(
            execution_payload
        )
    )
    payload = {
        "schema_version": "specgraph.export.proof-summary.v1",
        "project_id": project_id,
        "plan_id": plan["id"],
        "plan_status": plan["status"],
        "plan_input_fingerprint": plan["input_fingerprint"],
        "atom_count": plan["atom_count"],
        "artifact_count": len(artifacts),
        "artifact_hashes": artifact_hashes,
        "traceability_sha256": traceability_hash,
        "authority_graph_sha256": authority_hash,
        "execution_graph_sha256": execution_hash,
        "acceptance": {
            "plan_status_verified": plan["status"] == "VERIFIED",
            "traceability_items": len(traceability),
            "artifact_hashes_present": all(
                len(value) == 64
                for value in artifact_hashes.values()
            ),
        },
        "verifier_identity": "specgraph.export.proof-summary.v1",
    }
    return {
        **payload,
        "proof_summary_sha256": sha256_bytes(
            canonical_json_bytes(payload)
        ),
    }


def verify_export_proof_summary(
    path: Path,
) -> list[dict[str, object]]:
    try:
        proof = json.loads(
            path.read_text(
                encoding="utf-8"
            )
        )
    except (
        UnicodeDecodeError,
        json.JSONDecodeError,
    ):
        return [
            {
                "severity": "ERROR",
                "code": "EXPORT_PROOF_SUMMARY_INVALID",
                "message": (
                    "export_proof_summary.json is not valid UTF-8 JSON."
                ),
                "artifact_path": "export_proof_summary.json",
            }
        ]

    if not isinstance(proof, dict):
        return [
            {
                "severity": "ERROR",
                "code": "EXPORT_PROOF_SUMMARY_INVALID",
                "message": (
                    "export_proof_summary.json must contain an object."
                ),
                "artifact_path": "export_proof_summary.json",
            }
        ]

    observed = proof.get(
        "proof_summary_sha256"
    )
    payload = {
        key: value
        for key, value
        in proof.items()
        if key != "proof_summary_sha256"
    }
    expected = sha256_bytes(
        canonical_json_bytes(payload)
    )
    if observed != expected:
        return [
            {
                "severity": "ERROR",
                "code": "EXPORT_PROOF_SUMMARY_CHECKSUM_MISMATCH",
                "message": (
                    "export_proof_summary.json internal checksum does not match."
                ),
                "artifact_path": "export_proof_summary.json",
            }
        ]
    return []
