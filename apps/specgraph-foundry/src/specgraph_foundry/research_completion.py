"""Completing or failing a research task.

Both end a claim, and they are together because the difference between them is
one decision -- whether the dimension is now settled -- and reading them apart
hides that.
"""

from __future__ import annotations

from .research_events import record_research_event
from .research_vocabulary import APPLICABILITY
from .primitives import new_id, utc_now
from .research_leases import require_lease
from .research_queries import get_task
import json
import sqlite3

from .database import Database
from .errors import ConflictError, NotFoundError, ValidationError
from .primitives import canonical_json, new_id, parse_time, utc_now, utc_now_datetime



def complete_task(
    database: Database,
    task_id: str,
    worker_id: str,
    conclusion: str,
    applicability: str,
    confidence: float,
    evidence_ids: list[str],
) -> dict[str, object]:
    conclusion = conclusion.strip()
    applicability = applicability.strip().upper()
    evidence_ids = list(
        dict.fromkeys(
            item.strip()
            for item in evidence_ids
            if item.strip()
        )
    )

    if not conclusion:
        raise ValidationError("conclusion is required")

    if applicability not in APPLICABILITY:
        raise ValidationError(
            "invalid applicability"
        )

    if not 0.0 <= confidence <= 1.0:
        raise ValidationError(
            "confidence must be between 0 and 1"
        )

    if not evidence_ids:
        raise ValidationError(
            "at least one evidence item is required"
        )

    timestamp = utc_now()
    claim_id = new_id("claim")

    with database.connect() as connection:
        task = require_lease(
            connection,
            task_id,
            worker_id,
        )

        placeholders = ",".join(
            "?" for _ in evidence_ids
        )

        evidence = connection.execute(
            f"""
            SELECT id
            FROM research_evidence
            WHERE task_id = ?
              AND id IN ({placeholders})
            """,
            (task_id, *evidence_ids),
        ).fetchall()

        found = {
            str(row["id"])
            for row in evidence
        }

        if found != set(evidence_ids):
            raise ValidationError(
                "evidence must belong to the task"
            )

        connection.execute(
            """
            INSERT INTO research_claims(
                id,
                project_id,
                task_id,
                atom_id,
                dimension,
                conclusion,
                applicability,
                confidence,
                status,
                created_at,
                updated_at
            )
            VALUES(?,?,?,?,?,?,?,?,?,?,?)
            """,
            (
                claim_id,
                task["project_id"],
                task_id,
                task["atom_id"],
                task["dimension"],
                conclusion,
                applicability,
                confidence,
                "ACCEPTED",
                timestamp,
                timestamp,
            ),
        )

        for evidence_id in evidence_ids:
            connection.execute(
                """
                INSERT INTO research_claim_evidence(
                    claim_id,
                    evidence_id
                )
                VALUES(?,?)
                """,
                (claim_id, evidence_id),
            )

        dimension_status = (
            "RESOLVED"
            if applicability == "APPLICABLE"
            else "NOT_APPLICABLE"
        )

        connection.execute(
            """
            UPDATE atom_dimensions
            SET applicability = ?,
                status = ?,
                rationale = ?,
                updated_at = ?
            WHERE atom_id = ?
              AND dimension = ?
            """,
            (
                applicability,
                dimension_status,
                conclusion,
                timestamp,
                task["atom_id"],
                task["dimension"],
            ),
        )

        result = {
            "claim_id": claim_id,
            "conclusion": conclusion,
            "applicability": applicability,
            "confidence": confidence,
            "evidence_ids": evidence_ids,
        }

        connection.execute(
            """
            UPDATE research_tasks
            SET status = 'COMPLETE',
                lease_owner = NULL,
                lease_expires_at = NULL,
                result_json = ?,
                updated_at = ?
            WHERE id = ?
            """,
            (
                json.dumps(result, sort_keys=True),
                timestamp,
                task_id,
            ),
        )

        record_research_event(
            connection,
            task_id,
            "COMPLETED",
            worker_id,
            result,
        )

    return get_task(database, task_id)


def fail_task(
    database: Database,
    task_id: str,
    worker_id: str,
    error_message: str,
    retryable: bool = True,
) -> dict[str, object]:
    error_message = error_message.strip()

    if not error_message:
        raise ValidationError(
            "error_message is required"
        )

    status = "PENDING" if retryable else "FAILED"
    result = {
        "error": error_message,
        "retryable": retryable,
    }

    with database.connect() as connection:
        require_lease(
            connection,
            task_id,
            worker_id,
        )

        connection.execute(
            """
            UPDATE research_tasks
            SET status = ?,
                lease_owner = NULL,
                lease_expires_at = NULL,
                result_json = ?,
                updated_at = ?
            WHERE id = ?
            """,
            (
                status,
                json.dumps(result, sort_keys=True),
                utc_now(),
                task_id,
            ),
        )

        record_research_event(
            connection,
            task_id,
            "FAILED",
            worker_id,
            result,
        )

    return get_task(database, task_id)
