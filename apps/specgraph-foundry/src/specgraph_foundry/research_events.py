"""The research event trail and its row decoder."""

from __future__ import annotations

from .primitives import new_id, utc_now
import json
import sqlite3

from .database import Database
from .errors import ConflictError, NotFoundError, ValidationError
from .primitives import canonical_json, new_id, parse_time, utc_now, utc_now_datetime



def record_research_event(
    connection: sqlite3.Connection,
    task_id: str,
    event_type: str,
    worker_id: str | None,
    payload: dict[str, object],
) -> None:
    connection.execute(
        """
        INSERT INTO research_task_events(
            id,
            task_id,
            event_type,
            worker_id,
            payload_json,
            created_at
        )
        VALUES(?,?,?,?,?,?)
        """,
        (
            new_id("research-event"),
            task_id,
            event_type,
            worker_id,
            json.dumps(payload, sort_keys=True),
            utc_now(),
        ),
    )


def normalize_research_event(
    event: dict[str, object],
) -> dict[str, object]:
    payload = event.pop("payload_json", "{}")
    event["payload"] = json.loads(str(payload))
    return event
