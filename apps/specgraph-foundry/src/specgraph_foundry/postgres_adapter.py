"""Making a Postgres connection behave like the sqlite one.

Placeholder translation, JSON column handling, row and scalar normalisation, and
the cursor and connection wrappers.

All of it exists so the rest of the package can be written against one dialect.
Keeping it in `database` meant every reader of `Database.connect` scrolled 400
lines of dialect adaptation to reach it, and made it easy to miss that these
rules are the only place a value changes shape between backends.
"""

from __future__ import annotations

from datetime import date
from decimal import Decimal
from types import TracebackType
from typing import Any
import sqlite3
import json
import re
import uuid
from datetime import datetime

def translate_qmark_sql(sql: str) -> str:
    result: list[str] = []
    single_quote = False
    double_quote = False
    index = 0

    while index < len(sql):
        character = sql[index]

        if character == "'" and not double_quote:
            if (
                single_quote
                and index + 1 < len(sql)
                and sql[index + 1] == "'"
            ):
                result.extend(["'", "'"])
                index += 2
                continue

            single_quote = not single_quote
            result.append(character)
            index += 1
            continue

        if character == '"' and not single_quote:
            double_quote = not double_quote
            result.append(character)
            index += 1
            continue

        if (
            character == "?"
            and not single_quote
            and not double_quote
        ):
            result.append("%s")
        else:
            result.append(character)

        index += 1

    return "".join(result)


JSON_COLUMNS = {
    "payload_json",
    "result_json",
    "config_json",
    "evidence_json",
    "response_body_json",
    "route_law_json",
    "territories_json",
    "metadata_json",
    "input_json",
    "considered_json",
}


UUID_PATTERN = re.compile(
    r"^[0-9a-fA-F]{8}-"
    r"[0-9a-fA-F]{4}-"
    r"[1-5][0-9a-fA-F]{3}-"
    r"[89abAB][0-9a-fA-F]{3}-"
    r"[0-9a-fA-F]{12}$"
)


ISO_DATETIME_PATTERN = re.compile(
    r"^\d{4}-\d{2}-\d{2}T"
    r"\d{2}:\d{2}:\d{2}"
    r"(?:\.\d+)?"
    r"(?:Z|[+-]\d{2}:\d{2})$"
)


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


def split_sql_list(
    value: str,
) -> list[str]:
    items: list[str] = []
    current: list[str] = []
    depth = 0
    single_quote = False
    double_quote = False
    index = 0

    while index < len(value):
        character = value[index]

        if character == "'" and not double_quote:
            if (
                single_quote
                and index + 1 < len(value)
                and value[index + 1] == "'"
            ):
                current.extend(["'", "'"])
                index += 2
                continue

            single_quote = not single_quote
            current.append(character)
            index += 1
            continue

        if character == '"' and not single_quote:
            double_quote = not double_quote
            current.append(character)
            index += 1
            continue

        if not single_quote and not double_quote:
            if character == "(":
                depth += 1
            elif character == ")":
                depth -= 1
            elif character == "," and depth == 0:
                items.append(
                    "".join(current).strip()
                )
                current = []
                index += 1
                continue

        current.append(character)
        index += 1

    if current:
        items.append(
            "".join(current).strip()
        )

    return items


def parameter_count(
    sql: str,
) -> int:
    single_quote = False
    double_quote = False
    count = 0
    index = 0

    while index < len(sql):
        character = sql[index]

        if character == "'" and not double_quote:
            if (
                single_quote
                and index + 1 < len(sql)
                and sql[index + 1] == "'"
            ):
                index += 2
                continue

            single_quote = not single_quote
            index += 1
            continue

        if character == '"' and not single_quote:
            double_quote = not double_quote
            index += 1
            continue

        if (
            character == "?"
            and not single_quote
            and not double_quote
        ):
            count += 1

        index += 1

    return count


def postgres_json_parameter_indexes(
    sql: str,
) -> set[int]:
    indexes: set[int] = set()

    insert = re.search(
        r"""
        INSERT\s+INTO\s+
        (?:[a-zA-Z_][a-zA-Z0-9_]*\.)?
        [a-zA-Z_][a-zA-Z0-9_]*
        \s*\((.*?)\)
        \s*VALUES\s*\((.*?)\)
        """,
        sql,
        flags=(
            re.IGNORECASE
            | re.DOTALL
            | re.VERBOSE
        ),
    )

    if insert is not None:
        columns = split_sql_list(
            insert.group(1)
        )
        values = split_sql_list(
            insert.group(2)
        )
        parameter_index = 0

        for column, expression in zip(
            columns,
            values,
            strict=False,
        ):
            column_name = (
                column.strip()
                .split(".")[-1]
                .strip('"')
                .lower()
            )

            expression_count = (
                parameter_count(expression)
            )

            if (
                column_name in JSON_COLUMNS
                and expression.strip() == "?"
            ):
                indexes.add(parameter_index)

            parameter_index += (
                expression_count
            )

    update = re.search(
        r"""
        UPDATE\s+
        (?:[a-zA-Z_][a-zA-Z0-9_]*\.)?
        [a-zA-Z_][a-zA-Z0-9_]*
        \s+SET\s+
        (.*?)
        (?=\s+WHERE\s+|\Z)
        """,
        sql,
        flags=(
            re.IGNORECASE
            | re.DOTALL
            | re.VERBOSE
        ),
    )

    if update is not None:
        prefix = sql[: update.start(1)]
        parameter_index = (
            parameter_count(prefix)
        )

        for assignment in split_sql_list(
            update.group(1)
        ):
            left, separator, right = (
                assignment.partition("=")
            )

            expression_count = (
                parameter_count(right)
            )

            if separator:
                column_name = (
                    left.strip()
                    .split(".")[-1]
                    .strip('"')
                    .lower()
                )

                if (
                    column_name
                    in JSON_COLUMNS
                    and right.strip() == "?"
                ):
                    indexes.add(
                        parameter_index
                    )

            parameter_index += (
                expression_count
            )

    return indexes


def adapt_postgres_scalar(
    value: object,
) -> object:
    if not isinstance(value, str):
        return value

    if UUID_PATTERN.fullmatch(value):
        return uuid.UUID(value)

    if ISO_DATETIME_PATTERN.fullmatch(
        value
    ):
        return datetime.fromisoformat(
            value.replace(
                "Z",
                "+00:00",
            )
        )

    return value


def adapt_postgres_parameters(
    sql: str,
    parameters: tuple[object, ...],
    json_wrapper: Any,
) -> tuple[object, ...]:
    json_indexes = (
        postgres_json_parameter_indexes(
            sql
        )
    )

    adapted: list[object] = []

    for index, value in enumerate(
        parameters
    ):
        if index in json_indexes:
            if isinstance(value, str):
                try:
                    parsed = json.loads(value)
                except json.JSONDecodeError as error:
                    raise ValueError(
                        "JSON database parameter "
                        "is not valid JSON"
                    ) from error
            elif isinstance(
                value,
                (dict, list),
            ):
                parsed = value
            else:
                parsed = value

            adapted.append(
                json_wrapper(parsed)
            )
            continue

        adapted.append(
            adapt_postgres_scalar(value)
        )

    return tuple(adapted)


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
