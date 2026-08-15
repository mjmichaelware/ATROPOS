"""Receipt validation: the gates a submitted receipt must pass.

A single 580-line method on `ExecutionService` -- the largest unit of code in
this project, and larger than most whole modules in the package.

It referenced no instance state at all, so it was never a method. Moving it out
says so, and lets a gate be tested without first constructing a service, a
database and a run to reach it.

Returns findings rather than raising: a receipt can fail several gates at once
and an operator needs all of them. Fixing one per round trip is the failure mode
this shape exists to avoid.
"""

from __future__ import annotations

import hashlib
import json
import sqlite3

from .primitives import valid_sha256, valid_string_list


def validate_receipt(
    connection: sqlite3.Connection,
    node: sqlite3.Row,
    actor_id: str,
    outcome: str,
    summary: str,
    evidence: dict[str, object],
) -> list[dict[str, str]]:
    findings: list[
        dict[str, str]
    ] = []
    recorded_codes: set[str] = set()

    def reject(
        gate_code: str,
        message: str,
    ) -> None:
        if gate_code in recorded_codes:
            return

        recorded_codes.add(gate_code)

        findings.append(
            {
                "gate_code": gate_code,
                "severity": "ERROR",
                "message": message,
            }
        )

    if outcome != "SUCCESS":
        reject(
            "RUNTIME_OUTCOME_NOT_SUCCESS",
            (
                "Only successful runtime outcomes "
                "can complete execution nodes."
            ),
        )

    if (
        len(summary) < 20
        or summary.casefold()
        in {
            "ok",
            "done",
            "success",
            "passed",
            "complete",
            "completed",
        }
    ):
        reject(
            "NO_CONSTANT_FAKE_RESULT",
            (
                "Receipt summary must describe "
                "specific completed work."
            ),
        )

    source_atom_ids = evidence.get(
        "source_atom_ids"
    )

    if (
        not valid_string_list(
            source_atom_ids
        )
        or str(node["atom_id"])
        not in source_atom_ids
    ):
        reject(
            "NO_SOURCELESS_REQUIREMENT",
            (
                "Receipt must cite the bound "
                "source atom."
            ),
        )

    stage = str(node["stage"])

    if stage == "CONTRACT":
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

    elif stage == "IMPLEMENTATION":
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

    elif stage == "VERIFICATION":
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

    else:
        reject(
            "UNKNOWN_EXECUTION_STAGE",
            (
                "Execution node stage is not "
                "supported."
            ),
        )

    return findings
