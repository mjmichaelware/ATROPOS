import hashlib
import json
import sqlite3
import uuid
from datetime import UTC, datetime, timedelta

from .database import Database
from .research_claims import claim_task, heartbeat
from .research_completion import complete_task, fail_task
from .research_evidence import add_evidence, get_evidence
from .research_events import normalize_research_event, record_research_event
from .research_leases import require_lease
from .research_queries import gap_matrix, get_task
from .research_schema import RESEARCH_SCHEMA
from .errors import ConflictError, NotFoundError, ValidationError




def utc_now_datetime() -> datetime:
    return datetime.now(UTC)




def new_id(prefix: str) -> str:
    return str(uuid.uuid4())


def parse_time(value: str) -> datetime:
    parsed = datetime.fromisoformat(value)

    if parsed.tzinfo is None:
        parsed = parsed.replace(tzinfo=UTC)

    return parsed.astimezone(UTC)


class ResearchService:


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
        """Delegates to :func:`research_claims.claim_task`."""
        return claim_task(
            self.database,
            project_id,
            worker_id,
            lease_seconds,
        )


    def heartbeat(
        self,
        task_id: str,
        worker_id: str,
        lease_seconds: int = 900,
    ) -> dict[str, object]:
        """Delegates to :func:`research_claims.heartbeat`."""
        return heartbeat(
            self.database,
            task_id,
            worker_id,
            lease_seconds,
        )


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
        """Delegates to :func:`research_evidence.add_evidence`."""
        return add_evidence(
            self.database,
            task_id,
            worker_id,
            source_uri,
            source_title,
            excerpt,
            publisher,
            evidence_type,
            reliability,
        )


    def complete_task(
        self,
        task_id: str,
        worker_id: str,
        conclusion: str,
        applicability: str,
        confidence: float,
        evidence_ids: list[str],
    ) -> dict[str, object]:
        """Delegates to :func:`research_completion.complete_task`."""
        return complete_task(
            self.database,
            task_id,
            worker_id,
            conclusion,
            applicability,
            confidence,
            evidence_ids,
        )


    def fail_task(
        self,
        task_id: str,
        worker_id: str,
        error_message: str,
        retryable: bool = True,
    ) -> dict[str, object]:
        """Delegates to :func:`research_completion.fail_task`."""
        return fail_task(
            self.database,
            task_id,
            worker_id,
            error_message,
            retryable,
        )


    def get_task(
        self,
        task_id: str,
    ) -> dict[str, object]:
        """Delegates to :func:`research_queries.get_task`."""
        return get_task(
            self.database,
            task_id,
        )


    def get_evidence(
        self,
        evidence_id: str,
    ) -> dict[str, object]:
        """Delegates to :func:`research_evidence.get_evidence`."""
        return get_evidence(
            self.database,
            evidence_id,
        )


    def gap_matrix(
        self,
        project_id: str,
    ) -> dict[str, object]:
        """Delegates to :func:`research_queries.gap_matrix`."""
        return gap_matrix(
            self.database,
            project_id,
        )


    def _require_lease(
        self,
        connection: sqlite3.Connection,
        task_id: str,
        worker_id: str,
    ) -> sqlite3.Row:
        """Delegates to :func:`research_leases.require_lease`."""
        return require_lease(
            connection,
            task_id,
            worker_id,
        )


    @staticmethod
    def _event(
        connection: sqlite3.Connection,
        task_id: str,
        event_type: str,
        worker_id: str | None,
        payload: dict[str, object],
    ) -> None:
        """Delegates to :func:`research_events.event`."""
        return event(
            connection,
            task_id,
            event_type,
            worker_id,
            payload,
        )


    @staticmethod
    def _normalize_event(
        event: dict[str, object],
    ) -> dict[str, object]:
        """Delegates to :func:`research_events.normalize_event`."""
        return normalize_research_event(
            event,
        )

