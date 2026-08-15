"""Storage result shapes, typed failures, and the response size limits.

Five failure types rather than one: a missing object, an oversized one, a
dependency outage and a protocol violation each mean something different to the
caller, and only one of them is worth retrying.
"""

from __future__ import annotations

from dataclasses import dataclass
from datetime import UTC, datetime

def _utc_now() -> datetime:
    return datetime.now(UTC)

MAX_JSON_RESPONSE_BYTES = 64 * 1024


class StorageProtocolError(RuntimeError):
    pass


class StorageDependencyError(RuntimeError):
    pass


class StoragePermanentError(RuntimeError):
    """Raised when storage rejects an upload with a 4xx response.

    4xx responses (auth failures, RLS violations, bad requests) indicate
    a configuration or protocol problem that will not resolve on retry.
    """
    pass


class StorageObjectMissingError(RuntimeError):
    pass


class StorageObjectTooLargeError(RuntimeError):
    pass


@dataclass(frozen=True)
class SignedUploadTarget:
    url: str
    expires_at: str
    required_headers: dict[str, str]


@dataclass(frozen=True)
class SignedDownloadTarget:
    url: str
    expires_at: str


@dataclass(frozen=True)
class DownloadedObject:
    data: bytes
    media_type: str | None
