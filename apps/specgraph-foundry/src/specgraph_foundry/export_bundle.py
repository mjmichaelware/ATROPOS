"""Assembling the artifact bundle.

Reads the whole project -- documents, atoms, dimensions, claims, evidence,
relations, plan bindings -- and turns it into the twelve artifacts plus their
manifest. The individual artifact builders live in their own modules; this is
what gathers their inputs and hashes the result.

Separate from :mod:`exports` because it is the only place that queries every
table at once, and separate from the builders because assembling inputs and
formatting output fail in different ways.
"""

from __future__ import annotations

import json
import sqlite3

from .database import Database
from .errors import NotFoundError
from .export_bindings import normalize_binding
from .export_bundle_reads import load_project_records
from .export_handoff import build_handoff
from .export_schema import EXPORT_TYPE
from .export_markdown import build_markdown
from .export_proof import (
    build_export_proof_summary,
    canonical_json_bytes,
    sha256_bytes,
)
from .export_traceability import build_traceability
from .planning import PlanningService
from .rendering import markdown_to_plain_text


def build_bundle(
    database: Database,
    planning: PlanningService,
    project_id: str,
    plan: dict[str, object],
) -> dict[str, object]:
    records = load_project_records(database, project_id)
    project_row = records["project_row"]
    documents = records["documents"]
    sections = records["sections"]
    atoms = records["atoms"]
    dimensions = records["dimensions"]
    claims = records["claims"]
    evidence = records["evidence"]
    claim_evidence = records["claim_evidence"]
    bindings = records["bindings"]

    project = dict(project_row)
    relations = (
        planning.list_relations(
            project_id
        )
    )

    sources_payload = {
        "documents": documents,
        "sections": sections,
    }

    authority_payload = {
        "relations": relations,
        "graph": plan[
            "authority_graph"
        ],
    }

    execution_payload = {
        "plan": {
            key: value
            for key, value in plan.items()
            if key
            not in {
                "authority_graph",
                "execution_graph",
                "bindings",
                "findings",
                "ready_nodes",
            }
        },
        "graph": plan[
            "execution_graph"
        ],
        "bindings": plan[
            "bindings"
        ],
        "findings": plan[
            "findings"
        ],
        "ready_nodes": plan[
            "ready_nodes"
        ],
    }

    research_payload = {
        "dimensions": dimensions,
        "claims": claims,
        "evidence": evidence,
        "claim_evidence": (
            claim_evidence
        ),
    }

    traceability = (
        build_traceability(
            atoms=atoms,
            documents=documents,
            dimensions=dimensions,
            claims=claims,
            evidence=evidence,
            claim_evidence=(
                claim_evidence
            ),
            plan_bindings=list(
                plan["bindings"]
            ),
            relations=relations,
        )
    )

    handoff = build_handoff(
        project=project,
        plan=plan,
        traceability=traceability,
        bindings=bindings,
    )

    markdown = build_markdown(
        project=project,
        plan=plan,
        atoms=atoms,
        traceability=traceability,
        bindings=bindings,
    )

    artifact_values: dict[
        str,
        bytes,
    ] = {
        "project.json": (
            canonical_json_bytes(
                project
            )
        ),
        "sources.json": (
            canonical_json_bytes(
                sources_payload
            )
        ),
        "atoms.json": (
            canonical_json_bytes(
                {"atoms": atoms}
            )
        ),
        "research.json": (
            canonical_json_bytes(
                research_payload
            )
        ),
        "authority_graph.json": (
            canonical_json_bytes(
                authority_payload
            )
        ),
        "execution_graph.json": (
            canonical_json_bytes(
                execution_payload
            )
        ),
        "traceability.json": (
            canonical_json_bytes(
                {
                    "items": (
                        traceability
                    )
                }
            )
        ),
        "integration_bindings.json": (
            canonical_json_bytes(
                {"bindings": bindings}
            )
        ),
        "atropos_handoff.json": (
            canonical_json_bytes(
                handoff
            )
        ),
        "implementation_blueprint.md": (
            markdown.encode("utf-8")
        ),
        "implementation_blueprint.txt": (
            markdown_to_plain_text(
                markdown
            ).encode("utf-8")
        ),
    }
    artifact_values[
        "export_proof_summary.json"
    ] = canonical_json_bytes(
        build_export_proof_summary(
            project_id=project_id,
            plan=plan,
            artifacts=artifact_values,
            traceability=traceability,
            authority_payload=authority_payload,
            execution_payload=execution_payload,
        )
    )

    artifact_metadata = {
        name: {
            "sha256": sha256_bytes(
                content
            ),
            "bytes": len(content),
        }
        for name, content
        in sorted(
            artifact_values.items()
        )
    }

    bundle_fingerprint = (
        sha256_bytes(
            canonical_json_bytes(
                artifact_metadata
            )
        )
    )

    manifest = {
        "schema": (
            "specgraph.export.manifest.v1"
        ),
        "export_type": EXPORT_TYPE,
        "project_id": project_id,
        "plan_id": plan["id"],
        "plan_input_fingerprint": (
            plan["input_fingerprint"]
        ),
        "bundle_fingerprint": (
            bundle_fingerprint
        ),
        "compiler_version_fingerprint": (
            "specgraph-v1"
        ),
        "artifact_count": len(
            artifact_values
        ),
        "proof_summary": {
            "path": "export_proof_summary.json",
            "sha256": artifact_metadata[
                "export_proof_summary.json"
            ]["sha256"],
            "verifier_identity": (
                "specgraph.export.proof-summary.v1"
            ),
        },
        "artifacts": (
            artifact_metadata
        ),
    }

    return {
        "project": project,
        "artifacts": artifact_values,
        "artifact_checksums": {
            name: metadata[
                "sha256"
            ]
            for name, metadata
            in artifact_metadata.items()
        },
        "bundle_fingerprint": (
            bundle_fingerprint
        ),
        "manifest": manifest,
    }
