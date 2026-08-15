"""Reading operations back, and the public shape of one."""

from __future__ import annotations

from .operation_models import *  # noqa: F401,F403
from .pagination import CursorCodec
from .pagination import CursorScope
from .pagination import pagination_headers
from .pagination import parse_pagination_query
import json


def list_project(
    store,
    *,
    owner_id: str,
    project_id: str,
    raw_path: str,
) -> tuple[list[dict[str, object]], dict[str, str]]:
    request = parse_pagination_query(raw_path)
    scope = CursorScope(
        collection="operations",
        owner_id=owner_id,
        parent_id=project_id,
    )
    boundary = None
    if request.cursor is not None:
        boundary = CursorCodec(store.cursor_signing_key).decode(
            request.cursor,
            scope=scope,
        )

    parameters: list[object] = [owner_id, project_id]
    clause = ""
    if boundary is not None:
        clause = """
          AND (
                created_at < ?
                OR (
                    created_at = ?
                    AND id < ?
                )
              )
        """
        parameters.extend(
            [
                str(boundary["created_at"]),
                str(boundary["created_at"]),
                str(boundary["id"]),
            ]
        )
    parameters.append(request.limit + 1)
    with store.database.connect() as connection:
        rows = connection.execute(
            f"""
            SELECT *
            FROM operations
            WHERE owner_id = ?
              AND project_id = ?
            {clause}
            ORDER BY created_at DESC, id DESC
            LIMIT ?
            """,
            tuple(parameters),
        ).fetchall()

    has_more = len(rows) > request.limit
    selected = [dict(row) for row in rows[: request.limit]]
    next_cursor = None
    if has_more and selected:
        last = selected[-1]
        next_cursor = CursorCodec(store.cursor_signing_key).encode(
            scope,
            {
                "created_at": last["created_at"],
                "id": last["id"],
            },
        )
    return (
        [public(row) for row in selected],
        pagination_headers(
            limit=request.limit,
            count=len(selected),
            has_more=has_more,
            next_cursor=next_cursor,
        ),
    )


def public(row: dict[str, object]) -> dict[str, object]:
    result = {
        "id": str(row["id"]),
        "project_id": str(row["project_id"]),
        "operation_type": str(row["operation_type"]),
        "state": str(row["state"]),
        "phase": str(row["phase"]),
        "progress_current": int(row["progress_current"]),
        "progress_total": int(row["progress_total"]),
        "attempt_count": int(row["attempt_count"]),
        "max_attempts": int(row["max_attempts"]),
        "created_at": str(row["created_at"]),
        "updated_at": str(row["updated_at"]),
        "timeout_at": str(row["timeout_at"]),
        "started_at": row["started_at"],
        "finished_at": row["finished_at"],
        "cancel_requested_at": row["cancel_requested_at"],
    }
    if row["result_json"] is not None:
        result["result"] = json.loads(str(row["result_json"]))
    if row["error_code"] is not None:
        result["error_code"] = str(row["error_code"])
        result["error_message"] = str(row["error_message"] or "")
    return result
