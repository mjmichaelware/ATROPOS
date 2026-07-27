from __future__ import annotations

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


@dataclass(frozen=True)
class RouteMetadata:
    operation: str
    path_params: dict[str, str]
    idempotency_required: bool = False
    response_etag_kind: str | None = None
    collection_etag_kind: str | None = None
    concurrency_kind: str | None = None


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
        if self.source_uploads is None:
            return None

        try:
            if (
                len(parts) == 4
                and parts[:2] == ["v1", "projects"]
                and parts[3] == "source-uploads"
                and request.method == "POST"
            ):
                upload_id = (
                    claim.record.id
                    if claim is not None
                    else str(uuid.uuid4())
                )
                body = self.source_uploads.create_intent(
                    owner_id=principal_user_id,
                    authorization=(
                        request.authorization or ""
                    ),
                    project_id=parts[2],
                    upload_id=upload_id,
                    filename=str(
                        request.payload.get(
                            "filename",
                            "",
                        )
                    ),
                    media_type=str(
                        request.payload.get(
                            "media_type",
                            "",
                        )
                    ),
                    byte_size=int(
                        request.payload.get(
                            "byte_size",
                            0,
                        )
                    ),
                    sha256=str(
                        request.payload.get(
                            "sha256",
                            "",
                        )
                    ),
                )
                response = self._success_response(
                    status=201,
                    body=body,
                    headers=common_headers,
                    route=route,
                    replayed=(
                        False
                        if claim is not None
                        else None
                    ),
                )

                if claim is not None and route is not None:
                    self.idempotency.mark_succeeded(
                        record_id=claim.record.id,
                        http_status=response.status,
                        response_body={
                            "upload_id": str(
                                body["id"]
                            )
                        },
                        resource_type=route.operation,
                        resource_id=str(body["id"]),
                    )

                return response

            if (
                len(parts) == 3
                and parts[:2] == ["v1", "source-uploads"]
                and request.method == "GET"
            ):
                body = self.source_uploads.get_status(
                    owner_id=principal_user_id,
                    upload_id=parts[2],
                )
                return ApiResponse(
                    status=200,
                    body=body,
                    headers=common_headers,
                )

            if (
                len(parts) == 4
                and parts[:2] == ["v1", "source-uploads"]
                and parts[3] == "finalize"
                and request.method == "POST"
            ):
                raw_base64 = request.payload.get(
                    "raw_base64"
                )
                body = self.source_uploads.finalize(
                    owner_id=principal_user_id,
                    authorization=(
                        request.authorization or ""
                    ),
                    upload_id=parts[2],
                    raw_base64=(
                        raw_base64
                        if isinstance(raw_base64, str)
                        else None
                    ),
                )
                response = self._success_response(
                    status=201,
                    body=body,
                    headers=common_headers,
                    route=route,
                    replayed=(
                        False
                        if claim is not None
                        else None
                    ),
                )

                if claim is not None and route is not None:
                    self.idempotency.mark_succeeded(
                        record_id=claim.record.id,
                        http_status=response.status,
                        response_body=response.body,
                        resource_type=route.operation,
                        resource_id=str(
                            body["document_id"]
                        ),
                    )

                return response
        except NotFoundError:
            failure = not_found_response(
                request_id=request.request_id
            )
        except ValidationError as error:
            code = "VALIDATION_ERROR"
            status = 400
            message = str(error)

            if "filename" in message:
                code = "INVALID_FILENAME"
            elif (
                "media_type" in message
                or "media type" in message
            ):
                code = "UNSUPPORTED_MEDIA_TYPE"
                status = 415
                message = "source media type is not supported"
            elif "maximum" in message:
                code = "SOURCE_TOO_LARGE"
                status = 413
                message = "source exceeds the configured maximum"

            failure = error_response(
                status=status,
                code=code,
                message=message,
                request_id=request.request_id,
                headers=common_headers,
            )
        except UploadExpiredError:
            failure = error_response(
                status=409,
                code="UPLOAD_EXPIRED",
                message="upload intent has expired",
                request_id=request.request_id,
                headers=common_headers,
            )
        except UploadStateConflictError:
            failure = error_response(
                status=409,
                code="UPLOAD_STATE_CONFLICT",
                message="upload state does not permit this operation",
                request_id=request.request_id,
                headers=common_headers,
            )
        except UploadIntegrityMismatchError:
            failure = error_response(
                status=409,
                code="UPLOAD_INTEGRITY_MISMATCH",
                message="uploaded source failed integrity verification",
                request_id=request.request_id,
                headers=common_headers,
            )
        except InvalidSourceEncodingError:
            failure = error_response(
                status=400,
                code="INVALID_SOURCE_ENCODING",
                message="uploaded source must be valid UTF-8 text",
                request_id=request.request_id,
                headers=common_headers,
            )
        except InvalidDocumentUploadError:
            failure = error_response(
                status=400,
                code="INVALID_DOCUMENT",
                message="uploaded document is invalid",
                request_id=request.request_id,
                headers=common_headers,
            )
        except DocumentEncryptedUploadError:
            failure = error_response(
                status=415,
                code="DOCUMENT_ENCRYPTED",
                message="uploaded document is encrypted",
                request_id=request.request_id,
                headers=common_headers,
            )
        except DocumentLimitExceededUploadError:
            failure = error_response(
                status=413,
                code="DOCUMENT_LIMIT_EXCEEDED",
                message="uploaded document exceeds configured limits",
                request_id=request.request_id,
                headers=common_headers,
            )
        except NoExtractableTextUploadError:
            failure = error_response(
                status=415,
                code="NO_EXTRACTABLE_TEXT",
                message="uploaded document contains no extractable text",
                request_id=request.request_id,
                headers=common_headers,
            )
        except UploadDependencyUnavailableError:
            failure = error_response(
                status=503,
                code="DEPENDENCY_UNAVAILABLE",
                message="document processing dependency is unavailable",
                request_id=request.request_id,
                headers=common_headers,
            )
        else:
            return None

        if claim is not None:
            self.idempotency.mark_failed(
                record_id=claim.record.id,
                http_status=failure.status,
                response_body=failure.body,
            )

        return failure

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
        if self.durable_exports is None:
            return None

        try:
            if (
                len(parts) == 4
                and parts[:2] == ["v1", "plans"]
                and parts[3] == "exports"
                and request.method == "POST"
            ):
                body = self.durable_exports.export_plan(
                    owner_id=principal_user_id,
                    authorization=(
                        request.authorization or ""
                    ),
                    plan_id=parts[2],
                )
                response = self._success_response(
                    status=201,
                    body=body,
                    headers=common_headers,
                    route=route,
                    replayed=(
                        False
                        if claim is not None
                        else None
                    ),
                )

                if claim is not None and route is not None:
                    self.idempotency.mark_succeeded(
                        record_id=claim.record.id,
                        http_status=response.status,
                        response_body=response.body,
                        resource_type=route.operation,
                        resource_id=str(body["id"]),
                    )

                return response

            if (
                len(parts) == 3
                and parts[:2] == ["v1", "exports"]
                and request.method == "GET"
            ):
                body = self.durable_exports.get_export(
                    parts[2],
                    owner_id=principal_user_id,
                )
                return ApiResponse(
                    status=200,
                    body=body,
                    headers=common_headers,
                )

            if (
                len(parts) == 4
                and parts[:2] == ["v1", "exports"]
                and parts[3] == "verify"
                and request.method == "POST"
            ):
                body = self.durable_exports.verify_export(
                    owner_id=principal_user_id,
                    authorization=(
                        request.authorization or ""
                    ),
                    export_id=parts[2],
                )
                response = self._success_response(
                    status=200,
                    body=body,
                    headers=common_headers,
                    route=route,
                    replayed=(
                        False
                        if claim is not None
                        else None
                    ),
                )

                if claim is not None and route is not None:
                    self.idempotency.mark_succeeded(
                        record_id=claim.record.id,
                        http_status=response.status,
                        response_body=response.body,
                        resource_type=route.operation,
                        resource_id=parts[2],
                    )

                return response

            if (
                len(parts) == 4
                and parts[:2] == ["v1", "exports"]
                and parts[3] == "download"
                and request.method == "GET"
            ):
                body = self.durable_exports.download(
                    owner_id=principal_user_id,
                    authorization=(
                        request.authorization or ""
                    ),
                    export_id=parts[2],
                )
                return ApiResponse(
                    status=200,
                    body=body,
                    headers=common_headers,
                )

            if (
                len(parts) == 4
                and parts[:2] == ["v1", "projects"]
                and parts[3] == "exports"
                and request.method == "GET"
            ):
                body = {
                    "items": self.durable_exports.list_exports(
                        parts[2]
                    )
                }
                return ApiResponse(
                    status=200,
                    body=body,
                    headers=common_headers,
                )

            if (
                len(parts) == 4
                and parts[:2] == ["v1", "plans"]
                and parts[3] == "execution-runs"
                and request.method == "POST"
            ):
                body = self.durable_exports.start_execution_run(
                    owner_id=principal_user_id,
                    plan_id=parts[2],
                    runtime_system=str(
                        request.payload.get(
                            "runtime_system",
                            "",
                        )
                    ),
                    runtime_run_id=str(
                        request.payload.get(
                            "runtime_run_id",
                            "",
                        )
                    ),
                    export_id=(
                        str(request.payload["export_id"])
                        if request.payload.get(
                            "export_id"
                        )
                        is not None
                        else None
                    ),
                )
                response = self._success_response(
                    status=201,
                    body=body,
                    headers=common_headers,
                    route=route,
                    replayed=(
                        False
                        if claim is not None
                        else None
                    ),
                )

                if claim is not None and route is not None:
                    self.idempotency.mark_succeeded(
                        record_id=claim.record.id,
                        http_status=response.status,
                        response_body=response.body,
                        resource_type=route.operation,
                        resource_id=str(body["id"]),
                    )

                return response
        except NotFoundError:
            failure = not_found_response(
                request_id=request.request_id
            )
        except ValidationError as error:
            failure = error_response(
                status=400,
                code="VALIDATION_ERROR",
                message=str(error),
                request_id=request.request_id,
                headers=common_headers,
            )
        except ArtifactLimitExceededError:
            failure = error_response(
                status=413,
                code="ARTIFACT_LIMIT_EXCEEDED",
                message="export artifacts exceed configured limits",
                request_id=request.request_id,
                headers=common_headers,
            )
        except ArtifactAlreadyExistsError:
            failure = error_response(
                status=409,
                code="ARTIFACT_ALREADY_EXISTS",
                message="artifact object already exists",
                request_id=request.request_id,
                headers=common_headers,
            )
        except ArtifactNotVerifiedError:
            failure = error_response(
                status=409,
                code="ARTIFACT_NOT_VERIFIED",
                message="export artifacts are not verified",
                request_id=request.request_id,
                headers=common_headers,
            )
        except ArtifactIntegrityError:
            failure = error_response(
                status=409,
                code="ARTIFACT_INTEGRITY_FAILED",
                message="stored artifact bytes failed verification",
                request_id=request.request_id,
                headers=common_headers,
            )
        except ArtifactStorageUnavailableError:
            failure = error_response(
                status=503,
                code="STORAGE_UNAVAILABLE",
                message="artifact storage is unavailable",
                request_id=request.request_id,
                headers=common_headers,
            )
        else:
            return None

        if claim is not None:
            self.idempotency.mark_failed(
                record_id=claim.record.id,
                http_status=failure.status,
                response_body=failure.body,
            )

        return failure

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
        if self.operations is None:
            return None

        try:
            if (
                len(parts) == 3
                and parts[:2] == ["v1", "operations"]
                and request.method == "GET"
            ):
                # Every frontend caller of pollOperation() - extraction,
                # research completion, plan synthesis/verification, handoff,
                # execution, source upload finalization - independently and
                # consistently expects this nested under "operation", matching
                # the shape the 202 submission response already uses. Returning
                # the operation flat here (as this endpoint used to) makes
                # pollOperation's very first status check crash with
                # "Cannot read properties of undefined (reading 'state')".
                return ApiResponse(
                    status=200,
                    body={
                        "operation": self.operations.get(
                            owner_id=principal_user_id,
                            operation_id=parts[2],
                        )
                    },
                    headers=common_headers,
                )

            if (
                len(parts) == 4
                and parts[:2] == ["v1", "operations"]
                and parts[3] == "cancel"
                and request.method == "POST"
            ):
                body = self.operations.cancel(
                    owner_id=principal_user_id,
                    operation_id=parts[2],
                )
                response = self._success_response(
                    status=200,
                    body=body,
                    headers=common_headers,
                    route=route,
                    replayed=(
                        False
                        if claim is not None
                        else None
                    ),
                )
                if claim is not None and route is not None:
                    self.idempotency.mark_succeeded(
                        record_id=claim.record.id,
                        http_status=response.status,
                        response_body=response.body,
                        resource_type=route.operation,
                        resource_id=parts[2],
                    )
                return response

            if (
                len(parts) == 4
                and parts[:2] == ["v1", "projects"]
                and parts[3] == "operations"
                and request.method == "GET"
            ):
                items, page_headers = self.operations.list_project(
                    owner_id=principal_user_id,
                    project_id=parts[2],
                    raw_path=request.raw_path,
                )
                return ApiResponse(
                    status=200,
                    body={"items": items},
                    headers={
                        **common_headers,
                        **page_headers,
                    },
                )
        except NotFoundError:
            failure = not_found_response(
                request_id=request.request_id
            )
        except ConflictError:
            failure = error_response(
                status=409,
                code="OPERATION_CONFLICT",
                message="operation state conflicts with the request",
                request_id=request.request_id,
                headers=common_headers,
            )
        except ValidationError as error:
            failure = error_response(
                status=400,
                code="VALIDATION_ERROR",
                message=str(error),
                request_id=request.request_id,
                headers=common_headers,
            )
        else:
            return None

        if claim is not None:
            self.idempotency.mark_failed(
                record_id=claim.record.id,
                http_status=failure.status,
                response_body=failure.body,
            )
        return failure

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
        if (
            self.operations is None
            or self.operation_handlers is None
            or route is None
            or route.operation not in ASYNC_OPERATION_TYPES
            or is_operation_path(request.raw_path)
        ):
            return None

        try:
            safe_request = self.operation_handlers.safe_request(
                operation_type=route.operation,
                path_params=route.path_params,
                payload=request.payload,
            )
            project_id = self.operation_handlers.project_id_for_request(
                operation_type=route.operation,
                path_params=route.path_params,
                payload=request.payload,
            )
            operation = self.operations.submit(
                owner_id=principal_user_id,
                project_id=project_id,
                operation_type=route.operation,
                request=safe_request,
            )
            if self.worker_trigger is not None:
                self.worker_trigger.kick()
        except NotFoundError:
            failure = not_found_response(
                request_id=request.request_id
            )
        except ValidationError as error:
            failure = error_response(
                status=400,
                code="VALIDATION_ERROR",
                message=str(error),
                request_id=request.request_id,
                headers=common_headers,
            )
        else:
            headers = {
                **common_headers,
                "location": operation_location(operation),
                "retry-after": str(
                    self.operations.settings.poll_seconds
                ),
            }
            response = self._success_response(
                status=202,
                body={"operation": operation},
                headers=headers,
                route=route,
                replayed=(
                    False
                    if claim is not None
                    else None
                ),
            )
            if claim is not None:
                self.idempotency.mark_succeeded(
                    record_id=claim.record.id,
                    http_status=response.status,
                    response_body=response.body,
                    resource_type=route.operation,
                    resource_id=str(operation["id"]),
                )
            return response

        if claim is not None:
            self.idempotency.mark_failed(
                record_id=claim.record.id,
                http_status=failure.status,
                response_body=failure.body,
            )
        return failure

    def _claim_idempotency(
        self,
        route: RouteMetadata,
        database: RequestScopedDatabase,
        request: ApiRequest,
        headers: dict[str, str],
    ) -> ClaimResult | ApiResponse:
        key = request.idempotency_key

        if key is None:
            return error_response(
                status=400,
                code="IDEMPOTENCY_KEY_REQUIRED",
                message="Idempotency-Key is required",
                request_id=request.request_id,
                headers=headers,
            )

        try:
            claim = self.idempotency.claim(
                owner_id=database.owner_id or "",
                operation=route.operation,
                idempotency_key=key,
                request_hash=canonical_request_hash(
                    operation=route.operation,
                    owner_id=database.owner_id
                    or "",
                    route_params=route.path_params,
                    payload=request.payload,
                ),
            )
        except ValidationError:
            return error_response(
                status=400,
                code="INVALID_IDEMPOTENCY_KEY",
                message="Idempotency-Key is invalid",
                request_id=request.request_id,
                headers=headers,
            )
        except ConflictError as error:
            if "different request" in str(error):
                return error_response(
                    status=409,
                    code="IDEMPOTENCY_KEY_REUSED",
                    message=(
                        "Idempotency-Key has already been used for a different request"
                    ),
                    request_id=request.request_id,
                    headers=headers,
                )

            return error_response(
                status=409,
                code="IDEMPOTENCY_IN_PROGRESS",
                message=(
                    "an equivalent request is already in progress"
                ),
                request_id=request.request_id,
                headers=headers,
            )

        return claim

    def _enforce_concurrency(
        self,
        database: RequestScopedDatabase,
        legacy_api: Api,
        route: RouteMetadata,
        request: ApiRequest,
    ) -> ApiResponse | None:
        current = self._editable_resource(
            database,
            legacy_api,
            route,
            request.payload,
        )

        if current is None:
            return None

        if request.if_match is None:
            return error_response(
                status=428,
                code="PRECONDITION_REQUIRED",
                message="If-Match is required",
                request_id=request.request_id,
            )

        try:
            provided = validate_if_match(
                request.if_match
            )
        except ValidationError:
            return error_response(
                status=400,
                code="INVALID_PRECONDITION",
                message="If-Match is invalid",
                request_id=request.request_id,
            )

        expected = self._etag_for_resource(
            route.concurrency_kind,
            current,
        )

        if provided != expected:
            return error_response(
                status=412,
                code="PRECONDITION_FAILED",
                message="resource has changed",
                request_id=request.request_id,
            )

        return None

    def _success_response(
        self,
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
            response = self._decorate_collection_etags(
                response,
                route.collection_etag_kind,
            )

        if (
            route.response_etag_kind
            is not None
        ):
            response = self._decorate_resource_etag(
                response,
                route.response_etag_kind,
            )

        return response

    def _decorate_collection_etags(
        self,
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
                self._etag_for_resource(
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

    def _decorate_resource_etag(
        self,
        response: ApiResponse,
        kind: str,
    ) -> ApiResponse:
        if not isinstance(response.body, dict):
            return response

        etag = self._etag_for_resource(
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

    def _editable_resource(
        self,
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

    @staticmethod
    def _etag_for_resource(
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

    @staticmethod
    def _resource_reference(
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

    @staticmethod
    def _workspace_body(
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

    @staticmethod
    def _route_metadata(
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
