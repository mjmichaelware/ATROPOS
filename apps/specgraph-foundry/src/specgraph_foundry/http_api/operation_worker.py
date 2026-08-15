"""The worker side: claiming an operation, reporting on it, and expiry.

A lease is what makes an operation safe to retry -- `recover_expired` is the only
thing that can hand abandoned work to another worker, and it must not be able to
take work that is still held.
"""

from __future__ import annotations
from .operation_models import OperationCancelled
from .operation_models import OperationLease
from .operation_models import WorkerLeaseLost
from .operation_models import sanitize_message
from .operation_models import *  # noqa: F401,F403
from ..errors import ConflictError, ValidationError
from .idempotency import iso_now
from .idempotency import parse_time
from .idempotency import utc_now
from datetime import timedelta
import secrets
import json

def renewed_lease_expiry(store) -> str:
    return (utc_now() + timedelta(seconds=store.settings.lease_seconds)).isoformat()



def claim(
    store,
    *,
    worker_id: str,
) -> OperationLease | None:
    store.recover_expired()
    now = utc_now()
    now_text = now.isoformat()
    lease_token = secrets.token_urlsafe(32)
    token_hash = store.hash_lease_token(lease_token)
    lease_expires = (
        now + timedelta(seconds=store.settings.lease_seconds)
    ).isoformat()

    with store.database.connect() as connection:
        connection.execute("BEGIN IMMEDIATE")
        row = connection.execute(
            """
            SELECT *
            FROM operations
            WHERE state = 'QUEUED'
              AND next_attempt_at <= ?
              AND timeout_at > ?
            ORDER BY next_attempt_at, created_at, id
            LIMIT 1
            """,
            (now_text, now_text),
        ).fetchone()
        if row is None:
            return None
        updated = connection.execute(
            """
            UPDATE operations
            SET state = 'CLAIMED',
                worker_id = ?,
                lease_token_hash = ?,
                lease_expires_at = ?,
                heartbeat_at = ?,
                attempt_count = attempt_count + 1,
                updated_at = ?
            WHERE id = ?
              AND state = 'QUEUED'
            """,
            (
                worker_id,
                token_hash,
                lease_expires,
                now_text,
                now_text,
                row["id"],
            ),
        )
        if updated.rowcount != 1:
            return None
        claimed = connection.execute(
            "SELECT * FROM operations WHERE id = ?",
            (row["id"],),
        ).fetchone()
    return OperationLease(
        operation=dict(claimed),
        worker_id=worker_id,
        lease_token=lease_token,
    )


def progress(
    store,
    lease: OperationLease,
    *,
    phase: str,
    current: int,
    total: int,
) -> dict[str, object]:
    if current < 0 or total < 1 or current > total:
        raise ValidationError("operation progress is invalid")
    row = leased_update(store, 
        lease,
        expected_states={"RUNNING", "CANCEL_REQUESTED"},
        assignments={
            "phase": phase,
            "progress_current": current,
            "progress_total": total,
            "heartbeat_at": iso_now(),
            "lease_expires_at": renewed_lease_expiry(store, ),
        },
    )
    if str(row["state"]) == "CANCEL_REQUESTED":
        raise OperationCancelled("operation cancellation requested")
    return row


def fail(
    store,
    lease: OperationLease,
    *,
    code: str,
    message: str,
    retryable: bool,
) -> dict[str, object]:
    row = lease_row(store, lease)
    now = utc_now()
    if retryable and int(row["attempt_count"]) < int(row["max_attempts"]):
        delay = min(
            store.settings.retry_base_seconds
            * (2 ** max(0, int(row["attempt_count"]) - 1)),
            300,
        )
        return leased_update(store, 
            lease,
            expected_states={"CLAIMED", "RUNNING", "CANCEL_REQUESTED"},
            assignments={
                "state": "QUEUED",
                "phase": "retry_scheduled",
                "worker_id": None,
                "lease_token_hash": None,
                "lease_expires_at": None,
                "heartbeat_at": None,
                "next_attempt_at": (
                    now + timedelta(seconds=delay)
                ).isoformat(),
                "error_code": code,
                "error_message": sanitize_message(message),
            },
        )
    return leased_update(store, 
        lease,
        expected_states={"CLAIMED", "RUNNING", "CANCEL_REQUESTED"},
        assignments={
            "state": "FAILED",
            "phase": "failed",
            "error_code": code,
            "error_message": sanitize_message(message),
            "finished_at": now.isoformat(),
        },
    )


def recover_expired(store) -> None:
    now = utc_now()
    now_text = now.isoformat()
    with store.database.connect() as connection:
        connection.execute("BEGIN IMMEDIATE")
        rows = connection.execute(
            """
            SELECT *
            FROM operations
            WHERE state IN ('CLAIMED','RUNNING','CANCEL_REQUESTED','QUEUED')
              AND (
                timeout_at <= ?
                OR (
                    lease_expires_at IS NOT NULL
                    AND lease_expires_at <= ?
                )
              )
            ORDER BY created_at, id
            """,
            (now_text, now_text),
        ).fetchall()
        for row in rows:
            state = str(row["state"])
            if parse_time(str(row["timeout_at"])) <= now:
                connection.execute(
                    """
                    UPDATE operations
                    SET state = 'TIMED_OUT',
                        phase = 'timed_out',
                        finished_at = ?,
                        updated_at = ?
                    WHERE id = ?
                      AND state = ?
                    """,
                    (now_text, now_text, row["id"], state),
                )
                continue
            if state == "CANCEL_REQUESTED":
                connection.execute(
                    """
                    UPDATE operations
                    SET state = 'CANCELLED',
                        phase = 'cancelled',
                        finished_at = ?,
                        updated_at = ?
                    WHERE id = ?
                      AND state = 'CANCEL_REQUESTED'
                    """,
                    (now_text, now_text, row["id"]),
                )
            elif int(row["attempt_count"]) < int(row["max_attempts"]):
                delay = min(
                    store.settings.retry_base_seconds
                    * (2 ** max(0, int(row["attempt_count"]) - 1)),
                    300,
                )
                connection.execute(
                    """
                    UPDATE operations
                    SET state = 'QUEUED',
                        phase = 'lease_expired',
                        worker_id = NULL,
                        lease_token_hash = NULL,
                        lease_expires_at = NULL,
                        heartbeat_at = NULL,
                        next_attempt_at = ?,
                        updated_at = ?
                    WHERE id = ?
                      AND state IN ('CLAIMED','RUNNING')
                    """,
                    (
                        (now + timedelta(seconds=delay)).isoformat(),
                        now_text,
                        row["id"],
                    ),
                )
            else:
                connection.execute(
                    """
                    UPDATE operations
                    SET state = 'FAILED',
                        phase = 'attempts_exhausted',
                        error_code = 'OPERATION_IN_PROGRESS',
                        error_message = 'operation lease expired',
                        finished_at = ?,
                        updated_at = ?
                    WHERE id = ?
                      AND state IN ('CLAIMED','RUNNING')
                    """,
                    (now_text, now_text, row["id"]),
                )


def leased_update(
    store,
    lease: OperationLease,
    *,
    expected_states: set[str],
    assignments: dict[str, object],
) -> dict[str, object]:
    # Read, validate, and write inside a single connection so that no
    # concurrent recover_expired() call can sneak between the SELECT
    # and the UPDATE and clear our lease fields while we're holding
    # an apparently-valid row in memory.
    token_hash = store.hash_lease_token(lease.lease_token)
    op_id = lease.operation["id"]

    assignments = dict(assignments)
    now = utc_now()
    assignments["updated_at"] = now.isoformat()
    columns = ", ".join(f"{key} = ?" for key in assignments)

    with store.database.connect() as connection:
        connection.execute("BEGIN IMMEDIATE")
        row = connection.execute(
            """
            SELECT *
            FROM operations
            WHERE id = ?
              AND worker_id = ?
              AND lease_token_hash = ?
            """,
            (op_id, lease.worker_id, token_hash),
        ).fetchone()

        if row is None:
            raise WorkerLeaseLost("worker lease is not current")

        state = str(row["state"])
        if state not in expected_states:
            raise ConflictError("operation state does not allow this transition")

        if row["lease_expires_at"] is None or parse_time(
            str(row["lease_expires_at"])
        ) <= now:
            raise WorkerLeaseLost("worker lease expired")

        values = list(assignments.values())
        values.extend([op_id, lease.worker_id, token_hash, state])
        updated = connection.execute(
            f"""
            UPDATE operations
            SET {columns}
            WHERE id = ?
              AND worker_id = ?
              AND lease_token_hash = ?
              AND state = ?
            """,
            tuple(values),
        )
        if updated.rowcount != 1:
            raise WorkerLeaseLost("worker lease is no longer current")
        stored = connection.execute(
            "SELECT * FROM operations WHERE id = ?",
            (op_id,),
        ).fetchone()
    return dict(stored)


def lease_row(
    store,
    lease: OperationLease,
) -> dict[str, object]:
    with store.database.connect() as connection:
        row = connection.execute(
            """
            SELECT *
            FROM operations
            WHERE id = ?
              AND worker_id = ?
              AND lease_token_hash = ?
            """,
            (
                lease.operation["id"],
                lease.worker_id,
                store.hash_lease_token(lease.lease_token),
            ),
        ).fetchone()
    if row is None:
        raise WorkerLeaseLost("worker lease is not current")
    return dict(row)
