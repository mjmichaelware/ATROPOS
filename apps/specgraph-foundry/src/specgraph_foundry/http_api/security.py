from __future__ import annotations

import hashlib
import re
import threading
import time
from collections import OrderedDict
from dataclasses import dataclass, field
from urllib.parse import unquote_to_bytes, urlsplit

from ..errors import ValidationError
from .resource_limits import ResourceLimitSettings


class SecurityRejection(ValidationError):
    def __init__(self, code: str, message: str, status: int, retry_after: int | None = None) -> None:
        super().__init__(message)
        self.code = code
        self.status = status
        self.retry_after = retry_after


@dataclass(frozen=True)
class SecuritySettings:
    allowed_hosts: tuple[str, ...] = ("127.0.0.1", "localhost")
    rate_limit_enabled: bool = True
    rate_limit_requests: int = 120
    rate_limit_window_seconds: int = 60
    max_limiter_entries: int = 4096

    def __post_init__(self) -> None:
        if self.rate_limit_requests < 1:
            raise ValueError("rate limit requests must be positive")
        if self.rate_limit_window_seconds < 1:
            raise ValueError("rate limit window must be positive")
        if self.max_limiter_entries < 16:
            raise ValueError("rate limiter entry cap is too small")


def validate_host(host: str | None, settings: SecuritySettings) -> None:
    if host is None or not host.strip():
        raise SecurityRejection("VALIDATION_ERROR", "Host header is required", 400)
    normalized = host.strip().lower()
    hostname = normalized.rsplit(":", 1)[0] if ":" in normalized else normalized
    allowed = {item.lower() for item in settings.allowed_hosts if item}
    if normalized not in allowed and hostname not in allowed:
        raise SecurityRejection("VALIDATION_ERROR", "Host header is not allowed", 400)


def validate_request_target(target: str, settings: ResourceLimitSettings) -> None:
    if len(target.encode("utf-8")) > settings.max_request_target_bytes:
        raise SecurityRejection("REQUEST_TARGET_TOO_LARGE", "request target is too large", 414)
    if any(ord(ch) < 0x20 for ch in target) or "\\" in target or "#" in target:
        raise SecurityRejection("VALIDATION_ERROR", "request target is invalid", 400)
    if re.search(r"%(?![0-9A-Fa-f]{2})", target):
        raise SecurityRejection("VALIDATION_ERROR", "request target is invalid", 400)
    split = urlsplit(target)
    if split.scheme or split.netloc or split.username or split.password:
        raise SecurityRejection("VALIDATION_ERROR", "request target is invalid", 400)
    try:
        unquote_to_bytes(split.path)
        unquote_to_bytes(split.query)
    except Exception as error:
        raise SecurityRejection("VALIDATION_ERROR", "request target is invalid", 400) from error


def validate_headers(headers: list[tuple[str, str]], settings: ResourceLimitSettings) -> None:
    if len(headers) > settings.max_header_count:
        raise SecurityRejection("HEADERS_TOO_LARGE", "too many headers", 431)
    total = 0
    for key, value in headers:
        total += len(key.encode("utf-8")) + len(value.encode("utf-8"))
    if total > settings.max_header_bytes:
        raise SecurityRejection("HEADERS_TOO_LARGE", "headers exceed the configured limit", 431)


def validate_content_type(method: str, length: int, content_type: str | None) -> None:
    if length <= 0 or method in {"GET", "HEAD", "DELETE"}:
        return
    media_type = (content_type or "").split(";", 1)[0].strip().lower()
    if media_type != "application/json":
        raise SecurityRejection("UNSUPPORTED_MEDIA_TYPE", "content type is not supported", 415)


def security_headers(local_http: bool = True) -> dict[str, str]:
    return {
        "x-content-type-options": "nosniff",
        "referrer-policy": "no-referrer",
        "x-frame-options": "DENY",
        "content-security-policy": "default-src 'none'; frame-ancestors 'none'",
        "permissions-policy": "geolocation=(), microphone=(), camera=()",
        "cross-origin-resource-policy": "same-origin",
        "cache-control": "no-store",
    }


@dataclass
class RateLimiter:
    settings: SecuritySettings
    entries: OrderedDict[str, tuple[int, float]] = field(default_factory=OrderedDict)
    lock: threading.Lock = field(default_factory=threading.Lock)

    def check(self, key: str, *, now: float | None = None) -> int | None:
        if not self.settings.rate_limit_enabled:
            return None
        current = time.monotonic() if now is None else now
        digest = hashlib.sha256(key.encode("utf-8")).hexdigest()
        with self.lock:
            expired = [
                item
                for item, (_, reset_at) in self.entries.items()
                if reset_at <= current
            ]
            for item in expired:
                self.entries.pop(item, None)
            count, reset_at = self.entries.get(
                digest,
                (0, current + self.settings.rate_limit_window_seconds),
            )
            if len(self.entries) >= self.settings.max_limiter_entries and digest not in self.entries:
                self.entries.popitem(last=False)
            if current >= reset_at:
                count = 0
                reset_at = current + self.settings.rate_limit_window_seconds
            count += 1
            self.entries[digest] = (count, reset_at)
            if count > self.settings.rate_limit_requests:
                return max(1, int(reset_at - current))
        return None
