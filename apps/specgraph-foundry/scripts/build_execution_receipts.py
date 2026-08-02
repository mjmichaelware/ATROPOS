from pathlib import Path
from textwrap import dedent

ROOT = Path.cwd()

if ROOT.name != "specgraph-foundry" or not (ROOT / ".git").is_dir():
    raise SystemExit(f"Wrong repository: {ROOT}")


def write(path: str, content: str) -> None:
    target = ROOT / path
    target.parent.mkdir(parents=True, exist_ok=True)
    target.write_text(
        dedent(content).lstrip(),
        encoding="utf-8",
    )
    print(f"WROTE {path}")


def insert_after(
    path: str,
    marker: str,
    addition: str,
    installed_marker: str,
) -> None:
    target = ROOT / path
    content = target.read_text(encoding="utf-8")

    if installed_marker in content:
        print(f"SKIPPED {path}: already installed")
        return

    if marker not in content:
        raise SystemExit(
            f"PATCH MARKER NOT FOUND IN {path}:\n{marker}"
        )

    target.write_text(
        content.replace(
            marker,
            marker + addition,
            1,
        ),
        encoding="utf-8",
    )
    print(f"UPDATED {path}")


def insert_before(
    path: str,
    marker: str,
    addition: str,
    installed_marker: str,
) -> None:
    target = ROOT / path
    content = target.read_text(encoding="utf-8")

    if installed_marker in content:
        print(f"SKIPPED {path}: already installed")
        return

    if marker not in content:
        raise SystemExit(
            f"PATCH MARKER NOT FOUND IN {path}:\n{marker}"
        )

    target.write_text(
        content.replace(
            marker,
            addition + marker,
            1,
        ),
        encoding="utf-8",
    )
    print(f"UPDATED {path}")


write(
    "src/specgraph_foundry/execution.py",
    r'''
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


    EXECUTION_SCHEMA = """
    PRAGMA foreign_keys = ON;

    CREATE TABLE IF NOT EXISTS execution_runs (
        id TEXT PRIMARY KEY,
        project_id TEXT NOT NULL
            REFERENCES projects(id)
            ON DELETE CASCADE,
        plan_version_id TEXT NOT NULL
            REFERENCES plan_versions(id)
            ON DELETE CASCADE,
        export_id TEXT
            REFERENCES exports(id)
            ON DELETE SET NULL,
        runtime_system TEXT NOT NULL,
        runtime_run_id TEXT NOT NULL,
        status TEXT NOT NULL,
        input_fingerprint TEXT NOT NULL,
        created_at TEXT NOT NULL,
        started_at TEXT NOT NULL,
        completed_at TEXT,
        verified_at TEXT,
        UNIQUE(
            runtime_system,
            runtime_run_id
        )
    );

    CREATE TABLE IF NOT EXISTS execution_run_nodes (
        id TEXT PRIMARY KEY,
        run_id TEXT NOT NULL
            REFERENCES execution_runs(id)
            ON DELETE CASCADE,
        graph_node_id TEXT NOT NULL
            REFERENCES graph_nodes(id)
            ON DELETE CASCADE,
        atom_id TEXT NOT NULL
            REFERENCES atoms(id)
            ON DELETE CASCADE,
        stage TEXT NOT NULL,
        sequence_number INTEGER NOT NULL,
        title TEXT NOT NULL,
        status TEXT NOT NULL,
        lease_owner TEXT,
        lease_expires_at TEXT,
        attempt_count INTEGER NOT NULL DEFAULT 0,
        accepted_receipt_id TEXT,
        created_at TEXT NOT NULL,
        updated_at TEXT NOT NULL,
        UNIQUE(
            run_id,
            graph_node_id
        )
    );

    CREATE TABLE IF NOT EXISTS execution_attempts (
        id TEXT PRIMARY KEY,
        run_node_id TEXT NOT NULL
            REFERENCES execution_run_nodes(id)
            ON DELETE CASCADE,
        worker_id TEXT NOT NULL,
        status TEXT NOT NULL,
        lease_expires_at TEXT NOT NULL,
        started_at TEXT NOT NULL,
        completed_at TEXT,
        error_message TEXT
    );

    CREATE TABLE IF NOT EXISTS execution_receipts (
        id TEXT PRIMARY KEY,
        run_id TEXT NOT NULL
            REFERENCES execution_runs(id)
            ON DELETE CASCADE,
        run_node_id TEXT NOT NULL
            REFERENCES execution_run_nodes(id)
            ON DELETE CASCADE,
        attempt_id TEXT NOT NULL
            REFERENCES execution_attempts(id)
            ON DELETE CASCADE,
        actor_system TEXT NOT NULL,
        actor_id TEXT NOT NULL,
        outcome TEXT NOT NULL,
        summary TEXT NOT NULL,
        evidence_json TEXT NOT NULL,
        evidence_sha256 TEXT NOT NULL,
        validation_status TEXT NOT NULL,
        created_at TEXT NOT NULL,
        UNIQUE(
            run_node_id,
            evidence_sha256
        )
    );

    CREATE TABLE IF NOT EXISTS execution_validation_findings (
        id TEXT PRIMARY KEY,
        run_id TEXT NOT NULL
            REFERENCES execution_runs(id)
            ON DELETE CASCADE,
        run_node_id TEXT
            REFERENCES execution_run_nodes(id)
            ON DELETE CASCADE,
        receipt_id TEXT
            REFERENCES execution_receipts(id)
            ON DELETE CASCADE,
        gate_code TEXT NOT NULL,
        severity TEXT NOT NULL,
        message TEXT NOT NULL,
        created_at TEXT NOT NULL
    );

    CREATE TABLE IF NOT EXISTS execution_events (
        id TEXT PRIMARY KEY,
        run_id TEXT NOT NULL
            REFERENCES execution_runs(id)
            ON DELETE CASCADE,
        run_node_id TEXT
            REFERENCES execution_run_nodes(id)
            ON DELETE CASCADE,
        event_type TEXT NOT NULL,
        actor_id TEXT,
        payload_json TEXT NOT NULL DEFAULT '{}',
        created_at TEXT NOT NULL
    );

    CREATE INDEX IF NOT EXISTS idx_execution_runs_project
        ON execution_runs(
            project_id,
            created_at
        );

    CREATE INDEX IF NOT EXISTS idx_execution_runs_plan
        ON execution_runs(
            plan_version_id,
            status
        );

    CREATE INDEX IF NOT EXISTS idx_execution_nodes_run
        ON execution_run_nodes(
            run_id,
            status,
            sequence_number
        );

    CREATE INDEX IF NOT EXISTS idx_execution_attempts_node
        ON execution_attempts(
            run_node_id,
            status
        );

    CREATE INDEX IF NOT EXISTS idx_execution_receipts_node
        ON execution_receipts(
            run_node_id,
            validation_status
        );

    CREATE INDEX IF NOT EXISTS idx_execution_findings_run
        ON execution_validation_findings(
            run_id,
            severity
        );

    CREATE INDEX IF NOT EXISTS idx_execution_events_run
        ON execution_events(
            run_id,
            created_at
        );
    """


    STAGES = {
        "CONTRACT",
        "IMPLEMENTATION",
        "VERIFICATION",
    }

    RUN_ACTIVE_STATUSES = {
        "RUNNING",
    }

    SHA256_PATTERN = re.compile(
        r"^[0-9a-f]{64}$"
    )


    def utc_now_datetime() -> datetime:
        return datetime.now(UTC)


    def utc_now() -> str:
        return utc_now_datetime().isoformat()


    def new_id(prefix: str) -> str:
        return f"{prefix}-{uuid.uuid4()}"


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
                        int(all_complete),
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
                        int(complete),
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
            connection.execute(
                """
                INSERT INTO execution_events(
                    id,
                    run_id,
                    run_node_id,
                    event_type,
                    actor_id,
                    payload_json,
                    created_at
                )
                VALUES(?,?,?,?,?,?,?)
                """,
                (
                    new_id("execution-event"),
                    run_id,
                    run_node_id,
                    event_type,
                    actor_id,
                    canonical_json(payload),
                    utc_now(),
                ),
            )

        @staticmethod
        def _normalize_receipt(
            record: dict[str, object],
        ) -> dict[str, object]:
            record["evidence"] = json.loads(
                str(
                    record.pop(
                        "evidence_json"
                    )
                )
            )

            return record

        @staticmethod
        def _normalize_event(
            record: dict[str, object],
        ) -> dict[str, object]:
            record["payload"] = json.loads(
                str(
                    record.pop(
                        "payload_json"
                    )
                )
            )

            return record
    ''',
)

write(
    "tests/test_execution.py",
    r'''
    import tempfile
    import unittest
    import uuid
    from pathlib import Path

    from specgraph_foundry.atoms import (
        AtomService,
    )
    from specgraph_foundry.database import (
        Database,
    )
    from specgraph_foundry.execution import (
        ExecutionService,
    )
    from specgraph_foundry.ingestion import (
        IngestionService,
    )
    from specgraph_foundry.planning import (
        PlanningService,
    )
    from specgraph_foundry.research import (
        ResearchService,
    )
    from specgraph_foundry.services import (
        ProjectService,
    )


    class ExecutionTest(unittest.TestCase):
        def setUp(self) -> None:
            self.temp = (
                tempfile.TemporaryDirectory()
            )

            self.database = Database(
                Path(self.temp.name)
                / "test.sqlite3"
            )
            self.database.initialize()

            self.projects = ProjectService(
                self.database
            )
            self.ingestion = (
                IngestionService(
                    self.database
                )
            )
            self.atoms = AtomService(
                self.database
            )
            self.research = (
                ResearchService(
                    self.database
                )
            )
            self.planning = (
                PlanningService(
                    self.database
                )
            )
            self.execution = (
                ExecutionService(
                    self.database
                )
            )

        def tearDown(self) -> None:
            self.temp.cleanup()

        def _project_and_plan(
            self,
            resolved: bool,
        ) -> tuple[
            dict[str, object],
            dict[str, object],
            dict[str, object],
        ]:
            suffix = uuid.uuid4().hex[:8]

            project = self.projects.create(
                f"execution-{suffix}",
                "Execution Test",
            )

            document = (
                self.ingestion.ingest_text(
                    project_id=str(
                        project["id"]
                    ),
                    title="Authority",
                    content=(
                        "The service must preserve "
                        "source authority.\n"
                    ),
                    chunk_bytes=32,
                )
            )

            extraction = (
                self.atoms.extract_document(
                    str(document["id"])
                )
            )

            atom = extraction["atoms"][0]

            if resolved:
                count = 0

                while True:
                    worker = (
                        f"researcher-{count}"
                    )

                    task = (
                        self.research.claim_task(
                            str(project["id"]),
                            worker,
                            300,
                        )
                    )

                    if task is None:
                        break

                    evidence = (
                        self.research.add_evidence(
                            task_id=str(
                                task["id"]
                            ),
                            worker_id=worker,
                            source_uri=(
                                "urn:test:"
                                + str(task["id"])
                            ),
                            source_title=(
                                "Test Authority"
                            ),
                            excerpt=(
                                "The requirement is "
                                "applicable."
                            ),
                            evidence_type=(
                                "USER_DECISION"
                            ),
                            reliability=1.0,
                        )
                    )

                    self.research.complete_task(
                        task_id=str(
                            task["id"]
                        ),
                        worker_id=worker,
                        conclusion=(
                            "This dimension applies "
                            "to the requirement."
                        ),
                        applicability=(
                            "APPLICABLE"
                        ),
                        confidence=1.0,
                        evidence_ids=[
                            str(evidence["id"])
                        ],
                    )

                    count += 1

            plan = self.planning.synthesize(
                str(project["id"]),
                allow_open_research=(
                    not resolved
                ),
            )

            return project, atom, plan

        def _start(
            self,
            plan: dict[str, object],
        ) -> dict[str, object]:
            return self.execution.start_run(
                plan_id=str(plan["id"]),
                runtime_system="ATROPOS",
                runtime_run_id=(
                    "runtime-"
                    + uuid.uuid4().hex
                ),
            )

        @staticmethod
        def _contract_evidence(
            atom_id: str,
        ) -> dict[str, object]:
            return {
                "source_atom_ids": [
                    atom_id
                ],
                "acceptance_criteria": [
                    (
                        "The service preserves "
                        "source authority."
                    )
                ],
            }

        @staticmethod
        def _implementation_evidence(
            atom_id: str,
        ) -> dict[str, object]:
            return {
                "source_atom_ids": [
                    atom_id
                ],
                "changed_files": [
                    {
                        "path": (
                            "src/service.py"
                        ),
                        "sha256": "a" * 64,
                        "responsibility": (
                            "Implements source-authority "
                            "preservation."
                        ),
                    }
                ],
                "commands": [
                    {
                        "command": (
                            "python -m unittest"
                        ),
                        "exit_code": 0,
                        "stdout_sha256": (
                            "b" * 64
                        ),
                    }
                ],
                "diff_sha256": "c" * 64,
                "call_sites": [
                    "src/app.py:main"
                ],
                "reachability": [
                    "POST /v1/service"
                ],
                "rollback": {
                    "strategy": (
                        "Revert the implementation "
                        "commit."
                    ),
                    "recovery_command": (
                        "git revert HEAD"
                    ),
                },
            }

        @staticmethod
        def _verification_evidence(
            atom_id: str,
            implementation_receipt_id: str,
        ) -> dict[str, object]:
            return {
                "source_atom_ids": [
                    atom_id
                ],
                "commands": [
                    {
                        "command": (
                            "python -m unittest"
                        ),
                        "exit_code": 0,
                    }
                ],
                "tests": [
                    {
                        "name": (
                            "source authority "
                            "is preserved"
                        ),
                        "status": "PASSED",
                        "assertions": 3,
                    }
                ],
                "independent_verification": (
                    True
                ),
                "verified_receipt_ids": [
                    implementation_receipt_id
                ],
            }

        def _complete_contract(
            self,
            run_id: str,
            atom_id: str,
        ) -> dict[str, object]:
            claim = self.execution.claim_node(
                run_id,
                "contract-worker",
                lease_seconds=300,
            )

            self.assertEqual(
                claim["node"]["stage"],
                "CONTRACT",
            )

            return self.execution.submit_receipt(
                run_node_id=str(
                    claim["node"]["id"]
                ),
                worker_id="contract-worker",
                actor_system="ATROPOS",
                outcome="SUCCESS",
                summary=(
                    "Defined concrete acceptance "
                    "criteria for source authority."
                ),
                evidence=self._contract_evidence(
                    atom_id
                ),
            )

        def _complete_implementation(
            self,
            run_id: str,
            atom_id: str,
            worker: str = "builder",
        ) -> dict[str, object]:
            claim = self.execution.claim_node(
                run_id,
                worker,
                lease_seconds=300,
            )

            self.assertEqual(
                claim["node"]["stage"],
                "IMPLEMENTATION",
            )

            return self.execution.submit_receipt(
                run_node_id=str(
                    claim["node"]["id"]
                ),
                worker_id=worker,
                actor_system="ATROPOS",
                outcome="SUCCESS",
                summary=(
                    "Implemented source-authority "
                    "preservation with connected "
                    "runtime call sites."
                ),
                evidence=(
                    self._implementation_evidence(
                        atom_id
                    )
                ),
            )

        def _complete_verification(
            self,
            run_id: str,
            atom_id: str,
            implementation_receipt_id: str,
            worker: str = "verifier",
        ) -> dict[str, object]:
            claim = self.execution.claim_node(
                run_id,
                worker,
                lease_seconds=300,
            )

            self.assertEqual(
                claim["node"]["stage"],
                "VERIFICATION",
            )

            return self.execution.submit_receipt(
                run_node_id=str(
                    claim["node"]["id"]
                ),
                worker_id=worker,
                actor_system="ATROPOS",
                outcome="SUCCESS",
                summary=(
                    "Independently verified source "
                    "authority behavior with "
                    "assertion-bearing tests."
                ),
                evidence=(
                    self._verification_evidence(
                        atom_id,
                        implementation_receipt_id,
                    )
                ),
            )

        def test_valid_execution_flow(
            self,
        ) -> None:
            _, atom, plan = (
                self._project_and_plan(
                    resolved=True
                )
            )

            run = self._start(plan)
            run_id = str(run["id"])
            atom_id = str(atom["id"])

            self._complete_contract(
                run_id,
                atom_id,
            )

            implementation = (
                self._complete_implementation(
                    run_id,
                    atom_id,
                )
            )

            self.assertEqual(
                implementation[
                    "validation_status"
                ],
                "ACCEPTED",
            )

            verification = (
                self._complete_verification(
                    run_id,
                    atom_id,
                    str(
                        implementation["id"]
                    ),
                )
            )

            self.assertEqual(
                verification[
                    "validation_status"
                ],
                "ACCEPTED",
            )

            result = (
                self.execution.verify_run(
                    run_id
                )
            )

            self.assertTrue(
                result["valid"]
            )
            self.assertEqual(
                result["status"],
                "VERIFIED",
            )

        def test_empty_implementation_rejected(
            self,
        ) -> None:
            _, atom, plan = (
                self._project_and_plan(
                    resolved=True
                )
            )

            run = self._start(plan)
            run_id = str(run["id"])
            atom_id = str(atom["id"])

            self._complete_contract(
                run_id,
                atom_id,
            )

            claim = self.execution.claim_node(
                run_id,
                "builder",
                lease_seconds=300,
            )

            receipt = (
                self.execution.submit_receipt(
                    run_node_id=str(
                        claim["node"]["id"]
                    ),
                    worker_id="builder",
                    actor_system="ATROPOS",
                    outcome="SUCCESS",
                    summary=(
                        "Reported implementation "
                        "without concrete changes."
                    ),
                    evidence={
                        "source_atom_ids": [
                            atom_id
                        ]
                    },
                )
            )

            self.assertEqual(
                receipt[
                    "validation_status"
                ],
                "REJECTED",
            )

            codes = {
                finding["gate_code"]
                for finding
                in receipt["findings"]
            }

            self.assertIn(
                "NO_EMPTY_IMPLEMENTATION",
                codes,
            )
            self.assertIn(
                (
                    "NO_DISCONNECTED_PUBLIC_"
                    "COMPONENT"
                ),
                codes,
            )
            self.assertIn(
                "NO_UNREACHABLE_FEATURE",
                codes,
            )

        def test_self_verification_rejected(
            self,
        ) -> None:
            _, atom, plan = (
                self._project_and_plan(
                    resolved=True
                )
            )

            run = self._start(plan)
            run_id = str(run["id"])
            atom_id = str(atom["id"])

            self._complete_contract(
                run_id,
                atom_id,
            )

            implementation = (
                self._complete_implementation(
                    run_id,
                    atom_id,
                    worker="builder",
                )
            )

            verification = (
                self._complete_verification(
                    run_id,
                    atom_id,
                    str(
                        implementation["id"]
                    ),
                    worker="builder",
                )
            )

            self.assertEqual(
                verification[
                    "validation_status"
                ],
                "REJECTED",
            )

            codes = {
                finding["gate_code"]
                for finding
                in verification[
                    "findings"
                ]
            }

            self.assertIn(
                "NO_SELF_VERIFICATION",
                codes,
            )

        def test_unresearched_implementation_rejected(
            self,
        ) -> None:
            _, atom, plan = (
                self._project_and_plan(
                    resolved=False
                )
            )

            run = self._start(plan)
            run_id = str(run["id"])
            atom_id = str(atom["id"])

            self._complete_contract(
                run_id,
                atom_id,
            )

            receipt = (
                self._complete_implementation(
                    run_id,
                    atom_id,
                )
            )

            self.assertEqual(
                receipt[
                    "validation_status"
                ],
                "REJECTED",
            )

            codes = {
                finding["gate_code"]
                for finding
                in receipt["findings"]
            }

            self.assertIn(
                (
                    "NO_UNRESEARCHED_"
                    "IMPLEMENTATION"
                ),
                codes,
            )

        def test_tampering_invalidates_run(
            self,
        ) -> None:
            _, atom, plan = (
                self._project_and_plan(
                    resolved=True
                )
            )

            run = self._start(plan)
            run_id = str(run["id"])
            atom_id = str(atom["id"])

            self._complete_contract(
                run_id,
                atom_id,
            )

            implementation = (
                self._complete_implementation(
                    run_id,
                    atom_id,
                )
            )

            self._complete_verification(
                run_id,
                atom_id,
                str(implementation["id"]),
            )

            first = self.execution.verify_run(
                run_id
            )

            self.assertTrue(first["valid"])

            with self.database.connect() as connection:
                connection.execute(
                    """
                    UPDATE execution_receipts
                    SET evidence_json = ?
                    WHERE id = ?
                    """,
                    (
                        '{"tampered":true}',
                        implementation["id"],
                    ),
                )

            second = (
                self.execution.verify_run(
                    run_id
                )
            )

            self.assertFalse(
                second["valid"]
            )

            codes = {
                finding["gate_code"]
                for finding
                in second["findings"]
            }

            self.assertIn(
                "EVIDENCE_HASH_MISMATCH",
                codes,
            )


    if __name__ == "__main__":
        unittest.main()
    ''',
)

write(
    "supabase/migrations/20260712000700_execution.sql",
    r'''
    create table if not exists public.execution_runs (
        id uuid primary key default gen_random_uuid(),
        project_id uuid not null
            references public.projects(id)
            on delete cascade,
        plan_version_id uuid not null
            references public.plan_versions(id)
            on delete cascade,
        export_id uuid
            references public.exports(id)
            on delete set null,
        runtime_system text not null,
        runtime_run_id text not null,
        status text not null,
        input_fingerprint text not null,
        created_at timestamptz not null default now(),
        started_at timestamptz not null default now(),
        completed_at timestamptz,
        verified_at timestamptz,
        unique(
            runtime_system,
            runtime_run_id
        )
    );

    create table if not exists public.execution_run_nodes (
        id uuid primary key default gen_random_uuid(),
        run_id uuid not null
            references public.execution_runs(id)
            on delete cascade,
        graph_node_id uuid not null
            references public.graph_nodes(id)
            on delete cascade,
        atom_id uuid not null
            references public.atoms(id)
            on delete cascade,
        stage text not null,
        sequence_number bigint not null,
        title text not null,
        status text not null,
        lease_owner text,
        lease_expires_at timestamptz,
        attempt_count bigint not null default 0,
        accepted_receipt_id uuid,
        created_at timestamptz not null default now(),
        updated_at timestamptz not null default now(),
        unique(
            run_id,
            graph_node_id
        )
    );

    create table if not exists public.execution_attempts (
        id uuid primary key default gen_random_uuid(),
        run_node_id uuid not null
            references public.execution_run_nodes(id)
            on delete cascade,
        worker_id text not null,
        status text not null,
        lease_expires_at timestamptz not null,
        started_at timestamptz not null default now(),
        completed_at timestamptz,
        error_message text
    );

    create table if not exists public.execution_receipts (
        id uuid primary key default gen_random_uuid(),
        run_id uuid not null
            references public.execution_runs(id)
            on delete cascade,
        run_node_id uuid not null
            references public.execution_run_nodes(id)
            on delete cascade,
        attempt_id uuid not null
            references public.execution_attempts(id)
            on delete cascade,
        actor_system text not null,
        actor_id text not null,
        outcome text not null,
        summary text not null,
        evidence_json jsonb not null,
        evidence_sha256 text not null,
        validation_status text not null,
        created_at timestamptz not null default now(),
        unique(
            run_node_id,
            evidence_sha256
        )
    );

    create table if not exists public.execution_validation_findings (
        id uuid primary key default gen_random_uuid(),
        run_id uuid not null
            references public.execution_runs(id)
            on delete cascade,
        run_node_id uuid
            references public.execution_run_nodes(id)
            on delete cascade,
        receipt_id uuid
            references public.execution_receipts(id)
            on delete cascade,
        gate_code text not null,
        severity text not null,
        message text not null,
        created_at timestamptz not null default now()
    );

    create table if not exists public.execution_events (
        id uuid primary key default gen_random_uuid(),
        run_id uuid not null
            references public.execution_runs(id)
            on delete cascade,
        run_node_id uuid
            references public.execution_run_nodes(id)
            on delete cascade,
        event_type text not null,
        actor_id text,
        payload_json jsonb not null
            default '{}'::jsonb,
        created_at timestamptz not null default now()
    );

    create index if not exists idx_execution_runs_project
        on public.execution_runs(
            project_id,
            created_at
        );

    create index if not exists idx_execution_nodes_run
        on public.execution_run_nodes(
            run_id,
            status,
            sequence_number
        );

    create index if not exists idx_execution_receipts_node
        on public.execution_receipts(
            run_node_id,
            validation_status
        );

    alter table public.execution_runs
        enable row level security;

    alter table public.execution_run_nodes
        enable row level security;

    alter table public.execution_attempts
        enable row level security;

    alter table public.execution_receipts
        enable row level security;

    alter table public.execution_validation_findings
        enable row level security;

    alter table public.execution_events
        enable row level security;
    ''',
)

insert_after(
    "src/specgraph_foundry/api.py",
    "from .exports import ExportService\n",
    "from .execution import ExecutionService\n",
    "from .execution import ExecutionService",
)

insert_after(
    "src/specgraph_foundry/api.py",
    "        self.exports = ExportService(database)\n",
    "        self.execution = ExecutionService(database)\n",
    "self.execution = ExecutionService",
)

api_routes = r'''
            if (
                len(parts) == 4
                and parts[:2] == ["v1", "plans"]
                and parts[3] == "execution-runs"
                and method == "POST"
            ):
                export_value = payload.get(
                    "export_id"
                )

                return 201, self.execution.start_run(
                    plan_id=parts[2],
                    runtime_system=str(
                        payload.get(
                            "runtime_system",
                            "",
                        )
                    ),
                    runtime_run_id=str(
                        payload.get(
                            "runtime_run_id",
                            "",
                        )
                    ),
                    export_id=(
                        str(export_value)
                        if export_value
                        else None
                    ),
                )

            if (
                len(parts) == 4
                and parts[:2] == ["v1", "projects"]
                and parts[3] == "execution-runs"
                and method == "GET"
            ):
                return 200, {
                    "items": self.execution.list_runs(
                        parts[2]
                    )
                }

            if (
                len(parts) == 3
                and parts[:2] == ["v1", "execution-runs"]
                and method == "GET"
            ):
                return 200, self.execution.get_run(
                    parts[2]
                )

            if (
                len(parts) == 4
                and parts[:2] == ["v1", "execution-runs"]
                and parts[3] == "claim"
                and method == "POST"
            ):
                node_value = payload.get(
                    "run_node_id"
                )

                return 200, {
                    "claim": self.execution.claim_node(
                        run_id=parts[2],
                        worker_id=str(
                            payload.get(
                                "worker_id",
                                "",
                            )
                        ),
                        run_node_id=(
                            str(node_value)
                            if node_value
                            else None
                        ),
                        lease_seconds=int(
                            payload.get(
                                "lease_seconds",
                                900,
                            )
                        ),
                    )
                }

            if (
                len(parts) == 4
                and parts[:2] == ["v1", "execution-runs"]
                and parts[3] == "verify"
                and method == "POST"
            ):
                return 200, self.execution.verify_run(
                    parts[2]
                )

            if (
                len(parts) == 4
                and parts[:2] == ["v1", "execution-nodes"]
                and parts[3] == "heartbeat"
                and method == "POST"
            ):
                return 200, self.execution.heartbeat(
                    run_node_id=parts[2],
                    worker_id=str(
                        payload.get(
                            "worker_id",
                            "",
                        )
                    ),
                    lease_seconds=int(
                        payload.get(
                            "lease_seconds",
                            900,
                        )
                    ),
                )

            if (
                len(parts) == 4
                and parts[:2] == ["v1", "execution-nodes"]
                and parts[3] == "receipts"
                and method == "POST"
            ):
                evidence = payload.get(
                    "evidence",
                    {},
                )

                if not isinstance(evidence, dict):
                    raise ValidationError(
                        "evidence must be an object"
                    )

                return 201, self.execution.submit_receipt(
                    run_node_id=parts[2],
                    worker_id=str(
                        payload.get(
                            "worker_id",
                            "",
                        )
                    ),
                    actor_system=str(
                        payload.get(
                            "actor_system",
                            "",
                        )
                    ),
                    outcome=str(
                        payload.get(
                            "outcome",
                            "",
                        )
                    ),
                    summary=str(
                        payload.get(
                            "summary",
                            "",
                        )
                    ),
                    evidence=evidence,
                )

'''

insert_before(
    "src/specgraph_foundry/api.py",
    "            return 404, {\n",
    api_routes,
    'parts[3] == "execution-runs"',
)

insert_after(
    "src/specgraph_foundry/cli.py",
    "from .exports import ExportService\n",
    "from .execution import ExecutionService\n",
    "from .execution import ExecutionService",
)

cli_parsers = r'''
    start_execution = commands.add_parser(
        "start-execution"
    )
    start_execution.add_argument("plan_id")
    start_execution.add_argument(
        "runtime_system"
    )
    start_execution.add_argument(
        "runtime_run_id"
    )
    start_execution.add_argument(
        "--export-id"
    )

    execution_runs = commands.add_parser(
        "list-execution-runs"
    )
    execution_runs.add_argument(
        "project_id"
    )

    execution_run = commands.add_parser(
        "execution-run"
    )
    execution_run.add_argument("run_id")

    claim_execution = commands.add_parser(
        "claim-execution-node"
    )
    claim_execution.add_argument("run_id")
    claim_execution.add_argument(
        "worker_id"
    )
    claim_execution.add_argument(
        "--run-node-id"
    )
    claim_execution.add_argument(
        "--lease-seconds",
        type=int,
        default=900,
    )

    execution_heartbeat = commands.add_parser(
        "execution-heartbeat"
    )
    execution_heartbeat.add_argument(
        "run_node_id"
    )
    execution_heartbeat.add_argument(
        "worker_id"
    )
    execution_heartbeat.add_argument(
        "--lease-seconds",
        type=int,
        default=900,
    )

    submit_receipt = commands.add_parser(
        "submit-execution-receipt"
    )
    submit_receipt.add_argument(
        "run_node_id"
    )
    submit_receipt.add_argument(
        "worker_id"
    )
    submit_receipt.add_argument(
        "actor_system"
    )
    submit_receipt.add_argument("outcome")
    submit_receipt.add_argument("summary")
    submit_receipt.add_argument(
        "evidence_json"
    )

    verify_execution = commands.add_parser(
        "verify-execution-run"
    )
    verify_execution.add_argument("run_id")

'''

insert_before(
    "src/specgraph_foundry/cli.py",
    '    server = commands.add_parser("serve")\n',
    cli_parsers,
    '"start-execution"',
)

insert_after(
    "src/specgraph_foundry/cli.py",
    "    exports = ExportService(database)\n",
    "    execution = ExecutionService(database)\n",
    "execution = ExecutionService",
)

cli_commands = r'''
    if args.command == "start-execution":
        output(
            execution.start_run(
                plan_id=args.plan_id,
                runtime_system=(
                    args.runtime_system
                ),
                runtime_run_id=(
                    args.runtime_run_id
                ),
                export_id=args.export_id,
            )
        )
        return 0

    if args.command == "list-execution-runs":
        output(
            {
                "items": execution.list_runs(
                    args.project_id
                )
            }
        )
        return 0

    if args.command == "execution-run":
        output(
            execution.get_run(
                args.run_id
            )
        )
        return 0

    if args.command == "claim-execution-node":
        output(
            {
                "claim": execution.claim_node(
                    run_id=args.run_id,
                    worker_id=args.worker_id,
                    run_node_id=(
                        args.run_node_id
                    ),
                    lease_seconds=(
                        args.lease_seconds
                    ),
                )
            }
        )
        return 0

    if args.command == "execution-heartbeat":
        output(
            execution.heartbeat(
                run_node_id=(
                    args.run_node_id
                ),
                worker_id=args.worker_id,
                lease_seconds=(
                    args.lease_seconds
                ),
            )
        )
        return 0

    if args.command == "submit-execution-receipt":
        try:
            evidence = json.loads(
                args.evidence_json
            )
        except json.JSONDecodeError as error:
            raise SystemExit(
                "evidence_json must be valid JSON"
            ) from error

        if not isinstance(evidence, dict):
            raise SystemExit(
                "evidence_json must be "
                "a JSON object"
            )

        output(
            execution.submit_receipt(
                run_node_id=(
                    args.run_node_id
                ),
                worker_id=args.worker_id,
                actor_system=(
                    args.actor_system
                ),
                outcome=args.outcome,
                summary=args.summary,
                evidence=evidence,
            )
        )
        return 0

    if args.command == "verify-execution-run":
        output(
            execution.verify_run(
                args.run_id
            )
        )
        return 0

'''

insert_before(
    "src/specgraph_foundry/cli.py",
    "    suffix = uuid.uuid4().hex[:8]\n",
    cli_commands,
    'args.command == "start-execution"',
)

readme_path = ROOT / "README.md"
readme = readme_path.read_text(
    encoding="utf-8"
)

section = dedent(
    r'''

    ## Runtime receipts and independent completion gates

    SpecGraph Foundry does not trust a runtime system's
    success claim by itself. ATROPOS and other runtimes submit
    immutable execution receipts, which Foundry independently
    evaluates before completing a plan node.

    Enforced gates include:

    - `NO_EMPTY_IMPLEMENTATION`
    - `NO_CONSTANT_FAKE_RESULT`
    - `NO_DISCONNECTED_PUBLIC_COMPONENT`
    - `NO_UNREACHABLE_FEATURE`
    - `NO_MEANINGLESS_TEST`
    - `NO_SOURCELESS_REQUIREMENT`
    - `NO_UNRESEARCHED_IMPLEMENTATION`
    - `NO_SELF_VERIFICATION`
    - `NO_MIXED_FILE_RESPONSIBILITY`
    - `NO_UNJUSTIFIED_NOT_APPLICABLE_DIMENSION`

    Runtime node state is stored per execution run. The
    immutable execution DAG remains separate from operational
    claims, attempts, leases, receipts, findings, and events.

    ```bash
    python -m specgraph_foundry start-execution \
      PLAN_ID \
      ATROPOS \
      RUNTIME_RUN_ID

    python -m specgraph_foundry claim-execution-node \
      EXECUTION_RUN_ID \
      WORKER_ID

    python -m specgraph_foundry verify-execution-run \
      EXECUTION_RUN_ID
    ```
    '''
)

if (
    "## Runtime receipts and independent completion gates"
    not in readme
):
    readme_path.write_text(
        readme.rstrip()
        + "\n"
        + section.lstrip(),
        encoding="utf-8",
    )
    print("UPDATED README.md")

print("EXECUTION RECEIPT BACKEND CREATED")
