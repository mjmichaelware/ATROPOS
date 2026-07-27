from __future__ import annotations

import base64
import binascii
import hashlib
import hmac
import json
from dataclasses import dataclass
from urllib.parse import parse_qsl, urlparse

from ..errors import ValidationError


DEFAULT_PAGE_LIMIT = 50
MAX_PAGE_LIMIT = 100
WORKSPACE_PREVIEW_LIMIT = 5
CURSOR_VERSION = 1
MAX_CURSOR_LENGTH = 512
PLACEHOLDER_SIGNING_KEY = (
    "replace-with-a-long-random-secret"
)
MIN_SIGNING_KEY_LENGTH = 32


@dataclass(frozen=True)
class PaginationRequest:
    limit: int = DEFAULT_PAGE_LIMIT
    cursor: str | None = None


@dataclass(frozen=True)
class CursorScope:
    collection: str
    owner_id: str
    parent_id: str


@dataclass(frozen=True)
class PageWindow:
    items: list[dict[str, object]]
    has_more: bool
    boundary: dict[str, object] | None


class CursorCodec:
    def __init__(
        self,
        signing_key: str | None,
    ) -> None:
        self._key = self._normalize_key(
            signing_key
        )

    @staticmethod
    def _normalize_key(
        signing_key: str | None,
    ) -> bytes:
        value = (
            signing_key.strip()
            if signing_key is not None
            else ""
        )

        if (
            not value
            or value
            == PLACEHOLDER_SIGNING_KEY
            or len(value) < MIN_SIGNING_KEY_LENGTH
        ):
            raise RuntimeError(
                "cursor signing key is unavailable"
            )

        return value.encode("utf-8")

    def encode(
        self,
        scope: CursorScope,
        boundary: dict[str, object],
    ) -> str:
        payload = {
            "v": CURSOR_VERSION,
            "c": scope.collection,
            "o": scope.owner_id,
            "p": scope.parent_id,
            "s": boundary,
        }
        serialized = json.dumps(
            payload,
            sort_keys=True,
            separators=(",", ":"),
        ).encode("utf-8")
        signature = hmac.new(
            self._key,
            serialized,
            hashlib.sha256,
        ).digest()
        return ".".join(
            (
                _urlsafe_b64encode(serialized),
                _urlsafe_b64encode(signature),
            )
        )

    def decode(
        self,
        cursor: str,
        *,
        scope: CursorScope,
    ) -> dict[str, object]:
        if not cursor.strip():
            raise ValidationError(
                "cursor must not be blank"
            )

        if len(cursor) > MAX_CURSOR_LENGTH:
            raise ValidationError(
                "cursor is invalid"
            )

        try:
            payload_part, signature_part = (
                cursor.split(".", 1)
            )
            serialized = _urlsafe_b64decode(
                payload_part
            )
            signature = _urlsafe_b64decode(
                signature_part
            )
        except (
            ValueError,
            binascii.Error,
        ):
            raise ValidationError(
                "cursor is invalid"
            ) from None

        expected = hmac.new(
            self._key,
            serialized,
            hashlib.sha256,
        ).digest()

        if not hmac.compare_digest(
            expected,
            signature,
        ):
            raise ValidationError(
                "cursor is invalid"
            )

        try:
            payload = json.loads(
                serialized.decode("utf-8")
            )
        except (
            UnicodeDecodeError,
            json.JSONDecodeError,
        ):
            raise ValidationError(
                "cursor is invalid"
            ) from None

        if not isinstance(payload, dict):
            raise ValidationError(
                "cursor is invalid"
            )

        if payload.get("v") != CURSOR_VERSION:
            raise ValidationError(
                "cursor is invalid"
            )

        if (
            payload.get("c") != scope.collection
            or payload.get("o") != scope.owner_id
            or payload.get("p") != scope.parent_id
        ):
            raise ValidationError(
                "cursor is invalid"
            )

        boundary = payload.get("s")

        if not isinstance(boundary, dict):
            raise ValidationError(
                "cursor is invalid"
            )

        return boundary


def parse_pagination_query(
    raw_path: str,
) -> PaginationRequest:
    query = urlparse(raw_path).query

    if not query:
        return PaginationRequest()

    try:
        pairs = parse_qsl(
            query,
            keep_blank_values=True,
            strict_parsing=False,
            encoding="utf-8",
            errors="strict",
        )
    except ValueError as error:
        raise ValidationError(
            "query string is invalid"
        ) from error

    values: dict[str, str] = {}

    for key, value in pairs:
        if key not in {"limit", "cursor"}:
            raise ValidationError(
                f"unsupported query parameter: {key}"
            )

        if key in values:
            raise ValidationError(
                f"duplicate query parameter: {key}"
            )

        values[key] = value

    limit = DEFAULT_PAGE_LIMIT

    if "limit" in values:
        raw_limit = values["limit"]

        if not raw_limit:
            raise ValidationError(
                "limit must be an integer"
            )

        try:
            limit = int(raw_limit)
        except ValueError as error:
            raise ValidationError(
                "limit must be an integer"
            ) from error

        if limit < 1:
            raise ValidationError(
                "limit must be at least 1"
            )

        if limit > MAX_PAGE_LIMIT:
            raise ValidationError(
                "limit must be at most 100"
            )

    cursor = values.get("cursor")

    if cursor is not None:
        if not cursor:
            raise ValidationError(
                "cursor must not be blank"
            )

        if len(cursor) > MAX_CURSOR_LENGTH:
            raise ValidationError(
                "cursor is invalid"
            )

    return PaginationRequest(
        limit=limit,
        cursor=cursor,
    )


def pagination_headers(
    *,
    limit: int,
    count: int,
    has_more: bool,
    next_cursor: str | None,
) -> dict[str, str]:
    headers = {
        "x-page-limit": str(limit),
        "x-page-count": str(count),
        "x-has-more": (
            "true" if has_more else "false"
        ),
    }

    if next_cursor is not None:
        headers["x-next-cursor"] = next_cursor

    return headers


def _urlsafe_b64encode(
    value: bytes,
) -> str:
    return (
        base64.urlsafe_b64encode(value)
        .decode("ascii")
        .rstrip("=")
    )


def _urlsafe_b64decode(
    value: str,
) -> bytes:
    padding = "=" * (-len(value) % 4)
    return base64.urlsafe_b64decode(
        value + padding
    )
