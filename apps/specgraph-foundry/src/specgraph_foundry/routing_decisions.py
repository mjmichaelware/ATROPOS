"""Recording and reading back why a route was chosen."""

from __future__ import annotations

from .routing_providers import get_provider
import json
import sqlite3

from .database import Database
from .errors import ConflictError, NotFoundError, ValidationError
from .primitives import canonical_json, new_id, utc_now
from .sensitive_keys import contains_sensitive_key



def record_decision(
    database: Database,
    project_id: str,
    territory: str,
    decision_type: str,
    selected_provider: (
        dict[str, object] | None
    ),
    paid_unlock_id: str | None,
    retry_at: str | None,
    rationale: str,
    offline_capable: bool,
    considered: list[
        dict[str, object]
    ],
) -> dict[str, object]:
    decision_id = new_id(
        "route-decision"
    )

    with database.connect() as connection:
        connection.execute(
            """
            INSERT INTO route_decisions(
                id,
                project_id,
                territory,
                decision_type,
                selected_provider_id,
                paid_unlock_id,
                retry_at,
                rationale,
                input_json,
                considered_json,
                created_at
            )
            VALUES(?,?,?,?,?,?,?,?,?,?,?)
            """,
            (
                decision_id,
                project_id,
                territory,
                decision_type,
                (
                    selected_provider["id"]
                    if selected_provider
                    else None
                ),
                paid_unlock_id,
                retry_at,
                rationale,
                canonical_json(
                    {
                        "territory": (
                            territory
                        ),
                        "offline_capable": (
                            offline_capable
                        ),
                    }
                ),
                canonical_json(
                    considered
                ),
                utc_now(),
            ),
        )

    return get_decision(database, 
        decision_id
    )


def get_decision(
    database: Database,
    decision_id: str,
) -> dict[str, object]:
    with database.connect() as connection:
        row = connection.execute(
            """
            SELECT *
            FROM route_decisions
            WHERE id = ?
            """,
            (decision_id,),
        ).fetchone()

    if row is None:
        raise NotFoundError(
            f"route decision not found: "
            f"{decision_id}"
        )

    result = dict(row)
    result["input"] = json.loads(
        str(
            result.pop(
                "input_json"
            )
        )
    )
    result["considered"] = json.loads(
        str(
            result.pop(
                "considered_json"
            )
        )
    )

    if (
        result[
            "selected_provider_id"
        ]
        is not None
    ):
        result["selected_provider"] = (
            get_provider(database, 
                str(
                    result[
                        "selected_provider_id"
                    ]
                )
            )
        )
    else:
        result[
            "selected_provider"
        ] = None

    return result
