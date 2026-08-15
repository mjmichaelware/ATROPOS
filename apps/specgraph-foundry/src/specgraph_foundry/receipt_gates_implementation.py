"""Gates for an IMPLEMENTATION-stage receipt.

The largest gate set, because implementation is where a receipt can most easily
claim more than it did: files touched that share no responsibility, public
components wired to nothing, features with no reachable entry point, failure
paths with no evidence, work done against unresearched requirements.

Each gate answers a different way of appearing finished without being finished.
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
    changed_files = evidence.get(
        "changed_files"
    )

    if (
        not isinstance(
            changed_files,
            list,
        )
        or not changed_files
    ):
        reject(
            "NO_EMPTY_IMPLEMENTATION",
            (
                "Implementation receipt must "
                "identify changed files."
            ),
        )
        changed_files = []

    paths: list[str] = []

    for item in changed_files:
        if not isinstance(item, dict):
            reject(
                "NO_MIXED_FILE_RESPONSIBILITY",
                (
                    "Every changed-file record "
                    "must be an object."
                ),
            )
            continue

        path = item.get("path")
        responsibility = item.get(
            "responsibility"
        )
        digest = item.get(
            "sha256"
        )

        if (
            not isinstance(path, str)
            or not path.strip()
            or path.startswith("/")
            or ".."
            in path.split("/")
        ):
            reject(
                "NO_MIXED_FILE_RESPONSIBILITY",
                (
                    "Changed files require safe "
                    "relative paths."
                ),
            )
        else:
            paths.append(path)

        if (
            not isinstance(
                responsibility,
                str,
            )
            or len(
                responsibility.strip()
            )
            < 8
        ):
            reject(
                "NO_MIXED_FILE_RESPONSIBILITY",
                (
                    "Each changed file requires "
                    "one explicit responsibility."
                ),
            )

        if not valid_sha256(digest):
            reject(
                "NO_EMPTY_IMPLEMENTATION",
                (
                    "Each changed file requires "
                    "a SHA-256 digest."
                ),
            )

    if len(paths) != len(set(paths)):
        reject(
            "NO_MIXED_FILE_RESPONSIBILITY",
            (
                "Changed-file paths must not "
                "be duplicated."
            ),
        )

    commands = evidence.get(
        "commands"
    )

    if (
        not isinstance(commands, list)
        or not commands
    ):
        reject(
            "NO_EMPTY_IMPLEMENTATION",
            (
                "Implementation receipt requires "
                "executed command evidence."
            ),
        )
        commands = []

    for command in commands:
        if (
            not isinstance(command, dict)
            or not isinstance(
                command.get("command"),
                str,
            )
            or not command[
                "command"
            ].strip()
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
        ):
            reject(
                "NO_EMPTY_IMPLEMENTATION",
                (
                    "Implementation commands "
                    "must be concrete and "
                    "successful."
                ),
            )

    if not valid_sha256(
        evidence.get(
            "diff_sha256"
        )
    ):
        reject(
            "NO_EMPTY_IMPLEMENTATION",
            (
                "Implementation receipt requires "
                "a diff SHA-256 digest."
            ),
        )

    if not valid_string_list(
        evidence.get(
            "call_sites"
        )
    ):
        reject(
            "NO_DISCONNECTED_PUBLIC_COMPONENT",
            (
                "Implementation must identify "
                "its public call sites."
            ),
        )

    if not valid_string_list(
        evidence.get(
            "reachability"
        )
    ):
        reject(
            "NO_UNREACHABLE_FEATURE",
            (
                "Implementation must provide "
                "reachability evidence."
            ),
        )

    rollback = evidence.get(
        "rollback"
    )

    if (
        not isinstance(rollback, dict)
        or not isinstance(
            rollback.get(
                "strategy"
            ),
            str,
        )
        or len(
            rollback[
                "strategy"
            ].strip()
        )
        < 8
        or not isinstance(
            rollback.get(
                "recovery_command"
            ),
            str,
        )
        or not rollback[
            "recovery_command"
        ].strip()
    ):
        reject(
            "NO_FAILURE_EVIDENCE",
            (
                "Implementation requires a "
                "rollback strategy and recovery "
                "command."
            ),
        )

    open_dimensions = (
        connection.execute(
            """
            SELECT dimension
            FROM atom_dimensions
            WHERE atom_id = ?
              AND status = 'OPEN'
            ORDER BY dimension
            """,
            (node["atom_id"],),
        ).fetchall()
    )

    if open_dimensions:
        reject(
            "NO_UNRESEARCHED_IMPLEMENTATION",
            (
                "Implementation cannot complete "
                "while research dimensions "
                "remain open."
            ),
        )

    unjustified_na = (
        connection.execute(
            """
            SELECT dimension.dimension
            FROM atom_dimensions
            AS dimension
            LEFT JOIN research_claims
            AS claim
              ON claim.atom_id =
                 dimension.atom_id
             AND claim.dimension =
                 dimension.dimension
             AND claim.applicability =
                 'NOT_APPLICABLE'
            WHERE dimension.atom_id = ?
              AND dimension.status =
                  'NOT_APPLICABLE'
              AND (
                  claim.id IS NULL
                  OR NOT EXISTS (
                      SELECT 1
                      FROM
                          research_claim_evidence
                          AS link
                      WHERE link.claim_id =
                            claim.id
                  )
              )
            ORDER BY
                dimension.dimension
            """,
            (node["atom_id"],),
        ).fetchall()
    )

    if unjustified_na:
        reject(
            (
                "NO_UNJUSTIFIED_NOT_"
                "APPLICABLE_DIMENSION"
            ),
            (
                "Every not-applicable dimension "
                "requires an evidence-backed "
                "claim."
            ),
        )
