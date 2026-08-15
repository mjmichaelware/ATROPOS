"""Existence checks and row normalisation shared across routing."""

from __future__ import annotations

from datetime import datetime
import json
import sqlite3

from .database import Database
from .errors import ConflictError, NotFoundError, ValidationError
from .primitives import parse_time
from .sensitive_keys import contains_sensitive_key



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


def normalize_policy(
    record: dict[str, object],
) -> dict[str, object]:
    record["route_law"] = json.loads(
        str(
            record.pop(
                "route_law_json"
            )
        )
    )
    record[
        "allow_offline_degraded"
    ] = bool(
        record[
            "allow_offline_degraded"
        ]
    )
    record[
        "paid_emergency_enabled"
    ] = bool(
        record[
            "paid_emergency_enabled"
        ]
    )

    return record


def normalize_provider(
    record: dict[str, object],
) -> dict[str, object]:
    record["territories"] = json.loads(
        str(
            record.pop(
                "territories_json"
            )
        )
    )
    record["metadata"] = json.loads(
        str(
            record.pop(
                "metadata_json"
            )
        )
    )
    record["enabled"] = bool(
        record["enabled"]
    )

    return record


def normalize_renderer(
    record: dict[str, object],
) -> dict[str, object]:
    record["territories"] = json.loads(
        str(
            record.pop(
                "territories_json"
            )
        )
    )
    record["metadata"] = json.loads(
        str(
            record.pop(
                "metadata_json"
            )
        )
    )
    record["enabled"] = bool(
        record["enabled"]
    )

    return record


def is_cooling(
    provider: dict[str, object],
    now: datetime,
) -> bool:
    if provider["status"] == "COOLDOWN":
        return True

    cooldown_until = provider[
        "cooldown_until"
    ]

    return (
        cooldown_until is not None
        and parse_time(
            str(cooldown_until)
        )
        > now
    )
