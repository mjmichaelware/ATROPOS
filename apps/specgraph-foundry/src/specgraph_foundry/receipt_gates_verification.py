"""Gates for a VERIFICATION-stage receipt.

Verification is the stage that can most cheaply be faked -- a test that asserts
nothing passes as readily as one that asserts everything -- so these gates are
about whether the checking was real: meaningless tests, and an actor verifying
work it performed itself.
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
    tests = evidence.get("tests")

    if (
        not isinstance(tests, list)
        or not tests
    ):
        reject(
            "NO_MEANINGLESS_TEST",
            (
                "Verification receipt requires "
                "test evidence."
            ),
        )
        tests = []

    for test in tests:
        if (
            not isinstance(test, dict)
            or not isinstance(
                test.get("name"),
                str,
            )
            or not test[
                "name"
            ].strip()
            or test.get("status")
            != "PASSED"
            or type(
                test.get(
                    "assertions"
                )
            )
            is not int
            or test[
                "assertions"
            ]
            <= 0
        ):
            reject(
                "NO_MEANINGLESS_TEST",
                (
                    "Every verification test "
                    "must pass and contain at "
                    "least one assertion."
                ),
            )

    commands = evidence.get(
        "commands"
    )

    if (
        not isinstance(commands, list)
        or not commands
        or any(
            not isinstance(
                command,
                dict,
            )
            or type(
                command.get(
                    "exit_code"
                )
            )
            is not int
            or command[
                "exit_code"
            ]
            != 0
            for command in commands
        )
    ):
        reject(
            "NO_MEANINGLESS_TEST",
            (
                "Verification requires "
                "successful test commands."
            ),
        )

    implementation_receipt = (
        connection.execute(
            """
            SELECT receipt.*
            FROM execution_run_nodes
            AS implementation
            JOIN execution_receipts
            AS receipt
              ON receipt.id =
                 implementation.
                 accepted_receipt_id
            WHERE implementation.run_id = ?
              AND implementation.atom_id = ?
              AND implementation.stage =
                  'IMPLEMENTATION'
              AND implementation.status =
                  'COMPLETE'
            """,
            (
                node["run_id"],
                node["atom_id"],
            ),
        ).fetchone()
    )

    if implementation_receipt is None:
        reject(
            (
                "NO_UNVERIFIED_"
                "IMPLEMENTATION_RECEIPT"
            ),
            (
                "Verification requires an "
                "accepted implementation "
                "receipt."
            ),
        )
    else:
        verified_receipt_ids = (
            evidence.get(
                "verified_receipt_ids"
            )
        )

        if (
            not valid_string_list(
                verified_receipt_ids
            )
            or str(
                implementation_receipt[
                    "id"
                ]
            )
            not in verified_receipt_ids
        ):
            reject(
                (
                    "NO_UNVERIFIED_"
                    "IMPLEMENTATION_RECEIPT"
                ),
                (
                    "Verification must identify "
                    "the implementation receipt "
                    "it evaluated."
                ),
            )

        if (
            actor_id
            == implementation_receipt[
                "actor_id"
            ]
        ):
            reject(
                "NO_SELF_VERIFICATION",
                (
                    "Implementation and "
                    "verification must be "
                    "performed by different "
                    "actors."
                ),
            )

    if (
        evidence.get(
            "independent_verification"
        )
        is not True
    ):
        reject(
            "NO_SELF_VERIFICATION",
            (
                "Verification must explicitly "
                "declare independent review."
            ),
        )
