"""Route metadata for planning, exports, execution, routing and operations."""

from __future__ import annotations

from .gateway_models import RouteMetadata


def match(
    method: str,
    parts: list[str],
) -> RouteMetadata | None:
    if (
        len(parts) == 4
        and parts[:2] == ["v1", "plans"]
        and parts[3] == "verify"
        and method == "POST"
    ):
        return RouteMetadata(
            operation="verify_plan",
            path_params={"plan_id": parts[2]},
            idempotency_required=True,
        )

    if (
        len(parts) == 4
        and parts[:2] == ["v1", "projects"]
        and parts[3] == "bindings"
    ):
        if method == "GET":
            return RouteMetadata(
                operation="list_project_bindings",
                path_params={
                    "project_id": parts[2]
                },
                collection_etag_kind="binding",
            )

        if method == "POST":
            return RouteMetadata(
                operation="create_project_binding",
                path_params={
                    "project_id": parts[2]
                },
                idempotency_required=True,
                response_etag_kind="binding",
                concurrency_kind="binding",
            )

    if (
        len(parts) == 4
        and parts[:2] == ["v1", "plans"]
        and parts[3] == "exports"
        and method == "POST"
    ):
        return RouteMetadata(
            operation="export_plan",
            path_params={"plan_id": parts[2]},
            idempotency_required=True,
        )

    if (
        len(parts) == 4
        and parts[:2] == ["v1", "exports"]
        and parts[3] == "verify"
        and method == "POST"
    ):
        return RouteMetadata(
            operation="verify_export",
            path_params={
                "export_id": parts[2]
            },
            idempotency_required=True,
        )

    if (
        len(parts) == 4
        and parts[:2] == ["v1", "exports"]
        and parts[3] == "download"
        and method == "GET"
    ):
        return RouteMetadata(
            operation="download_export_artifacts",
            path_params={
                "export_id": parts[2]
            },
        )

    if (
        len(parts) == 4
        and parts[:2] == ["v1", "plans"]
        and parts[3] == "execution-runs"
        and method == "POST"
    ):
        return RouteMetadata(
            operation="start_execution_run",
            path_params={"plan_id": parts[2]},
            idempotency_required=True,
        )

    if (
        len(parts) == 4
        and parts[:2] == ["v1", "execution-runs"]
    ):
        if parts[3] == "claim" and method == "POST":
            return RouteMetadata(
                operation="claim_execution_run_node",
                path_params={"run_id": parts[2]},
                idempotency_required=True,
            )

        if parts[3] == "verify" and method == "POST":
            return RouteMetadata(
                operation="verify_execution_run",
                path_params={"run_id": parts[2]},
                idempotency_required=True,
            )

    if (
        len(parts) == 4
        and parts[:2] == ["v1", "execution-nodes"]
        and parts[3] == "receipts"
        and method == "POST"
    ):
        return RouteMetadata(
            operation="submit_execution_receipt",
            path_params={
                "run_node_id": parts[2]
            },
            idempotency_required=True,
        )

    if (
        len(parts) == 4
        and parts[:2] == ["v1", "projects"]
        and parts[3] == "routing-policy"
    ):
        if method == "GET":
            return RouteMetadata(
                operation="get_routing_policy",
                path_params={
                    "project_id": parts[2]
                },
                response_etag_kind="routing_policy",
            )

        if method == "POST":
            return RouteMetadata(
                operation="set_routing_policy",
                path_params={
                    "project_id": parts[2]
                },
                response_etag_kind="routing_policy",
                concurrency_kind="routing_policy",
            )

    if (
        len(parts) == 4
        and parts[:2] == ["v1", "projects"]
        and parts[3] == "providers"
    ):
        if method == "GET":
            return RouteMetadata(
                operation="list_project_providers",
                path_params={
                    "project_id": parts[2]
                },
                collection_etag_kind="provider",
            )

        if method == "POST":
            return RouteMetadata(
                operation="create_project_provider",
                path_params={
                    "project_id": parts[2]
                },
                idempotency_required=True,
                response_etag_kind="provider",
                concurrency_kind="provider",
            )

    if (
        len(parts) == 4
        and parts[:2] == ["v1", "providers"]
        and parts[3] == "health"
        and method == "POST"
    ):
        return RouteMetadata(
            operation="record_provider_health",
            path_params={
                "provider_id": parts[2]
            },
            idempotency_required=True,
        )

    if (
        len(parts) == 4
        and parts[:2] == ["v1", "projects"]
        and parts[3] == "renderers"
    ):
        if method == "GET":
            return RouteMetadata(
                operation="list_project_renderers",
                path_params={
                    "project_id": parts[2]
                },
                collection_etag_kind="renderer",
            )

        if method == "POST":
            return RouteMetadata(
                operation="create_project_renderer",
                path_params={
                    "project_id": parts[2]
                },
                idempotency_required=True,
                response_etag_kind="renderer",
                concurrency_kind="renderer",
            )

    if (
        len(parts) == 5
        and parts[:2] == ["v1", "projects"]
        and parts[3] == "renderers"
        and parts[4] == "select"
        and method == "POST"
    ):
        return RouteMetadata(
            operation="select_project_renderer",
            path_params={
                "project_id": parts[2]
            },
            idempotency_required=True,
        )

    if (
        len(parts) == 4
        and parts[:2] == ["v1", "projects"]
        and parts[3] == "paid-unlocks"
        and method == "POST"
    ):
        return RouteMetadata(
            operation="grant_project_paid_unlock",
            path_params={
                "project_id": parts[2]
            },
            idempotency_required=True,
        )

    if (
        len(parts) == 4
        and parts[:2] == ["v1", "projects"]
        and parts[3] == "route-decisions"
        and method == "POST"
    ):
        return RouteMetadata(
            operation="create_project_route_decision",
            path_params={
                "project_id": parts[2]
            },
            idempotency_required=True,
        )

    return None
