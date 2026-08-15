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
from .execution_leases import (
    RUN_ACTIVE_STATUSES,
    expire_leases,
    refresh_run_status,
    require_active_claim,
    require_active_run,
)
from .execution_queries import (
    get_attempt,
    get_receipt,
    get_run,
    get_run_node,
    list_runs,
    ready_nodes,
)
from .receipt_validation import validate_receipt
from .run_verification import verify_run
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
        """Delegates to :func:`run_verification.verify_run`."""
        return verify_run(
            self.database,
            run_id,
        )


    def get_run(
        self,
        run_id: str,
    ) -> dict[str, object]:
        """Delegates to :func:`execution_queries.get_run`."""
        return get_run(
            self.database,
            run_id,
        )


    def list_runs(
        self,
        project_id: str,
    ) -> list[dict[str, object]]:
        """Delegates to :func:`execution_queries.list_runs`."""
        return list_runs(
            self.database,
            project_id,
        )


    def ready_nodes(
        self,
        run_id: str,
    ) -> list[dict[str, object]]:
        """Delegates to :func:`execution_queries.ready_nodes`."""
        return ready_nodes(
            self.database,
            run_id,
        )


    def get_run_node(
        self,
        run_node_id: str,
    ) -> dict[str, object]:
        """Delegates to :func:`execution_queries.get_run_node`."""
        return get_run_node(
            self.database,
            run_node_id,
        )


    def get_attempt(
        self,
        attempt_id: str,
    ) -> dict[str, object]:
        """Delegates to :func:`execution_queries.get_attempt`."""
        return get_attempt(
            self.database,
            attempt_id,
        )


    def get_receipt(
        self,
        receipt_id: str,
    ) -> dict[str, object]:
        """Delegates to :func:`execution_queries.get_receipt`."""
        return get_receipt(
            self.database,
            receipt_id,
        )


    def _validate_receipt(
        self,
        connection: sqlite3.Connection,
        node: sqlite3.Row,
        actor_id: str,
        outcome: str,
        summary: str,
        evidence: dict[str, object],
    ) -> list[dict[str, str]]:
        """Delegates to :func:`receipt_validation.validate_receipt`.

        Kept as a method so call sites and the subclass seam are
        unchanged; the gates are no longer this class's business.
        """
        return validate_receipt(
            connection,
            node,
            actor_id,
            outcome,
            summary,
            evidence,
        )


    def _refresh_run_status(
        self,
        run_id: str,
    ) -> None:
        """Delegates to :func:`execution_leases.refresh_run_status`."""
        return refresh_run_status(
            self.database,
            run_id,
        )


    def _require_active_run(
        self,
        connection: sqlite3.Connection,
        run_id: str,
    ) -> sqlite3.Row:
        """Delegates to :func:`execution_leases.require_active_run`."""
        return require_active_run(
            connection,
            run_id,
        )


    def _require_active_claim(
        self,
        connection: sqlite3.Connection,
        run_node_id: str,
        worker_id: str,
    ) -> tuple[
        sqlite3.Row,
        sqlite3.Row,
    ]:
        """Delegates to :func:`execution_leases.require_active_claim`."""
        return require_active_claim(
            connection,
            run_node_id,
            worker_id,
        )


    def _expire_leases(
        self,
        connection: sqlite3.Connection,
        run_id: str,
        now: datetime,
    ) -> None:
        """Delegates to :func:`execution_leases.expire_leases`."""
        return expire_leases(
            connection,
            run_id,
            now,
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

