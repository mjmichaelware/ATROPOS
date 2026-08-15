"""Verifying a synthesized plan.

Whether a plan may be executed: every atom bound, every stage present, the graph
acyclic, no open research where it is not allowed. Separate from synthesis
because building a plan and judging one are different jobs, and a synthesizer
that also grades its own output tends to grade it generously.
"""

from __future__ import annotations

from collections import defaultdict
import json
import sqlite3

from .database import Database
from .errors import ConflictError, NotFoundError, ValidationError
from .primitives import new_id, utc_now
from .plan_graph_rules import graph_has_cycle
from .stages import STAGES



def verify_plan(
    database: Database,
    plan_id: str,
) -> dict[str, object]:
    with database.connect() as connection:
        plan = connection.execute(
            """
            SELECT *
            FROM plan_versions
            WHERE id = ?
            """,
            (plan_id,),
        ).fetchone()

        if plan is None:
            raise NotFoundError(
                f"plan not found: {plan_id}"
            )

        nodes = [
            dict(row)
            for row in connection.execute(
                """
                SELECT *
                FROM graph_nodes
                WHERE graph_id = ?
                ORDER BY node_key
                """,
                (
                    plan[
                        "execution_graph_id"
                    ],
                ),
            ).fetchall()
        ]

        edges = [
            dict(row)
            for row in connection.execute(
                """
                SELECT *
                FROM graph_edges
                WHERE graph_id = ?
                ORDER BY id
                """,
                (
                    plan[
                        "execution_graph_id"
                    ],
                ),
            ).fetchall()
        ]

        bindings = [
            dict(row)
            for row in connection.execute(
                """
                SELECT *
                FROM plan_node_bindings
                WHERE plan_version_id = ?
                ORDER BY
                    sequence_number,
                    stage
                """,
                (plan_id,),
            ).fetchall()
        ]

        connection.execute(
            """
            DELETE FROM
                plan_verification_findings
            WHERE plan_version_id = ?
            """,
            (plan_id,),
        )

        findings: list[
            dict[str, object]
        ] = []

        node_ids = {
            str(node["id"])
            for node in nodes
        }

        if len(node_ids) != int(
            plan["node_count"]
        ):
            findings.append(
                {
                    "severity": "ERROR",
                    "code": (
                        "NODE_COUNT_MISMATCH"
                    ),
                    "message": (
                        "Stored plan node count does "
                        "not match execution graph."
                    ),
                    "entity_id": plan_id,
                }
            )

        if len(bindings) != len(nodes):
            findings.append(
                {
                    "severity": "ERROR",
                    "code": (
                        "UNBOUND_EXECUTION_NODE"
                    ),
                    "message": (
                        "Every execution node must "
                        "have exactly one atom binding."
                    ),
                    "entity_id": plan_id,
                }
            )

        binding_stages: dict[
            str,
            set[str],
        ] = defaultdict(set)

        for binding in bindings:
            binding_stages[
                str(binding["atom_id"])
            ].add(
                str(binding["stage"])
            )

            if (
                str(
                    binding[
                        "graph_node_id"
                    ]
                )
                not in node_ids
            ):
                findings.append(
                    {
                        "severity": "ERROR",
                        "code": (
                            "BINDING_NODE_MISSING"
                        ),
                        "message": (
                            "Plan binding references "
                            "a missing graph node."
                        ),
                        "entity_id": str(
                            binding["id"]
                        ),
                    }
                )

        for atom_id, stages in (
            binding_stages.items()
        ):
            if stages != set(STAGES):
                findings.append(
                    {
                        "severity": "ERROR",
                        "code": (
                            "ATOM_STAGE_INCOMPLETE"
                        ),
                        "message": (
                            "Every atom requires "
                            "contract, implementation, "
                            "and verification stages."
                        ),
                        "entity_id": atom_id,
                    }
                )

        if graph_has_cycle(
            node_ids,
            edges,
        ):
            findings.append(
                {
                    "severity": "ERROR",
                    "code": (
                        "EXECUTION_GRAPH_CYCLE"
                    ),
                    "message": (
                        "Execution graph contains "
                        "a dependency cycle."
                    ),
                    "entity_id": str(
                        plan[
                            "execution_graph_id"
                        ]
                    ),
                }
            )

        if int(
            plan["open_dimension_count"]
        ) > 0:
            findings.append(
                {
                    "severity": "WARNING",
                    "code": (
                        "OPEN_RESEARCH_DIMENSIONS"
                    ),
                    "message": (
                        "Plan contains atoms with "
                        "unresolved research dimensions."
                    ),
                    "entity_id": plan_id,
                }
            )

        for finding in findings:
            connection.execute(
                """
                INSERT INTO
                    plan_verification_findings(
                        id,
                        plan_version_id,
                        severity,
                        code,
                        message,
                        entity_id,
                        created_at
                    )
                VALUES(?,?,?,?,?,?,?)
                """,
                (
                    new_id("finding"),
                    plan_id,
                    finding["severity"],
                    finding["code"],
                    finding["message"],
                    finding["entity_id"],
                    utc_now(),
                ),
            )

        error_count = sum(
            finding["severity"] == "ERROR"
            for finding in findings
        )

        if error_count:
            status = "INVALID"
        elif (
            int(
                plan[
                    "open_dimension_count"
                ]
            )
            > 0
            and not bool(
                plan[
                    "allow_open_research"
                ]
            )
        ):
            status = "BLOCKED"
        else:
            status = "VERIFIED"

        verified_at = utc_now()

        connection.execute(
            """
            UPDATE plan_versions
            SET status = ?,
                verified_at = ?
            WHERE id = ?
            """,
            (
                status,
                verified_at,
                plan_id,
            ),
        )

    return {
        "plan_id": plan_id,
        "status": status,
        "error_count": error_count,
        "finding_count": len(findings),
        "findings": findings,
        "verified_at": verified_at,
    }
