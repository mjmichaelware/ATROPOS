"""Run verification: whether a completed run actually passed.

`verify_run` was a 415-line method. It is the gate between "every node reported
success" and "the run is verified" -- different claims, and the entire point of
the function is that the second does not follow from the first.

Its own module because it is the only place in the execution path that reads the
*whole* run at once; everything else operates on a single node or a single
claim.
"""

from __future__ import annotations

import hashlib
import json
import sqlite3

from .database import Database
from .errors import ConflictError, NotFoundError, ValidationError
from .execution_events import record_event
from .primitives import canonical_json, new_id, utc_now, valid_sha256


def verify_run(
    database: Database,
    run_id: str,
) -> dict[str, object]:
    timestamp = utc_now()
    findings: list[
        dict[str, object]
    ] = []

    with database.connect() as connection:
        run = connection.execute(
            """
            SELECT
                run.*,
                plan.execution_graph_id,
                plan.node_count,
                plan.input_fingerprint
                AS current_plan_fingerprint
            FROM execution_runs AS run
            JOIN plan_versions AS plan
              ON plan.id =
                 run.plan_version_id
            WHERE run.id = ?
            """,
            (run_id,),
        ).fetchone()

        if run is None:
            raise NotFoundError(
                f"execution run not found: "
                f"{run_id}"
            )

        nodes = connection.execute(
            """
            SELECT *
            FROM execution_run_nodes
            WHERE run_id = ?
            ORDER BY
                sequence_number,
                CASE stage
                    WHEN 'CONTRACT' THEN 1
                    WHEN 'IMPLEMENTATION' THEN 2
                    WHEN 'VERIFICATION' THEN 3
                    ELSE 4
                END,
                id
            """,
            (run_id,),
        ).fetchall()

        if (
            run["input_fingerprint"]
            != run[
                "current_plan_fingerprint"
            ]
        ):
            findings.append(
                {
                    "gate_code": (
                        "PLAN_FINGERPRINT_"
                        "MISMATCH"
                    ),
                    "severity": "ERROR",
                    "message": (
                        "Execution plan fingerprint "
                        "changed after run creation."
                    ),
                    "run_node_id": None,
                }
            )

        if len(nodes) != int(
            run["node_count"]
        ):
            findings.append(
                {
                    "gate_code": (
                        "EXECUTION_NODE_COUNT_"
                        "MISMATCH"
                    ),
                    "severity": "ERROR",
                    "message": (
                        "Execution run does not "
                        "contain every plan node."
                    ),
                    "run_node_id": None,
                }
            )

        if run["export_id"] is not None:
            export = connection.execute(
                """
                SELECT
                    status,
                    plan_version_id
                FROM exports
                WHERE id = ?
                """,
                (run["export_id"],),
            ).fetchone()

            if (
                export is None
                or export["status"]
                != "VERIFIED"
                or export[
                    "plan_version_id"
                ]
                != run[
                    "plan_version_id"
                ]
            ):
                findings.append(
                    {
                        "gate_code": (
                            "EXPORT_VERIFICATION_"
                            "FAILED"
                        ),
                        "severity": "ERROR",
                        "message": (
                            "Linked export is missing, "
                            "invalid, or belongs to a "
                            "different plan."
                        ),
                        "run_node_id": None,
                    }
                )

        for node in nodes:
            node_id = str(
                node["id"]
            )

            if node["status"] != "COMPLETE":
                findings.append(
                    {
                        "gate_code": (
                            "EXECUTION_NODE_"
                            "INCOMPLETE"
                        ),
                        "severity": "ERROR",
                        "message": (
                            "Execution node has not "
                            "completed."
                        ),
                        "run_node_id": node_id,
                    }
                )
                continue

            receipt_id = node[
                "accepted_receipt_id"
            ]

            if receipt_id is None:
                findings.append(
                    {
                        "gate_code": (
                            "ACCEPTED_RECEIPT_"
                            "MISSING"
                        ),
                        "severity": "ERROR",
                        "message": (
                            "Completed node has no "
                            "accepted receipt."
                        ),
                        "run_node_id": node_id,
                    }
                )
                continue

            receipt = connection.execute(
                """
                SELECT *
                FROM execution_receipts
                WHERE id = ?
                  AND run_node_id = ?
                """,
                (
                    receipt_id,
                    node_id,
                ),
            ).fetchone()

            if receipt is None:
                findings.append(
                    {
                        "gate_code": (
                            "ACCEPTED_RECEIPT_"
                            "NOT_FOUND"
                        ),
                        "severity": "ERROR",
                        "message": (
                            "Accepted receipt record "
                            "does not exist."
                        ),
                        "run_node_id": node_id,
                    }
                )
                continue

            if (
                receipt[
                    "validation_status"
                ]
                != "ACCEPTED"
            ):
                findings.append(
                    {
                        "gate_code": (
                            "RECEIPT_STATUS_INVALID"
                        ),
                        "severity": "ERROR",
                        "message": (
                            "Completed node references "
                            "a rejected receipt."
                        ),
                        "run_node_id": node_id,
                    }
                )

            try:
                evidence = json.loads(
                    receipt[
                        "evidence_json"
                    ]
                )
            except json.JSONDecodeError:
                evidence = None

            if not isinstance(
                evidence,
                dict,
            ):
                findings.append(
                    {
                        "gate_code": (
                            "RECEIPT_EVIDENCE_"
                            "INVALID"
                        ),
                        "severity": "ERROR",
                        "message": (
                            "Receipt evidence is not "
                            "valid JSON object data."
                        ),
                        "run_node_id": node_id,
                    }
                )
                continue

            payload = {
                "actor_system": receipt[
                    "actor_system"
                ],
                "actor_id": receipt[
                    "actor_id"
                ],
                "outcome": receipt[
                    "outcome"
                ],
                "summary": receipt[
                    "summary"
                ],
                "evidence": evidence,
            }

            actual_sha256 = (
                hashlib.sha256(
                    canonical_json(
                        payload
                    ).encode(
                        "utf-8"
                    )
                ).hexdigest()
            )

            if (
                actual_sha256
                != receipt[
                    "evidence_sha256"
                ]
            ):
                findings.append(
                    {
                        "gate_code": (
                            "EVIDENCE_HASH_"
                            "MISMATCH"
                        ),
                        "severity": "ERROR",
                        "message": (
                            "Stored receipt evidence "
                            "has changed since it was "
                            "accepted."
                        ),
                        "run_node_id": node_id,
                    }
                )

        connection.execute(
            """
            DELETE FROM
                execution_validation_findings
            WHERE run_id = ?
              AND receipt_id IS NULL
            """,
            (run_id,),
        )

        for finding in findings:
            connection.execute(
                """
                INSERT INTO
                    execution_validation_findings(
                        id,
                        run_id,
                        run_node_id,
                        receipt_id,
                        gate_code,
                        severity,
                        message,
                        created_at
                    )
                VALUES(?,?,?,?,?,?,?,?)
                """,
                (
                    new_id(
                        "execution-finding"
                    ),
                    run_id,
                    finding[
                        "run_node_id"
                    ],
                    None,
                    finding[
                        "gate_code"
                    ],
                    finding["severity"],
                    finding["message"],
                    timestamp,
                ),
            )

        complete_count = sum(
            node["status"] == "COMPLETE"
            for node in nodes
        )

        all_complete = (
            bool(nodes)
            and complete_count
            == len(nodes)
        )

        valid = (
            all_complete
            and not findings
        )

        if valid:
            status = "VERIFIED"
        elif all_complete:
            status = "INVALID"
        else:
            status = "RUNNING"

        connection.execute(
            """
            UPDATE execution_runs
            SET status = ?,
                completed_at = CASE
                    WHEN ? THEN
                        COALESCE(
                            completed_at,
                            ?
                        )
                    ELSE completed_at
                END,
                verified_at = ?
            WHERE id = ?
            """,
            (
                status,
                all_complete,
                timestamp,
                timestamp,
                run_id,
            ),
        )

        record_event(
            connection,
            run_id,
            None,
            "RUN_VERIFIED",
            "specgraph-foundry",
            {
                "valid": valid,
                "status": status,
                "finding_count": len(
                    findings
                ),
            },
        )

    return {
        "run_id": run_id,
        "valid": valid,
        "status": status,
        "finding_count": len(
            findings
        ),
        "findings": findings,
        "verified_at": timestamp,
    }
