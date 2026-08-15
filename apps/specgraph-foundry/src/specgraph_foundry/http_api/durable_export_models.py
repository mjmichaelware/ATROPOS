"""Durable export result shape, manifest version, and typed failures."""

from __future__ import annotations

from dataclasses import dataclass

MANIFEST_VERSION = "specgraph.artifact.manifest.v1"


class ArtifactNotVerifiedError(RuntimeError):
    pass


class ArtifactLimitExceededError(RuntimeError):
    pass


@dataclass(frozen=True)
class DurableExportResult:
    status: int
    body: dict[str, object]
