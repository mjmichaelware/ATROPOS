"""The project-level source workspace view.

Everything a client needs to render one project's sources at once: documents,
upload state, derivation provenance and counts. 316 lines because it answers a
whole screen in one call, which is deliberate -- the alternative is a client
making a dozen round trips and rendering them out of order.
"""

from __future__ import annotations

from .source_workspace_helpers import *  # noqa: F401,F403
from ..errors import NotFoundError
from .source_upload_helpers import WORKSPACE_PREVIEW_LIMIT
from .source_workspace_document import upload_status
import json


def get_project(
    service,
    project_id: str,
) -> dict[str, object]:
    with service.database.connect() as connection:
        project = connection.execute(
            """
            SELECT
                id,
                slug,
                name,
                description,
                created_at
            FROM projects
            WHERE id = ?
            """,
            (project_id,),
        ).fetchone()

        if project is None:
            raise NotFoundError(
                f"project not found: {project_id}"
            )

        document_total = count(
            connection,
            """
            SELECT COUNT(*) AS value
            FROM source_documents
            WHERE project_id = ?
            """,
            (project_id,),
        )
        upload_total = count(
            connection,
            """
            SELECT COUNT(*) AS value
            FROM source_uploads
            WHERE owner_id = ?
              AND project_id = ?
            """,
            (
                service.database.owner_id or "",
                project_id,
            ),
        )
        upload_rows = connection.execute(
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
                service.database.owner_id or "",
                project_id,
                WORKSPACE_PREVIEW_LIMIT,
            ),
        ).fetchall()

        document_rows = connection.execute(
            """
            SELECT
                id,
                project_id,
                title,
                media_type,
                sha256,
                byte_count,
                line_count,
                created_at
            FROM source_documents
            WHERE project_id = ?
            ORDER BY created_at DESC, id DESC
            LIMIT ?
            """,
            (
                project_id,
                WORKSPACE_PREVIEW_LIMIT,
            ),
        ).fetchall()

        document_items = []

        for document_row in document_rows:
            document = dict(document_row)
            document_id = str(document["id"])
            document["section_count"] = count(
                connection,
                """
                SELECT COUNT(*) AS value
                FROM source_sections
                WHERE document_id = ?
                """,
                (document_id,),
            )
            document["chunk_count"] = count(
                connection,
                """
                SELECT COUNT(*) AS value
                FROM source_chunks
                WHERE document_id = ?
                """,
                (document_id,),
            )
            document["atom_count"] = count(
                connection,
                """
                SELECT COUNT(*) AS value
                FROM atoms
                WHERE document_id = ?
                """,
                (document_id,),
            )
            document["latest_ingestion"] = latest(
                connection,
                """
                SELECT
                    id,
                    status,
                    chunk_bytes,
                    section_count,
                    chunk_count,
                    covered_bytes,
                    coverage_sha256,
                    error_message,
                    created_at,
                    completed_at
                FROM ingestion_runs
                WHERE document_id = ?
                ORDER BY created_at DESC, id DESC
                LIMIT 1
                """,
                (document_id,),
            )
            document["latest_extraction"] = latest(
                connection,
                """
                SELECT
                    id,
                    extractor_version,
                    source_sha256,
                    status,
                    scanned_bytes,
                    scanned_lines,
                    statement_count,
                    atom_count,
                    dimension_count,
                    research_task_count,
                    error_message,
                    created_at,
                    completed_at
                FROM extraction_runs
                WHERE document_id = ?
                ORDER BY created_at DESC, id DESC
                LIMIT 1
                """,
                (document_id,),
            )
            document_items.append(document)

        summary = {
            "documents": document_total,
            "bytes": int(
                scalar(
                    connection,
                    """
                    SELECT COALESCE(SUM(byte_count), 0) AS value
                    FROM source_documents
                    WHERE project_id = ?
                    """,
                    (project_id,),
                )
            ),
            "lines": int(
                scalar(
                    connection,
                    """
                    SELECT COALESCE(SUM(line_count), 0) AS value
                    FROM source_documents
                    WHERE project_id = ?
                    """,
                    (project_id,),
                )
            ),
            "sections": count(
                connection,
                """
                SELECT COUNT(*) AS value
                FROM source_sections AS section
                JOIN source_documents AS document
                  ON document.id = section.document_id
                WHERE document.project_id = ?
                """,
                (project_id,),
            ),
            "chunks": count(
                connection,
                """
                SELECT COUNT(*) AS value
                FROM source_chunks AS chunk
                JOIN source_documents AS document
                  ON document.id = chunk.document_id
                WHERE document.project_id = ?
                """,
                (project_id,),
            ),
            "atoms": count(
                connection,
                """
                SELECT COUNT(*) AS value
                FROM atoms
                WHERE project_id = ?
                """,
                (project_id,),
            ),
            "completed_ingestions": count(
                connection,
                """
                SELECT COUNT(*) AS value
                FROM ingestion_runs
                WHERE project_id = ?
                  AND status = 'COMPLETE'
                """,
                (project_id,),
            ),
            "completed_extractions": count(
                connection,
                """
                SELECT COUNT(*) AS value
                FROM extraction_runs
                WHERE project_id = ?
                  AND status = 'COMPLETE'
                """,
                (project_id,),
            ),
        }
        upload_items = []

        for upload_row in upload_rows:
            upload = dict(upload_row)
            item = {
                "id": str(upload["id"]),
                "status": upload_status(
                    upload
                ),
                "filename": str(
                    upload[
                        "original_filename"
                    ]
                ),
                "media_type": str(
                    upload[
                        "declared_media_type"
                    ]
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

            upload_items.append(item)

    return {
        "project": dict(project),
        "summary": summary,
        "documents": document_items,
        "documents_count": document_total,
        "documents_has_more": (
            document_total
            > len(document_items)
        ),
        "documents_route": (
            f"/v1/projects/{project_id}/documents"
        ),
        "uploads": upload_items,
        "uploads_count": upload_total,
        "uploads_has_more": (
            upload_total > len(upload_items)
        ),
        "uploads_route": "/v1/source-uploads/{upload_id}",
    }
