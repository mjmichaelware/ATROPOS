from __future__ import annotations

import hashlib
import json
import sqlite3
import uuid
from dataclasses import dataclass
from datetime import UTC, datetime, timedelta

from ..database import Database
from ..errors import ConflictError, ValidationError
from .models import ApiResponse


MAX_REPLAY_BODY_BYTES = 5 * 1024 * 1024
MINIMUM_KEY_LENGTH = 16
MAXIMUM_KEY_LENGTH = 200
MAXIMUM_KEY_BYTES = 256
DEFAULT_TTL_SECONDS = 900
PLACEHOLDER_SIGNING_KEY = (
    "replace-with-a-long-random-secret"
)


def utc_now() -> datetime:
    return datetime.now(UTC)


def iso_now() -> str:
    return utc_now().isoformat()


def parse_time(value: str) -> datetime:
    parsed = datetime.fromisoformat(value)

    if parsed.tzinfo is None:
        parsed = parsed.replace(tzinfo=UTC)

    return parsed.astimezone(UTC)


def validate_idempotency_key(
    value: str | None,
) -> str:
    if value is None:
        raise ValidationError(
            "Idempotency-Key is required"
        )

    normalized = value.strip()

    if not normalized:
        raise ValidationError(
            "Idempotency-Key is required"
        )

    if len(normalized) < MINIMUM_KEY_LENGTH:
        raise ValidationError(
            "Idempotency-Key is too short"
        )

    if len(normalized) > MAXIMUM_KEY_LENGTH:
        raise ValidationError(
            "Idempotency-Key is too long"
        )

    for character in normalized:
        codepoint = ord(character)

        if codepoint < 0x21 or codepoint > 0x7E:
            raise ValidationError(
                "Idempotency-Key must use visible ASCII"
            )

    return normalized


def canonical_json_text(
    value: object,
) -> str:
    try:
        return json.dumps(
            value,
            sort_keys=True,
            separators=(",", ":"),
            ensure_ascii=False,
            allow_nan=False,
        )
    except (TypeError, ValueError) as error:
        raise ValidationError(
            "request payload must be valid JSON"
        ) from error


def canonical_request_hash(
    *,
    operation: str,
    owner_id: str,
    route_params: dict[str, object],
    payload: dict[str, object],
) -> str:
    encoded = canonical_json_text(
        {
            "operation": operation,
            "owner_id": owner_id,
            "route_params": route_params,
            "payload": payload,
        }
    ).encode("utf-8")

    return hashlib.sha256(encoded).hexdigest()


@dataclass(frozen=True)
class IdempotencyRecord:
    id: str
    owner_id: str
    operation: str
    key_hash: str
    request_hash: str
    state: str
    http_status: int | None
    response_body_json: str | None
    resource_type: str | None
    resource_id: str | None
    created_at: str
    updated_at: str
    expires_at: str

    @classmethod
    def from_row(
        cls,
        row: dict[str, object],
    ) -> "IdempotencyRecord":
        return cls(
            id=str(row["id"]),
            owner_id=str(row["owner_id"]),
            operation=str(row["operation"]),
            key_hash=str(
                row["idempotency_key_hash"]
            ),
            request_hash=str(
                row["canonical_request_hash"]
            ),
            state=str(row["state"]),
            http_status=(
                int(row["http_status"])
                if row["http_status"] is not None
                else None
            ),
            response_body_json=(
                str(row["response_body_json"])
                if row["response_body_json"]
                is not None
                else None
            ),
            resource_type=(
                str(row["resource_type"])
                if row["resource_type"] is not None
                else None
            ),
            resource_id=(
                str(row["resource_id"])
                if row["resource_id"] is not None
                else None
            ),
            created_at=str(row["created_at"]),
            updated_at=str(row["updated_at"]),
            expires_at=str(row["expires_at"]),
        )


@dataclass(frozen=True)
class ClaimResult:
    record: IdempotencyRecord
    replay: ApiResponse | None = None


class IdempotencyStore:
    def __init__(
        self,
        database: Database,
    ) -> None:
        self.database = database

    def claim(
        self,
        *,
        owner_id: str,
        operation: str,
        idempotency_key: str,
        request_hash: str,
        ttl_seconds: int = DEFAULT_TTL_SECONDS,
    ) -> ClaimResult:
        if ttl_seconds < 30:
            raise ValidationError(
                "idempotency TTL must be at least 30 seconds"
            )

        normalized_key = validate_idempotency_key(
            idempotency_key
        )
        key_hash = hashlib.sha256(
            normalized_key.encode("utf-8")
        ).hexdigest()
        now = utc_now()
        now_text = now.isoformat()
        expires_at = (
            now + timedelta(seconds=ttl_seconds)
        ).isoformat()

        while True:
            with self.database.connect() as connection:
                connection.execute("BEGIN IMMEDIATE")
                row = connection.execute(
                    """
                    SELECT *
                    FROM idempotency_records
                    WHERE owner_id = ?
                      AND operation = ?
                      AND idempotency_key_hash = ?
                    """,
                    (
                        owner_id,
                        operation,
                        key_hash,
                    ),
                ).fetchone()

                if row is None:
                    record_id = str(uuid.uuid4())

                    try:
                        connection.execute(
                            """
                            INSERT INTO idempotency_records(
                                id,
                                owner_id,
                                operation,
                                idempotency_key_hash,
                                canonical_request_hash,
                                state,
                                created_at,
                                updated_at,
                                expires_at
                            )
                            VALUES(?,?,?,?,?,?,?,?,?)
                            """,
                            (
                                record_id,
                                owner_id,
                                operation,
                                key_hash,
                                request_hash,
                                "IN_PROGRESS",
                                now_text,
                                now_text,
                                expires_at,
                            ),
                        )
                    except sqlite3.IntegrityError:
                        continue

                    created = connection.execute(
                        """
                        SELECT *
                        FROM idempotency_records
                        WHERE id = ?
                        """,
                        (record_id,),
                    ).fetchone()

                    if created is None:
                        continue

                    return ClaimResult(
                        record=IdempotencyRecord.from_row(
                            dict(created)
                        )
                    )

                record = IdempotencyRecord.from_row(
                    dict(row)
                )

                if record.request_hash != request_hash:
                    raise ConflictError(
                        "idempotency key cannot be reused for a different request"
                    )

                if record.state == "SUCCEEDED":
                    return ClaimResult(
                        record=record,
                        replay=self._replay_response(
                            record
                        ),
                    )

                expired = (
                    parse_time(record.expires_at)
                    <= now
                )

                if record.state == "IN_PROGRESS" and not expired:
                    raise ConflictError(
                        "idempotent request is already in progress"
                    )

                updated = connection.execute(
                    """
                    UPDATE idempotency_records
                    SET state = 'IN_PROGRESS',
                        http_status = NULL,
                        response_body_json = NULL,
                        resource_type = NULL,
                        resource_id = NULL,
                        updated_at = ?,
                        expires_at = ?
                    WHERE id = ?
                      AND (
                        state = 'FAILED'
                        OR expires_at <= ?
                      )
                    """,
                    (
                        now_text,
                        expires_at,
                        record.id,
                        now_text,
                    ),
                )

                if updated.rowcount != 1:
                    raise ConflictError(
                        "idempotent request is already in progress"
                    )

                refreshed = connection.execute(
                    """
                    SELECT *
                    FROM idempotency_records
                    WHERE id = ?
                    """,
                    (record.id,),
                ).fetchone()

                if refreshed is None:
                    continue

                return ClaimResult(
                    record=IdempotencyRecord.from_row(
                        dict(refreshed)
                    )
                )

    def mark_succeeded(
        self,
        *,
        record_id: str,
        http_status: int,
        response_body: dict[str, object],
        resource_type: str | None = None,
        resource_id: str | None = None,
        ttl_seconds: int = DEFAULT_TTL_SECONDS,
    ) -> None:
        body_json = canonical_json_text(
            response_body
        )

        if len(body_json.encode("utf-8")) > MAX_REPLAY_BODY_BYTES:
            raise ValidationError(
                "idempotent replay body exceeds the storage limit"
            )

        now = iso_now()
        expires_at = (
            utc_now() + timedelta(seconds=ttl_seconds)
        ).isoformat()

        with self.database.connect() as connection:
            connection.execute(
                """
                UPDATE idempotency_records
                SET state = 'SUCCEEDED',
                    http_status = ?,
                    response_body_json = ?,
                    resource_type = ?,
                    resource_id = ?,
                    updated_at = ?,
                    expires_at = ?
                WHERE id = ?
                """,
                (
                    http_status,
                    body_json,
                    resource_type,
                    resource_id,
                    now,
                    expires_at,
                    record_id,
                ),
            )

    def mark_failed(
        self,
        *,
        record_id: str,
        http_status: int,
        response_body: dict[str, object],
        ttl_seconds: int = 60,
    ) -> None:
        body_json = canonical_json_text(
            response_body
        )

        if len(body_json.encode("utf-8")) > MAX_REPLAY_BODY_BYTES:
            body_json = canonical_json_text({})

        now = iso_now()
        expires_at = (
            utc_now() + timedelta(seconds=ttl_seconds)
        ).isoformat()

        with self.database.connect() as connection:
            connection.execute(
                """
                UPDATE idempotency_records
                SET state = 'FAILED',
                    http_status = ?,
                    response_body_json = ?,
                    updated_at = ?,
                    expires_at = ?
                WHERE id = ?
                """,
                (
                    http_status,
                    body_json,
                    now,
                    expires_at,
                    record_id,
                ),
            )

    @staticmethod
    def _replay_response(
        record: IdempotencyRecord,
    ) -> ApiResponse:
        if (
            record.http_status is None
            or record.response_body_json is None
        ):
            raise ConflictError(
                "idempotent replay record is incomplete"
            )

        decoded = json.loads(
            record.response_body_json
        )

        if not isinstance(decoded, dict):
            raise ConflictError(
                "idempotent replay record is invalid"
            )

        return ApiResponse(
            status=record.http_status,
            body=decoded,
            headers={},
        )
