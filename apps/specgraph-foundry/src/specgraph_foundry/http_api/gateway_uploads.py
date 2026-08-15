"""Turning a source-upload service call into an HTTP response."""

from __future__ import annotations

from .gateway_models import RouteMetadata
from ..errors import NotFoundError, ValidationError
from .error_contract import error_response
from .error_contract import not_found_response
from .gateway_responses import success_response
from .idempotency import ClaimResult
from .models import ApiRequest
from .models import ApiResponse
from .source_uploads import DocumentEncryptedUploadError
from .source_uploads import DocumentLimitExceededUploadError
from .source_uploads import InvalidDocumentUploadError
from .source_uploads import InvalidSourceEncodingError
from .source_uploads import NoExtractableTextUploadError
from .source_uploads import UploadDependencyUnavailableError
from .source_uploads import UploadExpiredError
from .source_uploads import UploadIntegrityMismatchError
from .source_uploads import UploadStateConflictError
import uuid
import json


def source_upload_response(
    api,
    *,
    principal_user_id: str,
    request: ApiRequest,
    parts: list[str],
    route: RouteMetadata | None,
    claim: ClaimResult | None,
    common_headers: dict[str, str],
) -> ApiResponse | None:
    if api.source_uploads is None:
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
            body = api.source_uploads.create_intent(
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
            body = api.source_uploads.get_status(
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
            body = api.source_uploads.finalize(
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
        api.idempotency.mark_failed(
            record_id=claim.record.id,
            http_status=failure.status,
            response_body=failure.body,
        )

    return failure
