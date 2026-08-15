"""The idempotency record, claim result, and request hashing.

Key validation and the canonical request hash in particular: two requests are
"the same request" exactly when this function says so, which is the whole
contract an idempotency key rests on.
"""

from __future__ import annotations

from .models import ApiResponse
import hashlib
import json
import re
from dataclasses import dataclass
from datetime import UTC, datetime

from ..errors import ValidationError

MAX_REPLAY_BODY_BYTES = 5 * 1024 * 1024

MINIMUM_KEY_LENGTH = 16

MAXIMUM_KEY_LENGTH = 200

DEFAULT_TTL_SECONDS = 900

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
