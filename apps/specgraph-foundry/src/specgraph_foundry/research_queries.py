"""Reading tasks back, and the gap matrix.

The gap matrix is the project-wide view: which dimensions of which atoms are
still open. It is the number a release decision is made on.
"""

from __future__ import annotations

from .research_events import normalize_research_event
import json
import sqlite3

from .database import Database
from .errors import ConflictError, NotFoundError, ValidationError
from .primitives import canonical_json, new_id, parse_time, utc_now, utc_now_datetime



def get_task(
    database: Database,
    task_id: str,
) -> dict[str, object]:
    with database.connect() as connection:
        task = connection.execute(
            """
            SELECT
                task.*,
                atom.canonical_statement,
                atom.kind,
                atom.modality
            FROM research_tasks AS task
            JOIN atoms AS atom
              ON atom.id = task.atom_id
            WHERE task.id = ?
            """,
            (task_id,),
        ).fetchone()

        if task is None:
            raise NotFoundError(
                f"research task not found: {task_id}"
            )

        evidence = connection.execute(
            """
            SELECT *
            FROM research_evidence
            WHERE task_id = ?
            ORDER BY created_at, id
            """,
            (task_id,),
        ).fetchall()

        claim = connection.execute(
            """
            SELECT *
            FROM research_claims
            WHERE task_id = ?
            """,
            (task_id,),
        ).fetchone()

        events = connection.execute(
            """
            SELECT *
            FROM research_task_events
            WHERE task_id = ?
            ORDER BY created_at, id
            """,
            (task_id,),
        ).fetchall()

    result = dict(task)
    result_json = result.pop("result_json", None)
    result["result"] = (
        json.loads(str(result_json))
        if result_json
        else None
    )
    result["evidence"] = [
        dict(row) for row in evidence
    ]
    result["claim"] = (
        dict(claim)
        if claim is not None
        else None
    )
    result["events"] = [
        normalize_research_event(dict(row))
        for row in events
    ]

    return result


def gap_matrix(
    database: Database,
    project_id: str,
) -> dict[str, object]:
    with database.connect() as connection:
        project = connection.execute(
            """
            SELECT id, slug, name
            FROM projects
            WHERE id = ?
            """,
            (project_id,),
        ).fetchone()

        if project is None:
            raise NotFoundError(
                f"project not found: {project_id}"
            )

        atoms = connection.execute(
            """
            SELECT
                id,
                document_id,
                ordinal,
                kind,
                modality,
                canonical_statement
            FROM atoms
            WHERE project_id = ?
            ORDER BY document_id, ordinal, id
            """,
            (project_id,),
        ).fetchall()

        dimensions = connection.execute(
            """
            SELECT
                dimensions.atom_id,
                dimensions.dimension,
                dimensions.applicability,
                dimensions.status,
                dimensions.rationale,
                tasks.id AS task_id,
                tasks.status AS task_status
            FROM atom_dimensions AS dimensions
            JOIN atoms
              ON atoms.id = dimensions.atom_id
            LEFT JOIN research_tasks AS tasks
              ON tasks.atom_id = dimensions.atom_id
             AND tasks.dimension = dimensions.dimension
            WHERE atoms.project_id = ?
            ORDER BY
                dimensions.atom_id,
                dimensions.dimension
            """,
            (project_id,),
        ).fetchall()

    grouped: dict[
        str,
        list[dict[str, object]],
    ] = {}

    for row in dimensions:
        grouped.setdefault(
            str(row["atom_id"]),
            [],
        ).append(dict(row))

    atom_results = []
    total = 0
    resolved = 0
    not_applicable = 0
    open_count = 0
    ready_atoms = 0

    for atom_row in atoms:
        atom = dict(atom_row)
        atom_dimensions = grouped.get(
            str(atom["id"]),
            [],
        )

        atom_resolved = sum(
            item["status"] == "RESOLVED"
            for item in atom_dimensions
        )

        atom_not_applicable = sum(
            item["status"] == "NOT_APPLICABLE"
            for item in atom_dimensions
        )

        atom_open = sum(
            item["status"] == "OPEN"
            for item in atom_dimensions
        )

        atom_ready = (
            bool(atom_dimensions)
            and atom_open == 0
        )

        if atom_ready:
            ready_atoms += 1

        total += len(atom_dimensions)
        resolved += atom_resolved
        not_applicable += atom_not_applicable
        open_count += atom_open

        atom["dimensions"] = atom_dimensions
        atom["ready"] = atom_ready
        atom["open_dimensions"] = atom_open
        atom_results.append(atom)

    atom_count = len(atom_results)

    return {
        "project": dict(project),
        "summary": {
            "atom_count": atom_count,
            "ready_atoms": ready_atoms,
            "blocked_atoms": atom_count - ready_atoms,
            "total_dimensions": total,
            "resolved_dimensions": resolved,
            "not_applicable_dimensions": (
                not_applicable
            ),
            "open_dimensions": open_count,
            "ready": (
                atom_count > 0
                and ready_atoms == atom_count
            ),
        },
        "atoms": atom_results,
    }
