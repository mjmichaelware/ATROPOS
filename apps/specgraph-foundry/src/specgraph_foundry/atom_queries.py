"""Reading atoms and extractions back.

Read-only views. Separated from extraction for the same reason as everywhere
else here: writing atoms and reading them are different jobs with different
failure modes.
"""

from __future__ import annotations

from .atom_research_tasks import normalize_task

import json
import sqlite3

from .database import Database
from .errors import NotFoundError, ValidationError



def get_extraction(
    database: Database,
    extraction_run_id: str,
) -> dict[str, object]:
    with database.connect() as connection:
        run = connection.execute(
            """
            SELECT *
            FROM extraction_runs
            WHERE id = ?
            """,
            (
                extraction_run_id,
            ),
        ).fetchone()

        if run is None:
            raise NotFoundError(
                "extraction run not found: "
                f"{extraction_run_id}"
            )

        atoms = connection.execute(
            """
            SELECT *
            FROM atoms
            WHERE extraction_run_id = ?
            ORDER BY ordinal
            """,
            (
                extraction_run_id,
            ),
        ).fetchall()

    result = dict(run)
    result["atoms"] = [
        dict(row)
        for row in atoms
    ]

    return result


def list_atoms_page(
    database: Database,
    document_id: str,
    limit: int,
    boundary: dict[str, object] | None = None,
) -> tuple[
    list[dict[str, object]],
    bool,
    dict[str, object] | None,
]:
    parameters: list[object] = [document_id]
    predicate = ""

    if boundary is not None:
        predicate = """
            AND (
                ordinal > ?
                OR (
                    ordinal = ?
                    AND id > ?
                )
            )
        """
        ordinal = int(boundary.get("ordinal", 0))
        parameters.extend(
            [
                ordinal,
                ordinal,
                str(boundary.get("id", "")),
            ]
        )

    parameters.append(limit + 1)

    with database.connect() as connection:
        rows = connection.execute(
            f"""
            SELECT *
            FROM atoms
            WHERE document_id = ?
            {predicate}
            ORDER BY ordinal, id
            LIMIT ?
            """,
            tuple(parameters),
        ).fetchall()

    items = [
        dict(row)
        for row in rows[:limit]
    ]
    has_more = len(rows) > limit
    boundary_item = (
        {
            "ordinal": int(
                items[-1]["ordinal"]
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


def get_atom(
    database: Database,
    atom_id: str,
) -> dict[str, object]:
    with database.connect() as connection:
        atom = connection.execute(
            """
            SELECT *
            FROM atoms
            WHERE id = ?
            """,
            (atom_id,),
        ).fetchone()

        if atom is None:
            raise NotFoundError(
                f"atom not found: {atom_id}"
            )

        dimensions = connection.execute(
            """
            SELECT *
            FROM atom_dimensions
            WHERE atom_id = ?
            ORDER BY dimension
            """,
            (atom_id,),
        ).fetchall()

        tasks = connection.execute(
            """
            SELECT *
            FROM research_tasks
            WHERE atom_id = ?
            ORDER BY dimension
            """,
            (atom_id,),
        ).fetchall()

    result = dict(atom)
    result["dimensions"] = [
        dict(row)
        for row in dimensions
    ]
    result["research_tasks"] = [
        normalize_task(
            dict(row)
        )
        for row in tasks
    ]

    return result


def list_atoms(
    database: Database,
    document_id: str,
) -> list[dict[str, object]]:
    with database.connect() as connection:
        rows = connection.execute(
            """
            SELECT *
            FROM atoms
            WHERE document_id = ?
            ORDER BY ordinal
            """,
            (document_id,),
        ).fetchall()

    return [
        dict(row)
        for row in rows
    ]
