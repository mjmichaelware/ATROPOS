"""Idempotency claims and optimistic concurrency enforcement.

The two checks that decide whether a mutation may proceed: has this exact
request already been served, and is the caller acting on the version they think
they are.
"""

from __future__ import annotations

from typing import TYPE_CHECKING

if TYPE_CHECKING:
    from .gateway import Api

from .gateway_models import RouteMetadata
from ..errors import ConflictError, ValidationError
from .concurrency import validate_if_match
from .database import RequestScopedDatabase
from .error_contract import error_response
from .gateway_etags import editable_resource
from .gateway_etags import etag_for_resource
from .idempotency import ClaimResult
from .idempotency import canonical_request_hash
from .models import ApiRequest
from .models import ApiResponse
import json


def claim_idempotency(
    api,
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
        claim = api.idempotency.claim(
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


def enforce_concurrency(
    api,
    database: RequestScopedDatabase,
    legacy_api: Api,
    route: RouteMetadata,
    request: ApiRequest,
) -> ApiResponse | None:
    current = editable_resource(
        api,
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

    expected = etag_for_resource(
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
