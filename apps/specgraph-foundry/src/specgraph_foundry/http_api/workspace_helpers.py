"""Row counting and latest-of, shared by every workspace view."""

from __future__ import annotations

from typing import Any

def count(
    connection: Any,
    sql: str,
    parameters: tuple[object, ...],
) -> int:
    row = connection.execute(
        sql,
        parameters,
    ).fetchone()

    if row is None:
        return 0

    return int(row["value"])


def latest_row(
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
