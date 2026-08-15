"""ETags and the resources they identify.

Concurrency control depends on these being computed the same way for every
resource, which is the reason they are together and nowhere else.
"""

from __future__ import annotations

from typing import TYPE_CHECKING

if TYPE_CHECKING:
    from .gateway import Api

from .gateway_models import RouteMetadata
from .concurrency import binding_etag
from .concurrency import provider_etag
from .concurrency import renderer_etag
from .concurrency import routing_policy_etag
from .database import RequestScopedDatabase
from .models import ApiResponse
import json


def decorate_collection_etags(
    api,
    response: ApiResponse,
    kind: str,
) -> ApiResponse:
    items = response.body.get("items")

    if not isinstance(items, list):
        return response

    decorated_items: list[object] = []

    for item in items:
        if not isinstance(item, dict):
            decorated_items.append(item)
            continue

        decorated = dict(item)
        decorated["etag"] = (
            etag_for_resource(
                kind,
                decorated,
            )
        )
        decorated_items.append(decorated)

    body = dict(response.body)
    body["items"] = decorated_items
    return ApiResponse(
        status=response.status,
        body=body,
        headers=response.headers,
    )


def decorate_resource_etag(
    api,
    response: ApiResponse,
    kind: str,
) -> ApiResponse:
    if not isinstance(response.body, dict):
        return response

    etag = etag_for_resource(
        kind,
        response.body,
    )
    body = dict(response.body)
    body["etag"] = etag
    headers = dict(response.headers)
    headers["etag"] = etag

    return ApiResponse(
        status=response.status,
        body=body,
        headers=headers,
    )


def etag_for_resource(
    kind: str | None,
    resource: dict[str, object],
) -> str:
    if kind == "binding":
        return binding_etag(resource)
    if kind == "routing_policy":
        return routing_policy_etag(resource)
    if kind == "provider":
        return provider_etag(resource)
    if kind == "renderer":
        return renderer_etag(resource)

    raise ValueError(
        f"unsupported ETag kind: {kind}"
    )


def editable_resource(
    api,
    database: RequestScopedDatabase,
    legacy_api: Api,
    route: RouteMetadata,
    payload: dict[str, object],
) -> dict[str, object] | None:
    if route.concurrency_kind == "routing_policy":
        with database.connect() as connection:
            row = connection.execute(
                """
                SELECT *
                FROM project_policies
                WHERE project_id = ?
                """,
                (
                    route.path_params[
                        "project_id"
                    ],
                ),
            ).fetchone()

        if row is None:
            return None

        return legacy_api.routing._normalize_policy(
            dict(row)
        )

    if route.concurrency_kind == "binding":
        system_name = str(
            payload.get("system_name", "")
        ).strip()
        binding_type = str(
            payload.get("binding_type", "")
        ).strip().upper()

        if (
            not system_name
            or not binding_type
        ):
            return None

        with database.connect() as connection:
            row = connection.execute(
                """
                SELECT *
                FROM integration_bindings
                WHERE project_id = ?
                  AND system_name = ?
                  AND binding_type = ?
                """,
                (
                    route.path_params[
                        "project_id"
                    ],
                    system_name,
                    binding_type,
                ),
            ).fetchone()

        if row is None:
            return None

        return legacy_api.exports._normalize_binding(
            dict(row)
        )

    if route.concurrency_kind == "provider":
        name = str(
            payload.get("name", "")
        ).strip()

        if not name:
            return None

        with database.connect() as connection:
            row = connection.execute(
                """
                SELECT *
                FROM provider_configs
                WHERE project_id = ?
                  AND name = ?
                """,
                (
                    route.path_params[
                        "project_id"
                    ],
                    name,
                ),
            ).fetchone()

        if row is None:
            return None

        return legacy_api.routing._normalize_provider(
            dict(row)
        )

    if route.concurrency_kind == "renderer":
        name = str(
            payload.get("name", "")
        ).strip()

        if not name:
            return None

        with database.connect() as connection:
            row = connection.execute(
                """
                SELECT *
                FROM renderer_configs
                WHERE project_id = ?
                  AND name = ?
                """,
                (
                    route.path_params[
                        "project_id"
                    ],
                    name,
                ),
            ).fetchone()

        if row is None:
            return None

        return legacy_api.routing._normalize_renderer(
            dict(row)
        )

    return None


def resource_reference(
    route: RouteMetadata,
    body: dict[str, object],
) -> tuple[str | None, str | None]:
    if "id" in body and isinstance(
        body["id"],
        str,
    ):
        return route.operation, str(
            body["id"]
        )

    task = body.get("task")
    if isinstance(task, dict) and isinstance(
        task.get("id"),
        str,
    ):
        return route.operation, str(
            task["id"]
        )

    claim = body.get("claim")
    if isinstance(claim, dict):
        attempt = claim.get("attempt")
        if isinstance(
            attempt,
            dict,
        ) and isinstance(
            attempt.get("id"),
            str,
        ):
            return route.operation, str(
                attempt["id"]
            )

    renderer = body.get("renderer")
    if isinstance(
        renderer,
        dict,
    ) and isinstance(
        renderer.get("id"),
        str,
    ):
        return route.operation, str(
            renderer["id"]
        )

    return None, None
