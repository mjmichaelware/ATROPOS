"""Shaping successful responses."""

from __future__ import annotations

from typing import TYPE_CHECKING

if TYPE_CHECKING:
    from ..database import Database

from .gateway_models import RouteMetadata
from .gateway_etags import decorate_collection_etags
from .gateway_etags import decorate_resource_etag
from .handoff_workspace import HandoffWorkspaceService
from .models import ApiResponse
from .planning_workspace import PlanningWorkspaceService
from .research_workspace import ResearchWorkspaceService
from .source_workspace import SourceWorkspaceService
import json


def success_response(
    api,
    *,
    status: int,
    body: dict[str, object],
    headers: dict[str, str],
    route: RouteMetadata | None,
    replayed: bool | None,
) -> ApiResponse:
    response = ApiResponse(
        status=status,
        body=body,
        headers=dict(headers),
    )

    if replayed is not None:
        response.headers[
            "idempotency-replayed"
        ] = (
            "true"
            if replayed
            else "false"
        )

    if route is None:
        return response

    if (
        route.collection_etag_kind
        is not None
    ):
        response = decorate_collection_etags(
            api,
            response,
            route.collection_etag_kind,
        )

    if (
        route.response_etag_kind
        is not None
    ):
        response = decorate_resource_etag(
            api,
            response,
            route.response_etag_kind,
        )

    return response


def workspace_body(
    database: Database,
    parts: list[str],
) -> dict[str, object] | None:
    if (
        len(parts) == 4
        and parts[:2] == [
            "v1",
            "projects",
        ]
    ):
        project_id = parts[2]
        workspace_name = parts[3]

        if workspace_name == "source-workspace":
            return (
                SourceWorkspaceService(
                    database
                ).get_project(project_id)
            )

        if workspace_name == "research-workspace":
            return (
                ResearchWorkspaceService(
                    database
                ).get(project_id)
            )

        if workspace_name == "planning-workspace":
            return (
                PlanningWorkspaceService(
                    database
                ).get(project_id)
            )

        if workspace_name == "handoff-workspace":
            return (
                HandoffWorkspaceService(
                    database
                ).get(project_id)
            )

    if (
        len(parts) == 4
        and parts[:2] == [
            "v1",
            "documents",
        ]
        and parts[3] == "provenance"
    ):
        return (
            SourceWorkspaceService(
                database
            ).get_document(
                parts[2]
            )
        )

    return None
