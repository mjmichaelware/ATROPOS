"""Claiming an upload for finalisation, decoding its bytes, and failing it.

The bookkeeping around finalisation: taking the intent so two finalisations
cannot both proceed, turning client-provided bytes into content, and recording a
failure without losing the reason.

Separate from :mod:`source_upload_finalize` because that module decides whether
the content is acceptable and this one decides whether we are allowed to look at
it yet.
"""

from __future__ import annotations

from .idempotency import parse_time
from .source_upload_errors import InvalidDocumentUploadError
from .source_upload_errors import UploadExpiredError
from .source_upload_errors import UploadStateConflictError
from .source_upload_helpers import FINALIZING_TTL_SECONDS
from .source_upload_helpers import display_status
from .storage import DownloadedObject
from .storage import _utc_now
import base64
import binascii
import json
from datetime import timedelta
from hmac import compare_digest

from ..errors import ConflictError, NotFoundError, ValidationError
from .source_upload_errors import *  # noqa: F401,F403
from .source_upload_helpers import *  # noqa: F401,F403

def claim_for_finalization(
    service,
    *,
    owner_id: str,
    upload_id: str,
):
    now = _utc_now()
    now_text = now.isoformat()
    expires_at = (
        now
        + timedelta(
            seconds=FINALIZING_TTL_SECONDS
        )
    ).isoformat()

    while True:
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

            upload = dict(row)
            status = display_status(service, upload)

            if status == "EXPIRED":
                connection.execute(
                    """
                    UPDATE source_uploads
                    SET status = 'EXPIRED',
                        updated_at = ?
                    WHERE id = ?
                    """,
                    (now_text, upload_id),
                )
                raise UploadExpiredError(
                    "upload intent has expired"
                )

            if status == "FINALIZED":
                return row

            if status == "FAILED":
                raise UploadStateConflictError(
                    "upload cannot be finalized"
                )

            if status == "FINALIZING":
                stale = (
                    parse_time(service, 
                        str(upload["expires_at"])
                    )
                    <= now
                )
                if not stale:
                    raise UploadStateConflictError(
                        "upload finalization is already in progress"
                    )

            updated = connection.execute(
                """
                UPDATE source_uploads
                SET status = 'FINALIZING',
                    updated_at = ?,
                    expires_at = ?
                WHERE id = ?
                  AND owner_id = ?
                  AND (
                    status IN ('PENDING', 'UPLOADED')
                    OR (
                        status = 'FINALIZING'
                        AND expires_at <= ?
                    )
                  )
                """,
                (
                    now_text,
                    expires_at,
                    upload_id,
                    owner_id,
                    now_text,
                ),
            )

            if updated.rowcount != 1:
                continue

            refreshed = connection.execute(
                """
                SELECT *
                FROM source_uploads
                WHERE id = ?
                  AND owner_id = ?
                """,
                (upload_id, owner_id),
            ).fetchone()

            if refreshed is None:
                raise NotFoundError(
                    f"source upload not found: {upload_id}"
                )

            return refreshed


def decode_client_provided_bytes(
    service,
    raw_base64: str,
    upload_id: str,
) -> DownloadedObject:
    try:
        data = base64.b64decode(
            raw_base64,
            validate=True,
        )
    except (binascii.Error, ValueError) as error:
        mark_failed(service, 
            upload_id,
            "INVALID_DOCUMENT",
        )
        raise InvalidDocumentUploadError(
            "uploaded source bytes are not valid base64"
        ) from error

    if len(data) > service.settings.max_source_bytes:
        mark_failed(service, 
            upload_id,
            "SOURCE_TOO_LARGE",
        )
        raise ValidationError(
            "source exceeds the configured maximum"
        )

    return DownloadedObject(
        data=data,
        media_type=None,
    )


def mark_failed(
    service,
    upload_id: str,
    failure_code: str,
    *,
    actual_bytes: int | None = None,
    actual_sha256: str | None = None,
) -> None:
    now = _utc_now().isoformat()
    with service.database.connect() as connection:
        connection.execute(
            """
            UPDATE source_uploads
            SET status = 'FAILED',
                actual_bytes = ?,
                actual_sha256 = ?,
                failure_code = ?,
                updated_at = ?
            WHERE id = ?
            """,
            (
                actual_bytes,
                actual_sha256,
                failure_code,
                now,
                upload_id,
            ),
        )
