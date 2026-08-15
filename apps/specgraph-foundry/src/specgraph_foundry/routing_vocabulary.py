"""The closed vocabularies routing is defined in terms of.

Provider classes, cost classes, capabilities, statuses, the canonical route law,
and territory normalisation. Every routing module checks against these, so they
belong to none of them -- a constant owned by one consumer is a constant the
others import through a module they otherwise have no reason to know about.
"""

from __future__ import annotations

from .errors import ValidationError
import re

CANONICAL_ROUTE_LAW = [
    "LOCAL_TOOLCHAIN",
    "FREE_READY_PROVIDER",
    "FREE_FALLBACK_PROVIDER",
    "COOLDOWN_QUEUE",
    "OFFLINE_DEGRADED_MODE",
    "PAID_EMERGENCY_ONLY_BY_EXPLICIT_UNLOCK",
]


PROVIDER_CLASSES = {
    "LOCAL_TOOLCHAIN",
    "FREE_READY_PROVIDER",
    "FREE_FALLBACK_PROVIDER",
    "PAID_EMERGENCY",
}


COST_CLASSES = {
    "LOCAL",
    "FREE",
    "PAID",
}


PROVIDER_STATUSES = {
    "UNKNOWN",
    "READY",
    "DEGRADED",
    "DOWN",
    "COOLDOWN",
}


CLASS_COST_LAW = {
    "LOCAL_TOOLCHAIN": "LOCAL",
    "FREE_READY_PROVIDER": "FREE",
    "FREE_FALLBACK_PROVIDER": "FREE",
    "PAID_EMERGENCY": "PAID",
}


def normalize_territories(
    territories: list[str],
) -> list[str]:
    if not isinstance(territories, list):
        raise ValidationError(
            "territories must be a list"
        )

    normalized = sorted(
        {
            str(item).strip().upper()
            for item in territories
            if str(item).strip()
        }
    )

    if not normalized:
        raise ValidationError(
            "at least one territory is required"
        )

    return normalized
