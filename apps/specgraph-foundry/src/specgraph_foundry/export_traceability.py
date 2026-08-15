"""Requirement traceability: every atom mapped to what implements it.

Built once per export and consumed by both the handoff and the blueprint. Its
own module because it is the only part of an export that reasons about the
*relationships* between atoms -- claims, relations, plan bindings -- rather than
about files and digests.
"""

from __future__ import annotations

import json
import sqlite3



def build_traceability(
    atoms: list[dict[str, object]],
    documents: list[
        dict[str, object]
    ],
    dimensions: list[
        dict[str, object]
    ],
    claims: list[
        dict[str, object]
    ],
    evidence: list[
        dict[str, object]
    ],
    claim_evidence: list[
        dict[str, object]
    ],
    plan_bindings: list[
        dict[str, object]
    ],
    relations: list[
        dict[str, object]
    ],
) -> list[dict[str, object]]:
    document_by_id = {
        str(document["id"]): document
        for document in documents
    }

    dimensions_by_atom: dict[
        str,
        list[dict[str, object]],
    ] = {}

    for dimension in dimensions:
        dimensions_by_atom.setdefault(
            str(dimension["atom_id"]),
            [],
        ).append(dimension)

    claims_by_atom: dict[
        str,
        list[dict[str, object]],
    ] = {}

    for claim in claims:
        claims_by_atom.setdefault(
            str(claim["atom_id"]),
            [],
        ).append(claim)

    evidence_by_id = {
        str(item["id"]): item
        for item in evidence
    }

    evidence_ids_by_claim: dict[
        str,
        list[str],
    ] = {}

    for relation in claim_evidence:
        evidence_ids_by_claim.setdefault(
            str(relation["claim_id"]),
            [],
        ).append(
            str(
                relation[
                    "evidence_id"
                ]
            )
        )

    bindings_by_atom: dict[
        str,
        list[dict[str, object]],
    ] = {}

    for binding in plan_bindings:
        bindings_by_atom.setdefault(
            str(binding["atom_id"]),
            [],
        ).append(binding)

    outgoing_by_atom: dict[
        str,
        list[dict[str, object]],
    ] = {}

    incoming_by_atom: dict[
        str,
        list[dict[str, object]],
    ] = {}

    for relation in relations:
        outgoing_by_atom.setdefault(
            str(
                relation[
                    "from_atom_id"
                ]
            ),
            [],
        ).append(relation)

        incoming_by_atom.setdefault(
            str(
                relation[
                    "to_atom_id"
                ]
            ),
            [],
        ).append(relation)

    results = []

    for atom in atoms:
        atom_id = str(atom["id"])
        document = document_by_id.get(
            str(atom["document_id"])
        )

        atom_claims = []

        for claim in claims_by_atom.get(
            atom_id,
            [],
        ):
            claim_result = dict(claim)
            evidence_ids = (
                evidence_ids_by_claim.get(
                    str(claim["id"]),
                    [],
                )
            )

            claim_result[
                "evidence"
            ] = [
                evidence_by_id[
                    evidence_id
                ]
                for evidence_id
                in evidence_ids
                if evidence_id
                in evidence_by_id
            ]

            atom_claims.append(
                claim_result
            )

        results.append(
            {
                "atom_id": atom_id,
                "statement": atom[
                    "canonical_statement"
                ],
                "kind": atom["kind"],
                "modality": atom[
                    "modality"
                ],
                "source": {
                    "document_id": atom[
                        "document_id"
                    ],
                    "document_title": (
                        document["title"]
                        if document
                        else None
                    ),
                    "document_sha256": (
                        document["sha256"]
                        if document
                        else None
                    ),
                    "exact_quote": atom[
                        "exact_quote"
                    ],
                    "byte_start": atom[
                        "byte_start"
                    ],
                    "byte_end": atom[
                        "byte_end"
                    ],
                    "line_start": atom[
                        "line_start"
                    ],
                    "line_end": atom[
                        "line_end"
                    ],
                    "quote_sha256": atom[
                        "source_sha256"
                    ],
                },
                "dimensions": (
                    dimensions_by_atom.get(
                        atom_id,
                        [],
                    )
                ),
                "claims": atom_claims,
                "plan_nodes": (
                    bindings_by_atom.get(
                        atom_id,
                        [],
                    )
                ),
                "outgoing_relations": (
                    outgoing_by_atom.get(
                        atom_id,
                        [],
                    )
                ),
                "incoming_relations": (
                    incoming_by_atom.get(
                        atom_id,
                        [],
                    )
                ),
            }
        )

    return results
