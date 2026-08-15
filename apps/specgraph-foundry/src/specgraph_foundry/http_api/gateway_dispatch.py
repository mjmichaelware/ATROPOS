"""The request pipeline: authenticate, route, guard, serve.

Every request passes through here in the same order -- parse the path, find the
route, authenticate, claim idempotency, enforce concurrency, hand off. It is the
only place that order is expressed, and at 402 lines inside a 2,097-line class
it was the hardest thing in the package to find.
"""

from __future__ import annotations

from urllib.parse import urlparse
from ..errors import NotFoundError
from typing import TYPE_CHECKING

if TYPE_CHECKING:
    from .gateway import Api

from .auth import AuthenticationError
from .database import RequestScopedDatabase
from .error_contract import authentication_error_response
from .error_contract import internal_error_response
from .error_contract import normalize_legacy_failure
from .error_contract import not_found_response
from .gateway_version import application_version
from .health import live_response
from .health import readiness_response
from .health import startup_response
from .idempotency import ClaimResult
from .models import ApiRequest
from .models import ApiResponse
from .workspace import ProjectWorkspaceService
import json

def dispatch(
    api,
    request: ApiRequest,
) -> ApiResponse:
    # Deferred: gateway imports this module, so the legacy Api class
    # cannot be imported at module scope without a cycle.
    from .gateway import Api

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
                        api.database.backend
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
                api.database
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
                api.database,
                storage_ready=True,
                operations_ready=(
                    api.operations is not None
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
                api.authenticator.authenticate(
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
                api.database,
                principal,
            )
        )

        legacy_api = Api(
            request_database,
            cursor_signing_key=(
                api.cursor_signing_key
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
                body = api._workspace_body(
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

        route = api._route_metadata(
            request.method,
            parts,
        )

        if (
            api.enforce_mutation_guards
            and route is not None
            and route.idempotency_required
        ):
            claim_result = (
                api._claim_idempotency(
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
                    and api.source_uploads is not None
                    and claim.record.resource_id
                ):
                    replay_body = (
                        api.source_uploads.replay_create_intent(
                            owner_id=principal.user_id,
                            authorization=(
                                request.authorization
                                or ""
                            ),
                            upload_id=claim.record.resource_id,
                        )
                    )
                    return api._success_response(
                        status=201,
                        body=replay_body,
                        headers=common_headers,
                        route=route,
                        replayed=True,
                    )

                return api._success_response(
                    status=claim.replay.status,
                    body=claim.replay.body,
                    headers=common_headers,
                    route=route,
                    replayed=True,
                )

        if (
            api.enforce_mutation_guards
            and route is not None
            and route.concurrency_kind
            is not None
        ):
            precondition = (
                api._enforce_concurrency(
                    request_database,
                    legacy_api,
                    route,
                    request,
                )
            )

            if precondition is not None:
                if claim is not None:
                    api.idempotency.mark_failed(
                        record_id=claim.record.id,
                        http_status=precondition.status,
                        response_body=precondition.body,
                    )
                return precondition

        operation_api_response = (
            api._operation_api_response(
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

        async_response = api._async_operation_submission(
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
            api._source_upload_response(
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
            api._durable_export_response(
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
                api.idempotency.mark_failed(
                    record_id=claim.record.id,
                    http_status=failure.status,
                    response_body=failure.body,
                )

            return failure

        response = api._success_response(
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
                api._resource_reference(
                    route,
                    response.body,
                )
            )
            api.idempotency.mark_succeeded(
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
            api.idempotency.mark_failed(
                record_id=claim.record.id,
                http_status=failure.status,
                response_body=failure.body,
            )

        return failure
