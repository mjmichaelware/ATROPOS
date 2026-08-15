"""Integration bindings: what an export is wired to.

Binding is the only write path here that touches credentials, which is why it is
its own module -- `contains_sensitive_key` is consulted on every binding, and a
change to that check should not be made while thinking about bundles or
manifests.
"""

from __future__ import annotations

import json
import sqlite3

from .database import Database
from .errors import ConflictError, NotFoundError, ValidationError
from .primitives import new_id, utc_now
from .sensitive_keys import contains_sensitive_key


def bind_integration(
    database: Database,
    project_id: str,
    system_name: str,
    binding_type: str,
    config: dict[str, object],
    enabled: bool = True,
) -> dict[str, object]:
    system_name = system_name.strip()
    binding_type = binding_type.strip().upper()

    if not system_name:
        raise ValidationError(
            "system_name is required"
        )

    if not binding_type:
        raise ValidationError(
            "binding_type is required"
        )

    if not isinstance(config, dict):
        raise ValidationError(
            "integration config must be an object"
        )

    if contains_sensitive_key(config):
        raise ValidationError(
            "integration bindings must not "
            "contain secrets or credentials"
        )

    binding_id = new_id("binding")
    timestamp = utc_now()
    config_json = json.dumps(
        config,
        sort_keys=True,
        separators=(",", ":"),
        ensure_ascii=False,
    )

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
                f"project not found: {project_id}"
            )

        existing = connection.execute(
            """
            SELECT id
            FROM integration_bindings
            WHERE project_id = ?
              AND system_name = ?
              AND binding_type = ?
            """,
            (
                project_id,
                system_name,
                binding_type,
            ),
        ).fetchone()

        if existing is not None:
            binding_id = str(
                existing["id"]
            )

            connection.execute(
                """
                UPDATE integration_bindings
                SET config_json = ?,
                    enabled = ?,
                    updated_at = ?
                WHERE id = ?
                """,
                (
                    config_json,
                    enabled,
                    timestamp,
                    binding_id,
                ),
            )
        else:
            connection.execute(
                """
                INSERT INTO integration_bindings(
                    id,
                    project_id,
                    system_name,
                    binding_type,
                    config_json,
                    enabled,
                    created_at,
                    updated_at
                )
                VALUES(?,?,?,?,?,?,?,?)
                """,
                (
                    binding_id,
                    project_id,
                    system_name,
                    binding_type,
                    config_json,
                    enabled,
                    timestamp,
                    timestamp,
                ),
            )

    return get_binding(database, binding_id)


def get_binding(
    database: Database,
    binding_id: str,
) -> dict[str, object]:
    with database.connect() as connection:
        row = connection.execute(
            """
            SELECT *
            FROM integration_bindings
            WHERE id = ?
            """,
            (binding_id,),
        ).fetchone()

    if row is None:
        raise NotFoundError(
            f"integration binding not found: "
            f"{binding_id}"
        )

    return normalize_binding(
        dict(row)
    )


def list_bindings(
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
                f"project not found: {project_id}"
            )

        rows = connection.execute(
            """
            SELECT *
            FROM integration_bindings
            WHERE project_id = ?
            ORDER BY
                system_name,
                binding_type,
                id
            """,
            (project_id,),
        ).fetchall()

    return [
        normalize_binding(
            dict(row)
        )
        for row in rows
    ]


def normalize_binding(
    record: dict[str, object],
) -> dict[str, object]:
    config_json = record.pop(
        "config_json",
        "{}",
    )

    record["config"] = json.loads(
        str(config_json)
    )

    record["enabled"] = bool(
        record["enabled"]
    )

    return record
