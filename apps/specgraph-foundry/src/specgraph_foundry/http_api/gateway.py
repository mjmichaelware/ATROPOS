from __future__ import annotations

from .gateway_models import RouteMetadata
from .gateway_dispatch import dispatch
from .gateway_version import application_version
from .gateway_concurrency import claim_idempotency, enforce_concurrency
from .gateway_etags import (
    decorate_collection_etags,
    decorate_resource_etag,
    editable_resource,
    etag_for_resource,
    resource_reference,
)
from .gateway_exports import durable_export_response
from .gateway_operations import async_operation_submission, operation_api_response
from .gateway_responses import success_response, workspace_body
from .gateway_routes import route_metadata
from .gateway_uploads import source_upload_response

import uuid
from dataclasses import dataclass
from importlib.metadata import (
    PackageNotFoundError,
    version,
)
from urllib.parse import urlparse

from ..database import Database
from ..errors import ConflictError, NotFoundError, ValidationError
from .auth import (
    AuthenticationError,
    SupabaseAuthClient,
)
from .concurrency import (
    binding_etag,
    provider_etag,
    renderer_etag,
    routing_policy_etag,
    validate_if_match,
)
from .database import RequestScopedDatabase
from .artifact_storage import (
    ArtifactAlreadyExistsError,
    ArtifactIntegrityError,
    ArtifactStorageUnavailableError,
)
from .durable_exports import (
    ArtifactLimitExceededError,
    ArtifactNotVerifiedError,
    DurableExportService,
)
from .error_contract import (
    authentication_error_response,
    error_response,
    internal_error_response,
    normalize_legacy_failure,
    not_found_response,
)
from .handoff_workspace import (
    HandoffWorkspaceService,
)
from .health import (
    live_response,
    readiness_response,
    startup_response,
)
from .idempotency import (
    ClaimResult,
    IdempotencyStore,
    canonical_request_hash,
)
from .models import (
    ApiRequest,
    ApiResponse,
)
from .operation_handlers import (
    ASYNC_OPERATION_TYPES,
    OperationHandlerRegistry,
)
from .operations import (
    OperationStore,
    is_operation_path,
    operation_location,
)
from .planning_workspace import (
    PlanningWorkspaceService,
)
from .research_workspace import (
    ResearchWorkspaceService,
)
from .source_workspace import (
    SourceWorkspaceService,
)
from .source_uploads import (
    DocumentEncryptedUploadError,
    DocumentLimitExceededUploadError,
    InvalidDocumentUploadError,
    InvalidSourceEncodingError,
    NoExtractableTextUploadError,
    SourceUploadService,
    UploadDependencyUnavailableError,
    UploadExpiredError,
    UploadIntegrityMismatchError,
    UploadStateConflictError,
)
from .worker_trigger import CloudRunWorkerTrigger
from .workspace import (
    ProjectWorkspaceService,
)


class Api:
    """Lazy proxy preserving the historical patchable gateway.Api symbol."""

    def __init__(self, *args: object, **kwargs: object) -> None:
        from ..api import Api as LegacyApi

        self._delegate = LegacyApi(*args, **kwargs)

    def dispatch(
        self,
        *args: object,
        **kwargs: object,
    ) -> tuple[int, dict[str, object]]:
        return self._delegate.dispatch(*args, **kwargs)

    def __getattr__(self, name: str) -> object:
        return getattr(self._delegate, name)






class AuthenticatedApi:
    def __init__(
        self,
        database: Database,
        authenticator: SupabaseAuthClient,
        cursor_signing_key: str | None = None,
        source_uploads: SourceUploadService | None = None,
        durable_exports: DurableExportService | None = None,
        operations: OperationStore | None = None,
        operation_handlers: OperationHandlerRegistry | None = None,
        worker_trigger: CloudRunWorkerTrigger | None = None,
        *,
        enforce_mutation_guards: bool = False,
    ) -> None:
        self.database = database
        self.authenticator = authenticator
        self.cursor_signing_key = (
            cursor_signing_key
        )
        self.enforce_mutation_guards = (
            enforce_mutation_guards
        )
        self.idempotency = IdempotencyStore(
            database
        )
        self.source_uploads = source_uploads
        self.durable_exports = durable_exports
        self.operations = operations
        self.operation_handlers = operation_handlers
        self.worker_trigger = worker_trigger

    def dispatch(
        self,
        request: ApiRequest,
    ) -> ApiResponse:
        """Delegates to :func:`gateway_dispatch.dispatch`."""
        return dispatch(self, request)


    def _source_upload_response(
        self,
        *,
        principal_user_id: str,
        request: ApiRequest,
        parts: list[str],
        route: RouteMetadata | None,
        claim: ClaimResult | None,
        common_headers: dict[str, str],
    ) -> ApiResponse | None:
        """Delegates to :func:`gateway_uploads.source_upload_response`."""
        return source_upload_response(
            self,
            principal_user_id=principal_user_id,
            request=request,
            parts=parts,
            route=route,
            claim=claim,
            common_headers=common_headers,
        )


    def _durable_export_response(
        self,
        *,
        principal_user_id: str,
        request: ApiRequest,
        parts: list[str],
        route: RouteMetadata | None,
        claim: ClaimResult | None,
        common_headers: dict[str, str],
    ) -> ApiResponse | None:
        """Delegates to :func:`gateway_exports.durable_export_response`."""
        return durable_export_response(
            self,
            principal_user_id=principal_user_id,
            request=request,
            parts=parts,
            route=route,
            claim=claim,
            common_headers=common_headers,
        )


    def _operation_api_response(
        self,
        *,
        principal_user_id: str,
        request: ApiRequest,
        parts: list[str],
        route: RouteMetadata | None,
        claim: ClaimResult | None,
        common_headers: dict[str, str],
    ) -> ApiResponse | None:
        """Delegates to :func:`gateway_operations.operation_api_response`."""
        return operation_api_response(
            self,
            principal_user_id=principal_user_id,
            request=request,
            parts=parts,
            route=route,
            claim=claim,
            common_headers=common_headers,
        )


    def _async_operation_submission(
        self,
        *,
        principal_user_id: str,
        request: ApiRequest,
        parts: list[str],
        route: RouteMetadata | None,
        claim: ClaimResult | None,
        common_headers: dict[str, str],
    ) -> ApiResponse | None:
        """Delegates to :func:`gateway_operations.async_operation_submission`."""
        return async_operation_submission(
            self,
            principal_user_id=principal_user_id,
            request=request,
            parts=parts,
            route=route,
            claim=claim,
            common_headers=common_headers,
        )


    def _claim_idempotency(
        self,
        route: RouteMetadata,
        database: RequestScopedDatabase,
        request: ApiRequest,
        headers: dict[str, str],
    ) -> ClaimResult | ApiResponse:
        """Delegates to :func:`gateway_concurrency.claim_idempotency`."""
        return claim_idempotency(
            self,
            route,
            database,
            request,
            headers,
        )


    def _enforce_concurrency(
        self,
        database: RequestScopedDatabase,
        legacy_api: Api,
        route: RouteMetadata,
        request: ApiRequest,
    ) -> ApiResponse | None:
        """Delegates to :func:`gateway_concurrency.enforce_concurrency`."""
        return enforce_concurrency(
            self,
            database,
            legacy_api,
            route,
            request,
        )


    def _success_response(
        self,
        *,
        status: int,
        body: dict[str, object],
        headers: dict[str, str],
        route: RouteMetadata | None,
        replayed: bool | None,
    ) -> ApiResponse:
        """Delegates to :func:`gateway_responses.success_response`."""
        return success_response(
            self,
            status=status,
            body=body,
            headers=headers,
            route=route,
            replayed=replayed,
        )


    def _decorate_collection_etags(
        self,
        response: ApiResponse,
        kind: str,
    ) -> ApiResponse:
        """Delegates to :func:`gateway_etags.decorate_collection_etags`."""
        return decorate_collection_etags(
            self,
            response,
            kind,
        )


    def _decorate_resource_etag(
        self,
        response: ApiResponse,
        kind: str,
    ) -> ApiResponse:
        """Delegates to :func:`gateway_etags.decorate_resource_etag`."""
        return decorate_resource_etag(
            self,
            response,
            kind,
        )


    def _editable_resource(
        self,
        database: RequestScopedDatabase,
        legacy_api: Api,
        route: RouteMetadata,
        payload: dict[str, object],
    ) -> dict[str, object] | None:
        """Delegates to :func:`gateway_etags.editable_resource`."""
        return editable_resource(
            self,
            database,
            legacy_api,
            route,
            payload,
        )


    @staticmethod
    def _etag_for_resource(
        kind: str | None,
        resource: dict[str, object],
    ) -> str:
        """Delegates to :func:`gateway_etags.etag_for_resource`."""
        return etag_for_resource(
            kind,
            resource,
        )


    @staticmethod
    def _resource_reference(
        route: RouteMetadata,
        body: dict[str, object],
    ) -> tuple[str | None, str | None]:
        """Delegates to :func:`gateway_etags.resource_reference`."""
        return resource_reference(
            route,
            body,
        )


    @staticmethod
    def _workspace_body(
        database: Database,
        parts: list[str],
    ) -> dict[str, object] | None:
        """Delegates to :func:`gateway_responses.workspace_body`."""
        return workspace_body(
            database,
            parts,
        )


    @staticmethod
    def _route_metadata(
        method: str,
        parts: list[str],
    ) -> RouteMetadata | None:
        """Delegates to :func:`gateway_routes.route_metadata`."""
        return route_metadata(
            method,
            parts,
        )



def new_request(
    method: str,
    raw_path: str,
    headers: dict[str, str],
    payload: dict[str, object],
) -> ApiRequest:
    return ApiRequest(
        method=method.upper(),
        raw_path=raw_path,
        headers={
            key.casefold(): value
            for key, value in headers.items()
        },
        payload=payload,
        request_id=str(uuid.uuid4()),
    )
