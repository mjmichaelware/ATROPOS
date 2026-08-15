"""Existence checks and the plan input fingerprint.

Four small pure helpers that every other planning module needs and none of them
owns. Kept together because they are the questions asked *before* planning
starts -- does this project exist, does this atom, and is this plan the same
plan as last time.
"""

from __future__ import annotations

import hashlib
import json
import sqlite3

from .database import Database
from .errors import ConflictError, NotFoundError, ValidationError
from .primitives import new_id, utc_now



def require_project(
    connection: sqlite3.Connection,
    project_id: str,
) -> None:
    row = connection.execute(
        """
        SELECT id
        FROM projects
        WHERE id = ?
        """,
        (project_id,),
    ).fetchone()

    if row is None:
        raise NotFoundError(
            f"project not found: {project_id}"
        )


def require_atom(
    connection: sqlite3.Connection,
    project_id: str,
    atom_id: str,
) -> None:
    row = connection.execute(
        """
        SELECT id
        FROM atoms
        WHERE id = ?
          AND project_id = ?
        """,
        (
            atom_id,
            project_id,
        ),
    ).fetchone()

    if row is None:
        raise ValidationError(
            f"atom does not belong to "
            f"project: {atom_id}"
        )


def fingerprint(
    atoms: list[dict[str, object]],
    relations: list[
        dict[str, object]
    ],
    dimensions: list[
        dict[str, object]
    ],
) -> str:
    payload = {
        "atoms": atoms,
        "relations": relations,
        "dimensions": dimensions,
    }

    encoded = json.dumps(
        payload,
        sort_keys=True,
        separators=(",", ":"),
    ).encode("utf-8")

    return hashlib.sha256(
        encoded
    ).hexdigest()


def existing_plan(
    database: Database,
    project_id: str,
    fingerprint: str,
    allow_open_research: bool,
) -> sqlite3.Row | None:
    with database.connect() as connection:
        return connection.execute(
            """
            SELECT *
            FROM plan_versions
            WHERE project_id = ?
              AND input_fingerprint = ?
              AND allow_open_research = ?
            """,
            (
                project_id,
                fingerprint,
                allow_open_research,
            ),
        ).fetchone()
