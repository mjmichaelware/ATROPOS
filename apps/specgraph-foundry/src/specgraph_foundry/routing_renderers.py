"""Configuring and selecting document renderers."""

from __future__ import annotations

from .routing_vocabulary import normalize_territories
from .routing_guards import normalize_renderer
from .routing_guards import require_project
import json
import sqlite3

from .database import Database
from .errors import ConflictError, NotFoundError, ValidationError
from .primitives import canonical_json, new_id, utc_now
from .sensitive_keys import contains_sensitive_key



def configure_renderer(
    database: Database,
    project_id: str,
    name: str,
    renderer_type: str,
    territories: list[str],
    priority: int,
    metadata: dict[str, object] | None = None,
    enabled: bool = True,
) -> dict[str, object]:
    name = name.strip()
    renderer_type = (
        renderer_type.strip().upper()
    )
    metadata = metadata or {}

    if not name:
        raise ValidationError(
            "renderer name is required"
        )

    if not renderer_type:
        raise ValidationError(
            "renderer type is required"
        )

    if priority < 0:
        raise ValidationError(
            "priority cannot be negative"
        )

    if not isinstance(metadata, dict):
        raise ValidationError(
            "metadata must be an object"
        )

    if contains_sensitive_key(metadata):
        raise ValidationError(
            "renderer configuration must not "
            "contain secrets or credentials"
        )

    normalized_territories = (
        normalize_territories(
            territories
        )
    )

    timestamp = utc_now()
    renderer_id = new_id("renderer")

    with database.connect() as connection:
        require_project(
            connection,
            project_id,
        )

        existing = connection.execute(
            """
            SELECT id
            FROM renderer_configs
            WHERE project_id = ?
              AND name = ?
            """,
            (
                project_id,
                name,
            ),
        ).fetchone()

        if existing is None:
            connection.execute(
                """
                INSERT INTO renderer_configs(
                    id,
                    project_id,
                    name,
                    renderer_type,
                    territories_json,
                    priority,
                    enabled,
                    status,
                    metadata_json,
                    created_at,
                    updated_at
                )
                VALUES(?,?,?,?,?,?,?,?,?,?,?)
                """,
                (
                    renderer_id,
                    project_id,
                    name,
                    renderer_type,
                    canonical_json(
                        normalized_territories
                    ),
                    priority,
                    enabled,
                    "READY",
                    canonical_json(metadata),
                    timestamp,
                    timestamp,
                ),
            )
        else:
            renderer_id = str(
                existing["id"]
            )

            connection.execute(
                """
                UPDATE renderer_configs
                SET renderer_type = ?,
                    territories_json = ?,
                    priority = ?,
                    enabled = ?,
                    metadata_json = ?,
                    updated_at = ?
                WHERE id = ?
                """,
                (
                    renderer_type,
                    canonical_json(
                        normalized_territories
                    ),
                    priority,
                    enabled,
                    canonical_json(metadata),
                    timestamp,
                    renderer_id,
                ),
            )

    return get_renderer(database, 
        renderer_id
    )


def list_renderers(
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
            FROM renderer_configs
            WHERE project_id = ?
            ORDER BY
                priority,
                name,
                id
            """,
            (project_id,),
        ).fetchall()

    return [
        normalize_renderer(
            dict(row)
        )
        for row in rows
    ]


def get_renderer(
    database: Database,
    renderer_id: str,
) -> dict[str, object]:
    with database.connect() as connection:
        row = connection.execute(
            """
            SELECT *
            FROM renderer_configs
            WHERE id = ?
            """,
            (renderer_id,),
        ).fetchone()

    if row is None:
        raise NotFoundError(
            f"renderer not found: {renderer_id}"
        )

    return normalize_renderer(
        dict(row)
    )


def select_renderer(
    database: Database,
    project_id: str,
    territory: str,
) -> dict[str, object] | None:
    territory = territory.strip().upper()

    if not territory:
        raise ValidationError(
            "territory is required"
        )

    renderers = list_renderers(database, 
        project_id
    )

    eligible = [
        renderer
        for renderer in renderers
        if renderer["enabled"]
        and renderer["status"] == "READY"
        and (
            territory
            in renderer["territories"]
            or "*"
            in renderer["territories"]
        )
    ]

    if not eligible:
        return None

    eligible.sort(
        key=lambda item: (
            int(item["priority"]),
            str(item["name"]),
            str(item["id"]),
        )
    )

    return eligible[0]
