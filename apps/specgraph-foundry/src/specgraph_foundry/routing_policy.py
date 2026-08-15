"""The routing policy: what a project is allowed to do.

Whether paid providers may be reached at all, and under what unlock. Its own
module because it is the setting every routing decision is checked against.
"""

from __future__ import annotations

from .routing_vocabulary import CANONICAL_ROUTE_LAW
from .routing_guards import normalize_policy
from .routing_guards import require_project
import json
import sqlite3

from .database import Database
from .errors import ConflictError, NotFoundError, ValidationError
from .primitives import canonical_json, new_id, utc_now
from .sensitive_keys import contains_sensitive_key



def set_policy(
    database: Database,
    project_id: str,
    allow_offline_degraded: bool = True,
    paid_emergency_enabled: bool = False,
    max_paid_decisions_per_unlock: int = 1,
) -> dict[str, object]:
    if max_paid_decisions_per_unlock < 1:
        raise ValidationError(
            "max paid decisions must be positive"
        )

    timestamp = utc_now()
    policy_id = new_id("policy")

    with database.connect() as connection:
        require_project(
            connection,
            project_id,
        )

        existing = connection.execute(
            """
            SELECT id
            FROM project_policies
            WHERE project_id = ?
            """,
            (project_id,),
        ).fetchone()

        if existing is None:
            connection.execute(
                """
                INSERT INTO project_policies(
                    id,
                    project_id,
                    route_law_json,
                    allow_offline_degraded,
                    paid_emergency_enabled,
                    max_paid_decisions_per_unlock,
                    created_at,
                    updated_at
                )
                VALUES(?,?,?,?,?,?,?,?)
                """,
                (
                    policy_id,
                    project_id,
                    canonical_json(
                        CANONICAL_ROUTE_LAW
                    ),
                    allow_offline_degraded,
                    paid_emergency_enabled,
                    max_paid_decisions_per_unlock,
                    timestamp,
                    timestamp,
                ),
            )
        else:
            policy_id = str(
                existing["id"]
            )

            connection.execute(
                """
                UPDATE project_policies
                SET route_law_json = ?,
                    allow_offline_degraded = ?,
                    paid_emergency_enabled = ?,
                    max_paid_decisions_per_unlock = ?,
                    updated_at = ?
                WHERE id = ?
                """,
                (
                    canonical_json(
                        CANONICAL_ROUTE_LAW
                    ),
                    allow_offline_degraded,
                    paid_emergency_enabled,
                    max_paid_decisions_per_unlock,
                    timestamp,
                    policy_id,
                ),
            )

    return get_policy(database, project_id)


def get_policy(
    database: Database,
    project_id: str,
) -> dict[str, object]:
    with database.connect() as connection:
        require_project(
            connection,
            project_id,
        )

        row = connection.execute(
            """
            SELECT *
            FROM project_policies
            WHERE project_id = ?
            """,
            (project_id,),
        ).fetchone()

    if row is None:
        return set_policy(database, 
            project_id=project_id,
            allow_offline_degraded=True,
            paid_emergency_enabled=False,
            max_paid_decisions_per_unlock=1,
        )

    return normalize_policy(
        dict(row)
    )
