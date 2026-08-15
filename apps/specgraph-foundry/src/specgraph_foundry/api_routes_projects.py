"""Project-scoped routes.

`Api.dispatch` matched 42 routes in a 972-line try block. The blocks are
independent -- each recognises its own path and returns -- so reading one meant
scrolling past the rest. One module per resource family, each returning None
when the path is not its own.
"""

from __future__ import annotations

from .errors import ValidationError
import json


def match(api, method, parts, raw_path=None, payload=None):
    """Serves the request if this family owns the path, else returns None."""
    if parts == ["v1", "projects"]:
        if method == "GET":
            page = api._projects_page(
                raw_path
            )
            return 200, {
                "items": page.items
            }

        if method == "POST":
            return 201, api.projects.create(
                str(payload.get("slug", "")),
                str(payload.get("name", "")),
                str(payload.get("description", "")),
            )

    if (
        len(parts) == 3
        and parts[:2] == ["v1", "projects"]
        and method == "GET"
    ):
        return 200, api.projects.get(parts[2])

    if (
        len(parts) == 4
        and parts[:2] == ["v1", "projects"]
        and parts[3] == "documents"
    ):
        project_id = parts[2]

        if method == "GET":
            page = api._documents_page(
                project_id,
                raw_path,
            )
            return 200, {
                "items": page.items
            }

        if method == "POST":
            return 201, api.ingestion.ingest_text(
                project_id=project_id,
                title=str(payload.get("title", "")),
                content=str(payload.get("content", "")),
                media_type=str(
                    payload.get(
                        "media_type",
                        "text/plain",
                    )
                ),
                chunk_bytes=int(
                    payload.get(
                        "chunk_bytes",
                        32768,
                    )
                ),
            )

    if (
        len(parts) == 4
        and parts[:2] == ["v1", "projects"]
        and parts[3] == "research-tasks"
        and method == "GET"
    ):
        page = api._research_tasks_page(
            parts[2],
            raw_path,
        )
        return 200, {
            "items": page.items
        }

    if (
        len(parts) == 5
        and parts[:2] == ["v1", "projects"]
        and parts[3] == "research-tasks"
        and parts[4] == "claim"
        and method == "POST"
    ):
        return 200, {
            "task": api.research.claim_task(
                project_id=parts[2],
                worker_id=str(
                    payload.get("worker_id", "")
                ),
                lease_seconds=int(
                    payload.get(
                        "lease_seconds",
                        900,
                    )
                ),
            )
        }

    if (
        len(parts) == 4
        and parts[:2] == ["v1", "projects"]
        and parts[3] == "gap-matrix"
        and method == "GET"
    ):
        return 200, api.research.gap_matrix(
            parts[2]
        )

    if (
        len(parts) == 4
        and parts[:2] == ["v1", "projects"]
        and parts[3] == "relations"
    ):
        if method == "GET":
            page = api._relations_page(
                parts[2],
                raw_path,
            )
            return 200, {
                "items": page.items
            }

        if method == "POST":
            return 201, api.planning.add_relation(
                project_id=parts[2],
                from_atom_id=str(
                    payload.get(
                        "from_atom_id",
                        "",
                    )
                ),
                to_atom_id=str(
                    payload.get(
                        "to_atom_id",
                        "",
                    )
                ),
                relation_type=str(
                    payload.get(
                        "relation_type",
                        "",
                    )
                ),
                rationale=str(
                    payload.get(
                        "rationale",
                        "",
                    )
                ),
                confidence=float(
                    payload.get(
                        "confidence",
                        1.0,
                    )
                ),
                inferred=bool(
                    payload.get(
                        "inferred",
                        False,
                    )
                ),
            )

    if (
        len(parts) == 4
        and parts[:2] == ["v1", "projects"]
        and parts[3] == "plans"
    ):
        if method == "GET":
            return 200, {
                "items": api.planning.list_plans(
                    parts[2]
                )
            }

        if method == "POST":
            return 201, api.planning.synthesize(
                project_id=parts[2],
                allow_open_research=bool(
                    payload.get(
                        "allow_open_research",
                        False,
                    )
                ),
            )

    if (
        len(parts) == 4
        and parts[:2] == ["v1", "projects"]
        and parts[3] == "bindings"
    ):
        if method == "GET":
            return 200, {
                "items": api.exports.list_bindings(
                    parts[2]
                )
            }

        if method == "POST":
            config = payload.get(
                "config",
                {},
            )

            if not isinstance(config, dict):
                raise ValidationError(
                    "config must be an object"
                )

            return 201, api.exports.bind_integration(
                project_id=parts[2],
                system_name=str(
                    payload.get(
                        "system_name",
                        "",
                    )
                ),
                binding_type=str(
                    payload.get(
                        "binding_type",
                        "",
                    )
                ),
                config=config,
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
