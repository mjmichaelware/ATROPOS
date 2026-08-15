"""Execution events, and turning stored rows back into caller shapes.

The append-only trail every state change writes, plus the two functions that
decode a stored row. All three were already `@staticmethod` -- the class stating
that they have no opinion about when they run, which is what makes them the one
group here with no ordering concern at all.
"""

from __future__ import annotations

import json
import sqlite3

from .primitives import canonical_json, new_id, utc_now


def record_event(
    connection: sqlite3.Connection,
    run_id: str,
    run_node_id: str | None,
    event_type: str,
    actor_id: str | None,
    payload: dict[str, object],
) -> None:
    connection.execute(
        """
        INSERT INTO execution_events(
            id,
            run_id,
            run_node_id,
            event_type,
            actor_id,
            payload_json,
            created_at
        )
        VALUES(?,?,?,?,?,?,?)
        """,
        (
            new_id("execution-event"),
            run_id,
            run_node_id,
            event_type,
            actor_id,
            canonical_json(payload),
            utc_now(),
        ),
    )


def normalize_receipt(
    record: dict[str, object],
) -> dict[str, object]:
    record["evidence"] = json.loads(
        str(
            record.pop(
                "evidence_json"
            )
        )
    )

    return record


def normalize_event(
    record: dict[str, object],
) -> dict[str, object]:
    record["payload"] = json.loads(
        str(
            record.pop(
                "payload_json"
            )
        )
    )

    return record
