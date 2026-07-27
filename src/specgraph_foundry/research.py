import hashlib
import json
import sqlite3
import uuid
from datetime import UTC, datetime, timedelta

from .database import Database
from .errors import ConflictError, NotFoundError, ValidationError


RESEARCH_SCHEMA = """
PRAGMA foreign_keys = ON;

CREATE TABLE IF NOT EXISTS research_evidence (
    id TEXT PRIMARY KEY,
    project_id TEXT NOT NULL
        REFERENCES projects(id)
        ON DELETE CASCADE,
    task_id TEXT NOT NULL
        REFERENCES research_tasks(id)
        ON DELETE CASCADE,
    atom_id TEXT NOT NULL
        REFERENCES atoms(id)
        ON DELETE CASCADE,
    dimension TEXT NOT NULL,
    source_uri TEXT NOT NULL,
    source_title TEXT NOT NULL,
    publisher TEXT NOT NULL DEFAULT '',
    evidence_type TEXT NOT NULL,
    excerpt TEXT NOT NULL,
    content_sha256 TEXT NOT NULL,
    reliability REAL NOT NULL,
    retrieved_at TEXT NOT NULL,
    created_at TEXT NOT NULL,
    CHECK(reliability >= 0.0 AND reliability <= 1.0),
    UNIQUE(task_id, source_uri, content_sha256)
);

CREATE TABLE IF NOT EXISTS research_claims (
    id TEXT PRIMARY KEY,
    project_id TEXT NOT NULL
        REFERENCES projects(id)
        ON DELETE CASCADE,
    task_id TEXT NOT NULL UNIQUE
        REFERENCES research_tasks(id)
        ON DELETE CASCADE,
    atom_id TEXT NOT NULL
        REFERENCES atoms(id)
        ON DELETE CASCADE,
    dimension TEXT NOT NULL,
    conclusion TEXT NOT NULL,
    applicability TEXT NOT NULL,
    confidence REAL NOT NULL,
    status TEXT NOT NULL,
    created_at TEXT NOT NULL,
    updated_at TEXT NOT NULL,
    CHECK(confidence >= 0.0 AND confidence <= 1.0)
);

CREATE TABLE IF NOT EXISTS research_claim_evidence (
    claim_id TEXT NOT NULL
        REFERENCES research_claims(id)
        ON DELETE CASCADE,
    evidence_id TEXT NOT NULL
        REFERENCES research_evidence(id)
        ON DELETE CASCADE,
    PRIMARY KEY(claim_id, evidence_id)
);

CREATE TABLE IF NOT EXISTS research_task_events (
    id TEXT PRIMARY KEY,
    task_id TEXT NOT NULL
        REFERENCES research_tasks(id)
        ON DELETE CASCADE,
    event_type TEXT NOT NULL,
    worker_id TEXT,
    payload_json TEXT NOT NULL DEFAULT '{}',
    created_at TEXT NOT NULL
);

CREATE INDEX IF NOT EXISTS idx_research_evidence_task
    ON research_evidence(task_id, created_at);

CREATE INDEX IF NOT EXISTS idx_research_claims_atom
    ON research_claims(atom_id, dimension);

CREATE INDEX IF NOT EXISTS idx_research_events_task
    ON research_task_events(task_id, created_at);
"""


def utc_now() -> datetime:
    return datetime.now(UTC)


def iso_now() -> str:
    return utc_now().isoformat()


def new_id(prefix: str) -> str:
    return str(uuid.uuid4())


def parse_time(value: str) -> datetime:
    parsed = datetime.fromisoformat(value)

    if parsed.tzinfo is None:
        parsed = parsed.replace(tzinfo=UTC)

    return parsed.astimezone(UTC)


class ResearchService:
    EVIDENCE_TYPES = {
        "OFFICIAL_DOCUMENTATION",
        "PRIMARY_SOURCE",
        "RESEARCH_PAPER",
        "STANDARD",
        "LEGAL_AUTHORITY",
        "SOURCE_CODE",
        "TEST_RESULT",
        "USER_DECISION",
        "OTHER",
    }

    APPLICABILITY = {
        "APPLICABLE",
        "NOT_APPLICABLE",
    }

    def __init__(self, database: Database) -> None:
        self.database = database
        self.ensure_schema()

    def ensure_schema(self) -> None:
        with self.database.connect() as connection:
            connection.executescript(RESEARCH_SCHEMA)

    def claim_task(
        self,
        project_id: str,
        worker_id: str,
        lease_seconds: int = 900,
    ) -> dict[str, object] | None:
        worker_id = worker_id.strip()

        if not worker_id:
            raise ValidationError("worker_id is required")

        if lease_seconds < 30:
            raise ValidationError(
                "lease_seconds must be at least 30"
            )

        now = utc_now()
        expiration = (
            now + timedelta(seconds=lease_seconds)
        ).isoformat()

        with self.database.connect() as connection:
            connection.execute("BEGIN IMMEDIATE")

            project = connection.execute(
                "SELECT id FROM projects WHERE id = ?",
                (project_id,),
            ).fetchone()

            if project is None:
                raise NotFoundError(
                    f"project not found: {project_id}"
                )

            expired = connection.execute(
                """
                SELECT id
                FROM research_tasks
                WHERE project_id = ?
                  AND status = 'CLAIMED'
                  AND lease_expires_at IS NOT NULL
                  AND lease_expires_at <= ?
                """,
                (project_id, now.isoformat()),
            ).fetchall()

            for row in expired:
                task_id = str(row["id"])

                # WHERE id = ? alone isn't enough: on PostgreSQL (where
                # BEGIN IMMEDIATE is just a plain BEGIN, see below) a second
                # worker's UPDATE here can block on this row, then resume
                # after a third worker has already reclaimed *and*
                # re-claimed it as CLAIMED, and blindly stomp that fresh
                # claim back to PENDING since id-only matches regardless of
                # the row's current state. Re-checking status/expiration in
                # the WHERE clause makes this a no-op once another
                # transaction has already reclaimed the same row, and the
                # rowcount check below skips emitting a misleading
                # LEASE_EXPIRED event for a reclaim that didn't happen.
                cursor = connection.execute(
                    """
                    UPDATE research_tasks
                    SET status = 'PENDING',
                        lease_owner = NULL,
                        lease_expires_at = NULL,
                        updated_at = ?
                    WHERE id = ?
                      AND status = 'CLAIMED'
                      AND lease_expires_at IS NOT NULL
                      AND lease_expires_at <= ?
                    """,
                    (now.isoformat(), task_id, now.isoformat()),
                )

                if cursor.rowcount > 0:
                    self._event(
                        connection,
                        task_id,
                        "LEASE_EXPIRED",
                        None,
                        {},
                    )

            # BEGIN IMMEDIATE gives SQLite an upfront write lock, so a plain
            # SELECT here is already safe against concurrent claimers. On
            # PostgreSQL, database.py downgrades BEGIN IMMEDIATE to a plain
            # BEGIN (no such lock mode exists there) - without FOR UPDATE
            # SKIP LOCKED, two concurrent transactions can both select the
            # same PENDING row before either commits its UPDATE, and both
            # believe they claimed it. SKIP LOCKED is a no-op under SQLite's
            # single-writer model but is invalid SQLite syntax, so it can
            # only be sent on the PostgreSQL path.
            lock_clause = " FOR UPDATE SKIP LOCKED" if self.database.is_postgres else ""
            task = connection.execute(
                """
                SELECT *
                FROM research_tasks
                WHERE project_id = ?
                  AND status = 'PENDING'
                ORDER BY priority, created_at, id
                LIMIT 1
                """
                + lock_clause,
                (project_id,),
            ).fetchone()

            if task is None:
                return None

            task_id = str(task["id"])

            connection.execute(
                """
                UPDATE research_tasks
                SET status = 'CLAIMED',
                    lease_owner = ?,
                    lease_expires_at = ?,
                    attempt_count = attempt_count + 1,
                    updated_at = ?
                WHERE id = ?
                """,
                (
                    worker_id,
                    expiration,
                    now.isoformat(),
                    task_id,
                ),
            )

            self._event(
                connection,
                task_id,
                "CLAIMED",
                worker_id,
                {
                    "lease_seconds": lease_seconds,
                    "lease_expires_at": expiration,
                },
            )

        return self.get_task(task_id)

    def heartbeat(
        self,
        task_id: str,
        worker_id: str,
        lease_seconds: int = 900,
    ) -> dict[str, object]:
        if lease_seconds < 30:
            raise ValidationError(
                "lease_seconds must be at least 30"
            )

        expiration = (
            utc_now() + timedelta(seconds=lease_seconds)
        ).isoformat()

        with self.database.connect() as connection:
            self._require_lease(
                connection,
                task_id,
                worker_id,
            )

            connection.execute(
                """
                UPDATE research_tasks
                SET lease_expires_at = ?,
                    updated_at = ?
                WHERE id = ?
                """,
                (expiration, iso_now(), task_id),
            )

            self._event(
                connection,
                task_id,
                "HEARTBEAT",
                worker_id,
                {"lease_expires_at": expiration},
            )

        return self.get_task(task_id)

    def add_evidence(
        self,
        task_id: str,
        worker_id: str,
        source_uri: str,
        source_title: str,
        excerpt: str,
        publisher: str = "",
        evidence_type: str = "OTHER",
        reliability: float = 0.5,
    ) -> dict[str, object]:
        source_uri = source_uri.strip()
        source_title = source_title.strip()
        excerpt = excerpt.strip()
        evidence_type = evidence_type.strip().upper()

        if not source_uri:
            raise ValidationError("source_uri is required")

        if not source_title:
            raise ValidationError("source_title is required")

        if not excerpt:
            raise ValidationError("excerpt is required")

        if evidence_type not in self.EVIDENCE_TYPES:
            raise ValidationError(
                f"invalid evidence type: {evidence_type}"
            )

        if not 0.0 <= reliability <= 1.0:
            raise ValidationError(
                "reliability must be between 0 and 1"
            )

        evidence_id = new_id("evidence")
        digest = hashlib.sha256(
            excerpt.encode("utf-8")
        ).hexdigest()
        timestamp = iso_now()

        try:
            with self.database.connect() as connection:
                task = self._require_lease(
                    connection,
                    task_id,
                    worker_id,
                )

                connection.execute(
                    """
                    INSERT INTO research_evidence(
                        id,
                        project_id,
                        task_id,
                        atom_id,
                        dimension,
                        source_uri,
                        source_title,
                        publisher,
                        evidence_type,
                        excerpt,
                        content_sha256,
                        reliability,
                        retrieved_at,
                        created_at
                    )
                    VALUES(?,?,?,?,?,?,?,?,?,?,?,?,?,?)
                    """,
                    (
                        evidence_id,
                        task["project_id"],
                        task_id,
                        task["atom_id"],
                        task["dimension"],
                        source_uri,
                        source_title,
                        publisher.strip(),
                        evidence_type,
                        excerpt,
                        digest,
                        reliability,
                        timestamp,
                        timestamp,
                    ),
                )

                self._event(
                    connection,
                    task_id,
                    "EVIDENCE_ADDED",
                    worker_id,
                    {
                        "evidence_id": evidence_id,
                        "source_uri": source_uri,
                    },
                )

        except sqlite3.IntegrityError as error:
            raise ConflictError(
                "identical evidence already exists"
            ) from error

        return self.get_evidence(evidence_id)

    def complete_task(
        self,
        task_id: str,
        worker_id: str,
        conclusion: str,
        applicability: str,
        confidence: float,
        evidence_ids: list[str],
    ) -> dict[str, object]:
        conclusion = conclusion.strip()
        applicability = applicability.strip().upper()
        evidence_ids = list(
            dict.fromkeys(
                item.strip()
                for item in evidence_ids
                if item.strip()
            )
        )

        if not conclusion:
            raise ValidationError("conclusion is required")

        if applicability not in self.APPLICABILITY:
            raise ValidationError(
                "invalid applicability"
            )

        if not 0.0 <= confidence <= 1.0:
            raise ValidationError(
                "confidence must be between 0 and 1"
            )

        if not evidence_ids:
            raise ValidationError(
                "at least one evidence item is required"
            )

        timestamp = iso_now()
        claim_id = new_id("claim")

        with self.database.connect() as connection:
            task = self._require_lease(
                connection,
                task_id,
                worker_id,
            )

            placeholders = ",".join(
                "?" for _ in evidence_ids
            )

            evidence = connection.execute(
                f"""
                SELECT id
                FROM research_evidence
                WHERE task_id = ?
                  AND id IN ({placeholders})
                """,
                (task_id, *evidence_ids),
            ).fetchall()

            found = {
                str(row["id"])
                for row in evidence
            }

            if found != set(evidence_ids):
                raise ValidationError(
                    "evidence must belong to the task"
                )

            connection.execute(
                """
                INSERT INTO research_claims(
                    id,
                    project_id,
                    task_id,
                    atom_id,
                    dimension,
                    conclusion,
                    applicability,
                    confidence,
                    status,
                    created_at,
                    updated_at
                )
                VALUES(?,?,?,?,?,?,?,?,?,?,?)
                """,
                (
                    claim_id,
                    task["project_id"],
                    task_id,
                    task["atom_id"],
                    task["dimension"],
                    conclusion,
                    applicability,
                    confidence,
                    "ACCEPTED",
                    timestamp,
                    timestamp,
                ),
            )

            for evidence_id in evidence_ids:
                connection.execute(
                    """
                    INSERT INTO research_claim_evidence(
                        claim_id,
                        evidence_id
                    )
                    VALUES(?,?)
                    """,
                    (claim_id, evidence_id),
                )

            dimension_status = (
                "RESOLVED"
                if applicability == "APPLICABLE"
                else "NOT_APPLICABLE"
            )

            connection.execute(
                """
                UPDATE atom_dimensions
                SET applicability = ?,
                    status = ?,
                    rationale = ?,
                    updated_at = ?
                WHERE atom_id = ?
                  AND dimension = ?
                """,
                (
                    applicability,
                    dimension_status,
                    conclusion,
                    timestamp,
                    task["atom_id"],
                    task["dimension"],
                ),
            )

            result = {
                "claim_id": claim_id,
                "conclusion": conclusion,
                "applicability": applicability,
                "confidence": confidence,
                "evidence_ids": evidence_ids,
            }

            connection.execute(
                """
                UPDATE research_tasks
                SET status = 'COMPLETE',
                    lease_owner = NULL,
                    lease_expires_at = NULL,
                    result_json = ?,
                    updated_at = ?
                WHERE id = ?
                """,
                (
                    json.dumps(result, sort_keys=True),
                    timestamp,
                    task_id,
                ),
            )

            self._event(
                connection,
                task_id,
                "COMPLETED",
                worker_id,
                result,
            )

        return self.get_task(task_id)

    def fail_task(
        self,
        task_id: str,
        worker_id: str,
        error_message: str,
        retryable: bool = True,
    ) -> dict[str, object]:
        error_message = error_message.strip()

        if not error_message:
            raise ValidationError(
                "error_message is required"
            )

        status = "PENDING" if retryable else "FAILED"
        result = {
            "error": error_message,
            "retryable": retryable,
        }

        with self.database.connect() as connection:
            self._require_lease(
                connection,
                task_id,
                worker_id,
            )

            connection.execute(
                """
                UPDATE research_tasks
                SET status = ?,
                    lease_owner = NULL,
                    lease_expires_at = NULL,
                    result_json = ?,
                    updated_at = ?
                WHERE id = ?
                """,
                (
                    status,
                    json.dumps(result, sort_keys=True),
                    iso_now(),
                    task_id,
                ),
            )

            self._event(
                connection,
                task_id,
                "FAILED",
                worker_id,
                result,
            )

        return self.get_task(task_id)

    def get_task(
        self,
        task_id: str,
    ) -> dict[str, object]:
        with self.database.connect() as connection:
            task = connection.execute(
                """
                SELECT
                    task.*,
                    atom.canonical_statement,
                    atom.kind,
                    atom.modality
                FROM research_tasks AS task
                JOIN atoms AS atom
                  ON atom.id = task.atom_id
                WHERE task.id = ?
                """,
                (task_id,),
            ).fetchone()

            if task is None:
                raise NotFoundError(
                    f"research task not found: {task_id}"
                )

            evidence = connection.execute(
                """
                SELECT *
                FROM research_evidence
                WHERE task_id = ?
                ORDER BY created_at, id
                """,
                (task_id,),
            ).fetchall()

            claim = connection.execute(
                """
                SELECT *
                FROM research_claims
                WHERE task_id = ?
                """,
                (task_id,),
            ).fetchone()

            events = connection.execute(
                """
                SELECT *
                FROM research_task_events
                WHERE task_id = ?
                ORDER BY created_at, id
                """,
                (task_id,),
            ).fetchall()

        result = dict(task)
        result_json = result.pop("result_json", None)
        result["result"] = (
            json.loads(str(result_json))
            if result_json
            else None
        )
        result["evidence"] = [
            dict(row) for row in evidence
        ]
        result["claim"] = (
            dict(claim)
            if claim is not None
            else None
        )
        result["events"] = [
            self._normalize_event(dict(row))
            for row in events
        ]

        return result

    def get_evidence(
        self,
        evidence_id: str,
    ) -> dict[str, object]:
        with self.database.connect() as connection:
            row = connection.execute(
                """
                SELECT *
                FROM research_evidence
                WHERE id = ?
                """,
                (evidence_id,),
            ).fetchone()

        if row is None:
            raise NotFoundError(
                f"evidence not found: {evidence_id}"
            )

        return dict(row)

    def gap_matrix(
        self,
        project_id: str,
    ) -> dict[str, object]:
        with self.database.connect() as connection:
            project = connection.execute(
                """
                SELECT id, slug, name
                FROM projects
                WHERE id = ?
                """,
                (project_id,),
            ).fetchone()

            if project is None:
                raise NotFoundError(
                    f"project not found: {project_id}"
                )

            atoms = connection.execute(
                """
                SELECT
                    id,
                    document_id,
                    ordinal,
                    kind,
                    modality,
                    canonical_statement
                FROM atoms
                WHERE project_id = ?
                ORDER BY document_id, ordinal, id
                """,
                (project_id,),
            ).fetchall()

            dimensions = connection.execute(
                """
                SELECT
                    dimensions.atom_id,
                    dimensions.dimension,
                    dimensions.applicability,
                    dimensions.status,
                    dimensions.rationale,
                    tasks.id AS task_id,
                    tasks.status AS task_status
                FROM atom_dimensions AS dimensions
                JOIN atoms
                  ON atoms.id = dimensions.atom_id
                LEFT JOIN research_tasks AS tasks
                  ON tasks.atom_id = dimensions.atom_id
                 AND tasks.dimension = dimensions.dimension
                WHERE atoms.project_id = ?
                ORDER BY
                    dimensions.atom_id,
                    dimensions.dimension
                """,
                (project_id,),
            ).fetchall()

        grouped: dict[
            str,
            list[dict[str, object]],
        ] = {}

        for row in dimensions:
            grouped.setdefault(
                str(row["atom_id"]),
                [],
            ).append(dict(row))

        atom_results = []
        total = 0
        resolved = 0
        not_applicable = 0
        open_count = 0
        ready_atoms = 0

        for atom_row in atoms:
            atom = dict(atom_row)
            atom_dimensions = grouped.get(
                str(atom["id"]),
                [],
            )

            atom_resolved = sum(
                item["status"] == "RESOLVED"
                for item in atom_dimensions
            )

            atom_not_applicable = sum(
                item["status"] == "NOT_APPLICABLE"
                for item in atom_dimensions
            )

            atom_open = sum(
                item["status"] == "OPEN"
                for item in atom_dimensions
            )

            atom_ready = (
                bool(atom_dimensions)
                and atom_open == 0
            )

            if atom_ready:
                ready_atoms += 1

            total += len(atom_dimensions)
            resolved += atom_resolved
            not_applicable += atom_not_applicable
            open_count += atom_open

            atom["dimensions"] = atom_dimensions
            atom["ready"] = atom_ready
            atom["open_dimensions"] = atom_open
            atom_results.append(atom)

        atom_count = len(atom_results)

        return {
            "project": dict(project),
            "summary": {
                "atom_count": atom_count,
                "ready_atoms": ready_atoms,
                "blocked_atoms": atom_count - ready_atoms,
                "total_dimensions": total,
                "resolved_dimensions": resolved,
                "not_applicable_dimensions": (
                    not_applicable
                ),
                "open_dimensions": open_count,
                "ready": (
                    atom_count > 0
                    and ready_atoms == atom_count
                ),
            },
            "atoms": atom_results,
        }

    def _require_lease(
        self,
        connection: sqlite3.Connection,
        task_id: str,
        worker_id: str,
    ) -> sqlite3.Row:
        task = connection.execute(
            """
            SELECT *
            FROM research_tasks
            WHERE id = ?
            """,
            (task_id,),
        ).fetchone()

        if task is None:
            raise NotFoundError(
                f"research task not found: {task_id}"
            )

        if task["status"] != "CLAIMED":
            raise ConflictError(
                "research task is not claimed"
            )

        if task["lease_owner"] != worker_id:
            raise ConflictError(
                "research task belongs to another worker"
            )

        expiration = task["lease_expires_at"]

        if (
            expiration is None
            or parse_time(str(expiration)) <= utc_now()
        ):
            raise ConflictError(
                "research task lease has expired"
            )

        return task

    @staticmethod
    def _event(
        connection: sqlite3.Connection,
        task_id: str,
        event_type: str,
        worker_id: str | None,
        payload: dict[str, object],
    ) -> None:
        connection.execute(
            """
            INSERT INTO research_task_events(
                id,
                task_id,
                event_type,
                worker_id,
                payload_json,
                created_at
            )
            VALUES(?,?,?,?,?,?)
            """,
            (
                new_id("research-event"),
                task_id,
                event_type,
                worker_id,
                json.dumps(payload, sort_keys=True),
                iso_now(),
            ),
        )

    @staticmethod
    def _normalize_event(
        event: dict[str, object],
    ) -> dict[str, object]:
        payload = event.pop("payload_json", "{}")
        event["payload"] = json.loads(str(payload))
        return event
