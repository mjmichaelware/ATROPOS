"""Summarising uploads for a project view.

Read-only. Distinguishes raw authority from derived content, which is the
distinction the provenance tests exist to protect.
"""

from __future__ import annotations

from .source_upload_helpers import display_status
from .source_upload_helpers import *  # noqa: F401,F403
from ..errors import NotFoundError
from .pagination import WORKSPACE_PREVIEW_LIMIT
import json


def list_project_upload_previews(
    service,
    *,
    owner_id: str,
    project_id: str,
) -> tuple[list[dict[str, object]], int]:
    with service.database.connect() as connection:
        total = int(
            connection.execute(
                """
                SELECT COUNT(*) AS value
                FROM source_uploads
                WHERE owner_id = ?
                  AND project_id = ?
                """,
                (owner_id, project_id),
            ).fetchone()["value"]
        )
        rows = connection.execute(
            """
            SELECT
                id,
                status,
                original_filename,
                declared_media_type,
                expected_bytes,
                failure_code,
                created_at,
                updated_at,
                expires_at,
                document_id
            FROM source_uploads
            WHERE owner_id = ?
              AND project_id = ?
            ORDER BY created_at DESC, id DESC
            LIMIT ?
            """,
            (
                owner_id,
                project_id,
                WORKSPACE_PREVIEW_LIMIT,
            ),
        ).fetchall()

    items: list[dict[str, object]] = []

    for row in rows:
        upload = dict(row)
        item = {
            "id": str(upload["id"]),
            "status": display_status(service, upload),
            "filename": str(
                upload["original_filename"]
            ),
            "media_type": str(
                upload["declared_media_type"]
            ),
            "byte_size": int(
                upload["expected_bytes"]
            ),
            "failure_code": (
                str(upload["failure_code"])
                if upload["failure_code"]
                else None
            ),
            "created_at": str(
                upload["created_at"]
            ),
            "updated_at": str(
                upload["updated_at"]
            ),
            "detail_route": (
                f"/v1/source-uploads/{upload['id']}"
            ),
        }

        if upload["document_id"] is not None:
            item["document_id"] = str(
                upload["document_id"]
            )
            item["document_route"] = (
                f"/v1/documents/{upload['document_id']}"
            )

        items.append(item)

    return items, total


def raw_authority_summary(
    service,
    upload_id: str,
) -> dict[str, object]:
    with service.database.connect() as connection:
        row = connection.execute(
            """
            SELECT
                id,
                bucket,
                object_path,
                declared_media_type,
                actual_bytes,
                actual_sha256
            FROM source_uploads
            WHERE id = ?
            """,
            (upload_id,),
        ).fetchone()

    if row is None:
        raise NotFoundError(
            f"source upload not found: {upload_id}"
        )

    return {
        "source_upload_id": str(row["id"]),
        "bucket": str(row["bucket"]),
        "object_path": str(row["object_path"]),
        "original_media_type": str(
            row["declared_media_type"]
        ),
        "byte_count": int(row["actual_bytes"]),
        "sha256": str(row["actual_sha256"]),
    }


def derivation_summary(
    service,
    upload_id: str,
) -> dict[str, object]:
    with service.database.connect() as connection:
        row = connection.execute(
            """
            SELECT
                adapter_name,
                adapter_version,
                detected_media_type,
                derived_byte_count,
                derived_sha256,
                metadata_json,
                created_at
            FROM document_derivations
            WHERE source_upload_id = ?
            """,
            (upload_id,),
        ).fetchone()

    if row is None:
        raise NotFoundError(
            f"document derivation not found for upload: {upload_id}"
        )

    metadata = json.loads(
        str(row["metadata_json"])
    )

    return {
        "adapter_name": str(row["adapter_name"]),
        "adapter_version": str(
            row["adapter_version"]
        ),
        "detected_media_type": str(
            row["detected_media_type"]
        ),
        "derived_byte_count": int(
            row["derived_byte_count"]
        ),
        "derived_sha256": str(
            row["derived_sha256"]
        ),
        "locator_kind": str(
            metadata.get("locator_kind", "document")
        ),
        "locators_preview": list(
            metadata.get("locators_preview", [])
        ),
        "locators_count": int(
            metadata.get("locator_count", 0)
        ),
        "locators_has_more": bool(
            metadata.get("locators_has_more", False)
        ),
        "created_at": str(row["created_at"]),
    }
