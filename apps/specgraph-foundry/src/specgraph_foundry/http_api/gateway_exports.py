"""Turning a durable-export service call into an HTTP response."""

from __future__ import annotations

from .gateway_models import RouteMetadata
from ..errors import NotFoundError, ValidationError
from .artifact_storage import ArtifactAlreadyExistsError
from .artifact_storage import ArtifactIntegrityError
from .artifact_storage import ArtifactStorageUnavailableError
from .durable_exports import ArtifactLimitExceededError
from .durable_exports import ArtifactNotVerifiedError
from .error_contract import error_response
from .error_contract import not_found_response
from .gateway_responses import success_response
from .idempotency import ClaimResult
from .models import ApiRequest
from .models import ApiResponse
import json


def durable_export_response(
    api,
    *,
    principal_user_id: str,
    request: ApiRequest,
    parts: list[str],
    route: RouteMetadata | None,
    claim: ClaimResult | None,
    common_headers: dict[str, str],
) -> ApiResponse | None:
    if api.durable_exports is None:
        return None

    try:
        if (
            len(parts) == 4
            and parts[:2] == ["v1", "plans"]
            and parts[3] == "exports"
            and request.method == "POST"
        ):
            body = api.durable_exports.export_plan(
                owner_id=principal_user_id,
                authorization=(
                    request.authorization or ""
                ),
                plan_id=parts[2],
            )
            response = success_response(
                api,
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
                api.idempotency.mark_succeeded(
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
            body = api.durable_exports.get_export(
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
            body = api.durable_exports.verify_export(
                owner_id=principal_user_id,
                authorization=(
                    request.authorization or ""
                ),
                export_id=parts[2],
            )
            response = success_response(
                api,
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
                api.idempotency.mark_succeeded(
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
            body = api.durable_exports.download(
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
                "items": api.durable_exports.list_exports(
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
            body = api.durable_exports.start_execution_run(
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
            response = success_response(
                api,
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
                api.idempotency.mark_succeeded(
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
        api.idempotency.mark_failed(
            record_id=claim.record.id,
            http_status=failure.status,
            response_body=failure.body,
        )

    return failure
