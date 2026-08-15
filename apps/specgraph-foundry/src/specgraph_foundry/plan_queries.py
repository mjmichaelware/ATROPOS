"""Reading plans back.

`get_plan` assembles a plan from four tables plus its two graphs; `list_plans`
enumerates them. Read-only, and separated for the reason the other query modules
are: a module that both builds plans and reads them makes it impossible to tell
which a function does without reading it.
"""

from __future__ import annotations

import json
import sqlite3

from .database import Database
from .errors import NotFoundError
from .plan_guards import require_project


def get_plan(
    database: Database,
    graphs: object,
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

        bindings = [
            dict(row)
            for row in connection.execute(
                """
                SELECT
                    binding.*,
                    atom.canonical_statement,
                    atom.kind,
                    atom.modality
                FROM plan_node_bindings
                AS binding
                JOIN atoms AS atom
                  ON atom.id =
                     binding.atom_id
                WHERE
                    binding.plan_version_id = ?
                ORDER BY
                    binding.sequence_number,
                    CASE binding.stage
                        WHEN 'CONTRACT' THEN 1
                        WHEN 'IMPLEMENTATION'
                            THEN 2
                        WHEN 'VERIFICATION'
                            THEN 3
                        ELSE 4
                    END
                """,
                (plan_id,),
            ).fetchall()
        ]

        findings = [
            dict(row)
            for row in connection.execute(
                """
                SELECT *
                FROM
                    plan_verification_findings
                WHERE plan_version_id = ?
                ORDER BY
                    severity,
                    code,
                    id
                """,
                (plan_id,),
            ).fetchall()
        ]

    result = dict(plan)
    result["allow_open_research"] = bool(
        result["allow_open_research"]
    )
    result["bindings"] = bindings
    result["findings"] = findings
    result["authority_graph"] = (
        graphs.get(
            str(
                result[
                    "authority_graph_id"
                ]
            )
        )
    )
    result["execution_graph"] = (
        graphs.get(
            str(
                result[
                    "execution_graph_id"
                ]
            )
        )
    )
    result["ready_nodes"] = (
        graphs.ready_nodes(
            str(
                result[
                    "execution_graph_id"
                ]
            )
        )
    )

    return result


def list_plans(
    database: Database,
    graphs: object,
    project_id: str,
) -> list[dict[str, object]]:
    with database.connect() as connection:
        require_project(
            connection,
            project_id,
        )

        rows = connection.execute(
            """
            SELECT *
            FROM plan_versions
            WHERE project_id = ?
            ORDER BY created_at DESC, id
            """,
            (project_id,),
        ).fetchall()

    results = []

    for row in rows:
        item = dict(row)
        item[
            "allow_open_research"
        ] = bool(
            item[
                "allow_open_research"
            ]
        )
        results.append(item)

    return results
