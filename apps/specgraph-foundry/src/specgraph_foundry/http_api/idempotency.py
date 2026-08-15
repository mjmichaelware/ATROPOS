from __future__ import annotations

from .idempotency_models import ClaimResult
from .idempotency_models import IdempotencyRecord
from .idempotency_models import *  # noqa: F401,F403 - re-exported  # noqa: F401,F403
from .idempotency_claims import claim
from .idempotency_outcomes import mark_failed, mark_succeeded, replay_response

import hashlib
import json
import sqlite3
import uuid
from dataclasses import dataclass
from datetime import UTC, datetime, timedelta

from ..database import Database
from ..errors import ConflictError, ValidationError
from .models import ApiResponse


MAXIMUM_KEY_BYTES = 256
PLACEHOLDER_SIGNING_KEY = (
    "replace-with-a-long-random-secret"
)


















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
        """Delegates to :func:`idempotency_claims.claim`."""
        return claim(
            self,
            owner_id=owner_id,
            operation=operation,
            idempotency_key=idempotency_key,
            request_hash=request_hash,
            ttl_seconds=ttl_seconds,
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
        """Delegates to :func:`idempotency_outcomes.mark_succeeded`."""
        return mark_succeeded(
            self,
            record_id=record_id,
            http_status=http_status,
            response_body=response_body,
            resource_type=resource_type,
            resource_id=resource_id,
            ttl_seconds=ttl_seconds,
        )


    def mark_failed(
        self,
        *,
        record_id: str,
        http_status: int,
        response_body: dict[str, object],
        ttl_seconds: int = 60,
    ) -> None:
        """Delegates to :func:`idempotency_outcomes.mark_failed`."""
        return mark_failed(
            self,
            record_id=record_id,
            http_status=http_status,
            response_body=response_body,
            ttl_seconds=ttl_seconds,
        )


    @staticmethod
    def _replay_response(
        record: IdempotencyRecord,
    ) -> ApiResponse:
        """Delegates to :func:`idempotency_outcomes.replay_response`."""
        return replay_response(
            record,
        )

