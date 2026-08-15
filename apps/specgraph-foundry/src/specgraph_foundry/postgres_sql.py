"""Rewriting sqlite SQL and parameters for Postgres.

Placeholder translation, list splitting, parameter counting and the JSON column
map. This is the layer that changes the *statement*; :mod:`postgres_adapter`
changes the *values* and wraps the connection.

Separated because a bug here corrupts every query in the system while a bug
there corrupts one column, and they are worth reviewing at different levels of
paranoia.
"""

from __future__ import annotations

from datetime import datetime
from typing import Any
import uuid
import json
import re

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
