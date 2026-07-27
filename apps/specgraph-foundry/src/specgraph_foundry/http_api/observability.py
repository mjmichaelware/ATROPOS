from __future__ import annotations

import contextlib
import json
import sys
import time
import uuid
from dataclasses import dataclass, field
from datetime import UTC, datetime
from typing import Iterator


SAFE_LOG_FIELDS = {
    "timestamp",
    "severity",
    "service",
    "event",
    "request_id",
    "trace_id",
    "span_id",
    "method",
    "route",
    "status_code",
    "duration_ms",
    "response_bytes",
    "operation_type",
    "operation_state",
    "attempt_count",
    "retryable",
    "dependency",
    "error_code",
}

SENSITIVE_MARKERS = {
    "authorization",
    "bearer",
    "cookie",
    "idempotency",
    "cursor",
    "signed",
    "password",
    "secret",
    "token",
    "source",
    "evidence",
    "conclusion",
    "receipt",
    "select ",
    "insert ",
    "update ",
    "delete ",
}


def utc_timestamp() -> str:
    return datetime.now(UTC).isoformat()


def safe_route(raw_path: str) -> str:
    path = raw_path.split("?", 1)[0]
    parts = [part for part in path.split("/") if part]
    normalized: list[str] = []
    for part in parts:
        if part in {"v1", "health", "live", "startup", "ready", "version"}:
            normalized.append(part)
        elif part in {
            "projects",
            "documents",
            "source-uploads",
            "operations",
            "research-tasks",
            "atoms",
            "plans",
            "exports",
            "execution-runs",
            "execution-nodes",
            "providers",
            "renderers",
            "route-decisions",
            "workspace",
            "readiness",
            "source-workspace",
            "research-workspace",
            "planning-workspace",
            "handoff-workspace",
            "cancel",
            "extract",
            "verify",
            "finalize",
            "download",
            "claim",
            "complete",
            "evidence",
            "health",
            "receipts",
            "routing-policy",
            "bindings",
            "relations",
            "paid-unlocks",
            "route-decisions",
            "operations",
        }:
            normalized.append(part)
        else:
            normalized.append("{id}")
    return "/" + "/".join(normalized)


def parse_traceparent(value: str | None) -> tuple[str, str] | None:
    if value is None:
        return None
    parts = value.strip().split("-")
    if len(parts) != 4:
        return None
    version, trace_id, span_id, flags = parts
    if version != "00":
        return None
    if (
        len(trace_id) != 32
        or len(span_id) != 16
        or len(flags) != 2
        or set(trace_id) == {"0"}
        or set(span_id) == {"0"}
    ):
        return None
    try:
        int(trace_id, 16)
        int(span_id, 16)
        int(flags, 16)
    except ValueError:
        return None
    return trace_id, span_id


@dataclass
class Span:
    name: str
    trace_id: str
    span_id: str
    attributes: dict[str, object]
    start: float = field(default_factory=time.monotonic)
    end: float | None = None


class Observability:
    def __init__(
        self,
        *,
        service: str = "specgraph-foundry-api",
        enabled: bool = False,
        stream=None,
    ) -> None:
        self.service = service
        self.enabled = enabled
        self.stream = stream if stream is not None else sys.stdout
        self.spans: list[Span] = []
        self.metrics: dict[tuple[str, tuple[tuple[str, str], ...]], float] = {}

    def log(self, severity: str, event: str, **fields: object) -> None:
        payload = {
            "timestamp": utc_timestamp(),
            "severity": severity,
            "service": self.service,
            "event": event,
        }
        for key, value in fields.items():
            if key not in SAFE_LOG_FIELDS or value is None:
                continue
            text = str(value)
            lowered = text.lower()
            if any(marker in lowered for marker in SENSITIVE_MARKERS):
                continue
            payload[key] = value
        print(json.dumps(payload, sort_keys=True, separators=(",", ":")), file=self.stream)

    @contextlib.contextmanager
    def span(
        self,
        name: str,
        *,
        traceparent: str | None = None,
        attributes: dict[str, object] | None = None,
    ) -> Iterator[Span]:
        parsed = parse_traceparent(traceparent)
        trace_id = parsed[0] if parsed else uuid.uuid4().hex
        span_id = uuid.uuid4().hex[:16]
        safe_attributes: dict[str, object] = {}
        for key, value in (attributes or {}).items():
            if key in {"route", "method", "operation_type", "dependency", "status"}:
                safe_attributes[key] = value
        span = Span(name=name, trace_id=trace_id, span_id=span_id, attributes=safe_attributes)
        self.spans.append(span)
        try:
            yield span
        finally:
            span.end = time.monotonic()

    def metric(self, name: str, value: float = 1, **labels: str) -> None:
        allowed = {
            key: str(labels[key])
            for key in sorted(labels)
            if key in {"route", "method", "status", "operation_type", "state", "reason", "dependency"}
        }
        key = (name, tuple(allowed.items()))
        self.metrics[key] = self.metrics.get(key, 0) + value

    def shutdown(self, timeout_seconds: float = 1.0) -> None:
        return None


DEFAULT_OBSERVABILITY = Observability(enabled=False)
