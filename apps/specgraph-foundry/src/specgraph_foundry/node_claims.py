"""Claiming a node, and keeping a claim alive.

The two halves of holding work: `claim_node` takes a lease on a ready node,
`heartbeat` extends one. Together they are the contended path -- both open an
immediate transaction because two workers reaching for the same node is the
normal case, not the exceptional one.

Kept apart from :mod:`receipt_submission`, which is what happens when a claim is
given back. Taking work and returning it fail in different ways and are worth
reading separately.
"""

from __future__ import annotations

import json
import sqlite3
from datetime import timedelta

from .database import Database
from .errors import ConflictError, NotFoundError, ValidationError
from .execution_events import record_event
from .execution_queries import get_attempt, get_run_node
from .execution_leases import expire_leases, require_active_claim, require_active_run
from .primitives import canonical_json, new_id, utc_now, utc_now_datetime


def claim_node(
    database: Database,
    run_id: str,
    worker_id: str,
    run_node_id: str | None = None,
    lease_seconds: int = 900,
) -> dict[str, object] | None:
    worker_id = worker_id.strip()

    if not worker_id:
        raise ValidationError(
            "worker_id is required"
        )

    if lease_seconds < 30:
        raise ValidationError(
            "lease_seconds must be at least 30"
        )

    now = utc_now_datetime()
    expiration = (
        now
        + timedelta(
            seconds=lease_seconds
        )
    ).isoformat()

    with database.connect() as connection:
        connection.execute(
            "BEGIN IMMEDIATE"
        )

        run = require_active_run(
            connection,
            run_id,
        )

        expire_leases(
            connection,
            run_id,
            now,
        )

        parameters: list[object] = [
            run_id,
            run[
                "execution_graph_id"
            ],
        ]

        node_filter = ""

        if run_node_id is not None:
            node_filter = (
                "AND node.id = ?"
            )
            parameters.append(
                run_node_id
            )

        candidate = connection.execute(
            f"""
            SELECT node.*
            FROM execution_run_nodes
            AS node
            WHERE node.run_id = ?
              AND node.status = 'PENDING'
              AND NOT EXISTS (
                  SELECT 1
                  FROM graph_edges AS edge
                  JOIN execution_run_nodes
                  AS predecessor
                    ON predecessor.run_id =
                       node.run_id
                   AND predecessor.graph_node_id =
                       edge.from_node_id
                  WHERE edge.graph_id = ?
                    AND edge.to_node_id =
                        node.graph_node_id
                    AND predecessor.status
                        <> 'COMPLETE'
              )
              {node_filter}
            ORDER BY
                node.sequence_number,
                CASE node.stage
                    WHEN 'CONTRACT' THEN 1
                    WHEN 'IMPLEMENTATION' THEN 2
                    WHEN 'VERIFICATION' THEN 3
                    ELSE 4
                END,
                node.id
            LIMIT 1
            """,
            tuple(parameters),
        ).fetchone()

        if candidate is None:
            if run_node_id is None:
                return None

            existing = connection.execute(
                """
                SELECT id
                FROM execution_run_nodes
                WHERE id = ?
                  AND run_id = ?
                """,
                (
                    run_node_id,
                    run_id,
                ),
            ).fetchone()

            if existing is None:
                raise NotFoundError(
                    "execution node not found"
                )

            raise ConflictError(
                "execution node is not ready"
            )

        node_id = str(
            candidate["id"]
        )
        attempt_id = new_id(
            "execution-attempt"
        )

        connection.execute(
            """
            UPDATE execution_run_nodes
            SET status = 'CLAIMED',
                lease_owner = ?,
                lease_expires_at = ?,
                attempt_count =
                    attempt_count + 1,
                updated_at = ?
            WHERE id = ?
            """,
            (
                worker_id,
                expiration,
                now.isoformat(),
                node_id,
            ),
        )

        connection.execute(
            """
            INSERT INTO execution_attempts(
                id,
                run_node_id,
                worker_id,
                status,
                lease_expires_at,
                started_at
            )
            VALUES(?,?,?,?,?,?)
            """,
            (
                attempt_id,
                node_id,
                worker_id,
                "ACTIVE",
                expiration,
                now.isoformat(),
            ),
        )

        record_event(
            connection,
            run_id,
            node_id,
            "NODE_CLAIMED",
            worker_id,
            {
                "attempt_id": attempt_id,
                "lease_expires_at": (
                    expiration
                ),
            },
        )

    return {
        "node": get_run_node(database, 
            node_id
        ),
        "attempt": get_attempt(database, 
            attempt_id
        ),
    }


def heartbeat(
    database: Database,
    run_node_id: str,
    worker_id: str,
    lease_seconds: int = 900,
) -> dict[str, object]:
    if lease_seconds < 30:
        raise ValidationError(
            "lease_seconds must be at least 30"
        )

    expiration = (
        utc_now_datetime()
        + timedelta(
            seconds=lease_seconds
        )
    ).isoformat()

    with database.connect() as connection:
        node, attempt = (
            require_active_claim(
                connection,
                run_node_id,
                worker_id,
            )
        )

        connection.execute(
            """
            UPDATE execution_run_nodes
            SET lease_expires_at = ?,
                updated_at = ?
            WHERE id = ?
            """,
            (
                expiration,
                utc_now(),
                run_node_id,
            ),
        )

        connection.execute(
            """
            UPDATE execution_attempts
            SET lease_expires_at = ?
            WHERE id = ?
            """,
            (
                expiration,
                attempt["id"],
            ),
        )

        record_event(
            connection,
            str(node["run_id"]),
            run_node_id,
            "NODE_HEARTBEAT",
            worker_id,
            {
                "lease_expires_at": (
                    expiration
                )
            },
        )

    return get_run_node(database, 
        run_node_id
    )
