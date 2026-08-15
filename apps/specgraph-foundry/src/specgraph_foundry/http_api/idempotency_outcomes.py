"""Recording how a claimed request finished, and replaying it.

What makes the second identical request cheap: the stored response is returned
rather than the work being done twice.
"""

from __future__ import annotations

from .idempotency_models import IdempotencyRecord
from .operation_models import iso_now
from .operation_models import utc_now
from .operations import canonical_json_text
from .idempotency_models import *  # noqa: F401,F403
from ..errors import ConflictError, ValidationError
from .models import ApiResponse
from datetime import timedelta
import json


def mark_succeeded(
    store,
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

    with store.database.connect() as connection:
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
    store,
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

    with store.database.connect() as connection:
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


def replay_response(
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
