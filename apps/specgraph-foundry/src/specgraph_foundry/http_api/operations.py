from __future__ import annotations

import hashlib
import hmac
import json
import secrets
import socket
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


TERMINAL_STATES = {
    "SUCCEEDED",
    "FAILED",
    "CANCELLED",
    "TIMED_OUT",
}

ACTIVE_STATES = {
    "QUEUED",
    "CLAIMED",
    "RUNNING",
    "CANCEL_REQUESTED",
}

MAX_JSON_BYTES = 64 * 1024
MAX_ERROR_MESSAGE = 240


def utc_now() -> datetime:
    return datetime.now(UTC)


def iso_now() -> str:
    return utc_now().isoformat()


def parse_time(value: str) -> datetime:
    parsed = datetime.fromisoformat(value)
    if parsed.tzinfo is None:
        parsed = parsed.replace(tzinfo=UTC)
    return parsed.astimezone(UTC)


def canonical_json_text(value: object) -> str:
    return json.dumps(
        value,
        sort_keys=True,
        separators=(",", ":"),
        ensure_ascii=False,
        allow_nan=False,
    )


def safe_json(value: object) -> str:
    text = canonical_json_text(value)
    if len(text.encode("utf-8")) > MAX_JSON_BYTES:
        raise ValidationError("operation payload exceeds the safe limit")
    return text


def operation_fingerprint(
    *,
    owner_id: str,
    operation_type: str,
    request: dict[str, object],
) -> str:
    return hashlib.sha256(
        canonical_json_text(
            {
                "owner_id": owner_id,
                "operation_type": operation_type,
                "request": request,
            }
        ).encode("utf-8")
    ).hexdigest()


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


@dataclass(frozen=True)
class OperationLease:
    operation: dict[str, object]
    worker_id: str
    lease_token: str


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
        fingerprint = operation_fingerprint(
            owner_id=owner_id,
            operation_type=operation_type,
            request=request,
        )
        now = utc_now()
        now_text = now.isoformat()
        timeout_at = (
            now + timedelta(seconds=self.settings.timeout_seconds)
        ).isoformat()
        operation_id = str(uuid.uuid4())

        with self.database.connect() as connection:
            connection.execute("BEGIN IMMEDIATE")
            existing = connection.execute(
                """
                SELECT *
                FROM operations
                WHERE owner_id = ?
                  AND operation_type = ?
                  AND fingerprint = ?
                """,
                (owner_id, operation_type, fingerprint),
            ).fetchone()
            if existing is not None:
                return self._public(dict(existing))

            connection.execute(
                """
                INSERT INTO operations(
                    id,
                    owner_id,
                    project_id,
                    operation_type,
                    fingerprint,
                    state,
                    phase,
                    progress_current,
                    progress_total,
                    attempt_count,
                    max_attempts,
                    next_attempt_at,
                    timeout_at,
                    request_json,
                    created_at,
                    updated_at
                )
                VALUES(?,?,?,?,?,?,?,?,?,?,?,?,?,?,?,?)
                """,
                (
                    operation_id,
                    owner_id,
                    project_id,
                    operation_type,
                    fingerprint,
                    "QUEUED",
                    "queued",
                    0,
                    1,
                    0,
                    self.settings.max_attempts,
                    now_text,
                    timeout_at,
                    safe_json(request),
                    now_text,
                    now_text,
                ),
            )
            row = connection.execute(
                "SELECT * FROM operations WHERE id = ?",
                (operation_id,),
            ).fetchone()
        return self._public(dict(row))

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
        request = parse_pagination_query(raw_path)
        scope = CursorScope(
            collection="operations",
            owner_id=owner_id,
            parent_id=project_id,
        )
        boundary = None
        if request.cursor is not None:
            boundary = CursorCodec(self.cursor_signing_key).decode(
                request.cursor,
                scope=scope,
            )

        parameters: list[object] = [owner_id, project_id]
        clause = ""
        if boundary is not None:
            clause = """
              AND (
                    created_at < ?
                    OR (
                        created_at = ?
                        AND id < ?
                    )
                  )
            """
            parameters.extend(
                [
                    str(boundary["created_at"]),
                    str(boundary["created_at"]),
                    str(boundary["id"]),
                ]
            )
        parameters.append(request.limit + 1)
        with self.database.connect() as connection:
            rows = connection.execute(
                f"""
                SELECT *
                FROM operations
                WHERE owner_id = ?
                  AND project_id = ?
                {clause}
                ORDER BY created_at DESC, id DESC
                LIMIT ?
                """,
                tuple(parameters),
            ).fetchall()

        has_more = len(rows) > request.limit
        selected = [dict(row) for row in rows[: request.limit]]
        next_cursor = None
        if has_more and selected:
            last = selected[-1]
            next_cursor = CursorCodec(self.cursor_signing_key).encode(
                scope,
                {
                    "created_at": last["created_at"],
                    "id": last["id"],
                },
            )
        return (
            [self._public(row) for row in selected],
            pagination_headers(
                limit=request.limit,
                count=len(selected),
                has_more=has_more,
                next_cursor=next_cursor,
            ),
        )

    def cancel(
        self,
        *,
        owner_id: str,
        operation_id: str,
    ) -> dict[str, object]:
        now_text = iso_now()
        with self.database.connect() as connection:
            connection.execute("BEGIN IMMEDIATE")
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
            state = str(row["state"])
            if state in TERMINAL_STATES:
                return self._public(dict(row))
            if state == "QUEUED":
                next_state = "CANCELLED"
                finished_at = now_text
            else:
                next_state = "CANCEL_REQUESTED"
                finished_at = row["finished_at"]
            updated = connection.execute(
                """
                UPDATE operations
                SET state = ?,
                    cancel_requested_at = ?,
                    finished_at = ?,
                    updated_at = ?
                WHERE id = ?
                  AND owner_id = ?
                  AND state = ?
                """,
                (
                    next_state,
                    now_text,
                    finished_at,
                    now_text,
                    operation_id,
                    owner_id,
                    state,
                ),
            )
            if updated.rowcount != 1:
                raise ConflictError("operation state changed")
            row = connection.execute(
                "SELECT * FROM operations WHERE id = ?",
                (operation_id,),
            ).fetchone()
        return self._public(dict(row))

    def claim(
        self,
        *,
        worker_id: str,
    ) -> OperationLease | None:
        self.recover_expired()
        now = utc_now()
        now_text = now.isoformat()
        lease_token = secrets.token_urlsafe(32)
        token_hash = self.hash_lease_token(lease_token)
        lease_expires = (
            now + timedelta(seconds=self.settings.lease_seconds)
        ).isoformat()

        with self.database.connect() as connection:
            connection.execute("BEGIN IMMEDIATE")
            row = connection.execute(
                """
                SELECT *
                FROM operations
                WHERE state = 'QUEUED'
                  AND next_attempt_at <= ?
                  AND timeout_at > ?
                ORDER BY next_attempt_at, created_at, id
                LIMIT 1
                """,
                (now_text, now_text),
            ).fetchone()
            if row is None:
                return None
            updated = connection.execute(
                """
                UPDATE operations
                SET state = 'CLAIMED',
                    worker_id = ?,
                    lease_token_hash = ?,
                    lease_expires_at = ?,
                    heartbeat_at = ?,
                    attempt_count = attempt_count + 1,
                    updated_at = ?
                WHERE id = ?
                  AND state = 'QUEUED'
                """,
                (
                    worker_id,
                    token_hash,
                    lease_expires,
                    now_text,
                    now_text,
                    row["id"],
                ),
            )
            if updated.rowcount != 1:
                return None
            claimed = connection.execute(
                "SELECT * FROM operations WHERE id = ?",
                (row["id"],),
            ).fetchone()
        return OperationLease(
            operation=dict(claimed),
            worker_id=worker_id,
            lease_token=lease_token,
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
            },
        )

    def heartbeat(
        self,
        lease: OperationLease,
    ) -> dict[str, object]:
        return self._leased_update(
            lease,
            expected_states={"CLAIMED", "RUNNING", "CANCEL_REQUESTED"},
            assignments={
                "heartbeat_at": iso_now(),
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
        if current < 0 or total < 1 or current > total:
            raise ValidationError("operation progress is invalid")
        row = self._leased_update(
            lease,
            expected_states={"RUNNING", "CANCEL_REQUESTED"},
            assignments={
                "phase": phase,
                "progress_current": current,
                "progress_total": total,
                "heartbeat_at": iso_now(),
            },
        )
        if str(row["state"]) == "CANCEL_REQUESTED":
            raise OperationCancelled("operation cancellation requested")
        return row

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
        row = self._lease_row(lease)
        now = utc_now()
        if retryable and int(row["attempt_count"]) < int(row["max_attempts"]):
            delay = min(
                self.settings.retry_base_seconds
                * (2 ** max(0, int(row["attempt_count"]) - 1)),
                300,
            )
            return self._leased_update(
                lease,
                expected_states={"CLAIMED", "RUNNING", "CANCEL_REQUESTED"},
                assignments={
                    "state": "QUEUED",
                    "phase": "retry_scheduled",
                    "worker_id": None,
                    "lease_token_hash": None,
                    "lease_expires_at": None,
                    "heartbeat_at": None,
                    "next_attempt_at": (
                        now + timedelta(seconds=delay)
                    ).isoformat(),
                    "error_code": code,
                    "error_message": sanitize_message(message),
                },
            )
        return self._leased_update(
            lease,
            expected_states={"CLAIMED", "RUNNING", "CANCEL_REQUESTED"},
            assignments={
                "state": "FAILED",
                "phase": "failed",
                "error_code": code,
                "error_message": sanitize_message(message),
                "finished_at": now.isoformat(),
            },
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
        now = utc_now()
        now_text = now.isoformat()
        with self.database.connect() as connection:
            connection.execute("BEGIN IMMEDIATE")
            rows = connection.execute(
                """
                SELECT *
                FROM operations
                WHERE state IN ('CLAIMED','RUNNING','CANCEL_REQUESTED','QUEUED')
                  AND (
                    timeout_at <= ?
                    OR (
                        lease_expires_at IS NOT NULL
                        AND lease_expires_at <= ?
                    )
                  )
                ORDER BY created_at, id
                """,
                (now_text, now_text),
            ).fetchall()
            for row in rows:
                state = str(row["state"])
                if parse_time(str(row["timeout_at"])) <= now:
                    connection.execute(
                        """
                        UPDATE operations
                        SET state = 'TIMED_OUT',
                            phase = 'timed_out',
                            finished_at = ?,
                            updated_at = ?
                        WHERE id = ?
                          AND state = ?
                        """,
                        (now_text, now_text, row["id"], state),
                    )
                    continue
                if state == "CANCEL_REQUESTED":
                    connection.execute(
                        """
                        UPDATE operations
                        SET state = 'CANCELLED',
                            phase = 'cancelled',
                            finished_at = ?,
                            updated_at = ?
                        WHERE id = ?
                          AND state = 'CANCEL_REQUESTED'
                        """,
                        (now_text, now_text, row["id"]),
                    )
                elif int(row["attempt_count"]) < int(row["max_attempts"]):
                    delay = min(
                        self.settings.retry_base_seconds
                        * (2 ** max(0, int(row["attempt_count"]) - 1)),
                        300,
                    )
                    connection.execute(
                        """
                        UPDATE operations
                        SET state = 'QUEUED',
                            phase = 'lease_expired',
                            worker_id = NULL,
                            lease_token_hash = NULL,
                            lease_expires_at = NULL,
                            heartbeat_at = NULL,
                            next_attempt_at = ?,
                            updated_at = ?
                        WHERE id = ?
                          AND state IN ('CLAIMED','RUNNING')
                        """,
                        (
                            (now + timedelta(seconds=delay)).isoformat(),
                            now_text,
                            row["id"],
                        ),
                    )
                else:
                    connection.execute(
                        """
                        UPDATE operations
                        SET state = 'FAILED',
                            phase = 'attempts_exhausted',
                            error_code = 'OPERATION_IN_PROGRESS',
                            error_message = 'operation lease expired',
                            finished_at = ?,
                            updated_at = ?
                        WHERE id = ?
                          AND state IN ('CLAIMED','RUNNING')
                        """,
                        (now_text, now_text, row["id"]),
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
        row = self._lease_row(lease)
        if str(row["state"]) not in expected_states:
            raise ConflictError("operation state does not allow this transition")

        now = utc_now()
        if row["lease_expires_at"] is None or parse_time(
            str(row["lease_expires_at"])
        ) <= now:
            raise WorkerLeaseLost("worker lease expired")

        assignments = dict(assignments)
        assignments["updated_at"] = now.isoformat()
        columns = ", ".join(f"{key} = ?" for key in assignments)
        values = list(assignments.values())
        values.extend(
            [
                row["id"],
                lease.worker_id,
                self.hash_lease_token(lease.lease_token),
                str(row["state"]),
            ]
        )
        with self.database.connect() as connection:
            updated = connection.execute(
                f"""
                UPDATE operations
                SET {columns}
                WHERE id = ?
                  AND worker_id = ?
                  AND lease_token_hash = ?
                  AND state = ?
                """,
                tuple(values),
            )
            if updated.rowcount != 1:
                raise WorkerLeaseLost("worker lease is no longer current")
            stored = connection.execute(
                "SELECT * FROM operations WHERE id = ?",
                (row["id"],),
            ).fetchone()
        return dict(stored)

    def _lease_row(
        self,
        lease: OperationLease,
    ) -> dict[str, object]:
        with self.database.connect() as connection:
            row = connection.execute(
                """
                SELECT *
                FROM operations
                WHERE id = ?
                  AND worker_id = ?
                  AND lease_token_hash = ?
                """,
                (
                    lease.operation["id"],
                    lease.worker_id,
                    self.hash_lease_token(lease.lease_token),
                ),
            ).fetchone()
        if row is None:
            raise WorkerLeaseLost("worker lease is not current")
        return dict(row)

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
        result = {
            "id": str(row["id"]),
            "project_id": str(row["project_id"]),
            "operation_type": str(row["operation_type"]),
            "state": str(row["state"]),
            "phase": str(row["phase"]),
            "progress_current": int(row["progress_current"]),
            "progress_total": int(row["progress_total"]),
            "attempt_count": int(row["attempt_count"]),
            "max_attempts": int(row["max_attempts"]),
            "created_at": str(row["created_at"]),
            "updated_at": str(row["updated_at"]),
            "timeout_at": str(row["timeout_at"]),
            "started_at": row["started_at"],
            "finished_at": row["finished_at"],
            "cancel_requested_at": row["cancel_requested_at"],
        }
        if row["result_json"] is not None:
            result["result"] = json.loads(str(row["result_json"]))
        if row["error_code"] is not None:
            result["error_code"] = str(row["error_code"])
            result["error_message"] = str(row["error_message"] or "")
        return result


class OperationCancelled(RuntimeError):
    pass


class WorkerLeaseLost(RuntimeError):
    pass


def sanitize_message(message: str) -> str:
    safe = " ".join(str(message).split())
    if not safe:
        return "operation failed"
    return safe[:MAX_ERROR_MESSAGE]


def default_worker_id() -> str:
    return f"{socket.gethostname()}-{uuid.uuid4().hex[:12]}"


def operation_location(operation: dict[str, object]) -> str:
    return f"/v1/operations/{operation['id']}"


def is_operation_path(raw_path: str) -> bool:
    return urlparse(raw_path).path.startswith("/v1/operations")
