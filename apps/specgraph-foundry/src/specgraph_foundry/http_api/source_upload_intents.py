"""Creating an upload intent, replaying one, and reporting its state.

The first half of an upload: the client asks where to put bytes and gets a
signed target back. Separate from finalisation because nothing here has seen the
bytes yet -- every check is about the request, not the content.
"""

from __future__ import annotations

from .source_upload_helpers import create_intent_response
from .source_upload_helpers import display_status
from .source_upload_helpers import get_owned_upload
from .source_upload_helpers import normalize_media_type
from .source_upload_helpers import signed_target
from .source_upload_helpers import validate_byte_size
from .source_upload_helpers import validate_media_type
from .source_upload_helpers import *  # noqa: F401,F403
from ..errors import ConflictError, NotFoundError, ValidationError
from .document_security import InvalidDocumentError
from .document_security import normalized_filename
from .document_security import validate_sha256
from .storage import _utc_now
import json


def create_intent(
    service,
    *,
    owner_id: str,
    authorization: str,
    project_id: str,
    upload_id: str,
    filename: str,
    media_type: str,
    byte_size: int,
    sha256: str,
) -> dict[str, object]:
    try:
        original_filename = normalized_filename(
            filename
        )
    except InvalidDocumentError as error:
        raise ValidationError(str(error)) from error
    declared_media_type = normalize_media_type(service, 
        media_type
    )
    validate_media_type(service, 
        original_filename,
        declared_media_type,
    )
    expected_bytes = validate_byte_size(service, 
        byte_size
    )
    try:
        expected_sha256 = validate_sha256(
            sha256
        )
    except InvalidDocumentError as error:
        raise ValidationError(str(error)) from error
    object_path = (
        f"{owner_id}/{project_id}/{upload_id}/source"
    )
    now = _utc_now().isoformat()

    with service.database.connect() as connection:
        project = connection.execute(
            """
            SELECT id
            FROM projects
            WHERE id = ?
            """,
            (project_id,),
        ).fetchone()

        if project is None:
            raise NotFoundError(
                f"project not found: {project_id}"
            )

        existing = connection.execute(
            """
            SELECT *
            FROM source_uploads
            WHERE id = ?
            """,
            (upload_id,),
        ).fetchone()

        if existing is None:
            connection.execute(
                """
                INSERT INTO source_uploads(
                    id,
                    owner_id,
                    project_id,
                    bucket,
                    object_path,
                    original_filename,
                    declared_media_type,
                    expected_bytes,
                    expected_sha256,
                    status,
                    created_at,
                    updated_at,
                    expires_at
                )
                VALUES(?,?,?,?,?,?,?,?,?,?,?,?,?)
                """,
                (
                    upload_id,
                    owner_id,
                    project_id,
                    service.settings.bucket,
                    object_path,
                    original_filename,
                    declared_media_type,
                    expected_bytes,
                    expected_sha256,
                    "PENDING",
                    now,
                    now,
                    now,
                ),
            )
        else:
            row = dict(existing)
            if (
                str(row["owner_id"]) != owner_id
                or str(row["project_id"])
                != project_id
                or str(row["object_path"])
                != object_path
                or str(row["declared_media_type"])
                != declared_media_type
                or int(row["expected_bytes"])
                != expected_bytes
                or str(row["expected_sha256"])
                != expected_sha256
                or str(row["original_filename"])
                != original_filename
            ):
                raise ConflictError(
                    "upload intent cannot be reused for a different request"
                )

    signed = signed_target(service, 
        authorization=authorization,
        bucket=service.settings.bucket,
        object_path=object_path,
    )
    expires_at = signed.expires_at

    with service.database.connect() as connection:
        connection.execute(
            """
            UPDATE source_uploads
            SET status = CASE
                WHEN status = 'FINALIZED'
                THEN status
                ELSE 'PENDING'
            END,
                updated_at = ?,
                expires_at = ?
            WHERE id = ?
            """,
            (now, expires_at, upload_id),
        )

    return create_intent_response(service, 
        upload_id=upload_id,
        project_id=project_id,
        status="PENDING",
        bucket=service.settings.bucket,
        object_path=object_path,
        original_filename=original_filename,
        declared_media_type=declared_media_type,
        expected_bytes=expected_bytes,
        signed=signed,
    )


def replay_create_intent(
    service,
    *,
    owner_id: str,
    authorization: str,
    upload_id: str,
) -> dict[str, object]:
    row = get_owned_upload(service, 
        owner_id=owner_id,
        upload_id=upload_id,
    )
    signed = signed_target(service, 
        authorization=authorization,
        bucket=str(row["bucket"]),
        object_path=str(row["object_path"]),
    )
    now = _utc_now().isoformat()

    with service.database.connect() as connection:
        connection.execute(
            """
            UPDATE source_uploads
            SET updated_at = ?,
                expires_at = ?
            WHERE id = ?
            """,
            (
                now,
                signed.expires_at,
                upload_id,
            ),
        )

    return create_intent_response(service, 
        upload_id=str(row["id"]),
        project_id=str(row["project_id"]),
        status=display_status(service, dict(row)),
        bucket=str(row["bucket"]),
        object_path=str(row["object_path"]),
        original_filename=str(
            row["original_filename"]
        ),
        declared_media_type=str(
            row["declared_media_type"]
        ),
        expected_bytes=int(row["expected_bytes"]),
        signed=signed,
    )


def get_status(
    service,
    *,
    owner_id: str,
    upload_id: str,
) -> dict[str, object]:
    row = dict(
        get_owned_upload(service, 
            owner_id=owner_id,
            upload_id=upload_id,
        )
    )
    body = {
        "id": str(row["id"]),
        "project_id": str(row["project_id"]),
        "status": display_status(service, row),
        "bucket": str(row["bucket"]),
        "object_path": str(row["object_path"]),
        "filename": str(row["original_filename"]),
        "media_type": str(
            row["declared_media_type"]
        ),
        "byte_size": int(row["expected_bytes"]),
        "failure_code": (
            str(row["failure_code"])
            if row["failure_code"]
            else None
        ),
        "created_at": str(row["created_at"]),
        "updated_at": str(row["updated_at"]),
        "expires_at": str(row["expires_at"]),
        "finalized_at": (
            str(row["finalized_at"])
            if row["finalized_at"] is not None
            else None
        ),
    }

    if row["document_id"] is not None:
        document_id = str(row["document_id"])
        body["document_id"] = document_id
        body["document_route"] = (
            f"/v1/documents/{document_id}"
        )

    return body
