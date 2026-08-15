"""What each async operation type actually does.

One function per operation. They are together because they share nothing but
the registry that dispatches them -- and apart from the registry because
adding an operation type should not mean editing the dispatcher.
"""

from __future__ import annotations

from .operation_handler_context import *  # noqa: F401,F403
from ..execution import ExecutionService
from ..research import ResearchService
import json


def finalize_source_upload(
    registry,
    context: HandlerContext,
    path_params: dict[str, object],
    payload: dict[str, object],
) -> dict[str, object]:
    if context.source_uploads is None:
        raise DependencyUnavailable("source upload worker is unavailable")
    context.checkpoint("finalizing_source", 1, 3)
    raw_base64 = payload.get("raw_base64")
    result = context.source_uploads.finalize(
        owner_id=context.owner_id,
        authorization=context.authorization,
        upload_id=str(path_params["upload_id"]),
        raw_base64=(
            raw_base64
            if isinstance(raw_base64, str)
            else None
        ),
    )
    context.checkpoint("source_finalized", 3, 3)
    return {
        "document_id": result["document_id"],
        "status": result["status"],
    }


def complete_research_task(
    registry,
    context: HandlerContext,
    path_params: dict[str, object],
    payload: dict[str, object],
) -> dict[str, object]:
    context.checkpoint("completing_research", 1, 2)
    result = ResearchService(context.database).complete_task(
        task_id=str(path_params["task_id"]),
        worker_id=str(payload.get("worker_id", "")),
        conclusion=str(payload.get("conclusion", "")),
        applicability=str(payload.get("applicability", "")),
        confidence=float(payload.get("confidence", 0)),
        evidence_ids=[
            str(item)
            for item in payload.get("evidence_ids", [])
        ],
    )
    context.checkpoint("research_completed", 2, 2)
    return {
        "task_id": result["id"],
        "status": result["status"],
    }


def export_plan(
    registry,
    context: HandlerContext,
    path_params: dict[str, object],
) -> dict[str, object]:
    if context.durable_exports is None:
        raise DependencyUnavailable("export worker is unavailable")

    # Reporting progress once per artifact (not just once at the
    # start and once at the end) matters for more than visibility:
    # every checkpoint call renews the operation's lease. Exporting
    # uploads and verifies every artifact over real HTTP round trips
    # to Supabase Storage - with a dozen artifacts that easily
    # exceeds a single lease window if nothing renews it in between,
    # which previously surfaced as the operation silently losing its
    # lease mid-export and getting stuck retrying.
    def report_export_progress(current: int, total: int) -> None:
        context.checkpoint("uploading_and_verifying_artifacts", current, total)

    result = context.durable_exports.export_plan(
        owner_id=context.owner_id,
        authorization=context.authorization,
        plan_id=str(path_params["plan_id"]),
        on_progress=report_export_progress,
    )
    context.checkpoint("export_verified", 1, 1)
    return {
        "export_id": result["id"],
        "status": result["status"],
        "artifact_manifest": result.get("artifact_manifest"),
    }


def verify_export(
    registry,
    context: HandlerContext,
    path_params: dict[str, object],
) -> dict[str, object]:
    if context.durable_exports is None:
        raise DependencyUnavailable("export worker is unavailable")

    def report_verify_progress(current: int, total: int) -> None:
        context.checkpoint("verifying_export_artifacts", current, total)

    result = context.durable_exports.verify_export(
        owner_id=context.owner_id,
        authorization=context.authorization,
        export_id=str(path_params["export_id"]),
        on_progress=report_verify_progress,
    )
    context.checkpoint("export_artifacts_verified", 1, 1)
    return {
        "export_id": result["export_id"],
        "valid": result["valid"],
        "status": result["status"],
    }


def start_execution_run(
    registry,
    context: HandlerContext,
    path_params: dict[str, object],
    payload: dict[str, object],
) -> dict[str, object]:
    context.checkpoint("starting_execution", 1, 2)
    if context.durable_exports is not None:
        result = context.durable_exports.start_execution_run(
            owner_id=context.owner_id,
            plan_id=str(path_params["plan_id"]),
            runtime_system=str(payload.get("runtime_system", "")),
            runtime_run_id=str(payload.get("runtime_run_id", "")),
            export_id=(
                str(payload["export_id"])
                if payload.get("export_id") is not None
                else None
            ),
        )
    else:
        result = ExecutionService(context.database).start_run(
            plan_id=str(path_params["plan_id"]),
            runtime_system=str(payload.get("runtime_system", "")),
            runtime_run_id=str(payload.get("runtime_run_id", "")),
            export_id=(
                str(payload["export_id"])
                if payload.get("export_id") is not None
                else None
            ),
        )
    context.checkpoint("execution_started", 2, 2)
    return {
        "run_id": result["id"],
        "status": result["status"],
    }
