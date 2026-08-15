import hashlib
import json
import re
import sqlite3
import uuid
from collections import defaultdict
from datetime import UTC, datetime, timedelta

from .database import Database
from .errors import (
    ConflictError,
    NotFoundError,
    ValidationError,
)
from .exports import ExportService
from .planning import PlanningService
from .execution_events import normalize_event, normalize_receipt, record_event
from .execution_schema import EXECUTION_SCHEMA
from .primitives import (
    canonical_json,
    new_id,
    parse_time,
    utc_now,
    utc_now_datetime,
    valid_sha256,
    valid_string_list,
)




STAGES = {
    "CONTRACT",
    "IMPLEMENTATION",
    "VERIFICATION",
}

RUN_ACTIVE_STATUSES = {
    "RUNNING",
}

















class ExecutionService:
    def __init__(
        self,
        database: Database,
    ) -> None:
        self.database = database
        self.exports = ExportService(
            database
        )
        self.planning = PlanningService(
            database
        )
        self.ensure_schema()

    def ensure_schema(self) -> None:
        with self.database.connect() as connection:
            connection.executescript(
                EXECUTION_SCHEMA
            )

    def start_run(
        self,
        plan_id: str,
        runtime_system: str,
        runtime_run_id: str,
        export_id: str | None = None,
    ) -> dict[str, object]:
        runtime_system = runtime_system.strip()
        runtime_run_id = runtime_run_id.strip()

        if not runtime_system:
            raise ValidationError(
                "runtime_system is required"
            )

        if not runtime_run_id:
            raise ValidationError(
                "runtime_run_id is required"
            )

        plan = self.planning.get_plan(
            plan_id
        )

        if plan["status"] != "VERIFIED":
            raise ValidationError(
                "execution requires a VERIFIED plan"
            )

        if export_id is not None:
            export = self.exports.get_export(
                export_id
            )

            if export["status"] != "VERIFIED":
                raise ValidationError(
                    "execution export must be VERIFIED"
                )

            if (
                export["plan_version_id"]
                != plan_id
            ):
                raise ValidationError(
                    "export does not belong to plan"
                )

        run_id = new_id("execution-run")
        timestamp = utc_now()

        try:
            with self.database.connect() as connection:
                connection.execute(
                    """
                    INSERT INTO execution_runs(
                        id,
                        project_id,
                        plan_version_id,
                        export_id,
                        runtime_system,
                        runtime_run_id,
                        status,
                        input_fingerprint,
                        created_at,
                        started_at
                    )
                    VALUES(?,?,?,?,?,?,?,?,?,?)
                    """,
                    (
                        run_id,
                        plan["project_id"],
                        plan_id,
                        export_id,
                        runtime_system,
                        runtime_run_id,
                        "RUNNING",
                        plan[
                            "input_fingerprint"
                        ],
                        timestamp,
                        timestamp,
                    ),
                )

                for binding in plan[
                    "bindings"
                ]:
                    stage = str(
                        binding["stage"]
                    )

                    if stage not in STAGES:
                        raise ValidationError(
                            f"invalid plan stage: "
                            f"{stage}"
                        )

                    connection.execute(
                        """
                        INSERT INTO
                            execution_run_nodes(
                                id,
                                run_id,
                                graph_node_id,
                                atom_id,
                                stage,
                                sequence_number,
                                title,
                                status,
                                created_at,
                                updated_at
                            )
                        VALUES(?,?,?,?,?,?,?,?,?,?)
                        """,
                        (
                            new_id(
                                "execution-node"
                            ),
                            run_id,
                            binding[
                                "graph_node_id"
                            ],
                            binding["atom_id"],
                            stage,
                            binding[
                                "sequence_number"
                            ],
                            binding[
                                "canonical_statement"
                            ],
                            "PENDING",
                            timestamp,
                            timestamp,
                        ),
                    )

                self._event(
                    connection,
                    run_id,
                    None,
                    "RUN_STARTED",
                    runtime_system,
                    {
                        "plan_id": plan_id,
                        "export_id": export_id,
                        "runtime_run_id": (
                            runtime_run_id
                        ),
                    },
                )

        except sqlite3.IntegrityError as error:
            raise ConflictError(
                "runtime run identifier already exists"
            ) from error

        return self.get_run(run_id)

    def claim_node(
        self,
        run_id: str,
        worker_id: str,
        run_node_id: str | None = None,
        lease_seconds: int = 900,
    ) -> dict[str, object] | None:
        worker_id = worker_id.strip()

        if not worker_id:
            raise ValidationError(
                "worker_id is required"
            )

        if lease_seconds < 30:
            raise ValidationError(
                "lease_seconds must be at least 30"
            )

        now = utc_now_datetime()
        expiration = (
            now
            + timedelta(
                seconds=lease_seconds
            )
        ).isoformat()

        with self.database.connect() as connection:
            connection.execute(
                "BEGIN IMMEDIATE"
            )

            run = self._require_active_run(
                connection,
                run_id,
            )

            self._expire_leases(
                connection,
                run_id,
                now,
            )

            parameters: list[object] = [
                run_id,
                run[
                    "execution_graph_id"
                ],
            ]

            node_filter = ""

            if run_node_id is not None:
                node_filter = (
                    "AND node.id = ?"
                )
                parameters.append(
                    run_node_id
                )

            candidate = connection.execute(
                f"""
                SELECT node.*
                FROM execution_run_nodes
                AS node
                WHERE node.run_id = ?
                  AND node.status = 'PENDING'
                  AND NOT EXISTS (
                      SELECT 1
                      FROM graph_edges AS edge
                      JOIN execution_run_nodes
                      AS predecessor
                        ON predecessor.run_id =
                           node.run_id
                       AND predecessor.graph_node_id =
                           edge.from_node_id
                      WHERE edge.graph_id = ?
                        AND edge.to_node_id =
                            node.graph_node_id
                        AND predecessor.status
                            <> 'COMPLETE'
                  )
                  {node_filter}
                ORDER BY
                    node.sequence_number,
                    CASE node.stage
                        WHEN 'CONTRACT' THEN 1
                        WHEN 'IMPLEMENTATION' THEN 2
                        WHEN 'VERIFICATION' THEN 3
                        ELSE 4
                    END,
                    node.id
                LIMIT 1
                """,
                tuple(parameters),
            ).fetchone()

            if candidate is None:
                if run_node_id is None:
                    return None

                existing = connection.execute(
                    """
                    SELECT id
                    FROM execution_run_nodes
                    WHERE id = ?
                      AND run_id = ?
                    """,
                    (
                        run_node_id,
                        run_id,
                    ),
                ).fetchone()

                if existing is None:
                    raise NotFoundError(
                        "execution node not found"
                    )

                raise ConflictError(
                    "execution node is not ready"
                )

            node_id = str(
                candidate["id"]
            )
            attempt_id = new_id(
                "execution-attempt"
            )

            connection.execute(
                """
                UPDATE execution_run_nodes
                SET status = 'CLAIMED',
                    lease_owner = ?,
                    lease_expires_at = ?,
                    attempt_count =
                        attempt_count + 1,
                    updated_at = ?
                WHERE id = ?
                """,
                (
                    worker_id,
                    expiration,
                    now.isoformat(),
                    node_id,
                ),
            )

            connection.execute(
                """
                INSERT INTO execution_attempts(
                    id,
                    run_node_id,
                    worker_id,
                    status,
                    lease_expires_at,
                    started_at
                )
                VALUES(?,?,?,?,?,?)
                """,
                (
                    attempt_id,
                    node_id,
                    worker_id,
                    "ACTIVE",
                    expiration,
                    now.isoformat(),
                ),
            )

            self._event(
                connection,
                run_id,
                node_id,
                "NODE_CLAIMED",
                worker_id,
                {
                    "attempt_id": attempt_id,
                    "lease_expires_at": (
                        expiration
                    ),
                },
            )

        return {
            "node": self.get_run_node(
                node_id
            ),
            "attempt": self.get_attempt(
                attempt_id
            ),
        }

    def heartbeat(
        self,
        run_node_id: str,
        worker_id: str,
        lease_seconds: int = 900,
    ) -> dict[str, object]:
        if lease_seconds < 30:
            raise ValidationError(
                "lease_seconds must be at least 30"
            )

        expiration = (
            utc_now_datetime()
            + timedelta(
                seconds=lease_seconds
            )
        ).isoformat()

        with self.database.connect() as connection:
            node, attempt = (
                self._require_active_claim(
                    connection,
                    run_node_id,
                    worker_id,
                )
            )

            connection.execute(
                """
                UPDATE execution_run_nodes
                SET lease_expires_at = ?,
                    updated_at = ?
                WHERE id = ?
                """,
                (
                    expiration,
                    utc_now(),
                    run_node_id,
                ),
            )

            connection.execute(
                """
                UPDATE execution_attempts
                SET lease_expires_at = ?
                WHERE id = ?
                """,
                (
                    expiration,
                    attempt["id"],
                ),
            )

            self._event(
                connection,
                str(node["run_id"]),
                run_node_id,
                "NODE_HEARTBEAT",
                worker_id,
                {
                    "lease_expires_at": (
                        expiration
                    )
                },
            )

        return self.get_run_node(
            run_node_id
        )

    def submit_receipt(
        self,
        run_node_id: str,
        worker_id: str,
        actor_system: str,
        outcome: str,
        summary: str,
        evidence: dict[str, object],
    ) -> dict[str, object]:
        worker_id = worker_id.strip()
        actor_system = actor_system.strip()
        outcome = outcome.strip().upper()
        summary = summary.strip()

        if not worker_id:
            raise ValidationError(
                "worker_id is required"
            )

        if not actor_system:
            raise ValidationError(
                "actor_system is required"
            )

        if not isinstance(evidence, dict):
            raise ValidationError(
                "evidence must be an object"
            )

        receipt_payload = {
            "actor_system": actor_system,
            "actor_id": worker_id,
            "outcome": outcome,
            "summary": summary,
            "evidence": evidence,
        }

        evidence_json = canonical_json(
            evidence
        )

        evidence_sha256 = hashlib.sha256(
            canonical_json(
                receipt_payload
            ).encode("utf-8")
        ).hexdigest()

        receipt_id = new_id(
            "execution-receipt"
        )
        timestamp = utc_now()

        try:
            with self.database.connect() as connection:
                node, attempt = (
                    self._require_active_claim(
                        connection,
                        run_node_id,
                        worker_id,
                    )
                )

                findings = (
                    self._validate_receipt(
                        connection,
                        node,
                        worker_id,
                        outcome,
                        summary,
                        evidence,
                    )
                )

                accepted = not findings

                validation_status = (
                    "ACCEPTED"
                    if accepted
                    else "REJECTED"
                )

                connection.execute(
                    """
                    INSERT INTO execution_receipts(
                        id,
                        run_id,
                        run_node_id,
                        attempt_id,
                        actor_system,
                        actor_id,
                        outcome,
                        summary,
                        evidence_json,
                        evidence_sha256,
                        validation_status,
                        created_at
                    )
                    VALUES(?,?,?,?,?,?,?,?,?,?,?,?)
                    """,
                    (
                        receipt_id,
                        node["run_id"],
                        run_node_id,
                        attempt["id"],
                        actor_system,
                        worker_id,
                        outcome,
                        summary,
                        evidence_json,
                        evidence_sha256,
                        validation_status,
                        timestamp,
                    ),
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
                            node["run_id"],
                            run_node_id,
                            receipt_id,
                            finding["gate_code"],
                            finding["severity"],
                            finding["message"],
                            timestamp,
                        ),
                    )

                if accepted:
                    connection.execute(
                        """
                        UPDATE execution_run_nodes
                        SET status = 'COMPLETE',
                            accepted_receipt_id = ?,
                            lease_owner = NULL,
                            lease_expires_at = NULL,
                            updated_at = ?
                        WHERE id = ?
                        """,
                        (
                            receipt_id,
                            timestamp,
                            run_node_id,
                        ),
                    )

                    connection.execute(
                        """
                        UPDATE execution_attempts
                        SET status = 'COMPLETE',
                            completed_at = ?
                        WHERE id = ?
                        """,
                        (
                            timestamp,
                            attempt["id"],
                        ),
                    )

                    event_type = (
                        "RECEIPT_ACCEPTED"
                    )
                else:
                    connection.execute(
                        """
                        UPDATE execution_run_nodes
                        SET status = 'PENDING',
                            lease_owner = NULL,
                            lease_expires_at = NULL,
                            updated_at = ?
                        WHERE id = ?
                        """,
                        (
                            timestamp,
                            run_node_id,
                        ),
                    )

                    connection.execute(
                        """
                        UPDATE execution_attempts
                        SET status = 'FAILED',
                            completed_at = ?,
                            error_message = ?
                        WHERE id = ?
                        """,
                        (
                            timestamp,
                            (
                                "receipt failed "
                                "anti-fake gates"
                            ),
                            attempt["id"],
                        ),
                    )

                    event_type = (
                        "RECEIPT_REJECTED"
                    )

                self._event(
                    connection,
                    str(node["run_id"]),
                    run_node_id,
                    event_type,
                    worker_id,
                    {
                        "receipt_id": receipt_id,
                        "validation_status": (
                            validation_status
                        ),
                        "finding_count": len(
                            findings
                        ),
                    },
                )

        except sqlite3.IntegrityError as error:
            raise ConflictError(
                "identical receipt already exists "
                "for this execution node"
            ) from error

        self._refresh_run_status(
            str(node["run_id"])
        )

        return self.get_receipt(
            receipt_id
        )

    def verify_run(
        self,
        run_id: str,
    ) -> dict[str, object]:
        timestamp = utc_now()
        findings: list[
            dict[str, object]
        ] = []

        with self.database.connect() as connection:
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

            self._event(
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

    def get_run(
        self,
        run_id: str,
    ) -> dict[str, object]:
        with self.database.connect() as connection:
            run = connection.execute(
                """
                SELECT *
                FROM execution_runs
                WHERE id = ?
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

            attempts = connection.execute(
                """
                SELECT attempt.*
                FROM execution_attempts
                AS attempt
                JOIN execution_run_nodes
                AS node
                  ON node.id =
                     attempt.run_node_id
                WHERE node.run_id = ?
                ORDER BY
                    attempt.started_at,
                    attempt.id
                """,
                (run_id,),
            ).fetchall()

            receipts = connection.execute(
                """
                SELECT *
                FROM execution_receipts
                WHERE run_id = ?
                ORDER BY created_at, id
                """,
                (run_id,),
            ).fetchall()

            findings = connection.execute(
                """
                SELECT *
                FROM execution_validation_findings
                WHERE run_id = ?
                ORDER BY
                    created_at,
                    id
                """,
                (run_id,),
            ).fetchall()

            events = connection.execute(
                """
                SELECT *
                FROM execution_events
                WHERE run_id = ?
                ORDER BY created_at, id
                """,
                (run_id,),
            ).fetchall()

        result = dict(run)
        result["nodes"] = [
            dict(row)
            for row in nodes
        ]
        result["attempts"] = [
            dict(row)
            for row in attempts
        ]
        result["receipts"] = [
            self._normalize_receipt(
                dict(row)
            )
            for row in receipts
        ]
        result["findings"] = [
            dict(row)
            for row in findings
        ]
        result["events"] = [
            self._normalize_event(
                dict(row)
            )
            for row in events
        ]
        result["ready_nodes"] = (
            self.ready_nodes(run_id)
        )

        return result

    def list_runs(
        self,
        project_id: str,
    ) -> list[dict[str, object]]:
        with self.database.connect() as connection:
            project = connection.execute(
                """
                SELECT id
                FROM projects
                WHERE id = ?
                """,
                (project_id,),
            ).fetchone()

            if project is None:
                raise NotFoundError(
                    f"project not found: "
                    f"{project_id}"
                )

            rows = connection.execute(
                """
                SELECT *
                FROM execution_runs
                WHERE project_id = ?
                ORDER BY created_at DESC, id
                """,
                (project_id,),
            ).fetchall()

        return [
            dict(row)
            for row in rows
        ]

    def ready_nodes(
        self,
        run_id: str,
    ) -> list[dict[str, object]]:
        with self.database.connect() as connection:
            run = connection.execute(
                """
                SELECT
                    run.status,
                    plan.execution_graph_id
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

            if run["status"] not in {
                "RUNNING",
            }:
                return []

            rows = connection.execute(
                """
                SELECT node.*
                FROM execution_run_nodes
                AS node
                WHERE node.run_id = ?
                  AND node.status = 'PENDING'
                  AND NOT EXISTS (
                      SELECT 1
                      FROM graph_edges AS edge
                      JOIN execution_run_nodes
                      AS predecessor
                        ON predecessor.run_id =
                           node.run_id
                       AND predecessor.graph_node_id =
                           edge.from_node_id
                      WHERE edge.graph_id = ?
                        AND edge.to_node_id =
                            node.graph_node_id
                        AND predecessor.status
                            <> 'COMPLETE'
                  )
                ORDER BY
                    node.sequence_number,
                    CASE node.stage
                        WHEN 'CONTRACT' THEN 1
                        WHEN 'IMPLEMENTATION' THEN 2
                        WHEN 'VERIFICATION' THEN 3
                        ELSE 4
                    END,
                    node.id
                """,
                (
                    run_id,
                    run[
                        "execution_graph_id"
                    ],
                ),
            ).fetchall()

        return [
            dict(row)
            for row in rows
        ]

    def get_run_node(
        self,
        run_node_id: str,
    ) -> dict[str, object]:
        with self.database.connect() as connection:
            row = connection.execute(
                """
                SELECT *
                FROM execution_run_nodes
                WHERE id = ?
                """,
                (run_node_id,),
            ).fetchone()

        if row is None:
            raise NotFoundError(
                f"execution node not found: "
                f"{run_node_id}"
            )

        return dict(row)

    def get_attempt(
        self,
        attempt_id: str,
    ) -> dict[str, object]:
        with self.database.connect() as connection:
            row = connection.execute(
                """
                SELECT *
                FROM execution_attempts
                WHERE id = ?
                """,
                (attempt_id,),
            ).fetchone()

        if row is None:
            raise NotFoundError(
                f"execution attempt not found: "
                f"{attempt_id}"
            )

        return dict(row)

    def get_receipt(
        self,
        receipt_id: str,
    ) -> dict[str, object]:
        with self.database.connect() as connection:
            row = connection.execute(
                """
                SELECT *
                FROM execution_receipts
                WHERE id = ?
                """,
                (receipt_id,),
            ).fetchone()

            if row is None:
                raise NotFoundError(
                    f"execution receipt not found: "
                    f"{receipt_id}"
                )

            findings = connection.execute(
                """
                SELECT *
                FROM execution_validation_findings
                WHERE receipt_id = ?
                ORDER BY gate_code, id
                """,
                (receipt_id,),
            ).fetchall()

        result = self._normalize_receipt(
            dict(row)
        )

        result["findings"] = [
            dict(item)
            for item in findings
        ]

        return result

    def _validate_receipt(
        self,
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

    def _refresh_run_status(
        self,
        run_id: str,
    ) -> None:
        timestamp = utc_now()

        with self.database.connect() as connection:
            counts = connection.execute(
                """
                SELECT
                    COUNT(*) AS total,
                    SUM(
                        CASE
                            WHEN status = 'COMPLETE'
                            THEN 1
                            ELSE 0
                        END
                    ) AS complete_count
                FROM execution_run_nodes
                WHERE run_id = ?
                """,
                (run_id,),
            ).fetchone()

            total = int(
                counts["total"] or 0
            )
            complete_count = int(
                counts[
                    "complete_count"
                ]
                or 0
            )

            complete = (
                total > 0
                and total == complete_count
            )

            status = (
                "COMPLETE"
                if complete
                else "RUNNING"
            )

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
                        ELSE NULL
                    END
                WHERE id = ?
                  AND status NOT IN (
                      'VERIFIED',
                      'INVALID'
                  )
                """,
                (
                    status,
                    complete,
                    timestamp,
                    run_id,
                ),
            )

    def _require_active_run(
        self,
        connection: sqlite3.Connection,
        run_id: str,
    ) -> sqlite3.Row:
        run = connection.execute(
            """
            SELECT
                run.*,
                plan.execution_graph_id
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

        if run["status"] not in (
            RUN_ACTIVE_STATUSES
        ):
            raise ConflictError(
                "execution run is not active"
            )

        return run

    def _require_active_claim(
        self,
        connection: sqlite3.Connection,
        run_node_id: str,
        worker_id: str,
    ) -> tuple[
        sqlite3.Row,
        sqlite3.Row,
    ]:
        node = connection.execute(
            """
            SELECT *
            FROM execution_run_nodes
            WHERE id = ?
            """,
            (run_node_id,),
        ).fetchone()

        if node is None:
            raise NotFoundError(
                f"execution node not found: "
                f"{run_node_id}"
            )

        if node["status"] != "CLAIMED":
            raise ConflictError(
                "execution node is not claimed"
            )

        if node["lease_owner"] != worker_id:
            raise ConflictError(
                "execution node belongs to "
                "another worker"
            )

        expiration_value = node[
            "lease_expires_at"
        ]

        if (
            expiration_value is None
            or parse_time(
                str(expiration_value)
            )
            <= utc_now_datetime()
        ):
            raise ConflictError(
                "execution-node lease expired"
            )

        attempt = connection.execute(
            """
            SELECT *
            FROM execution_attempts
            WHERE run_node_id = ?
              AND worker_id = ?
              AND status = 'ACTIVE'
            ORDER BY started_at DESC, id DESC
            LIMIT 1
            """,
            (
                run_node_id,
                worker_id,
            ),
        ).fetchone()

        if attempt is None:
            raise ConflictError(
                "active execution attempt "
                "does not exist"
            )

        return node, attempt

    def _expire_leases(
        self,
        connection: sqlite3.Connection,
        run_id: str,
        now: datetime,
    ) -> None:
        rows = connection.execute(
            """
            SELECT *
            FROM execution_run_nodes
            WHERE run_id = ?
              AND status = 'CLAIMED'
              AND lease_expires_at IS NOT NULL
              AND lease_expires_at <= ?
            """,
            (
                run_id,
                now.isoformat(),
            ),
        ).fetchall()

        for node in rows:
            node_id = str(node["id"])

            connection.execute(
                """
                UPDATE execution_attempts
                SET status = 'EXPIRED',
                    completed_at = ?,
                    error_message = ?
                WHERE run_node_id = ?
                  AND status = 'ACTIVE'
                """,
                (
                    now.isoformat(),
                    "execution-node lease expired",
                    node_id,
                ),
            )

            connection.execute(
                """
                UPDATE execution_run_nodes
                SET status = 'PENDING',
                    lease_owner = NULL,
                    lease_expires_at = NULL,
                    updated_at = ?
                WHERE id = ?
                """,
                (
                    now.isoformat(),
                    node_id,
                ),
            )

            self._event(
                connection,
                run_id,
                node_id,
                "NODE_LEASE_EXPIRED",
                None,
                {},
            )

    @staticmethod
    def _event(
        connection: sqlite3.Connection,
        run_id: str,
        run_node_id: str | None,
        event_type: str,
        actor_id: str | None,
        payload: dict[str, object],
    ) -> None:
        """Delegates to :func:`execution_events.record_event`."""
        return record_event(
            connection,
            run_id,
            run_node_id,
            event_type,
            actor_id,
            payload,
        )


    @staticmethod
    def _normalize_receipt(
        record: dict[str, object],
    ) -> dict[str, object]:
        """Delegates to :func:`execution_events.normalize_receipt`."""
        return normalize_receipt(
            record,
        )


    @staticmethod
    def _normalize_event(
        record: dict[str, object],
    ) -> dict[str, object]:
        """Delegates to :func:`execution_events.normalize_event`."""
        return normalize_event(
            record,
        )

