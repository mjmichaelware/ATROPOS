"""Provider and routing routes.

`Api.dispatch` matched 42 routes in a 972-line try block. The blocks are
independent -- each recognises its own path and returns -- so reading one meant
scrolling past the rest. One module per resource family, each returning None
when the path is not its own.
"""

from __future__ import annotations

import json


def match(api, method, parts, raw_path=None, payload=None):
    """Serves the request if this family owns the path, else returns None."""
    if method == "GET" and parts == ["health"]:
        return 200, {
            "status": "ok",
            "service": "specgraph-foundry",
            "database": api.database.health(),
        }

    if (
        len(parts) == 4
        and parts[:2] == ["v1", "providers"]
        and parts[3] == "health"
        and method == "POST"
    ):
        cooldown_value = payload.get(
            "cooldown_seconds"
        )
        latency_value = payload.get(
            "latency_ms"
        )

        return 200, api.routing.record_health(
            provider_id=parts[2],
            status=str(
                payload.get("status", "")
            ),
            latency_ms=(
                float(latency_value)
                if latency_value is not None
                else None
            ),
            error_message=str(
                payload.get(
                    "error_message",
                    "",
                )
            ),
            cooldown_seconds=(
                int(cooldown_value)
                if cooldown_value is not None
                else None
            ),
        )

    if (
        len(parts) == 3
        and parts[:2] == ["v1", "route-decisions"]
        and method == "GET"
    ):
        return 200, api.routing.get_decision(
            parts[2]
        )

    return None
