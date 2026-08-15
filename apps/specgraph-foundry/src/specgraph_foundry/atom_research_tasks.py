"""The research tasks an atom implies.

One task per open dimension. Their own module because they are the boundary
between atomization and research -- the atom side creates them, the research
service claims and completes them, and neither should have to read the other.
"""

from __future__ import annotations

import json
import sqlite3

from .database import Database
from .errors import NotFoundError, ValidationError



def list_research_tasks(
    database: Database,
    project_id: str,
    status: str | None = None,
) -> list[dict[str, object]]:
    with database.connect() as connection:
        if status is None:
            rows = connection.execute(
                """
                SELECT *
                FROM research_tasks
                WHERE project_id = ?
                ORDER BY
                    priority,
                    created_at,
                    id
                """,
                (project_id,),
            ).fetchall()
        else:
            rows = connection.execute(
                """
                SELECT *
                FROM research_tasks
                WHERE project_id = ?
                  AND status = ?
                ORDER BY
                    priority,
                    created_at,
                    id
                """,
                (
                    project_id,
                    status,
                ),
            ).fetchall()

    return [
        normalize_task(
            dict(row)
        )
        for row in rows
    ]


def list_research_tasks_page(
    database: Database,
    project_id: str,
    limit: int,
    boundary: dict[str, object] | None = None,
) -> tuple[
    list[dict[str, object]],
    bool,
    dict[str, object] | None,
]:
    parameters: list[object] = [project_id]
    predicate = ""

    if boundary is not None:
        predicate = """
            AND (
                priority > ?
                OR (
                    priority = ?
                    AND (
                        created_at > ?
                        OR (
                            created_at = ?
                            AND id > ?
                        )
                    )
                )
            )
        """
        priority = int(
            boundary.get("priority", 0)
        )
        created_at = str(
            boundary.get("created_at", "")
        )
        parameters.extend(
            [
                priority,
                priority,
                created_at,
                created_at,
                str(boundary.get("id", "")),
            ]
        )

    parameters.append(limit + 1)

    with database.connect() as connection:
        rows = connection.execute(
            f"""
            SELECT *
            FROM research_tasks
            WHERE project_id = ?
            {predicate}
            ORDER BY
                priority,
                created_at,
                id
            LIMIT ?
            """,
            tuple(parameters),
        ).fetchall()

    items = [
        normalize_task(
            dict(row)
        )
        for row in rows[:limit]
    ]
    has_more = len(rows) > limit
    boundary_item = (
        {
            "priority": int(
                items[-1]["priority"]
            ),
            "created_at": str(
                items[-1]["created_at"]
            ),
            "id": str(items[-1]["id"]),
        }
        if items and has_more
        else None
    )

    return (
        items,
        has_more,
        boundary_item,
    )


def normalize_task(
    task: dict[str, object],
) -> dict[str, object]:
    result_json = task.get(
        "result_json"
    )

    if result_json:
        task["result"] = json.loads(
            str(result_json)
        )
    else:
        task["result"] = None

    task.pop(
        "result_json",
        None,
    )

    return task
