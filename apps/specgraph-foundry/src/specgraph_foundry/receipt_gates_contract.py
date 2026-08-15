"""Gates for a CONTRACT-stage receipt.

A contract node states what must be true. The only thing that can be wrong with
its receipt is that it states nothing -- so this is the shortest gate set, and
deliberately so: inventing further checks here would be inventing requirements
the plan never made.
"""

from __future__ import annotations

import hashlib
import json
import sqlite3
from collections.abc import Callable

from .primitives import valid_sha256, valid_string_list

Reject = Callable[[str, str], None]


def check(
    connection: sqlite3.Connection,
    node: sqlite3.Row,
    actor_id: str,
    evidence: dict[str, object],
    reject: Reject,
) -> None:
    """Applies this stage's gates, reporting each through `reject`.

    Takes the callback rather than returning findings so that de-duplication by
    gate code stays global to the receipt: a code raised in two places still
    reports once, which is what the single closure did.
    """
    criteria = evidence.get(
        "acceptance_criteria"
    )

    if (
        not valid_string_list(
            criteria
        )
        or any(
            len(item.strip()) < 8
            for item in criteria
        )
    ):
        reject(
            "NO_EMPTY_CONTRACT",
            (
                "Contract receipt requires "
                "specific acceptance criteria."
            ),
        )
