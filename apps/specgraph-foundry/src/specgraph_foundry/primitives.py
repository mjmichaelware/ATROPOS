"""Shared primitives: time, identifiers, canonical encoding, validation.

Lifted verbatim out of :mod:`execution`, which is also where seven other modules
had their own copies. `utc_now` and `new_id` were defined identically in eight
files and `canonical_json` in three -- and the copies had already drifted:
`research.utc_now()` returns a `datetime` where every other one returns an ISO
`str`. Same name, same import position, different type.

One owner each. `utc_now` is the stored string form and `utc_now_datetime` the
aware object; keeping the names distinct is what stops the return type depending
on which module you happen to be reading.
"""

from __future__ import annotations

import json
import re
import uuid
from datetime import UTC, datetime


SHA256_PATTERN = re.compile(
    r"^[0-9a-f]{64}$"
)


def utc_now_datetime() -> datetime:
    return datetime.now(UTC)


def utc_now() -> str:
    return utc_now_datetime().isoformat()


def new_id(prefix: str) -> str:
    return str(uuid.uuid4())


def parse_time(value: str) -> datetime:
    parsed = datetime.fromisoformat(value)

    if parsed.tzinfo is None:
        parsed = parsed.replace(tzinfo=UTC)

    return parsed.astimezone(UTC)


def canonical_json(
    value: object,
) -> str:
    return json.dumps(
        value,
        sort_keys=True,
        separators=(",", ":"),
        ensure_ascii=False,
    )


def valid_sha256(value: object) -> bool:
    return (
        isinstance(value, str)
        and SHA256_PATTERN.fullmatch(value)
        is not None
    )


def valid_string_list(
    value: object,
    minimum_length: int = 1,
) -> bool:
    return (
        isinstance(value, list)
        and len(value) >= minimum_length
        and all(
            isinstance(item, str)
            and bool(item.strip())
            for item in value
        )
    )


def iso_now() -> str:
    """The current instant as ISO text.

    An alias kept because research spells it this way; both it and
    `utc_now` return the stored string form.
    """
    return utc_now()
