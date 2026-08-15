"""Route metadata for projects, documents, atoms and research."""

from __future__ import annotations

from .gateway_models import RouteMetadata


def match(
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

    return None
