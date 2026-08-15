"""Authority relations between atoms.

How one requirement depends on, refines or conflicts with another. Written and
read here; consumed by the synthesizer when it orders work.
"""

from __future__ import annotations

import json
import sqlite3

from .database import Database
from .errors import ConflictError, NotFoundError, ValidationError
from .primitives import new_id, utc_now
from .relation_types import RELATION_TYPES
from .plan_guards import require_atom, require_project



def add_relation(
    database: Database,
    project_id: str,
    from_atom_id: str,
    to_atom_id: str,
    relation_type: str,
    rationale: str = "",
    confidence: float = 1.0,
    inferred: bool = False,
) -> dict[str, object]:
    relation_type = relation_type.strip().upper()

    if relation_type not in RELATION_TYPES:
        raise ValidationError(
            f"invalid relation type: {relation_type}"
        )

    if from_atom_id == to_atom_id:
        raise ValidationError(
            "authority relation cannot reference "
            "the same atom twice"
        )

    if not 0.0 <= confidence <= 1.0:
        raise ValidationError(
            "confidence must be between 0 and 1"
        )

    relation_id = new_id("relation")

    try:
        with database.connect() as connection:
            require_project(
                connection,
                project_id,
            )

            require_atom(
                connection,
                project_id,
                from_atom_id,
            )

            require_atom(
                connection,
                project_id,
                to_atom_id,
            )

            connection.execute(
                """
                INSERT INTO authority_relations(
                    id,
                    project_id,
                    from_atom_id,
                    to_atom_id,
                    relation_type,
                    rationale,
                    confidence,
                    inferred,
                    created_at
                )
                VALUES(?,?,?,?,?,?,?,?,?)
                """,
                (
                    relation_id,
                    project_id,
                    from_atom_id,
                    to_atom_id,
                    relation_type,
                    rationale.strip(),
                    confidence,
                    inferred,
                    utc_now(),
                ),
            )

    except sqlite3.IntegrityError as error:
        raise ConflictError(
            "authority relation already exists"
        ) from error

    return get_relation(database, relation_id)


def get_relation(
    database: Database,
    relation_id: str,
) -> dict[str, object]:
    with database.connect() as connection:
        row = connection.execute(
            """
            SELECT *
            FROM authority_relations
            WHERE id = ?
            """,
            (relation_id,),
        ).fetchone()

    if row is None:
        raise NotFoundError(
            f"relation not found: {relation_id}"
        )

    result = dict(row)
    result["inferred"] = bool(
        result["inferred"]
    )
    return result


def list_relations(
    database: Database,
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
            FROM authority_relations
            WHERE project_id = ?
            ORDER BY
                relation_type,
                created_at,
                id
            """,
            (project_id,),
        ).fetchall()

    results = []

    for row in rows:
        item = dict(row)
        item["inferred"] = bool(
            item["inferred"]
        )
        results.append(item)

    return results


def list_relations_page(
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
                relation_type > ?
                OR (
                    relation_type = ?
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
        relation_type = str(
            boundary.get("relation_type", "")
        )
        created_at = str(
            boundary.get("created_at", "")
        )
        parameters.extend(
            [
                relation_type,
                relation_type,
                created_at,
                created_at,
                str(boundary.get("id", "")),
            ]
        )

    parameters.append(limit + 1)

    with database.connect() as connection:
        require_project(
            connection,
            project_id,
        )

        rows = connection.execute(
            f"""
            SELECT *
            FROM authority_relations
            WHERE project_id = ?
            {predicate}
            ORDER BY
                relation_type,
                created_at,
                id
            LIMIT ?
            """,
            tuple(parameters),
        ).fetchall()

    items = []

    for row in rows[:limit]:
        item = dict(row)
        item["inferred"] = bool(
            item["inferred"]
        )
        items.append(item)

    has_more = len(rows) > limit
    boundary_item = (
        {
            "relation_type": str(
                items[-1]["relation_type"]
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
