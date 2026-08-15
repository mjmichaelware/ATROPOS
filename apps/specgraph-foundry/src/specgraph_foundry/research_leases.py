"""Whether a worker still holds a research task.

The lease check every write path consults before accepting anything.
"""

from __future__ import annotations

import json
import sqlite3

from .database import Database
from .errors import ConflictError, NotFoundError, ValidationError
from .primitives import parse_time, utc_now_datetime



def require_lease(
    connection: sqlite3.Connection,
    task_id: str,
    worker_id: str,
) -> sqlite3.Row:
    task = connection.execute(
        """
        SELECT *
        FROM research_tasks
        WHERE id = ?
        """,
        (task_id,),
    ).fetchone()

    if task is None:
        raise NotFoundError(
            f"research task not found: {task_id}"
        )

    if task["status"] != "CLAIMED":
        raise ConflictError(
            "research task is not claimed"
        )

    if task["lease_owner"] != worker_id:
        raise ConflictError(
            "research task belongs to another worker"
        )

    expiration = task["lease_expires_at"]

    if (
        expiration is None
        or parse_time(str(expiration)) <= utc_now_datetime()
    ):
        raise ConflictError(
            "research task lease has expired"
        )

    return task
