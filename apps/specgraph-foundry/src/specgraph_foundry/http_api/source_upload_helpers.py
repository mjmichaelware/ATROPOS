"""Shared helpers and constants for the upload lifecycle.

Ownership checks, media-type and size validation, the signed target, response
shapes and the retry around a storage download -- used by both halves of an
upload and belonging to neither.
"""

from __future__ import annotations

from ..errors import ConflictError, NotFoundError, ValidationError
from .document_security import InvalidDocumentError
from .document_security import assert_extension_matches_media_type
from .document_security import assert_supported_declared_media_type
from .storage import DownloadedObject
from .storage import SignedUploadTarget
from .storage import StorageObjectMissingError
from .storage_models import _utc_now
import re
import base64
import binascii
import hashlib
import json
import uuid
from datetime import timedelta
from hmac import compare_digest

SHA256_PATTERN = re.compile(r"^[0-9a-f]{64}$")


WORKSPACE_PREVIEW_LIMIT = 5


PLACEHOLDER_SIGNING_KEY = "replace-with-a-long-random-secret"


FINALIZING_TTL_SECONDS = 900


TERMINAL_UPLOAD_STATUSES = {
    "FINALIZED",
    "FAILED",
    "EXPIRED",
}


def download_with_retry(
    service,
    **kwargs: object,
) -> DownloadedObject:
    last_error: StorageObjectMissingError | None = None
    for delay in service._DOWNLOAD_RETRY_DELAYS_SECONDS:
        if delay:
            service._sleep(delay)
        try:
            return service.storage.download_object(**kwargs)
        except StorageObjectMissingError as error:
            last_error = error
    assert last_error is not None
    raise last_error


def restore_pending(
    service,
    upload_id: str,
) -> None:
    with service.database.connect() as connection:
        connection.execute(
            """
            UPDATE source_uploads
            SET status = 'PENDING',
                updated_at = ?
            WHERE id = ?
            """,
            (_utc_now().isoformat(), upload_id),
        )


def existing_document(
    service,
    *,
    project_id: str,
    sha256: str,
) -> dict[str, object]:
    with service.database.connect() as connection:
        row = connection.execute(
            """
            SELECT id
            FROM source_documents
            WHERE project_id = ?
              AND sha256 = ?
            """,
            (project_id, sha256),
        ).fetchone()

    if row is None:
        raise ConflictError(
            "source already exists in this project"
        )

    return service.ingestion.get_document(
        str(row["id"])
    )


def get_owned_upload(
    service,
    *,
    owner_id: str,
    upload_id: str,
):
    with service.database.connect() as connection:
        row = connection.execute(
            """
            SELECT *
            FROM source_uploads
            WHERE id = ?
              AND owner_id = ?
            """,
            (upload_id, owner_id),
        ).fetchone()

    if row is None:
        raise NotFoundError(
            f"source upload not found: {upload_id}"
        )

    return row


def create_intent_response(
    service,
    *,
    upload_id: str,
    project_id: str,
    status: str,
    bucket: str,
    object_path: str,
    original_filename: str,
    declared_media_type: str,
    expected_bytes: int,
    signed: SignedUploadTarget,
) -> dict[str, object]:
    return {
        "id": upload_id,
        "project_id": project_id,
        "status": status,
        "bucket": bucket,
        "object_path": object_path,
        "filename": original_filename,
        "media_type": declared_media_type,
        "byte_size": expected_bytes,
        "signed_upload_url": signed.url,
        "signed_url_expires_at": signed.expires_at,
        "required_upload_headers": signed.required_headers,
    }


def finalized_response(
    service,
    *,
    upload_id: str,
    document_id: str,
    upload_status: str,
) -> dict[str, object]:
    return {
        "upload_id": upload_id,
        "status": upload_status,
        "document_id": document_id,
        "document_route": f"/v1/documents/{document_id}",
        "document": service.ingestion.get_document(
            document_id
        ),
        "raw_authority": service._raw_authority_summary(
            upload_id
        ),
        "derivation": service._derivation_summary(
            upload_id
        ),
    }


def signed_target(
    service,
    *,
    authorization: str,
    bucket: str,
    object_path: str,
) -> SignedUploadTarget:
    return service.storage.create_signed_upload_target(
        authorization=authorization,
        bucket=bucket,
        object_path=object_path,
        ttl_seconds=service.settings.upload_url_ttl_seconds,
    )


def validate_byte_size(
    service,
    value: int,
) -> int:
    if value <= 0:
        raise ValidationError(
            "byte_size must be positive"
        )

    if value > service.settings.max_source_bytes:
        raise ValidationError(
            "source exceeds the configured maximum"
        )

    return value


def normalize_media_type(
    service,
    value: str,
) -> str:
    primary = str(value).split(";", 1)[0]
    normalized = primary.strip().lower()

    if not normalized:
        raise ValidationError(
            "media_type is required"
        )

    return normalized


def validate_media_type(
    service,
    filename: str,
    media_type: str,
) -> None:
    try:
        assert_supported_declared_media_type(
            media_type
        )
        assert_extension_matches_media_type(
            filename,
            media_type,
        )
    except InvalidDocumentError as error:
        raise ValidationError(str(error)) from error


def display_status(
    service,
    row: dict[str, object],
) -> str:
    status = str(row["status"])

    if status in TERMINAL_UPLOAD_STATUSES:
        return status

    if service._parse_time(
        str(row["expires_at"])
    ) <= _utc_now():
        return "EXPIRED"

    return status
