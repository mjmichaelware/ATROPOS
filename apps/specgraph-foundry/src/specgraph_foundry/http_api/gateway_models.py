"""The shape of a route's metadata.

Its own module because every gateway module needs it and it belongs to none of
them -- importing it from the dispatcher would make each of them depend on the
thing they were split out of.
"""

from __future__ import annotations

from dataclasses import dataclass, field

@dataclass(frozen=True)
class RouteMetadata:
    operation: str
    path_params: dict[str, str]
    idempotency_required: bool = False
    response_etag_kind: str | None = None
    collection_etag_kind: str | None = None
    concurrency_kind: str | None = None
