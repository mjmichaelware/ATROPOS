from __future__ import annotations

import hashlib
import json

from ..errors import ValidationError


def canonical_json_bytes(
    value: object,
) -> bytes:
    try:
        encoded = json.dumps(
            value,
            sort_keys=True,
            separators=(",", ":"),
            ensure_ascii=False,
            allow_nan=False,
        )
    except (TypeError, ValueError) as error:
        raise ValidationError(
            "request payload must be valid JSON"
        ) from error

    return encoded.encode("utf-8")


def strong_etag(
    kind: str,
    value: object,
) -> str:
    digest = hashlib.sha256(
        kind.encode("utf-8")
        + b"\x00"
        + canonical_json_bytes(value)
    ).hexdigest()
    return f"\"{digest}\""


def binding_etag(
    binding: dict[str, object],
) -> str:
    return strong_etag(
        "integration_binding",
        {
            "id": binding["id"],
            "project_id": binding["project_id"],
            "system_name": binding["system_name"],
            "binding_type": binding["binding_type"],
            "config": binding["config"],
            "enabled": binding["enabled"],
            "created_at": binding["created_at"],
            "updated_at": binding["updated_at"],
        },
    )


def routing_policy_etag(
    policy: dict[str, object],
) -> str:
    return strong_etag(
        "routing_policy",
        {
            "id": policy["id"],
            "project_id": policy["project_id"],
            "route_law": policy["route_law"],
            "allow_offline_degraded": policy[
                "allow_offline_degraded"
            ],
            "paid_emergency_enabled": policy[
                "paid_emergency_enabled"
            ],
            "max_paid_decisions_per_unlock": policy[
                "max_paid_decisions_per_unlock"
            ],
            "created_at": policy["created_at"],
            "updated_at": policy["updated_at"],
        },
    )


def provider_etag(
    provider: dict[str, object],
) -> str:
    return strong_etag(
        "provider_config",
        {
            "id": provider["id"],
            "project_id": provider["project_id"],
            "name": provider["name"],
            "provider_class": provider[
                "provider_class"
            ],
            "cost_class": provider["cost_class"],
            "territories": provider["territories"],
            "priority": provider["priority"],
            "enabled": provider["enabled"],
            "metadata": provider["metadata"],
            "created_at": provider["created_at"],
        },
    )


def renderer_etag(
    renderer: dict[str, object],
) -> str:
    return strong_etag(
        "renderer_config",
        {
            "id": renderer["id"],
            "project_id": renderer["project_id"],
            "name": renderer["name"],
            "renderer_type": renderer[
                "renderer_type"
            ],
            "territories": renderer["territories"],
            "priority": renderer["priority"],
            "enabled": renderer["enabled"],
            "metadata": renderer["metadata"],
            "created_at": renderer["created_at"],
        },
    )


def validate_if_match(
    value: str | None,
) -> str:
    if value is None:
        raise ValidationError(
            "If-Match is required"
        )

    normalized = value.strip()

    if not normalized:
        raise ValidationError(
            "If-Match is required"
        )

    if normalized == "*":
        raise ValidationError(
            "If-Match wildcard is not supported"
        )

    if (
        len(normalized) < 3
        or not normalized.startswith('"')
        or not normalized.endswith('"')
    ):
        raise ValidationError(
            "If-Match must be a strong ETag"
        )

    return normalized
