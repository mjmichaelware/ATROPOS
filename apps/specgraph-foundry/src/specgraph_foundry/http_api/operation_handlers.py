from __future__ import annotations

from .operation_handler_context import *  # noqa: F401,F403
from .operation_handler_impls import complete_research_task, export_plan, finalize_source_upload, start_execution_run, verify_export

from .operation_models import OperationCancelled
from .operation_models import OperationLease
from .operation_models import WorkerLeaseLost
from .operations import OperationStore
from .operation_models import *  # noqa: F401,F403
from ..errors import ConflictError, NotFoundError, ValidationError
from dataclasses import dataclass

from ..atoms import AtomService
from ..database import Database
from ..errors import ConflictError, NotFoundError, ValidationError
from ..execution import ExecutionService
from ..planning import PlanningService
from ..research import ResearchService
from .database import RequestScopedDatabase
from .artifact_storage import ArtifactStoragePermanentError
from .durable_exports import DurableExportService
from .models import Principal
from .source_uploads import SourceUploadService


ASYNC_OPERATION_TYPES = {
    "finalize_source_upload",
    "extract_document_atoms",
    "complete_research_task",
    "synthesize_project_plan",
    "verify_plan",
    "export_plan",
    "verify_export",
    "start_execution_run",
    "verify_execution_run",
}

RETRYABLE_ERROR_CODES = {
    "DEPENDENCY_UNAVAILABLE",
    "STORAGE_UNAVAILABLE",
}




class OperationHandlerRegistry:
    def __init__(
        self,
        database: Database,
        *,
        durable_exports: DurableExportService | None = None,
        source_uploads: SourceUploadService | None = None,
        worker_authorization: str = "Bearer worker",
    ) -> None:
        self.database = database
        self.durable_exports = durable_exports
        self.source_uploads = source_uploads
        self.worker_authorization = worker_authorization

    def project_id_for_request(
        self,
        *,
        operation_type: str,
        path_params: dict[str, str],
        payload: dict[str, object],
    ) -> str:
        if "project_id" in path_params:
            return path_params["project_id"]

        with self.database.connect() as connection:
            if operation_type == "finalize_source_upload":
                row = connection.execute(
                    "SELECT project_id FROM source_uploads WHERE id = ?",
                    (path_params["upload_id"],),
                ).fetchone()
            elif operation_type == "extract_document_atoms":
                row = connection.execute(
                    "SELECT project_id FROM source_documents WHERE id = ?",
                    (path_params["document_id"],),
                ).fetchone()
            elif operation_type == "complete_research_task":
                row = connection.execute(
                    "SELECT project_id FROM research_tasks WHERE id = ?",
                    (path_params["task_id"],),
                ).fetchone()
            elif operation_type in {"verify_plan", "export_plan", "start_execution_run"}:
                row = connection.execute(
                    "SELECT project_id FROM plan_versions WHERE id = ?",
                    (path_params["plan_id"],),
                ).fetchone()
            elif operation_type == "verify_export":
                row = connection.execute(
                    "SELECT project_id FROM exports WHERE id = ?",
                    (path_params["export_id"],),
                ).fetchone()
            elif operation_type == "verify_execution_run":
                row = connection.execute(
                    "SELECT project_id FROM execution_runs WHERE id = ?",
                    (path_params["run_id"],),
                ).fetchone()
            else:
                raise ValidationError("operation type is not asynchronous")

        if row is None:
            raise NotFoundError("resource not found")
        return str(row["project_id"])

    def safe_request(
        self,
        *,
        operation_type: str,
        path_params: dict[str, str],
        payload: dict[str, object],
    ) -> dict[str, object]:
        if operation_type not in ASYNC_OPERATION_TYPES:
            raise ValidationError("operation type is not asynchronous")
        return {
            "path_params": dict(path_params),
            "payload": dict(payload),
        }

    def run(
        self,
        operations: OperationStore,
        lease: OperationLease,
    ) -> dict[str, object]:
        row = lease.operation
        owner_id = str(row["owner_id"])
        scoped = RequestScopedDatabase(
            self.database,
            Principal(user_id=owner_id),
        )
        context = HandlerContext(
            owner_id=owner_id,
            database=scoped,
            operations=operations,
            lease=lease,
            durable_exports=self.durable_exports,
            source_uploads=self.source_uploads,
            authorization=self.worker_authorization,
        )
        request = operations.request_from_operation(row)
        path_params = request.get("path_params")
        payload = request.get("payload")
        if not isinstance(path_params, dict) or not isinstance(payload, dict):
            raise ValidationError("operation request is invalid")

        operation_type = str(row["operation_type"])
        if operation_type == "finalize_source_upload":
            return self._finalize_source_upload(context, path_params, payload)
        if operation_type == "extract_document_atoms":
            return self._extract_document_atoms(context, path_params)
        if operation_type == "complete_research_task":
            return self._complete_research_task(context, path_params, payload)
        if operation_type == "synthesize_project_plan":
            return self._synthesize_project_plan(context, path_params, payload)
        if operation_type == "verify_plan":
            return self._verify_plan(context, path_params)
        if operation_type == "export_plan":
            return self._export_plan(context, path_params)
        if operation_type == "verify_export":
            return self._verify_export(context, path_params)
        if operation_type == "start_execution_run":
            return self._start_execution_run(context, path_params, payload)
        if operation_type == "verify_execution_run":
            return self._verify_execution_run(context, path_params)

        raise ValidationError("operation type is not registered")

    def _finalize_source_upload(
        self,
        context: HandlerContext,
        path_params: dict[str, object],
        payload: dict[str, object],
    ) -> dict[str, object]:
        """Delegates to :func:`operation_handler_impls.finalize_source_upload`."""
        return finalize_source_upload(
            self,
            context,
            path_params,
            payload,
        )


    def _extract_document_atoms(
        self,
        context: HandlerContext,
        path_params: dict[str, object],
    ) -> dict[str, object]:
        context.checkpoint("extracting_atoms", 1, 2)
        result = AtomService(context.database).extract_document(
            str(path_params["document_id"])
        )
        context.checkpoint("atoms_extracted", 2, 2)
        return {
            "document_id": result["document_id"],
            "atom_count": len(result["atoms"]),
        }

    def _complete_research_task(
        self,
        context: HandlerContext,
        path_params: dict[str, object],
        payload: dict[str, object],
    ) -> dict[str, object]:
        """Delegates to :func:`operation_handler_impls.complete_research_task`."""
        return complete_research_task(
            self,
            context,
            path_params,
            payload,
        )


    def _synthesize_project_plan(
        self,
        context: HandlerContext,
        path_params: dict[str, object],
        payload: dict[str, object],
    ) -> dict[str, object]:
        context.checkpoint("synthesizing_plan", 1, 2)
        result = PlanningService(context.database).synthesize(
            str(path_params["project_id"]),
            allow_open_research=bool(
                payload.get("allow_open_research", False)
            ),
        )
        context.checkpoint("plan_synthesized", 2, 2)
        return {
            "plan_id": result["id"],
            "status": result["status"],
        }

    def _verify_plan(
        self,
        context: HandlerContext,
        path_params: dict[str, object],
    ) -> dict[str, object]:
        context.checkpoint("verifying_plan", 1, 2)
        result = PlanningService(context.database).verify_plan(
            str(path_params["plan_id"])
        )
        context.checkpoint("plan_verified", 2, 2)
        return {
            "plan_id": result["plan_id"],
            "valid": result["error_count"] == 0,
            "status": result["status"],
        }

    def _export_plan(
        self,
        context: HandlerContext,
        path_params: dict[str, object],
    ) -> dict[str, object]:
        """Delegates to :func:`operation_handler_impls.export_plan`."""
        return export_plan(
            self,
            context,
            path_params,
        )


    def _verify_export(
        self,
        context: HandlerContext,
        path_params: dict[str, object],
    ) -> dict[str, object]:
        """Delegates to :func:`operation_handler_impls.verify_export`."""
        return verify_export(
            self,
            context,
            path_params,
        )


    def _start_execution_run(
        self,
        context: HandlerContext,
        path_params: dict[str, object],
        payload: dict[str, object],
    ) -> dict[str, object]:
        """Delegates to :func:`operation_handler_impls.start_execution_run`."""
        return start_execution_run(
            self,
            context,
            path_params,
            payload,
        )


    def _verify_execution_run(
        self,
        context: HandlerContext,
        path_params: dict[str, object],
    ) -> dict[str, object]:
        context.checkpoint("verifying_execution", 1, 2)
        result = ExecutionService(context.database).verify_run(
            str(path_params["run_id"])
        )
        context.checkpoint("execution_verified", 2, 2)
        return {
            "run_id": result["run_id"],
            "valid": result["valid"],
            "status": result["status"],
        }




def classify_error(error: Exception) -> tuple[str, str, bool]:
    if isinstance(error, OperationCancelled):
        return "OPERATION_CANCELLED", "operation cancellation requested", False
    if isinstance(error, DependencyUnavailable):
        return "DEPENDENCY_UNAVAILABLE", "required dependency is unavailable", True
    if isinstance(error, ArtifactStoragePermanentError):
        return "STORAGE_CONFIGURATION_ERROR", "artifact storage configuration error", False
    if isinstance(error, NotFoundError):
        return "NOT_FOUND", "resource not found", False
    if isinstance(error, ConflictError):
        return "CONFLICT", "operation conflicts with stored state", False
    if isinstance(error, ValidationError):
        return "VALIDATION_ERROR", str(error), False
    return "INTERNAL_ERROR", "operation failed", True
