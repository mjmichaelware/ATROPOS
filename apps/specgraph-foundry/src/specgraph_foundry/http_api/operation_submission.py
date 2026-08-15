"""Submitting an operation, and cancelling one.

The two writes a client makes directly. Separate from the worker side because a
client and a worker fail differently: a client can be told its request was
invalid, a worker can only record that the work did not finish.
"""

from __future__ import annotations

from .operation_models import TERMINAL_STATES
from .operation_models import operation_fingerprint
from .operation_models import safe_json
from .operation_models import *  # noqa: F401,F403
from ..errors import ConflictError, NotFoundError
from .idempotency import iso_now
from .idempotency import utc_now
from .operation_queries import public
from datetime import timedelta
import sqlite3
import uuid
import json


def submit(
    store,
    *,
    owner_id: str,
    project_id: str,
    operation_type: str,
    request: dict[str, object],
) -> dict[str, object]:
    fingerprint = operation_fingerprint(
        owner_id=owner_id,
        operation_type=operation_type,
        request=request,
    )

    # Loops at most twice in practice: BEGIN IMMEDIATE alone would be
    # enough to prevent this race on SQLite (it takes a write lock
    # up front), but on the hosted Postgres path "BEGIN IMMEDIATE" is
    # translated to a plain BEGIN under READ COMMITTED, so two
    # concurrent requests (e.g. a double-click) can both pass the
    # SELECT below before either commits its INSERT. The active-state
    # partial unique index still catches that at the database level;
    # this retries the read-then-write cycle instead of surfacing the
    # resulting IntegrityError as a 500, exactly like
    # IdempotencyStore's claim() does for the same race.
    while True:
        now = utc_now()
        now_text = now.isoformat()
        timeout_at = (
            now + timedelta(seconds=store.settings.timeout_seconds)
        ).isoformat()
        operation_id = str(uuid.uuid4())

        with store.database.connect() as connection:
            connection.execute("BEGIN IMMEDIATE")
            # Only dedupe against an operation that is still in flight
            # (QUEUED/CLAIMED/RUNNING/CANCEL_REQUESTED). The fingerprint
            # is a pure function of (owner, type, request) and several
            # operation types - synthesize_project_plan, verify_plan,
            # verify_export - have a request shape that stays identical
            # across legitimate re-submissions (e.g. re-synthesizing a
            # project's plan after completing research, or re-verifying
            # after fixing an issue) even though the real, current
            # server-side state they act on has changed. Matching
            # against a TERMINAL prior operation here would silently
            # replay that operation's original result forever,
            # regardless of a fresh Idempotency-Key, and would
            # permanently block a plan/export from ever reaching a
            # different outcome once it had failed or been blocked
            # once.
            existing = connection.execute(
                """
                SELECT *
                FROM operations
                WHERE owner_id = ?
                  AND operation_type = ?
                  AND fingerprint = ?
                  AND state IN ('QUEUED','CLAIMED','RUNNING','CANCEL_REQUESTED')
                """,
                (owner_id, operation_type, fingerprint),
            ).fetchone()
            if existing is not None:
                return public(dict(existing))

            try:
                connection.execute(
                    """
                    INSERT INTO operations(
                        id,
                        owner_id,
                        project_id,
                        operation_type,
                        fingerprint,
                        state,
                        phase,
                        progress_current,
                        progress_total,
                        attempt_count,
                        max_attempts,
                        next_attempt_at,
                        timeout_at,
                        request_json,
                        created_at,
                        updated_at
                    )
                    VALUES(?,?,?,?,?,?,?,?,?,?,?,?,?,?,?,?)
                    """,
                    (
                        operation_id,
                        owner_id,
                        project_id,
                        operation_type,
                        fingerprint,
                        "QUEUED",
                        "queued",
                        0,
                        1,
                        0,
                        store.settings.max_attempts,
                        now_text,
                        timeout_at,
                        safe_json(request),
                        now_text,
                        now_text,
                    ),
                )
            except sqlite3.IntegrityError:
                continue

            row = connection.execute(
                "SELECT * FROM operations WHERE id = ?",
                (operation_id,),
            ).fetchone()
        return public(dict(row))


def cancel(
    store,
    *,
    owner_id: str,
    operation_id: str,
) -> dict[str, object]:
    now_text = iso_now()
    with store.database.connect() as connection:
        connection.execute("BEGIN IMMEDIATE")
        row = connection.execute(
            """
            SELECT *
            FROM operations
            WHERE id = ?
              AND owner_id = ?
            """,
            (operation_id, owner_id),
        ).fetchone()
        if row is None:
            raise NotFoundError(f"operation not found: {operation_id}")
        state = str(row["state"])
        if state in TERMINAL_STATES:
            return public(dict(row))
        if state == "QUEUED":
            next_state = "CANCELLED"
            finished_at = now_text
        else:
            next_state = "CANCEL_REQUESTED"
            finished_at = row["finished_at"]
        updated = connection.execute(
            """
            UPDATE operations
            SET state = ?,
                cancel_requested_at = ?,
                finished_at = ?,
                updated_at = ?
            WHERE id = ?
              AND owner_id = ?
              AND state = ?
            """,
            (
                next_state,
                now_text,
                finished_at,
                now_text,
                operation_id,
                owner_id,
                state,
            ),
        )
        if updated.rowcount != 1:
            raise ConflictError("operation state changed")
        row = connection.execute(
            "SELECT * FROM operations WHERE id = ?",
            (operation_id,),
        ).fetchone()
    return public(dict(row))
