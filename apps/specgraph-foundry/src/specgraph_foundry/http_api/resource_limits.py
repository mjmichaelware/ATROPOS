from __future__ import annotations

import math
from dataclasses import dataclass

from ..errors import ValidationError


@dataclass(frozen=True)
class ResourceLimitSettings:
    max_request_target_bytes: int = 2048
    max_header_count: int = 64
    max_header_bytes: int = 16384
    max_json_depth: int = 32
    max_json_items: int = 10000
    max_json_string_bytes: int = 65536
    max_concurrent_requests: int = 32
    request_deadline_seconds: int = 25

    def __post_init__(self) -> None:
        if self.max_request_target_bytes < 64:
            raise ValueError("request target limit is too small")
        if self.max_header_count < 1:
            raise ValueError("header count limit must be positive")
        if self.max_header_bytes < 128:
            raise ValueError("header byte limit is too small")
        if self.max_json_depth < 1:
            raise ValueError("JSON depth limit must be positive")
        if self.max_json_items < 1:
            raise ValueError("JSON item limit must be positive")
        if self.max_json_string_bytes < 1:
            raise ValueError("JSON string limit must be positive")
        if self.max_concurrent_requests < 1:
            raise ValueError("concurrent request limit must be positive")
        if not 1 <= self.request_deadline_seconds <= 3600:
            raise ValueError("request deadline is invalid")


class JsonLimitExceeded(ValidationError):
    pass


def validate_json_limits(
    value: object,
    settings: ResourceLimitSettings,
) -> None:
    count = 0

    def visit(item: object, depth: int) -> None:
        nonlocal count
        if depth > settings.max_json_depth:
            raise JsonLimitExceeded("JSON depth exceeds the configured limit")
        count += 1
        if count > settings.max_json_items:
            raise JsonLimitExceeded("JSON item count exceeds the configured limit")
        if isinstance(item, str):
            if len(item.encode("utf-8")) > settings.max_json_string_bytes:
                raise JsonLimitExceeded("JSON string exceeds the configured limit")
        elif isinstance(item, float):
            if not math.isfinite(item):
                raise JsonLimitExceeded("JSON numbers must be finite")
        elif isinstance(item, dict):
            for key, child in item.items():
                if not isinstance(key, str):
                    raise JsonLimitExceeded("JSON object keys must be strings")
                if len(key.encode("utf-8")) > settings.max_json_string_bytes:
                    raise JsonLimitExceeded("JSON key exceeds the configured limit")
                visit(child, depth + 1)
        elif isinstance(item, list):
            for child in item:
                visit(child, depth + 1)

    visit(value, 0)
