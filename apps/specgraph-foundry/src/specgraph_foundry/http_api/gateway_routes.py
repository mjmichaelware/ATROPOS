"""The route table: every path, method and the metadata that governs it.

465 lines of pure data in the middle of a 2,097-line class. It references no
instance state at all -- it is a description of the API, not part of its
behaviour -- so it belongs beside the dispatcher rather than inside it.
"""

from __future__ import annotations

from .gateway_models import RouteMetadata
import json


def route_metadata(
    method: str,
    parts: list[str],
) -> RouteMetadata | None:
    if parts == ["v1", "projects"] and method == "POST":
        return RouteMetadata(
            operation="create_project",
            path_params={},
        )

    if (
        len(parts) == 4
        and parts[:2] == ["v1", "projects"]
        and parts[3] == "documents"
    ):
        if method == "GET":
            return RouteMetadata(
                operation="list_project_documents",
                path_params={
                    "project_id": parts[2]
                },
            )
        if method == "POST":
            return RouteMetadata(
                operation="ingest_project_document",
                path_params={
                    "project_id": parts[2]
                },
                idempotency_required=True,
            )

    if (
        len(parts) == 4
        and parts[:2] == ["v1", "projects"]
        and parts[3] == "source-uploads"
        and method == "POST"
    ):
        return RouteMetadata(
            operation="create_source_upload_intent",
            path_params={
                "project_id": parts[2]
            },
            idempotency_required=True,
        )

    if (
        len(parts) == 3
        and parts[:2] == ["v1", "source-uploads"]
        and method == "GET"
    ):
        return RouteMetadata(
            operation="get_source_upload",
            path_params={
                "upload_id": parts[2]
            },
        )

    if (
        len(parts) == 4
        and parts[:2] == ["v1", "source-uploads"]
        and parts[3] == "finalize"
        and method == "POST"
    ):
        return RouteMetadata(
            operation="finalize_source_upload",
            path_params={
                "upload_id": parts[2]
            },
            idempotency_required=True,
        )

    if (
        len(parts) == 3
        and parts[:2] == ["v1", "operations"]
        and method == "GET"
    ):
        return RouteMetadata(
            operation="get_operation",
            path_params={
                "operation_id": parts[2]
            },
        )

    if (
        len(parts) == 4
        and parts[:2] == ["v1", "operations"]
        and parts[3] == "cancel"
        and method == "POST"
    ):
        return RouteMetadata(
            operation="cancel_operation",
            path_params={
                "operation_id": parts[2]
            },
            idempotency_required=True,
        )

    if (
        len(parts) == 4
        and parts[:2] == ["v1", "projects"]
        and parts[3] == "operations"
        and method == "GET"
    ):
        return RouteMetadata(
            operation="list_project_operations",
            path_params={
                "project_id": parts[2]
            },
        )

    if (
        len(parts) == 4
        and parts[:2] == ["v1", "documents"]
        and parts[3] == "extract"
        and method == "POST"
    ):
        return RouteMetadata(
            operation="extract_document_atoms",
            path_params={
                "document_id": parts[2]
            },
            idempotency_required=True,
        )

    if (
        len(parts) == 4
        and parts[:2] == ["v1", "documents"]
        and parts[3] == "atoms"
        and method == "GET"
    ):
        return RouteMetadata(
            operation="list_document_atoms",
            path_params={
                "document_id": parts[2]
            },
        )

    if (
        len(parts) == 4
        and parts[:2] == ["v1", "projects"]
        and parts[3] == "research-tasks"
        and method == "GET"
    ):
        return RouteMetadata(
            operation="list_project_research_tasks",
            path_params={
                "project_id": parts[2]
            },
        )

    if (
        len(parts) == 5
        and parts[:2] == ["v1", "projects"]
        and parts[3] == "research-tasks"
        and parts[4] == "claim"
        and method == "POST"
    ):
        return RouteMetadata(
            operation="claim_project_research_task",
            path_params={
                "project_id": parts[2]
            },
            idempotency_required=True,
        )

    if (
        len(parts) == 4
        and parts[:2] == ["v1", "research-tasks"]
    ):
        task_id = parts[2]
        suffix = parts[3]

        if suffix == "evidence" and method == "POST":
            return RouteMetadata(
                operation="add_research_evidence",
                path_params={"task_id": task_id},
                idempotency_required=True,
            )

        if suffix == "complete" and method == "POST":
            return RouteMetadata(
                operation="complete_research_task",
                path_params={"task_id": task_id},
                idempotency_required=True,
            )

    if (
        len(parts) == 4
        and parts[:2] == ["v1", "projects"]
        and parts[3] == "relations"
        and method == "GET"
    ):
        return RouteMetadata(
            operation="list_project_relations",
            path_params={
                "project_id": parts[2]
            },
        )

    if (
        len(parts) == 4
        and parts[:2] == ["v1", "projects"]
        and parts[3] == "plans"
        and method == "POST"
    ):
        return RouteMetadata(
            operation="synthesize_project_plan",
            path_params={
                "project_id": parts[2]
            },
            idempotency_required=True,
        )

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
