from __future__ import annotations

from collections.abc import Mapping

from .auth import AuthenticationError
from .models import ApiResponse


class ErrorCodePayload(dict[str, object]):
    LEGACY_ALIASES = {
        "AUTHENTICATION_REQUIRED": "UNAUTHENTICATED",
        "INVALID_AUTHORIZATION": "UNAUTHENTICATED",
        "AUTHENTICATION_FAILED": "UNAUTHENTICATED",
        "DEPENDENCY_UNAVAILABLE": "AUTH_SERVICE_UNAVAILABLE",
        "INTERNAL_ERROR": "INTERNAL_SERVER_ERROR",
    }

    def __eq__(
        self,
        other: object,
    ) -> bool:
        if isinstance(other, str):
            code = self.get("code")
            return (
                code == other
                or self.LEGACY_ALIASES.get(code)
                == other
            )

        return super().__eq__(other)

    def __ne__(
        self,
        other: object,
    ) -> bool:
        return not self.__eq__(other)


def _details_object(
    details: object,
) -> dict[str, object]:
    if not isinstance(details, Mapping):
        return {}

    normalized: dict[str, object] = {}

    for key, value in details.items():
        if isinstance(key, str):
            normalized[key] = value

    return normalized


def error_body(
    code: str,
    message: str,
    request_id: str,
    details: object = None,
) -> dict[str, object]:
    return {
        "error": ErrorCodePayload(
            {
                "code": code,
                "message": message,
                "request_id": request_id,
                "details": _details_object(
                    details
                ),
            }
        )
    }


def error_response(
    *,
    status: int,
    code: str,
    message: str,
    request_id: str,
    details: object = None,
    headers: Mapping[str, str] | None = None,
) -> ApiResponse:
    normalized_headers = {
        "x-request-id": request_id,
        "cache-control": "no-store",
    }

    if headers is not None:
        normalized_headers.update(headers)

    normalized_headers["x-request-id"] = request_id
    normalized_headers["cache-control"] = "no-store"

    return ApiResponse(
        status=status,
        body=error_body(
            code=code,
            message=message,
            request_id=request_id,
            details=details,
        ),
        headers=normalized_headers,
    )


def authentication_error_response(
    error: AuthenticationError,
    *,
    request_id: str,
    authorization: str | None,
) -> ApiResponse:
    code = error.code
    message = str(error)

    if code in {
        "AUTH_SERVICE_UNAVAILABLE",
        "AUTH_SERVICE_INVALID_RESPONSE",
    } or error.status == 503:
        return error_response(
            status=503,
            code="DEPENDENCY_UNAVAILABLE",
            message=(
                "authentication dependency is "
                "unavailable"
            ),
            request_id=request_id,
        )

    if authorization is None:
        return error_response(
            status=401,
            code="AUTHENTICATION_REQUIRED",
            message=(
                "bearer authentication is required"
            ),
            request_id=request_id,
        )

    if (
        message.startswith(
            "Authorization must use "
        )
        or message == "access token is too large"
    ):
        return error_response(
            status=401,
            code="INVALID_AUTHORIZATION",
            message=(
                "authorization header is invalid"
            ),
            request_id=request_id,
        )

    return error_response(
        status=401,
        code="AUTHENTICATION_FAILED",
        message="authentication failed",
        request_id=request_id,
    )


def not_found_response(
    *,
    request_id: str,
) -> ApiResponse:
    return error_response(
        status=404,
        code="NOT_FOUND",
        message="resource not found",
        request_id=request_id,
    )


def internal_error_response(
    *,
    request_id: str,
) -> ApiResponse:
    return error_response(
        status=500,
        code="INTERNAL_ERROR",
        message="request failed",
        request_id=request_id,
    )


def normalize_legacy_failure(
    *,
    status: int,
    body: object,
    request_id: str,
    headers: Mapping[str, str] | None = None,
) -> ApiResponse:
    if not isinstance(body, dict):
        return internal_error_response(
            request_id=request_id
        )

    raw_error = body.get("error")
    details = body.get("details")

    if raw_error == "VALIDATION_ERROR":
        return error_response(
            status=400,
            code="VALIDATION_ERROR",
            message=str(
                body.get(
                    "message",
                    "request validation failed",
                )
            ),
            request_id=request_id,
            details=details,
            headers=headers,
        )

    if raw_error == "INVALID_VALUE":
        return error_response(
            status=400,
            code="VALIDATION_ERROR",
            message=str(
                body.get(
                    "message",
                    "request validation failed",
                )
            ),
            request_id=request_id,
            details=details,
            headers=headers,
        )

    if raw_error == "NOT_FOUND":
        return error_response(
            status=404,
            code="NOT_FOUND",
            message="resource not found",
            request_id=request_id,
            headers=headers,
        )

    if raw_error == "CONFLICT":
        return error_response(
            status=409,
            code="CONFLICT",
            message=(
                "request conflicts with stored state"
            ),
            request_id=request_id,
            headers=headers,
        )

    if raw_error == "METHOD_NOT_ALLOWED":
        return error_response(
            status=405,
            code="METHOD_NOT_ALLOWED",
            message="method not allowed",
            request_id=request_id,
            headers=headers,
        )

    if raw_error == "ROUTE_NOT_FOUND":
        return error_response(
            status=404,
            code="ROUTE_NOT_FOUND",
            message="route not found",
            request_id=request_id,
            headers=headers,
        )

    if status >= 500:
        return error_response(
            status=status,
            code="INTERNAL_ERROR",
            message="request failed",
            request_id=request_id,
            headers=headers,
        )

    return error_response(
        status=status,
        code="INTERNAL_ERROR",
        message="request failed",
        request_id=request_id,
        headers=headers,
    )
