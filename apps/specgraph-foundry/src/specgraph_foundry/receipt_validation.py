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

from . import receipt_gates_contract as contract_gates
from . import receipt_gates_implementation as implementation_gates
from . import receipt_gates_verification as verification_gates
from .primitives import valid_string_list


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

    # Dispatch on stage. Each stage has its own module because the gate sets
    # answer different questions and share nothing but the reject callback --
    # implementation alone was 287 lines of this function.
    if stage == "CONTRACT":
        contract_gates.check(connection, node, actor_id, evidence, reject)
    elif stage == "IMPLEMENTATION":
        implementation_gates.check(connection, node, actor_id, evidence, reject)
    elif stage == "VERIFICATION":
        verification_gates.check(connection, node, actor_id, evidence, reject)
    else:
            reject(
                "UNKNOWN_EXECUTION_STAGE",
                (
                    "Execution node stage is not "
                    "supported."
                ),
            )

    return findings
