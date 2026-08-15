"""Project routes: creation, listing, settings and members."""

from __future__ import annotations

import json

from .errors import ConflictError, NotFoundError, ValidationError


def match(api, method, parts, raw_path=None, payload=None):
    """Serves the request if this half owns the path, else returns None."""
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

    return None
