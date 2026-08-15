"""Claiming a research task and keeping the claim alive.

The contended path: several researchers may reach for the same open dimension,
and only one may hold it.
"""

from __future__ import annotations

from .research_events import record_research_event
from .primitives import utc_now, utc_now_datetime
from .research_leases import require_lease
from .research_queries import get_task
from datetime import timedelta
import json
import sqlite3

from .database import Database
from .errors import ConflictError, NotFoundError, ValidationError
from .primitives import canonical_json, new_id, parse_time, utc_now, utc_now_datetime



def claim_task(
    database: Database,
    project_id: str,
    worker_id: str,
    lease_seconds: int = 900,
) -> dict[str, object] | None:
    worker_id = worker_id.strip()

    if not worker_id:
        raise ValidationError("worker_id is required")

    if lease_seconds < 30:
        raise ValidationError(
            "lease_seconds must be at least 30"
        )

    now = utc_now_datetime()
    expiration = (
        now + timedelta(seconds=lease_seconds)
    ).isoformat()

    with database.connect() as connection:
        connection.execute("BEGIN IMMEDIATE")

        project = connection.execute(
            "SELECT id FROM projects WHERE id = ?",
            (project_id,),
        ).fetchone()

        if project is None:
            raise NotFoundError(
                f"project not found: {project_id}"
            )

        expired = connection.execute(
            """
            SELECT id
            FROM research_tasks
            WHERE project_id = ?
              AND status = 'CLAIMED'
              AND lease_expires_at IS NOT NULL
              AND lease_expires_at <= ?
            """,
            (project_id, now.isoformat()),
        ).fetchall()

        for row in expired:
            task_id = str(row["id"])

            # WHERE id = ? alone isn't enough: on PostgreSQL (where
            # BEGIN IMMEDIATE is just a plain BEGIN, see below) a second
            # worker's UPDATE here can block on this row, then resume
            # after a third worker has already reclaimed *and*
            # re-claimed it as CLAIMED, and blindly stomp that fresh
            # claim back to PENDING since id-only matches regardless of
            # the row's current state. Re-checking status/expiration in
            # the WHERE clause makes this a no-op once another
            # transaction has already reclaimed the same row, and the
            # rowcount check below skips emitting a misleading
            # LEASE_EXPIRED event for a reclaim that didn't happen.
            cursor = connection.execute(
                """
                UPDATE research_tasks
                SET status = 'PENDING',
                    lease_owner = NULL,
                    lease_expires_at = NULL,
                    updated_at = ?
                WHERE id = ?
                  AND status = 'CLAIMED'
                  AND lease_expires_at IS NOT NULL
                  AND lease_expires_at <= ?
                """,
                (now.isoformat(), task_id, now.isoformat()),
            )

            if cursor.rowcount > 0:
                record_research_event(
                    connection,
                    task_id,
                    "LEASE_EXPIRED",
                    None,
                    {},
                )

        # BEGIN IMMEDIATE gives SQLite an upfront write lock, so a plain
        # SELECT here is already safe against concurrent claimers. On
        # PostgreSQL, database.py downgrades BEGIN IMMEDIATE to a plain
        # BEGIN (no such lock mode exists there) - without FOR UPDATE
        # SKIP LOCKED, two concurrent transactions can both select the
        # same PENDING row before either commits its UPDATE, and both
        # believe they claimed it. SKIP LOCKED is a no-op under SQLite's
        # single-writer model but is invalid SQLite syntax, so it can
        # only be sent on the PostgreSQL path.
        lock_clause = " FOR UPDATE SKIP LOCKED" if database.is_postgres else ""
        task = connection.execute(
            """
            SELECT *
            FROM research_tasks
            WHERE project_id = ?
              AND status = 'PENDING'
            ORDER BY priority, created_at, id
            LIMIT 1
            """
            + lock_clause,
            (project_id,),
        ).fetchone()

        if task is None:
            return None

        task_id = str(task["id"])

        connection.execute(
            """
            UPDATE research_tasks
            SET status = 'CLAIMED',
                lease_owner = ?,
                lease_expires_at = ?,
                attempt_count = attempt_count + 1,
                updated_at = ?
            WHERE id = ?
            """,
            (
                worker_id,
                expiration,
                now.isoformat(),
                task_id,
            ),
        )

        record_research_event(
            connection,
            task_id,
            "CLAIMED",
            worker_id,
            {
                "lease_seconds": lease_seconds,
                "lease_expires_at": expiration,
            },
        )

    return get_task(database, task_id)


def heartbeat(
    database: Database,
    task_id: str,
    worker_id: str,
    lease_seconds: int = 900,
) -> dict[str, object]:
    if lease_seconds < 30:
        raise ValidationError(
            "lease_seconds must be at least 30"
        )

    expiration = (
        utc_now_datetime() + timedelta(seconds=lease_seconds)
    ).isoformat()

    with database.connect() as connection:
        require_lease(
            connection,
            task_id,
            worker_id,
        )

        connection.execute(
            """
            UPDATE research_tasks
            SET lease_expires_at = ?,
                updated_at = ?
            WHERE id = ?
            """,
            (expiration, utc_now(), task_id),
        )

        record_research_event(
            connection,
            task_id,
            "HEARTBEAT",
            worker_id,
            {"lease_expires_at": expiration},
        )

    return get_task(database, task_id)
