"""Submitting a receipt against a claimed node.

Where a worker gives back the work it took. The gates themselves live in
:mod:`receipt_validation`; this is the transaction around them -- checking the
claim is still held, recording the attempt, and moving the node and run status
on what the gates returned.

Separate from the gates because a receipt can be rejected for two entirely
different reasons: the claim was not valid (this module), or the evidence did
not pass (that one). Collapsing them makes a lease expiry look like a failed
check.
"""

from __future__ import annotations

import hashlib
import json
import sqlite3

from .database import Database
from .errors import ConflictError, NotFoundError, ValidationError
from .execution_events import record_event
from .execution_leases import refresh_run_status, require_active_claim, require_active_run
from .execution_queries import get_attempt, get_receipt, get_run, get_run_node
from .primitives import canonical_json, new_id, utc_now
from .receipt_validation import validate_receipt


def submit_receipt(
    database: Database,
    run_node_id: str,
    worker_id: str,
    actor_system: str,
    outcome: str,
    summary: str,
    evidence: dict[str, object],
) -> dict[str, object]:
    worker_id = worker_id.strip()
    actor_system = actor_system.strip()
    outcome = outcome.strip().upper()
    summary = summary.strip()

    if not worker_id:
        raise ValidationError(
            "worker_id is required"
        )

    if not actor_system:
        raise ValidationError(
            "actor_system is required"
        )

    if not isinstance(evidence, dict):
        raise ValidationError(
            "evidence must be an object"
        )

    receipt_payload = {
        "actor_system": actor_system,
        "actor_id": worker_id,
        "outcome": outcome,
        "summary": summary,
        "evidence": evidence,
    }

    evidence_json = canonical_json(
        evidence
    )

    evidence_sha256 = hashlib.sha256(
        canonical_json(
            receipt_payload
        ).encode("utf-8")
    ).hexdigest()

    receipt_id = new_id(
        "execution-receipt"
    )
    timestamp = utc_now()

    try:
        with database.connect() as connection:
            node, attempt = (
                require_active_claim(
                    connection,
                    run_node_id,
                    worker_id,
                )
            )

            findings = (
                validate_receipt(
                    connection,
                    node,
                    worker_id,
                    outcome,
                    summary,
                    evidence,
                )
            )

            accepted = not findings

            validation_status = (
                "ACCEPTED"
                if accepted
                else "REJECTED"
            )

            connection.execute(
                """
                INSERT INTO execution_receipts(
                    id,
                    run_id,
                    run_node_id,
                    attempt_id,
                    actor_system,
                    actor_id,
                    outcome,
                    summary,
                    evidence_json,
                    evidence_sha256,
                    validation_status,
                    created_at
                )
                VALUES(?,?,?,?,?,?,?,?,?,?,?,?)
                """,
                (
                    receipt_id,
                    node["run_id"],
                    run_node_id,
                    attempt["id"],
                    actor_system,
                    worker_id,
                    outcome,
                    summary,
                    evidence_json,
                    evidence_sha256,
                    validation_status,
                    timestamp,
                ),
            )

            for finding in findings:
                connection.execute(
                    """
                    INSERT INTO
                        execution_validation_findings(
                            id,
                            run_id,
                            run_node_id,
                            receipt_id,
                            gate_code,
                            severity,
                            message,
                            created_at
                        )
                    VALUES(?,?,?,?,?,?,?,?)
                    """,
                    (
                        new_id(
                            "execution-finding"
                        ),
                        node["run_id"],
                        run_node_id,
                        receipt_id,
                        finding["gate_code"],
                        finding["severity"],
                        finding["message"],
                        timestamp,
                    ),
                )

            if accepted:
                connection.execute(
                    """
                    UPDATE execution_run_nodes
                    SET status = 'COMPLETE',
                        accepted_receipt_id = ?,
                        lease_owner = NULL,
                        lease_expires_at = NULL,
                        updated_at = ?
                    WHERE id = ?
                    """,
                    (
                        receipt_id,
                        timestamp,
                        run_node_id,
                    ),
                )

                connection.execute(
                    """
                    UPDATE execution_attempts
                    SET status = 'COMPLETE',
                        completed_at = ?
                    WHERE id = ?
                    """,
                    (
                        timestamp,
                        attempt["id"],
                    ),
                )

                event_type = (
                    "RECEIPT_ACCEPTED"
                )
            else:
                connection.execute(
                    """
                    UPDATE execution_run_nodes
                    SET status = 'PENDING',
                        lease_owner = NULL,
                        lease_expires_at = NULL,
                        updated_at = ?
                    WHERE id = ?
                    """,
                    (
                        timestamp,
                        run_node_id,
                    ),
                )

                connection.execute(
                    """
                    UPDATE execution_attempts
                    SET status = 'FAILED',
                        completed_at = ?,
                        error_message = ?
                    WHERE id = ?
                    """,
                    (
                        timestamp,
                        (
                            "receipt failed "
                            "anti-fake gates"
                        ),
                        attempt["id"],
                    ),
                )

                event_type = (
                    "RECEIPT_REJECTED"
                )

            record_event(
                connection,
                str(node["run_id"]),
                run_node_id,
                event_type,
                worker_id,
                {
                    "receipt_id": receipt_id,
                    "validation_status": (
                        validation_status
                    ),
                    "finding_count": len(
                        findings
                    ),
                },
            )

    except sqlite3.IntegrityError as error:
        raise ConflictError(
            "identical receipt already exists "
            "for this execution node"
        ) from error

    refresh_run_status(database, 
        str(node["run_id"])
    )

    return get_receipt(database, 
        receipt_id
    )
