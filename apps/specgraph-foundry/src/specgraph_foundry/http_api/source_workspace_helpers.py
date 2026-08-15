"""Small query helpers shared by both source-workspace views.

Row counting, latest-of, scalar extraction, preview rows and content truncation
-- used by the project view and the document view and belonging to neither.
"""

from __future__ import annotations

import json

from ..errors import NotFoundError
from .pagination import WORKSPACE_PREVIEW_LIMIT

CONTENT_PREVIEW_CHARS = 4096

def count(
    connection: Any,
    sql: str,
    parameters: tuple[object, ...],
) -> int:
    row = connection.execute(
        sql,
        parameters,
    ).fetchone()

    return (
        int(row["value"])
        if row is not None
        else 0
    )


def scalar(
    connection: Any,
    sql: str,
    parameters: tuple[object, ...],
) -> object:
    row = connection.execute(
        sql,
        parameters,
    ).fetchone()
    return (
        row["value"]
        if row is not None
        else 0
    )


def latest(
    connection: Any,
    sql: str,
    parameters: tuple[object, ...],
) -> dict[str, object] | None:
    row = connection.execute(
        sql,
        parameters,
    ).fetchone()
    return (
        dict(row)
        if row is not None
        else None
    )


def preview_rows(
    connection: Any,
    sql: str,
    parameters: tuple[object, ...],
) -> list[dict[str, object]]:
    rows = connection.execute(
        f"{sql}\nLIMIT ?",
        (*parameters, WORKSPACE_PREVIEW_LIMIT),
    ).fetchall()
    return [
        dict(row)
        for row in rows
    ]


def truncate_content(
    content: str,
) -> str:
    if len(content) <= CONTENT_PREVIEW_CHARS:
        return content

    return content[:CONTENT_PREVIEW_CHARS]
