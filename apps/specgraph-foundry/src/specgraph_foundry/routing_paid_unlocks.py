"""Paid unlocks: granting one, and spending it.

The only path by which a paid provider becomes reachable. Isolated so that
"what can authorise spending" is a question with a one-file answer.
"""

from __future__ import annotations

from .routing_guards import require_project
from .routing_policy import get_policy
from datetime import timedelta
import json
import sqlite3

from .database import Database
from .errors import ConflictError, NotFoundError, ValidationError
from .primitives import new_id, parse_time, utc_now, utc_now_datetime
from .sensitive_keys import contains_sensitive_key



def grant_paid_unlock(
    database: Database,
    project_id: str,
    actor_id: str,
    reason: str,
    ttl_seconds: int = 900,
    max_decisions: int | None = None,
    provider_id: str | None = None,
) -> dict[str, object]:
    actor_id = actor_id.strip()
    reason = reason.strip()

    if not actor_id:
        raise ValidationError(
            "actor_id is required"
        )

    if len(reason) < 12:
        raise ValidationError(
            "paid unlock reason must be specific"
        )

    if ttl_seconds < 30:
        raise ValidationError(
            "paid unlock TTL must be at "
            "least 30 seconds"
        )

    policy = get_policy(database, 
        project_id
    )

    if not policy[
        "paid_emergency_enabled"
    ]:
        raise ConflictError(
            "paid emergency routing is disabled "
            "by project policy"
        )

    allowed_decisions = (
        max_decisions
        if max_decisions is not None
        else int(
            policy[
                "max_paid_decisions_per_unlock"
            ]
        )
    )

    if allowed_decisions < 1:
        raise ValidationError(
            "max decisions must be positive"
        )

    expires_at = (
        utc_now_datetime()
        + timedelta(
            seconds=ttl_seconds
        )
    ).isoformat()

    unlock_id = new_id("paid-unlock")
    timestamp = utc_now()

    with database.connect() as connection:
        require_project(
            connection,
            project_id,
        )

        if provider_id is not None:
            provider = connection.execute(
                """
                SELECT *
                FROM provider_configs
                WHERE id = ?
                  AND project_id = ?
                """,
                (
                    provider_id,
                    project_id,
                ),
            ).fetchone()

            if provider is None:
                raise ValidationError(
                    "paid provider does not belong "
                    "to the project"
                )

            if (
                provider["provider_class"]
                != "PAID_EMERGENCY"
            ):
                raise ValidationError(
                    "unlock provider must be a "
                    "PAID_EMERGENCY provider"
                )

        connection.execute(
            """
            INSERT INTO paid_route_unlocks(
                id,
                project_id,
                provider_id,
                actor_id,
                reason,
                max_decisions,
                used_count,
                expires_at,
                created_at
            )
            VALUES(?,?,?,?,?,?,?,?,?)
            """,
            (
                unlock_id,
                project_id,
                provider_id,
                actor_id,
                reason,
                allowed_decisions,
                0,
                expires_at,
                timestamp,
            ),
        )

    return get_paid_unlock(database, 
        unlock_id
    )


def get_paid_unlock(
    database: Database,
    unlock_id: str,
) -> dict[str, object]:
    with database.connect() as connection:
        row = connection.execute(
            """
            SELECT *
            FROM paid_route_unlocks
            WHERE id = ?
            """,
            (unlock_id,),
        ).fetchone()

    if row is None:
        raise NotFoundError(
            f"paid unlock not found: {unlock_id}"
        )

    result = dict(row)
    result["active"] = (
        int(result["used_count"])
        < int(result["max_decisions"])
        and parse_time(
            str(result["expires_at"])
        )
        > utc_now_datetime()
    )

    return result


def consume_paid_unlock(
    database: Database,
    project_id: str,
    provider_id: str,
) -> dict[str, object] | None:
    now = utc_now()

    with database.connect() as connection:
        connection.execute(
            "BEGIN IMMEDIATE"
        )

        row = connection.execute(
            """
            SELECT *
            FROM paid_route_unlocks
            WHERE project_id = ?
              AND expires_at > ?
              AND used_count
                  < max_decisions
              AND (
                  provider_id IS NULL
                  OR provider_id = ?
              )
            ORDER BY
                CASE
                    WHEN provider_id = ?
                    THEN 0
                    ELSE 1
                END,
                created_at,
                id
            LIMIT 1
            """,
            (
                project_id,
                now,
                provider_id,
                provider_id,
            ),
        ).fetchone()

        if row is None:
            return None

        connection.execute(
            """
            UPDATE paid_route_unlocks
            SET used_count = used_count + 1
            WHERE id = ?
            """,
            (row["id"],),
        )

        unlock_id = str(row["id"])

    return get_paid_unlock(database, 
        unlock_id
    )
