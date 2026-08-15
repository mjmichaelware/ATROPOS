"""Operation vocabulary, lease shape, and the small shared helpers.

Terminal states, the lease record, the two control exceptions, and the time and
JSON helpers every operation module needs. They belong to none of the three
sides -- client, worker, reader -- so they live here rather than in whichever
one happened to be written first.
"""

from __future__ import annotations

from ..errors import ValidationError
MAX_JSON_BYTES = 14 * 1024 * 1024
MAX_ERROR_MESSAGE = 240
from .idempotency import canonical_json_text
import hashlib
import json
import re
import uuid
from dataclasses import dataclass
from datetime import UTC, datetime, timedelta

TERMINAL_STATES = {
    "SUCCEEDED",
    "FAILED",
    "CANCELLED",
    "TIMED_OUT",
}


def utc_now() -> datetime:
    return datetime.now(UTC)


def iso_now() -> str:
    return utc_now().isoformat()


def parse_time(value: str) -> datetime:
    parsed = datetime.fromisoformat(value)
    if parsed.tzinfo is None:
        parsed = parsed.replace(tzinfo=UTC)
    return parsed.astimezone(UTC)


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
class OperationLease:
    operation: dict[str, object]
    worker_id: str
    lease_token: str


class OperationCancelled(RuntimeError):
    pass


class WorkerLeaseLost(RuntimeError):
    pass


def sanitize_message(message: str) -> str:
    safe = " ".join(str(message).split())
    if not safe:
        return "operation failed"
    return safe[:MAX_ERROR_MESSAGE]
