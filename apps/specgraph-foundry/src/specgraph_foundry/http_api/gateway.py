from __future__ import annotations

from .gateway_models import RouteMetadata
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




def application_version() -> str:
    try:
        return version(
            "specgraph-foundry"
        )
    except PackageNotFoundError:
        return "development"


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
        common_headers = {
            "x-request-id": request.request_id,
            "cache-control": "no-store",
        }

        claim: ClaimResult | None = None

        try:
            path = urlparse(
                request.raw_path
            ).path

            parts = [
                part
                for part in path.split("/")
                if part
            ]

            if (
                request.method == "GET"
                and path == "/health"
            ):
                return ApiResponse(
                    status=200,
                    body={
                        "status": "ok",
                        "service": (
                            "specgraph-foundry"
                        ),
                        "version": (
                            application_version()
                        ),
                        "backend": (
                            self.database.backend
                        ),
                    },
                    headers=common_headers,
                )

            if (
                request.method == "GET"
                and path == "/health/live"
            ):
                return ApiResponse(
                    status=200,
                    body=live_response(),
                    headers=common_headers,
                )

            if (
                request.method == "GET"
                and path == "/health/startup"
            ):
                status, body = startup_response(
                    self.database
                )
                return ApiResponse(
                    status=status,
                    body=body,
                    headers=common_headers,
                )

            if (
                request.method == "GET"
                and path == "/health/ready"
            ):
                status, body = readiness_response(
                    self.database,
                    storage_ready=True,
                    operations_ready=(
                        self.operations is not None
                    ),
                )
                return ApiResponse(
                    status=status,
                    body=body,
                    headers=common_headers,
                )

            try:
                if (
                    request.method == "GET"
                    and path == "/version"
                ):
                    return ApiResponse(
                        status=200,
                        body={
                            "service": (
                                "specgraph-foundry"
                            ),
                            "version": (
                                application_version()
                            ),
                            "api_version": "v1",
                        },
                        headers=common_headers,
                    )

                principal = (
                    self.authenticator.authenticate(
                        request.authorization
                    )
                )

            except AuthenticationError as error:
                return authentication_error_response(
                    error,
                    request_id=request.request_id,
                    authorization=request.authorization,
                )

            if (
                request.method == "GET"
                and path == "/v1/me"
            ):
                return ApiResponse(
                    status=200,
                    body={
                        "user": principal.public(),
                    },
                    headers=common_headers,
                )

            request_database = (
                RequestScopedDatabase(
                    self.database,
                    principal,
                )
            )

            legacy_api = Api(
                request_database,
                cursor_signing_key=(
                    self.cursor_signing_key
                ),
            )

            if (
                request.method == "GET"
                and len(parts) == 4
                and parts[:2] == [
                    "v1",
                    "projects",
                ]
                and parts[3] in {
                    "workspace",
                    "readiness",
                }
            ):
                workspace = (
                    ProjectWorkspaceService(
                        request_database
                    )
                )

                try:
                    if parts[3] == "workspace":
                        body = workspace.get(
                            parts[2]
                        )
                    else:
                        body = workspace.readiness(
                            parts[2]
                        )
                except NotFoundError:
                    return not_found_response(
                        request_id=request.request_id
                    )

                return ApiResponse(
                    status=200,
                    body=body,
                    headers=common_headers,
                )

            if request.method == "GET":
                try:
                    body = self._workspace_body(
                        request_database,
                        parts,
                    )
                except NotFoundError:
                    return not_found_response(
                        request_id=request.request_id
                    )

                if body is not None:
                    return ApiResponse(
                        status=200,
                        body=body,
                        headers=common_headers,
                    )

            route = self._route_metadata(
                request.method,
                parts,
            )

            if (
                self.enforce_mutation_guards
                and route is not None
                and route.idempotency_required
            ):
                claim_result = (
                    self._claim_idempotency(
                        route,
                        request_database,
                        request,
                        common_headers,
                    )
                )

                if isinstance(
                    claim_result,
                    ApiResponse,
                ):
                    return claim_result

                claim = claim_result

                if claim.replay is not None:
                    if (
                        route.operation
                        == "create_source_upload_intent"
                        and self.source_uploads is not None
                        and claim.record.resource_id
                    ):
                        replay_body = (
                            self.source_uploads.replay_create_intent(
                                owner_id=principal.user_id,
                                authorization=(
                                    request.authorization
                                    or ""
                                ),
                                upload_id=claim.record.resource_id,
                            )
                        )
                        return self._success_response(
                            status=201,
                            body=replay_body,
                            headers=common_headers,
                            route=route,
                            replayed=True,
                        )

                    return self._success_response(
                        status=claim.replay.status,
                        body=claim.replay.body,
                        headers=common_headers,
                        route=route,
                        replayed=True,
                    )

            if (
                self.enforce_mutation_guards
                and route is not None
                and route.concurrency_kind
                is not None
            ):
                precondition = (
                    self._enforce_concurrency(
                        request_database,
                        legacy_api,
                        route,
                        request,
                    )
                )

                if precondition is not None:
                    if claim is not None:
                        self.idempotency.mark_failed(
                            record_id=claim.record.id,
                            http_status=precondition.status,
                            response_body=precondition.body,
                        )
                    return precondition

            operation_api_response = (
                self._operation_api_response(
                    principal_user_id=principal.user_id,
                    request=request,
                    parts=parts,
                    route=route,
                    claim=claim,
                    common_headers=common_headers,
                )
            )

            if operation_api_response is not None:
                return operation_api_response

            async_response = self._async_operation_submission(
                principal_user_id=principal.user_id,
                request=request,
                parts=parts,
                route=route,
                claim=claim,
                common_headers=common_headers,
            )

            if async_response is not None:
                return async_response

            source_upload_response = (
                self._source_upload_response(
                    principal_user_id=principal.user_id,
                    request=request,
                    parts=parts,
                    route=route,
                    claim=claim,
                    common_headers=common_headers,
                )
            )

            if source_upload_response is not None:
                return source_upload_response

            durable_export_response = (
                self._durable_export_response(
                    principal_user_id=principal.user_id,
                    request=request,
                    parts=parts,
                    route=route,
                    claim=claim,
                    common_headers=common_headers,
                )
            )

            if durable_export_response is not None:
                return durable_export_response

            status, body = legacy_api.dispatch(
                request.method,
                request.raw_path,
                request.payload,
            )

            if status >= 400:
                failure = normalize_legacy_failure(
                    status=status,
                    body=body,
                    request_id=request.request_id,
                    headers=common_headers,
                )

                if claim is not None:
                    self.idempotency.mark_failed(
                        record_id=claim.record.id,
                        http_status=failure.status,
                        response_body=failure.body,
                    )

                return failure

            response = self._success_response(
                status=status,
                body=body,
                headers={
                    **common_headers,
                    **legacy_api.response_headers,
                },
                route=route,
                replayed=(
                    False
                    if claim is not None
                    else None
                ),
            )

            if claim is not None:
                resource_type, resource_id = (
                    self._resource_reference(
                        route,
                        response.body,
                    )
                )
                self.idempotency.mark_succeeded(
                    record_id=claim.record.id,
                    http_status=response.status,
                    response_body=response.body,
                    resource_type=resource_type,
                    resource_id=resource_id,
                )

            return response
        except Exception:
            failure = internal_error_response(
                request_id=request.request_id
            )

            if claim is not None:
                self.idempotency.mark_failed(
                    record_id=claim.record.id,
                    http_status=failure.status,
                    response_body=failure.body,
                )

            return failure

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
