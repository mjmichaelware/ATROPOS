"""Read-only views over an execution run.

Every function here answers a question and changes nothing -- which is why they
are not in :mod:`execution`. That module is a state machine (claim, heartbeat,
submit, verify); a reader mixed into it could not be recognised as a reader
without reading it first.

They take a `Database` explicitly rather than carrying one, so the call site
shows that nothing is being mutated.
"""

from __future__ import annotations

import json
import sqlite3

from .database import Database
from .errors import NotFoundError
from .execution_events import normalize_event, normalize_receipt


def ready_nodes(
    database: Database,
    run_id: str,
) -> list[dict[str, object]]:
    with database.connect() as connection:
        run = connection.execute(
            """
            SELECT
                run.status,
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

        if run["status"] not in {
            "RUNNING",
        }:
            return []

        rows = connection.execute(
            """
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
            ORDER BY
                node.sequence_number,
                CASE node.stage
                    WHEN 'CONTRACT' THEN 1
                    WHEN 'IMPLEMENTATION' THEN 2
                    WHEN 'VERIFICATION' THEN 3
                    ELSE 4
                END,
                node.id
            """,
            (
                run_id,
                run[
                    "execution_graph_id"
                ],
            ),
        ).fetchall()

    return [
        dict(row)
        for row in rows
    ]


def get_run(
    database: Database,
    run_id: str,
) -> dict[str, object]:
    with database.connect() as connection:
        run = connection.execute(
            """
            SELECT *
            FROM execution_runs
            WHERE id = ?
            """,
            (run_id,),
        ).fetchone()

        if run is None:
            raise NotFoundError(
                f"execution run not found: "
                f"{run_id}"
            )

        nodes = connection.execute(
            """
            SELECT *
            FROM execution_run_nodes
            WHERE run_id = ?
            ORDER BY
                sequence_number,
                CASE stage
                    WHEN 'CONTRACT' THEN 1
                    WHEN 'IMPLEMENTATION' THEN 2
                    WHEN 'VERIFICATION' THEN 3
                    ELSE 4
                END,
                id
            """,
            (run_id,),
        ).fetchall()

        attempts = connection.execute(
            """
            SELECT attempt.*
            FROM execution_attempts
            AS attempt
            JOIN execution_run_nodes
            AS node
              ON node.id =
                 attempt.run_node_id
            WHERE node.run_id = ?
            ORDER BY
                attempt.started_at,
                attempt.id
            """,
            (run_id,),
        ).fetchall()

        receipts = connection.execute(
            """
            SELECT *
            FROM execution_receipts
            WHERE run_id = ?
            ORDER BY created_at, id
            """,
            (run_id,),
        ).fetchall()

        findings = connection.execute(
            """
            SELECT *
            FROM execution_validation_findings
            WHERE run_id = ?
            ORDER BY
                created_at,
                id
            """,
            (run_id,),
        ).fetchall()

        events = connection.execute(
            """
            SELECT *
            FROM execution_events
            WHERE run_id = ?
            ORDER BY created_at, id
            """,
            (run_id,),
        ).fetchall()

    result = dict(run)
    result["nodes"] = [
        dict(row)
        for row in nodes
    ]
    result["attempts"] = [
        dict(row)
        for row in attempts
    ]
    result["receipts"] = [
        normalize_receipt(
            dict(row)
        )
        for row in receipts
    ]
    result["findings"] = [
        dict(row)
        for row in findings
    ]
    result["events"] = [
        normalize_event(
            dict(row)
        )
        for row in events
    ]
    result["ready_nodes"] = (
        ready_nodes(database, run_id)
    )

    return result


def list_runs(
    database: Database,
    project_id: str,
) -> list[dict[str, object]]:
    with database.connect() as connection:
        project = connection.execute(
            """
            SELECT id
            FROM projects
            WHERE id = ?
            """,
            (project_id,),
        ).fetchone()

        if project is None:
            raise NotFoundError(
                f"project not found: "
                f"{project_id}"
            )

        rows = connection.execute(
            """
            SELECT *
            FROM execution_runs
            WHERE project_id = ?
            ORDER BY created_at DESC, id
            """,
            (project_id,),
        ).fetchall()

    return [
        dict(row)
        for row in rows
    ]


def get_run_node(
    database: Database,
    run_node_id: str,
) -> dict[str, object]:
    with database.connect() as connection:
        row = connection.execute(
            """
            SELECT *
            FROM execution_run_nodes
            WHERE id = ?
            """,
            (run_node_id,),
        ).fetchone()

    if row is None:
        raise NotFoundError(
            f"execution node not found: "
            f"{run_node_id}"
        )

    return dict(row)


def get_attempt(
    database: Database,
    attempt_id: str,
) -> dict[str, object]:
    with database.connect() as connection:
        row = connection.execute(
            """
            SELECT *
            FROM execution_attempts
            WHERE id = ?
            """,
            (attempt_id,),
        ).fetchone()

    if row is None:
        raise NotFoundError(
            f"execution attempt not found: "
            f"{attempt_id}"
        )

    return dict(row)


def get_receipt(
    database: Database,
    receipt_id: str,
) -> dict[str, object]:
    with database.connect() as connection:
        row = connection.execute(
            """
            SELECT *
            FROM execution_receipts
            WHERE id = ?
            """,
            (receipt_id,),
        ).fetchone()

        if row is None:
            raise NotFoundError(
                f"execution receipt not found: "
                f"{receipt_id}"
            )

        findings = connection.execute(
            """
            SELECT *
            FROM execution_validation_findings
            WHERE receipt_id = ?
            ORDER BY gate_code, id
            """,
            (receipt_id,),
        ).fetchall()

    result = normalize_receipt(
        dict(row)
    )

    result["findings"] = [
        dict(item)
        for item in findings
    ]

    return result
