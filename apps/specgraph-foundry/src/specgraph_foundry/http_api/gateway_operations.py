"""Async operation submission and the response an operation produces.

The seam between a request that returns immediately and work that continues
after it.
"""

from __future__ import annotations

from .gateway_models import RouteMetadata
from ..errors import ConflictError, NotFoundError, ValidationError
from .error_contract import error_response
from .error_contract import not_found_response
from .gateway_responses import success_response
from .idempotency import ClaimResult
from .models import ApiRequest
from .models import ApiResponse
from .operation_handlers import ASYNC_OPERATION_TYPES
from .operations import is_operation_path
from .operations import operation_location
import json


def operation_api_response(
    api,
    *,
    principal_user_id: str,
    request: ApiRequest,
    parts: list[str],
    route: RouteMetadata | None,
    claim: ClaimResult | None,
    common_headers: dict[str, str],
) -> ApiResponse | None:
    if api.operations is None:
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
                    "operation": api.operations.get(
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
            body = api.operations.cancel(
                owner_id=principal_user_id,
                operation_id=parts[2],
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
            and parts[:2] == ["v1", "projects"]
            and parts[3] == "operations"
            and request.method == "GET"
        ):
            items, page_headers = api.operations.list_project(
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
        api.idempotency.mark_failed(
            record_id=claim.record.id,
            http_status=failure.status,
            response_body=failure.body,
        )
    return failure


def async_operation_submission(
    api,
    *,
    principal_user_id: str,
    request: ApiRequest,
    parts: list[str],
    route: RouteMetadata | None,
    claim: ClaimResult | None,
    common_headers: dict[str, str],
) -> ApiResponse | None:
    if (
        api.operations is None
        or api.operation_handlers is None
        or route is None
        or route.operation not in ASYNC_OPERATION_TYPES
        or is_operation_path(request.raw_path)
    ):
        return None

    try:
        safe_request = api.operation_handlers.safe_request(
            operation_type=route.operation,
            path_params=route.path_params,
            payload=request.payload,
        )
        project_id = api.operation_handlers.project_id_for_request(
            operation_type=route.operation,
            path_params=route.path_params,
            payload=request.payload,
        )
        operation = api.operations.submit(
            owner_id=principal_user_id,
            project_id=project_id,
            operation_type=route.operation,
            request=safe_request,
        )
        if api.worker_trigger is not None:
            api.worker_trigger.kick()
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
                api.operations.settings.poll_seconds
            ),
        }
        response = success_response(
            api,
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
            api.idempotency.mark_succeeded(
                record_id=claim.record.id,
                http_status=response.status,
                response_body=response.body,
                resource_type=route.operation,
                resource_id=str(operation["id"]),
            )
        return response

    if claim is not None:
        api.idempotency.mark_failed(
            record_id=claim.record.id,
            http_status=failure.status,
            response_body=failure.body,
        )
    return failure
