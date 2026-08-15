"""Configuring providers and recording their health.

Registration, credentials and the health signal that decides whether a provider
is currently usable. Credentials are why this is separate: `contains_sensitive_key`
runs on every configuration, and that check should be edited deliberately.
"""

from __future__ import annotations

from .routing_vocabulary import CLASS_COST_LAW
from .routing_vocabulary import COST_CLASSES
from .routing_vocabulary import PROVIDER_CLASSES
from .routing_vocabulary import PROVIDER_STATUSES
from .routing_vocabulary import normalize_territories
from .routing_guards import normalize_provider
from .routing_guards import require_project
from datetime import timedelta
import json
import sqlite3

from .database import Database
from .errors import ConflictError, NotFoundError, ValidationError
from .primitives import canonical_json, new_id, utc_now, utc_now_datetime
from .sensitive_keys import contains_sensitive_key



def configure_provider(
    database: Database,
    project_id: str,
    name: str,
    provider_class: str,
    cost_class: str,
    territories: list[str],
    priority: int,
    metadata: dict[str, object] | None = None,
    enabled: bool = True,
) -> dict[str, object]:
    name = name.strip()
    provider_class = (
        provider_class.strip().upper()
    )
    cost_class = (
        cost_class.strip().upper()
    )
    metadata = metadata or {}

    if not name:
        raise ValidationError(
            "provider name is required"
        )

    if provider_class not in PROVIDER_CLASSES:
        raise ValidationError(
            f"invalid provider class: "
            f"{provider_class}"
        )

    if cost_class not in COST_CLASSES:
        raise ValidationError(
            f"invalid cost class: "
            f"{cost_class}"
        )

    expected_cost = CLASS_COST_LAW[
        provider_class
    ]

    if cost_class != expected_cost:
        raise ValidationError(
            f"{provider_class} requires "
            f"{expected_cost} cost class"
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
            "provider configuration must not "
            "contain secrets or credentials"
        )

    normalized_territories = (
        normalize_territories(
            territories
        )
    )

    timestamp = utc_now()
    provider_id = new_id("provider")

    with database.connect() as connection:
        require_project(
            connection,
            project_id,
        )

        existing = connection.execute(
            """
            SELECT id
            FROM provider_configs
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
                INSERT INTO provider_configs(
                    id,
                    project_id,
                    name,
                    provider_class,
                    cost_class,
                    territories_json,
                    priority,
                    enabled,
                    status,
                    metadata_json,
                    created_at,
                    updated_at
                )
                VALUES(?,?,?,?,?,?,?,?,?,?,?,?)
                """,
                (
                    provider_id,
                    project_id,
                    name,
                    provider_class,
                    cost_class,
                    canonical_json(
                        normalized_territories
                    ),
                    priority,
                    enabled,
                    "UNKNOWN",
                    canonical_json(metadata),
                    timestamp,
                    timestamp,
                ),
            )
        else:
            provider_id = str(
                existing["id"]
            )

            connection.execute(
                """
                UPDATE provider_configs
                SET provider_class = ?,
                    cost_class = ?,
                    territories_json = ?,
                    priority = ?,
                    enabled = ?,
                    metadata_json = ?,
                    updated_at = ?
                WHERE id = ?
                """,
                (
                    provider_class,
                    cost_class,
                    canonical_json(
                        normalized_territories
                    ),
                    priority,
                    enabled,
                    canonical_json(metadata),
                    timestamp,
                    provider_id,
                ),
            )

    return get_provider(database, 
        provider_id
    )


def list_providers(
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
            FROM provider_configs
            WHERE project_id = ?
            ORDER BY
                priority,
                name,
                id
            """,
            (project_id,),
        ).fetchall()

    return [
        normalize_provider(
            dict(row)
        )
        for row in rows
    ]


def get_provider(
    database: Database,
    provider_id: str,
) -> dict[str, object]:
    with database.connect() as connection:
        row = connection.execute(
            """
            SELECT *
            FROM provider_configs
            WHERE id = ?
            """,
            (provider_id,),
        ).fetchone()

    if row is None:
        raise NotFoundError(
            f"provider not found: {provider_id}"
        )

    return normalize_provider(
        dict(row)
    )


def record_health(
    database: Database,
    provider_id: str,
    status: str,
    latency_ms: float | None = None,
    error_message: str = "",
    cooldown_seconds: int | None = None,
) -> dict[str, object]:
    status = status.strip().upper()

    if status not in PROVIDER_STATUSES:
        raise ValidationError(
            f"invalid provider status: {status}"
        )

    if (
        latency_ms is not None
        and latency_ms < 0
    ):
        raise ValidationError(
            "latency cannot be negative"
        )

    if (
        cooldown_seconds is not None
        and cooldown_seconds < 1
    ):
        raise ValidationError(
            "cooldown must be positive"
        )

    if (
        status == "COOLDOWN"
        and cooldown_seconds is None
    ):
        raise ValidationError(
            "COOLDOWN status requires "
            "cooldown_seconds"
        )

    cooldown_until = None

    if cooldown_seconds is not None:
        cooldown_until = (
            utc_now_datetime()
            + timedelta(
                seconds=cooldown_seconds
            )
        ).isoformat()

    timestamp = utc_now()

    with database.connect() as connection:
        provider = connection.execute(
            """
            SELECT *
            FROM provider_configs
            WHERE id = ?
            """,
            (provider_id,),
        ).fetchone()

        if provider is None:
            raise NotFoundError(
                f"provider not found: "
                f"{provider_id}"
            )

        connection.execute(
            """
            UPDATE provider_configs
            SET status = ?,
                cooldown_until = ?,
                updated_at = ?
            WHERE id = ?
            """,
            (
                status,
                cooldown_until,
                timestamp,
                provider_id,
            ),
        )

        connection.execute(
            """
            INSERT INTO provider_health_events(
                id,
                provider_id,
                status,
                latency_ms,
                error_message,
                cooldown_until,
                created_at
            )
            VALUES(?,?,?,?,?,?,?)
            """,
            (
                new_id("provider-health"),
                provider_id,
                status,
                latency_ms,
                (
                    error_message.strip()
                    or None
                ),
                cooldown_until,
                timestamp,
            ),
        )

    return get_provider(database, 
        provider_id
    )
