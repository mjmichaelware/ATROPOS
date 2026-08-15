"""Re-checking one completed node against its stored receipt.

The per-node half of `verify_run`: for each node it re-reads the receipt,
recomputes the evidence digest, and reports any node whose stored hash no longer
matches its stored evidence.

Its own module because it is the only part of verification that is per-node --
everything else in `verify_run` is about the run as a whole (fingerprint drift,
node count, export binding, final status). It was also 168 of that function's
415 lines, which made the run-level logic hard to see at all.

Appends to `findings` rather than returning, matching the caller's accumulation
across several unrelated checks.
"""

from __future__ import annotations

import hashlib
import json
import sqlite3

from .primitives import canonical_json


def verify_nodes(
    connection: sqlite3.Connection,
    nodes: list[sqlite3.Row],
    findings: list[dict[str, object]],
) -> None:
    """Checks every node's receipt, recording a finding per tampered node."""
    for node in nodes:
        node_id = str(
            node["id"]
        )

        if node["status"] != "COMPLETE":
            findings.append(
                {
                    "gate_code": (
                        "EXECUTION_NODE_"
                        "INCOMPLETE"
                    ),
                    "severity": "ERROR",
                    "message": (
                        "Execution node has not "
                        "completed."
                    ),
                    "run_node_id": node_id,
                }
            )
            continue

        receipt_id = node[
            "accepted_receipt_id"
        ]

        if receipt_id is None:
            findings.append(
                {
                    "gate_code": (
                        "ACCEPTED_RECEIPT_"
                        "MISSING"
                    ),
                    "severity": "ERROR",
                    "message": (
                        "Completed node has no "
                        "accepted receipt."
                    ),
                    "run_node_id": node_id,
                }
            )
            continue

        receipt = connection.execute(
            """
            SELECT *
            FROM execution_receipts
            WHERE id = ?
              AND run_node_id = ?
            """,
            (
                receipt_id,
                node_id,
            ),
        ).fetchone()

        if receipt is None:
            findings.append(
                {
                    "gate_code": (
                        "ACCEPTED_RECEIPT_"
                        "NOT_FOUND"
                    ),
                    "severity": "ERROR",
                    "message": (
                        "Accepted receipt record "
                        "does not exist."
                    ),
                    "run_node_id": node_id,
                }
            )
            continue

        if (
            receipt[
                "validation_status"
            ]
            != "ACCEPTED"
        ):
            findings.append(
                {
                    "gate_code": (
                        "RECEIPT_STATUS_INVALID"
                    ),
                    "severity": "ERROR",
                    "message": (
                        "Completed node references "
                        "a rejected receipt."
                    ),
                    "run_node_id": node_id,
                }
            )

        try:
            evidence = json.loads(
                receipt[
                    "evidence_json"
                ]
            )
        except json.JSONDecodeError:
            evidence = None

        if not isinstance(
            evidence,
            dict,
        ):
            findings.append(
                {
                    "gate_code": (
                        "RECEIPT_EVIDENCE_"
                        "INVALID"
                    ),
                    "severity": "ERROR",
                    "message": (
                        "Receipt evidence is not "
                        "valid JSON object data."
                    ),
                    "run_node_id": node_id,
                }
            )
            continue

        payload = {
            "actor_system": receipt[
                "actor_system"
            ],
            "actor_id": receipt[
                "actor_id"
            ],
            "outcome": receipt[
                "outcome"
            ],
            "summary": receipt[
                "summary"
            ],
            "evidence": evidence,
        }

        actual_sha256 = (
            hashlib.sha256(
                canonical_json(
                    payload
                ).encode(
                    "utf-8"
                )
            ).hexdigest()
        )

        if (
            actual_sha256
            != receipt[
                "evidence_sha256"
            ]
        ):
            findings.append(
                {
                    "gate_code": (
                        "EVIDENCE_HASH_"
                        "MISMATCH"
                    ),
                    "severity": "ERROR",
                    "message": (
                        "Stored receipt evidence "
                        "has changed since it was "
                        "accepted."
                    ),
                    "run_node_id": node_id,
                }
            )
