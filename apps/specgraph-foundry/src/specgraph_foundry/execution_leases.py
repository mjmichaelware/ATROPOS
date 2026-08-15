"""Run status, claim checks and lease expiry.

Who may act on a node right now, and what a run's status should be given its
nodes. One concern from two directions: a lease decides whether an actor still
holds a node, and the run status is what those answers aggregate to.

Out of :mod:`execution` because they run on nearly every call -- claim,
heartbeat, submit and verify each consult them -- so they are the code most
often read while debugging, and they sat 1,500 lines into the file.

`RUN_ACTIVE_STATUSES` travels with them because they are what reads it.
"""

from __future__ import annotations

import json
import sqlite3
from datetime import datetime, timedelta

from .database import Database
from .errors import ConflictError, NotFoundError, ValidationError
from .execution_events import record_event
from .primitives import parse_time, utc_now, utc_now_datetime

RUN_ACTIVE_STATUSES = {
    "RUNNING",
}


def require_active_run(
    connection: sqlite3.Connection,
    run_id: str,
) -> sqlite3.Row:
    run = connection.execute(
        """
        SELECT
            run.*,
            plan.execution_graph_id
        FROM execution_runs AS run
        JOIN plan_versions AS plan
          ON plan.id =
             run.plan_version_id
        WHERE run.id = ?
        """,
        (run_id,),
    ).fetchone()

    if run is None:
        raise NotFoundError(
            f"execution run not found: "
            f"{run_id}"
        )

    if run["status"] not in (
        RUN_ACTIVE_STATUSES
    ):
        raise ConflictError(
            "execution run is not active"
        )

    return run


def require_active_claim(
    connection: sqlite3.Connection,
    run_node_id: str,
    worker_id: str,
) -> tuple[
    sqlite3.Row,
    sqlite3.Row,
]:
    node = connection.execute(
        """
        SELECT *
        FROM execution_run_nodes
        WHERE id = ?
        """,
        (run_node_id,),
    ).fetchone()

    if node is None:
        raise NotFoundError(
            f"execution node not found: "
            f"{run_node_id}"
        )

    if node["status"] != "CLAIMED":
        raise ConflictError(
            "execution node is not claimed"
        )

    if node["lease_owner"] != worker_id:
        raise ConflictError(
            "execution node belongs to "
            "another worker"
        )

    expiration_value = node[
        "lease_expires_at"
    ]

    if (
        expiration_value is None
        or parse_time(
            str(expiration_value)
        )
        <= utc_now_datetime()
    ):
        raise ConflictError(
            "execution-node lease expired"
        )

    attempt = connection.execute(
        """
        SELECT *
        FROM execution_attempts
        WHERE run_node_id = ?
          AND worker_id = ?
          AND status = 'ACTIVE'
        ORDER BY started_at DESC, id DESC
        LIMIT 1
        """,
        (
            run_node_id,
            worker_id,
        ),
    ).fetchone()

    if attempt is None:
        raise ConflictError(
            "active execution attempt "
            "does not exist"
        )

    return node, attempt


def refresh_run_status(
    database: Database,
    run_id: str,
) -> None:
    timestamp = utc_now()

    with database.connect() as connection:
        counts = connection.execute(
            """
            SELECT
                COUNT(*) AS total,
                SUM(
                    CASE
                        WHEN status = 'COMPLETE'
                        THEN 1
                        ELSE 0
                    END
                ) AS complete_count
            FROM execution_run_nodes
            WHERE run_id = ?
            """,
            (run_id,),
        ).fetchone()

        total = int(
            counts["total"] or 0
        )
        complete_count = int(
            counts[
                "complete_count"
            ]
            or 0
        )

        complete = (
            total > 0
            and total == complete_count
        )

        status = (
            "COMPLETE"
            if complete
            else "RUNNING"
        )

        connection.execute(
            """
            UPDATE execution_runs
            SET status = ?,
                completed_at = CASE
                    WHEN ? THEN
                        COALESCE(
                            completed_at,
                            ?
                        )
                    ELSE NULL
                END
            WHERE id = ?
              AND status NOT IN (
                  'VERIFIED',
                  'INVALID'
              )
            """,
            (
                status,
                complete,
                timestamp,
                run_id,
            ),
        )


def expire_leases(
    connection: sqlite3.Connection,
    run_id: str,
    now: datetime,
) -> None:
    rows = connection.execute(
        """
        SELECT *
        FROM execution_run_nodes
        WHERE run_id = ?
          AND status = 'CLAIMED'
          AND lease_expires_at IS NOT NULL
          AND lease_expires_at <= ?
        """,
        (
            run_id,
            now.isoformat(),
        ),
    ).fetchall()

    for node in rows:
        node_id = str(node["id"])

        connection.execute(
            """
            UPDATE execution_attempts
            SET status = 'EXPIRED',
                completed_at = ?,
                error_message = ?
            WHERE run_node_id = ?
              AND status = 'ACTIVE'
            """,
            (
                now.isoformat(),
                "execution-node lease expired",
                node_id,
            ),
        )

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
                now.isoformat(),
                node_id,
            ),
        )

        record_event(
            connection,
            run_id,
            node_id,
            "NODE_LEASE_EXPIRED",
            None,
            {},
        )
