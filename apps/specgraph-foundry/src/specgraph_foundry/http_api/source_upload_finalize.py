"""Finalising an upload: the bytes arrive and become a document.

The second half, and where every content-level failure lives -- integrity
mismatch, undecodable text, an encrypted or oversized document. It claims the
intent first so two finalisations of the same upload cannot both proceed.
"""

from __future__ import annotations

from .source_upload_claim import (
    claim_for_finalization,
    decode_client_provided_bytes,
    mark_failed,
)

import binascii
from hmac import compare_digest
from .source_upload_helpers import display_status
from .source_upload_helpers import download_with_retry
from .source_upload_helpers import existing_document
from .source_upload_helpers import finalized_response
from .source_upload_helpers import normalize_media_type
from .source_upload_helpers import restore_pending
from .source_upload_helpers import FINALIZING_TTL_SECONDS
from .source_upload_helpers import *  # noqa: F401,F403
from ..errors import ConflictError, NotFoundError, ValidationError
from .document_security import DependencyUnavailableError
from .document_security import DocumentEncryptedError
from .document_security import DocumentLimitExceededError
from .document_security import InvalidDocumentError
from .document_security import NoExtractableTextError
from .idempotency import parse_time
from .source_upload_errors import DocumentEncryptedUploadError
from .source_upload_errors import DocumentLimitExceededUploadError
from .source_upload_errors import InvalidDocumentUploadError
from .source_upload_errors import InvalidSourceEncodingError
from .source_upload_errors import NoExtractableTextUploadError
from .source_upload_errors import UploadDependencyUnavailableError
from .source_upload_errors import UploadExpiredError
from .source_upload_errors import UploadIntegrityMismatchError
from .source_upload_errors import UploadStateConflictError
from .storage import DownloadedObject
from .storage import StorageDependencyError
from .storage import StorageObjectMissingError
from .storage import StorageObjectTooLargeError
from .storage import StorageProtocolError
from .storage import _utc_now
from datetime import timedelta
import base64
import hashlib
import uuid
import json


def finalize(
    service,
    *,
    owner_id: str,
    authorization: str,
    upload_id: str,
    raw_base64: str | None = None,
) -> dict[str, object]:
    row = claim_for_finalization(service, 
        owner_id=owner_id,
        upload_id=upload_id,
    )
    upload = dict(row)

    if upload["document_id"] is not None:
        return finalized_response(service, 
            upload_id=upload_id,
            document_id=str(upload["document_id"]),
            upload_status="FINALIZED",
        )

    if raw_base64 is not None:
        # The browser already holds the exact bytes it just PUT to
        # Supabase Storage - verifying against those directly avoids
        # depending on Supabase's authenticated download route, which
        # has been observed reporting a freshly-uploaded object as
        # missing well past any reasonable consistency window.
        downloaded = decode_client_provided_bytes(service, 
            raw_base64,
            upload_id,
        )
    else:
        try:
            downloaded = download_with_retry(service, 
                authorization=authorization,
                bucket=str(upload["bucket"]),
                object_path=str(upload["object_path"]),
                max_bytes=service.settings.max_source_bytes,
            )
        except StorageObjectMissingError as error:
            restore_pending(service, upload_id)
            raise UploadStateConflictError(
                "upload bytes are not available"
            ) from error
        except StorageObjectTooLargeError as error:
            mark_failed(service, 
                upload_id,
                "SOURCE_TOO_LARGE",
            )
            raise ValidationError(
                "source exceeds the configured maximum"
            ) from error
        except (
            StorageDependencyError,
            StorageProtocolError,
        ) as error:
            restore_pending(service, upload_id)
            raise UploadDependencyUnavailableError(
                "storage dependency is unavailable"
            ) from error

    expected_bytes = int(upload["expected_bytes"])
    expected_sha256 = str(upload["expected_sha256"])
    actual_sha256 = hashlib.sha256(
        downloaded.data
    ).hexdigest()
    actual_bytes = len(downloaded.data)

    if actual_bytes != expected_bytes or not compare_digest(
        actual_sha256,
        expected_sha256,
    ):
        mark_failed(service, 
            upload_id,
            "UPLOAD_INTEGRITY_MISMATCH",
            actual_bytes=actual_bytes,
            actual_sha256=actual_sha256,
        )
        raise UploadIntegrityMismatchError(
            "uploaded source failed integrity verification"
        )

    declared_media_type = str(
        upload["declared_media_type"]
    )
    storage_media_type = (
        normalize_media_type(service, 
            downloaded.media_type
        )
        if downloaded.media_type
        else None
    )

    try:
        derivation = service.adapters.derive_text(
            filename=str(upload["original_filename"]),
            declared_media_type=declared_media_type,
            storage_media_type=storage_media_type,
            raw=downloaded.data,
        )
    except InvalidDocumentError as error:
        if "UTF-8" in str(error) or "binary content" in str(error):
            mark_failed(service, 
                upload_id,
                "INVALID_SOURCE_ENCODING",
                actual_bytes=actual_bytes,
                actual_sha256=actual_sha256,
            )
            raise InvalidSourceEncodingError(
                str(error)
            ) from error
        mark_failed(service, 
            upload_id,
            "INVALID_DOCUMENT",
            actual_bytes=actual_bytes,
            actual_sha256=actual_sha256,
        )
        raise InvalidDocumentUploadError(
            str(error)
        ) from error
    except DocumentEncryptedError as error:
        mark_failed(service, 
            upload_id,
            "DOCUMENT_ENCRYPTED",
            actual_bytes=actual_bytes,
            actual_sha256=actual_sha256,
        )
        raise DocumentEncryptedUploadError(
            str(error)
        ) from error
    except DocumentLimitExceededError as error:
        mark_failed(service, 
            upload_id,
            "DOCUMENT_LIMIT_EXCEEDED",
            actual_bytes=actual_bytes,
            actual_sha256=actual_sha256,
        )
        raise DocumentLimitExceededUploadError(
            str(error)
        ) from error
    except NoExtractableTextError as error:
        mark_failed(service, 
            upload_id,
            "NO_EXTRACTABLE_TEXT",
            actual_bytes=actual_bytes,
            actual_sha256=actual_sha256,
        )
        raise NoExtractableTextUploadError(
            str(error)
        ) from error
    except DependencyUnavailableError as error:
        restore_pending(service, upload_id)
        raise UploadDependencyUnavailableError(
            "document adapter dependency is unavailable"
        ) from error

    derived_bytes = derivation.derived_bytes
    derived_sha256 = hashlib.sha256(
        derived_bytes
    ).hexdigest()

    try:
        document = service.ingestion.ingest_uploaded_bytes(
            project_id=str(upload["project_id"]),
            title=str(upload["original_filename"]),
            raw=derived_bytes,
            media_type=declared_media_type,
            source_upload_id=upload_id,
        )
    except ConflictError:
        document = existing_document(service, 
            project_id=str(upload["project_id"]),
            sha256=derived_sha256,
        )
    except ValidationError as error:
        mark_failed(service, 
            upload_id,
            "INVALID_DOCUMENT",
            actual_bytes=actual_bytes,
            actual_sha256=actual_sha256,
        )
        raise InvalidDocumentUploadError(
            str(error)
        ) from error

    now = _utc_now().isoformat()

    with service.database.connect() as connection:
        connection.execute(
            """
            INSERT INTO document_derivations(
                id,
                owner_id,
                project_id,
                source_upload_id,
                source_document_id,
                adapter_name,
                adapter_version,
                original_media_type,
                detected_media_type,
                original_byte_count,
                original_sha256,
                derived_byte_count,
                derived_sha256,
                status,
                metadata_json,
                created_at
            )
            VALUES(?,?,?,?,?,?,?,?,?,?,?,?,?,?,?,?)
            ON CONFLICT(source_upload_id) DO UPDATE SET
                source_document_id = excluded.source_document_id,
                adapter_name = excluded.adapter_name,
                adapter_version = excluded.adapter_version,
                original_media_type = excluded.original_media_type,
                detected_media_type = excluded.detected_media_type,
                original_byte_count = excluded.original_byte_count,
                original_sha256 = excluded.original_sha256,
                derived_byte_count = excluded.derived_byte_count,
                derived_sha256 = excluded.derived_sha256,
                status = excluded.status,
                metadata_json = excluded.metadata_json,
                created_at = excluded.created_at
            """,
            (
                str(uuid.uuid4()),
                owner_id,
                str(upload["project_id"]),
                upload_id,
                str(document["id"]),
                derivation.adapter_name,
                derivation.adapter_version,
                declared_media_type,
                derivation.detected_media_type,
                actual_bytes,
                actual_sha256,
                len(derived_bytes),
                derived_sha256,
                "SUCCEEDED",
                json.dumps(
                    derivation.metadata,
                    sort_keys=True,
                    separators=(",", ":"),
                    ensure_ascii=False,
                ),
                now,
            ),
        )
        connection.execute(
            """
            UPDATE source_uploads
            SET status = 'FINALIZED',
                actual_bytes = ?,
                actual_sha256 = ?,
                document_id = ?,
                failure_code = NULL,
                updated_at = ?,
                finalized_at = ?
            WHERE id = ?
            """,
            (
                actual_bytes,
                actual_sha256,
                str(document["id"]),
                now,
                now,
                upload_id,
            ),
        )

    return finalized_response(service, 
        upload_id=upload_id,
        document_id=str(document["id"]),
        upload_status="FINALIZED",
    )






