"""Making a Postgres connection behave like the sqlite one.

Placeholder translation, JSON column handling, row and scalar normalisation, and
the cursor and connection wrappers.

All of it exists so the rest of the package can be written against one dialect.
Keeping it in `database` meant every reader of `Database.connect` scrolled 400
lines of dialect adaptation to reach it, and made it easy to miss that these
rules are the only place a value changes shape between backends.
"""

from __future__ import annotations

from .postgres_sql import (
    ISO_DATETIME_PATTERN,
    UUID_PATTERN,
    JSON_COLUMNS,
    adapt_postgres_parameters,
    adapt_postgres_scalar,
    parameter_count,
    postgres_json_parameter_indexes,
    split_sql_list,
    translate_qmark_sql,
)

from datetime import date
from decimal import Decimal
from types import TracebackType
from typing import Any
import sqlite3
import json
import re
import uuid
from datetime import datetime









class PostgresRow(dict[str, object]):
    def __getitem__(
        self,
        key: str | int,
    ) -> object:
        if isinstance(key, int):
            return tuple(self.values())[key]

        return super().__getitem__(key)


def normalize_postgres_value(
    value: object,
) -> object:
    if isinstance(value, uuid.UUID):
        return str(value)

    if isinstance(value, datetime):
        return value.isoformat()

    if isinstance(value, date):
        return value.isoformat()

    if isinstance(value, Decimal):
        if value == value.to_integral_value():
            return int(value)

        return float(value)

    if isinstance(value, (dict, list)):
        return json.dumps(
            value,
            sort_keys=True,
            separators=(",", ":"),
            ensure_ascii=False,
        )

    if isinstance(value, memoryview):
        return bytes(value)

    return value


def normalize_postgres_row(
    row: object,
) -> PostgresRow | None:
    if row is None:
        return None

    if not isinstance(row, dict):
        raise TypeError(
            "PostgreSQL row must be a mapping"
        )

    return PostgresRow(
        {
            str(key): normalize_postgres_value(
                value
            )
            for key, value in row.items()
        }
    )












class PostgresCursor:
    def __init__(
        self,
        cursor: Any,
    ) -> None:
        self._cursor = cursor

    @property
    def rowcount(self) -> int:
        return int(self._cursor.rowcount)

    def fetchone(
        self,
    ) -> PostgresRow | None:
        return normalize_postgres_row(
            self._cursor.fetchone()
        )

    def fetchall(
        self,
    ) -> list[PostgresRow]:
        return [
            row
            for raw_row
            in self._cursor.fetchall()
            if (
                row := normalize_postgres_row(
                    raw_row
                )
            )
            is not None
        ]

    def __iter__(self):
        for raw_row in self._cursor:
            row = normalize_postgres_row(
                raw_row
            )

            if row is not None:
                yield row

    def __getattr__(
        self,
        name: str,
    ) -> Any:
        return getattr(
            self._cursor,
            name,
        )


class PostgresConnection:
    def __init__(
        self,
        database_url: str,
    ) -> None:
        try:
            import psycopg
            from psycopg.rows import dict_row
            from psycopg.types.json import Jsonb
        except ImportError as error:
            raise RuntimeError(
                "PostgreSQL mode requires Psycopg 3. "
                "Install the optional postgres dependency."
            ) from error

        self._psycopg = psycopg
        self._json_wrapper = Jsonb
        self._connection = psycopg.connect(
            database_url,
            row_factory=dict_row,
            prepare_threshold=None,
        )

    def __enter__(
        self,
    ) -> "PostgresConnection":
        return self

    def __exit__(
        self,
        exception_type: type[BaseException] | None,
        exception: BaseException | None,
        traceback: TracebackType | None,
    ) -> bool:
        try:
            if exception_type is None:
                self._connection.commit()
            else:
                self._connection.rollback()
        finally:
            self._connection.close()

        return False

    def execute(
        self,
        sql: str,
        parameters: tuple[object, ...] = (),
    ) -> PostgresCursor:
        normalized = sql.strip()

        if (
            normalized.upper()
            == "BEGIN IMMEDIATE"
        ):
            translated = "BEGIN"
        else:
            translated = translate_qmark_sql(
                sql
            )

        adapted = adapt_postgres_parameters(
            sql,
            tuple(parameters),
            self._json_wrapper,
        )

        try:
            cursor = self._connection.execute(
                translated,
                adapted,
            )
        except self._psycopg.IntegrityError as error:
            raise sqlite3.IntegrityError(
                str(error)
            ) from error

        return PostgresCursor(cursor)

    def executescript(
        self,
        sql: str,
    ) -> None:
        # Hosted schema is managed exclusively by
        # Supabase migrations. SQLite bootstrap DDL
        # must never mutate the hosted schema.
        return None

    def close(self) -> None:
        self._connection.close()
