from __future__ import annotations

from .operation_models import *  # noqa: F401,F403 - re-exported

from .operation_submission import cancel, submit
from .operation_worker import claim, fail, lease_row, leased_update, progress, recover_expired, renewed_lease_expiry
from .operation_queries import list_project, public

import hashlib
import hmac
import json
import secrets
import socket
import sqlite3
import uuid
from dataclasses import dataclass
from datetime import UTC, datetime, timedelta
from urllib.parse import urlparse

from ..database import Database
from ..errors import ConflictError, NotFoundError, ValidationError
from .pagination import (
    CursorCodec,
    CursorScope,
    pagination_headers,
    parse_pagination_query,
)



ACTIVE_STATES = {
    "QUEUED",
    "CLAIMED",
    "RUNNING",
    "CANCEL_REQUESTED",
}









def canonical_json_text(value: object) -> str:
    return json.dumps(
        value,
        sort_keys=True,
        separators=(",", ":"),
        ensure_ascii=False,
        allow_nan=False,
    )






@dataclass(frozen=True)
class OperationSettings:
    lease_seconds: int = 60
    heartbeat_seconds: int = 15
    max_attempts: int = 3
    retry_base_seconds: int = 5
    timeout_seconds: int = 1800
    poll_seconds: int = 2

    def __post_init__(self) -> None:
        if not 15 <= self.lease_seconds <= 900:
            raise ValueError("operation lease seconds must be between 15 and 900")
        if not 1 <= self.heartbeat_seconds < self.lease_seconds:
            raise ValueError("operation heartbeat seconds must be below lease")
        if not 1 <= self.max_attempts <= 10:
            raise ValueError("operation max attempts must be between 1 and 10")
        if not 1 <= self.retry_base_seconds <= 300:
            raise ValueError("operation retry base seconds must be between 1 and 300")
        if not 30 <= self.timeout_seconds <= 7200:
            raise ValueError("operation timeout seconds must be between 30 and 7200")
        if not 1 <= self.poll_seconds <= 30:
            raise ValueError("operation poll seconds must be between 1 and 30")




class OperationStore:
    def __init__(
        self,
        database: Database,
        settings: OperationSettings | None = None,
        *,
        cursor_signing_key: str | None = None,
    ) -> None:
        self.database = database
        self.settings = settings or OperationSettings()
        self.cursor_signing_key = cursor_signing_key

    def submit(
        self,
        *,
        owner_id: str,
        project_id: str,
        operation_type: str,
        request: dict[str, object],
    ) -> dict[str, object]:
        """Delegates to :func:`operation_submission.submit`."""
        return submit(
            self,
            owner_id=owner_id,
            project_id=project_id,
            operation_type=operation_type,
            request=request,
        )


    def get(
        self,
        *,
        owner_id: str,
        operation_id: str,
    ) -> dict[str, object]:
        row = self._row_for_owner(owner_id, operation_id)
        return self._public(row)

    def list_project(
        self,
        *,
        owner_id: str,
        project_id: str,
        raw_path: str,
    ) -> tuple[list[dict[str, object]], dict[str, str]]:
        """Delegates to :func:`operation_queries.list_project`."""
        return list_project(
            self,
            owner_id=owner_id,
            project_id=project_id,
            raw_path=raw_path,
        )


    def cancel(
        self,
        *,
        owner_id: str,
        operation_id: str,
    ) -> dict[str, object]:
        """Delegates to :func:`operation_submission.cancel`."""
        return cancel(
            self,
            owner_id=owner_id,
            operation_id=operation_id,
        )


    def claim(
        self,
        *,
        worker_id: str,
    ) -> OperationLease | None:
        """Delegates to :func:`operation_worker.claim`."""
        return claim(
            self,
            worker_id=worker_id,
        )



    def start(
        self,
        lease: OperationLease,
        *,
        phase: str,
        total: int,
    ) -> dict[str, object]:
        return self._leased_update(
            lease,
            expected_states={"CLAIMED"},
            assignments={
                "state": "RUNNING",
                "phase": phase,
                "progress_current": 0,
                "progress_total": max(1, total),
                "started_at": iso_now(),
                "lease_expires_at": renewed_lease_expiry(self),
            },
        )

    def heartbeat(
        self,
        lease: OperationLease,
    ) -> dict[str, object]:
        # _leased_update() rejects any call once lease_expires_at has
        # passed - only claim() ever set that field before this fix, so
        # heartbeat() updated heartbeat_at (an observability timestamp)
        # without ever actually extending the deadline it's meant to keep
        # alive. Any handler whose real work took longer than
        # lease_seconds (e.g. export_plan uploading many artifacts to
        # Supabase Storage) would hit WorkerLeaseLost on its next
        # checkpoint call no matter how often it heartbeat.
        return self._leased_update(
            lease,
            expected_states={"CLAIMED", "RUNNING", "CANCEL_REQUESTED"},
            assignments={
                "heartbeat_at": iso_now(),
                "lease_expires_at": renewed_lease_expiry(self),
            },
        )

    def progress(
        self,
        lease: OperationLease,
        *,
        phase: str,
        current: int,
        total: int,
    ) -> dict[str, object]:
        """Delegates to :func:`operation_worker.progress`."""
        return progress(
            self,
            lease,
            phase=phase,
            current=current,
            total=total,
        )


    def succeed(
        self,
        lease: OperationLease,
        *,
        result: dict[str, object],
    ) -> dict[str, object]:
        return self._leased_update(
            lease,
            expected_states={"RUNNING", "CANCEL_REQUESTED"},
            assignments={
                "state": "SUCCEEDED",
                "phase": "succeeded",
                "progress_current": 1,
                "progress_total": 1,
                "result_json": safe_json(result),
                "finished_at": iso_now(),
            },
        )

    def fail(
        self,
        lease: OperationLease,
        *,
        code: str,
        message: str,
        retryable: bool,
    ) -> dict[str, object]:
        """Delegates to :func:`operation_worker.fail`."""
        return fail(
            self,
            lease,
            code=code,
            message=message,
            retryable=retryable,
        )


    def mark_cancelled(
        self,
        lease: OperationLease,
    ) -> dict[str, object]:
        return self._leased_update(
            lease,
            expected_states={"CLAIMED", "RUNNING", "CANCEL_REQUESTED"},
            assignments={
                "state": "CANCELLED",
                "phase": "cancelled",
                "finished_at": iso_now(),
            },
        )

    def recover_expired(self) -> None:
        """Delegates to :func:`operation_worker.recover_expired`."""
        return recover_expired(
            self,
        )


    def request_from_operation(
        self,
        row: dict[str, object],
    ) -> dict[str, object]:
        decoded = json.loads(str(row["request_json"]))
        if not isinstance(decoded, dict):
            raise ValidationError("operation request is invalid")
        return decoded

    def _leased_update(
        self,
        lease: OperationLease,
        *,
        expected_states: set[str],
        assignments: dict[str, object],
    ) -> dict[str, object]:
        # Read, validate, and write inside a single connection so that no
        # concurrent recover_expired() call can sneak between the SELECT
        # and the UPDATE and clear our lease fields while we're holding
        # an apparently-valid row in memory.
        """Delegates to :func:`operation_worker.leased_update`."""
        return leased_update(
            self,
            lease,
            expected_states=expected_states,
            assignments=assignments,
        )


    def _lease_row(
        self,
        lease: OperationLease,
    ) -> dict[str, object]:
        """Delegates to :func:`operation_worker.lease_row`."""
        return lease_row(
            self,
            lease,
        )


    def _row_for_owner(
        self,
        owner_id: str,
        operation_id: str,
    ) -> dict[str, object]:
        with self.database.connect() as connection:
            row = connection.execute(
                """
                SELECT *
                FROM operations
                WHERE id = ?
                  AND owner_id = ?
                """,
                (operation_id, owner_id),
            ).fetchone()
        if row is None:
            raise NotFoundError(f"operation not found: {operation_id}")
        return dict(row)

    @staticmethod
    def hash_lease_token(token: str) -> str:
        return hashlib.sha256(token.encode("utf-8")).hexdigest()

    @staticmethod
    def _public(row: dict[str, object]) -> dict[str, object]:
        """Delegates to :func:`operation_queries.public`."""
        return public(
            row,
        )









def default_worker_id() -> str:
    return f"{socket.gethostname()}-{uuid.uuid4().hex[:12]}"


def operation_location(operation: dict[str, object]) -> str:
    return f"/v1/operations/{operation['id']}"


def is_operation_path(raw_path: str) -> bool:
    return urlparse(raw_path).path.startswith("/v1/operations")
