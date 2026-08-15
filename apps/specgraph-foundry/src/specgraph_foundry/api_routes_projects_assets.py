"""Project routes: documents, atoms, bindings and exports hung off a project."""

from __future__ import annotations

import json

from .errors import ConflictError, NotFoundError, ValidationError


def match(api, method, parts, raw_path=None, payload=None):
    """Serves the request if this half owns the path, else returns None."""
    if (
        len(parts) == 4
        and parts[:2] == ["v1", "projects"]
        and parts[3] == "exports"
        and method == "GET"
    ):
        return 200, {
            "items": api.exports.list_exports(
                parts[2]
            )
        }

    if (
        len(parts) == 4
        and parts[:2] == ["v1", "projects"]
        and parts[3] == "execution-runs"
        and method == "GET"
    ):
        return 200, {
            "items": api.execution.list_runs(
                parts[2]
            )
        }

    if (
        len(parts) == 4
        and parts[:2] == ["v1", "projects"]
        and parts[3] == "routing-policy"
    ):
        if method == "GET":
            return 200, api.routing.get_policy(
                parts[2]
            )

        if method == "POST":
            return 200, api.routing.set_policy(
                project_id=parts[2],
                allow_offline_degraded=bool(
                    payload.get(
                        "allow_offline_degraded",
                        True,
                    )
                ),
                paid_emergency_enabled=bool(
                    payload.get(
                        "paid_emergency_enabled",
                        False,
                    )
                ),
                max_paid_decisions_per_unlock=int(
                    payload.get(
                        "max_paid_decisions_per_unlock",
                        1,
                    )
                ),
            )

    if (
        len(parts) == 4
        and parts[:2] == ["v1", "projects"]
        and parts[3] == "providers"
    ):
        if method == "GET":
            return 200, {
                "items": api.routing.list_providers(
                    parts[2]
                )
            }

        if method == "POST":
            territories = payload.get(
                "territories",
                [],
            )
            metadata = payload.get(
                "metadata",
                {},
            )

            if not isinstance(territories, list):
                raise ValidationError(
                    "territories must be a list"
                )

            if not isinstance(metadata, dict):
                raise ValidationError(
                    "metadata must be an object"
                )

            return 201, api.routing.configure_provider(
                project_id=parts[2],
                name=str(
                    payload.get("name", "")
                ),
                provider_class=str(
                    payload.get(
                        "provider_class",
                        "",
                    )
                ),
                cost_class=str(
                    payload.get(
                        "cost_class",
                        "",
                    )
                ),
                territories=[
                    str(item)
                    for item in territories
                ],
                priority=int(
                    payload.get(
                        "priority",
                        100,
                    )
                ),
                metadata=metadata,
                enabled=bool(
                    payload.get(
                        "enabled",
                        True,
                    )
                ),
            )

    if (
        len(parts) == 4
        and parts[:2] == ["v1", "projects"]
        and parts[3] == "renderers"
    ):
        if method == "GET":
            return 200, {
                "items": api.routing.list_renderers(
                    parts[2]
                )
            }

        if method == "POST":
            territories = payload.get(
                "territories",
                [],
            )
            metadata = payload.get(
                "metadata",
                {},
            )

            if not isinstance(territories, list):
                raise ValidationError(
                    "territories must be a list"
                )

            if not isinstance(metadata, dict):
                raise ValidationError(
                    "metadata must be an object"
                )

            return 201, api.routing.configure_renderer(
                project_id=parts[2],
                name=str(
                    payload.get("name", "")
                ),
                renderer_type=str(
                    payload.get(
                        "renderer_type",
                        "",
                    )
                ),
                territories=[
                    str(item)
                    for item in territories
                ],
                priority=int(
                    payload.get(
                        "priority",
                        100,
                    )
                ),
                metadata=metadata,
                enabled=bool(
                    payload.get(
                        "enabled",
                        True,
                    )
                ),
            )

    if (
        len(parts) == 5
        and parts[:2] == ["v1", "projects"]
        and parts[3] == "renderers"
        and parts[4] == "select"
        and method == "POST"
    ):
        return 200, {
            "renderer": api.routing.select_renderer(
                parts[2],
                str(
                    payload.get(
                        "territory",
                        "",
                    )
                ),
            )
        }

    if (
        len(parts) == 4
        and parts[:2] == ["v1", "projects"]
        and parts[3] == "paid-unlocks"
        and method == "POST"
    ):
        provider_value = payload.get(
            "provider_id"
        )
        max_value = payload.get(
            "max_decisions"
        )

        return 201, api.routing.grant_paid_unlock(
            project_id=parts[2],
            actor_id=str(
                payload.get("actor_id", "")
            ),
            reason=str(
                payload.get("reason", "")
            ),
            ttl_seconds=int(
                payload.get(
                    "ttl_seconds",
                    900,
                )
            ),
            max_decisions=(
                int(max_value)
                if max_value is not None
                else None
            ),
            provider_id=(
                str(provider_value)
                if provider_value
                else None
            ),
        )

    if (
        len(parts) == 4
        and parts[:2] == ["v1", "projects"]
        and parts[3] == "route-decisions"
        and method == "POST"
    ):
        return 201, api.routing.route(
            project_id=parts[2],
            territory=str(
                payload.get(
                    "territory",
                    "",
                )
            ),
            offline_capable=bool(
                payload.get(
                    "offline_capable",
                    False,
                )
            ),
        )

    return None
