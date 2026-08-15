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
from .node_claims import claim_node, heartbeat
from .receipt_submission import submit_receipt
from .run_lifecycle import STAGES, start_run
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
        """Delegates to :func:`run_lifecycle.start_run`."""
        return start_run(
            self.database,
            self.planning,
            self.exports,
            plan_id,
            runtime_system,
            runtime_run_id,
            export_id,
        )


    def claim_node(
        self,
        run_id: str,
        worker_id: str,
        run_node_id: str | None = None,
        lease_seconds: int = 900,
    ) -> dict[str, object] | None:
        """Delegates to :func:`node_claims.claim_node`."""
        return claim_node(
            self.database,
            run_id,
            worker_id,
            run_node_id,
            lease_seconds,
        )


    def heartbeat(
        self,
        run_node_id: str,
        worker_id: str,
        lease_seconds: int = 900,
    ) -> dict[str, object]:
        """Delegates to :func:`node_claims.heartbeat`."""
        return heartbeat(
            self.database,
            run_node_id,
            worker_id,
            lease_seconds,
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
        """Delegates to :func:`receipt_submission.submit_receipt`."""
        return submit_receipt(
            self.database,
            run_node_id,
            worker_id,
            actor_system,
            outcome,
            summary,
            evidence,
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

