"""Claiming an idempotency key.

169 lines because it is the whole decision: is this key new, a replay of the
same request, a reuse with a different payload, or a claim someone else still
holds. Each answer is a different HTTP status.
"""

from __future__ import annotations

from .idempotency_models import ClaimResult
from .idempotency_models import IdempotencyRecord
from .idempotency_models import validate_idempotency_key
from .operation_models import parse_time
from .operation_models import utc_now
from .idempotency_models import *  # noqa: F401,F403
from ..errors import ConflictError, ValidationError
from .idempotency_outcomes import replay_response
from datetime import timedelta
import hashlib
import sqlite3
import uuid
import json


def claim(
    store,
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
        with store.database.connect() as connection:
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
                    replay=replay_response(
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
