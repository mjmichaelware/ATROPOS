import json
from datetime import UTC, datetime
from typing import Any

from ..database import Database
from ..errors import NotFoundError
from .pagination import WORKSPACE_PREVIEW_LIMIT


CONTENT_PREVIEW_CHARS = 4096


class SourceWorkspaceService:
    def __init__(
        self,
        database: Database,
    ) -> None:
        self.database = database

    def get_project(
        self,
        project_id: str,
    ) -> dict[str, object]:
        with self.database.connect() as connection:
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

            document_total = self._count(
                connection,
                """
                SELECT COUNT(*) AS value
                FROM source_documents
                WHERE project_id = ?
                """,
                (project_id,),
            )
            upload_total = self._count(
                connection,
                """
                SELECT COUNT(*) AS value
                FROM source_uploads
                WHERE owner_id = ?
                  AND project_id = ?
                """,
                (
                    self.database.owner_id or "",
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
                    self.database.owner_id or "",
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
                document["section_count"] = self._count(
                    connection,
                    """
                    SELECT COUNT(*) AS value
                    FROM source_sections
                    WHERE document_id = ?
                    """,
                    (document_id,),
                )
                document["chunk_count"] = self._count(
                    connection,
                    """
                    SELECT COUNT(*) AS value
                    FROM source_chunks
                    WHERE document_id = ?
                    """,
                    (document_id,),
                )
                document["atom_count"] = self._count(
                    connection,
                    """
                    SELECT COUNT(*) AS value
                    FROM atoms
                    WHERE document_id = ?
                    """,
                    (document_id,),
                )
                document["latest_ingestion"] = self._latest(
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
                document["latest_extraction"] = self._latest(
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
                    self._scalar(
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
                    self._scalar(
                        connection,
                        """
                        SELECT COALESCE(SUM(line_count), 0) AS value
                        FROM source_documents
                        WHERE project_id = ?
                        """,
                        (project_id,),
                    )
                ),
                "sections": self._count(
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
                "chunks": self._count(
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
                "atoms": self._count(
                    connection,
                    """
                    SELECT COUNT(*) AS value
                    FROM atoms
                    WHERE project_id = ?
                    """,
                    (project_id,),
                ),
                "completed_ingestions": self._count(
                    connection,
                    """
                    SELECT COUNT(*) AS value
                    FROM ingestion_runs
                    WHERE project_id = ?
                      AND status = 'COMPLETE'
                    """,
                    (project_id,),
                ),
                "completed_extractions": self._count(
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
                    "status": self._upload_status(
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

    def get_document(
        self,
        document_id: str,
    ) -> dict[str, object]:
        owner_predicate = ""
        parameters: list[object] = [document_id]

        if self.database.owner_id is not None:
            owner_predicate = """
                AND (
                    source_documents.source_upload_id IS NULL
                    OR EXISTS(
                        SELECT 1
                        FROM source_uploads
                        WHERE source_uploads.id = source_documents.source_upload_id
                          AND source_uploads.owner_id = ?
                    )
                )
            """
            parameters.append(self.database.owner_id)

        with self.database.connect() as connection:
            document_row = connection.execute(
                f"""
                SELECT
                    id,
                    project_id,
                    title,
                    media_type,
                    sha256,
                    byte_count,
                    line_count,
                    source_upload_id,
                    content,
                    created_at
                FROM source_documents
                WHERE id = ?
                {owner_predicate}
                """,
                tuple(parameters),
            ).fetchone()

            if document_row is None:
                raise NotFoundError(
                    f"source document not found: {document_id}"
                )

            document = dict(document_row)
            document["content"] = self._truncate_content(
                str(document.get("content", ""))
            )
            document["content_truncated"] = (
                int(document["byte_count"])
                > len(
                    str(document["content"]).encode(
                        "utf-8"
                    )
                )
            )

            sections = self._preview_rows(
                connection,
                """
                SELECT
                    id,
                    document_id,
                    ordinal,
                    title,
                    heading_level,
                    byte_start,
                    byte_end,
                    line_start,
                    line_end,
                    created_at
                FROM source_sections
                WHERE document_id = ?
                ORDER BY ordinal, id
                """,
                (document_id,),
            )
            chunks = self._preview_rows(
                connection,
                """
                SELECT
                    id,
                    document_id,
                    section_id,
                    ordinal,
                    sha256,
                    byte_start,
                    byte_end,
                    line_start,
                    line_end,
                    content,
                    created_at
                FROM source_chunks
                WHERE document_id = ?
                ORDER BY ordinal, id
                """,
                (document_id,),
            )
            atoms = self._preview_rows(
                connection,
                """
                SELECT
                    id,
                    project_id,
                    document_id,
                    section_id,
                    extraction_run_id,
                    ordinal,
                    kind,
                    modality,
                    status,
                    canonical_statement,
                    exact_quote,
                    byte_start,
                    byte_end,
                    line_start,
                    line_end,
                    source_sha256,
                    confidence,
                    created_at
                FROM atoms
                WHERE document_id = ?
                ORDER BY ordinal, id
                """,
                (document_id,),
            )
            for atom in atoms:
                atom["dimensions"] = [
                    dict(row)
                    for row in connection.execute(
                        """
                        SELECT
                            id,
                            atom_id,
                            dimension,
                            applicability,
                            status,
                            rationale,
                            created_at,
                            updated_at
                        FROM atom_dimensions
                        WHERE atom_id = ?
                        ORDER BY dimension, id
                        LIMIT ?
                        """,
                        (
                            str(atom["id"]),
                            WORKSPACE_PREVIEW_LIMIT,
                        ),
                    ).fetchall()
                ]
            ingestion_runs = self._preview_rows(
                connection,
                """
                SELECT
                    id,
                    project_id,
                    document_id,
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
                """,
                (document_id,),
            )
            extraction_runs = self._preview_rows(
                connection,
                """
                SELECT
                    id,
                    project_id,
                    document_id,
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
                """,
                (document_id,),
            )
            section_total = self._count(
                connection,
                """
                SELECT COUNT(*) AS value
                FROM source_sections
                WHERE document_id = ?
                """,
                (document_id,),
            )
            chunk_total = self._count(
                connection,
                """
                SELECT COUNT(*) AS value
                FROM source_chunks
                WHERE document_id = ?
                """,
                (document_id,),
            )
            atom_total = self._count(
                connection,
                """
                SELECT COUNT(*) AS value
                FROM atoms
                WHERE document_id = ?
                """,
                (document_id,),
            )
            ingestion_total = self._count(
                connection,
                """
                SELECT COUNT(*) AS value
                FROM ingestion_runs
                WHERE document_id = ?
                """,
                (document_id,),
            )
            extraction_total = self._count(
                connection,
                """
                SELECT COUNT(*) AS value
                FROM extraction_runs
                WHERE document_id = ?
                """,
                (document_id,),
            )
            raw_authority = None
            derivation = None

            if document.get("source_upload_id") is not None:
                upload_row = connection.execute(
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
                    (str(document["source_upload_id"]),),
                ).fetchone()

                if upload_row is not None:
                    raw_authority = {
                        "source_upload_id": str(upload_row["id"]),
                        "bucket": str(upload_row["bucket"]),
                        "object_path": str(upload_row["object_path"]),
                        "original_media_type": str(
                            upload_row["declared_media_type"]
                        ),
                        "byte_count": int(upload_row["actual_bytes"]),
                        "sha256": str(upload_row["actual_sha256"]),
                    }

                derivation_row = connection.execute(
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
                    WHERE source_document_id = ?
                    """,
                    (document_id,),
                ).fetchone()

                if derivation_row is not None:
                    metadata = json.loads(
                        str(derivation_row["metadata_json"])
                    )
                    derivation = {
                        "adapter_name": str(
                            derivation_row["adapter_name"]
                        ),
                        "adapter_version": str(
                            derivation_row["adapter_version"]
                        ),
                        "detected_media_type": str(
                            derivation_row["detected_media_type"]
                        ),
                        "derived_byte_count": int(
                            derivation_row["derived_byte_count"]
                        ),
                        "derived_sha256": str(
                            derivation_row["derived_sha256"]
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
                        "created_at": str(
                            derivation_row["created_at"]
                        ),
                    }

        return {
            "document": document,
            "sections": sections,
            "sections_count": section_total,
            "sections_has_more": (
                section_total > len(sections)
            ),
            "chunks": chunks,
            "chunks_count": chunk_total,
            "chunks_has_more": (
                chunk_total > len(chunks)
            ),
            "atoms": atoms,
            "atoms_count": atom_total,
            "atoms_has_more": (
                atom_total > len(atoms)
            ),
            "atoms_route": (
                f"/v1/documents/{document_id}/atoms"
            ),
            "ingestion_runs": ingestion_runs,
            "ingestion_runs_count": ingestion_total,
            "ingestion_runs_has_more": (
                ingestion_total > len(ingestion_runs)
            ),
            "extraction_runs": extraction_runs,
            "extraction_runs_count": extraction_total,
            "extraction_runs_has_more": (
                extraction_total > len(extraction_runs)
            ),
            "provenance": {
                "sha256": document["sha256"],
                "byte_start": 0,
                "byte_end": document["byte_count"],
                "line_start": 1,
                "line_end": document["line_count"],
                "raw_authority": raw_authority,
                "derivation": derivation,
            },
        }

    @staticmethod
    def _count(
        connection: Any,
        sql: str,
        parameters: tuple[object, ...],
    ) -> int:
        row = connection.execute(
            sql,
            parameters,
        ).fetchone()

        return (
            int(row["value"])
            if row is not None
            else 0
        )

    @staticmethod
    def _scalar(
        connection: Any,
        sql: str,
        parameters: tuple[object, ...],
    ) -> object:
        row = connection.execute(
            sql,
            parameters,
        ).fetchone()
        return (
            row["value"]
            if row is not None
            else 0
        )

    @staticmethod
    def _latest(
        connection: Any,
        sql: str,
        parameters: tuple[object, ...],
    ) -> dict[str, object] | None:
        row = connection.execute(
            sql,
            parameters,
        ).fetchone()
        return (
            dict(row)
            if row is not None
            else None
        )

    @staticmethod
    def _preview_rows(
        connection: Any,
        sql: str,
        parameters: tuple[object, ...],
    ) -> list[dict[str, object]]:
        rows = connection.execute(
            f"{sql}\nLIMIT ?",
            (*parameters, WORKSPACE_PREVIEW_LIMIT),
        ).fetchall()
        return [
            dict(row)
            for row in rows
        ]

    @staticmethod
    def _truncate_content(
        content: str,
    ) -> str:
        if len(content) <= CONTENT_PREVIEW_CHARS:
            return content

        return content[:CONTENT_PREVIEW_CHARS]

    @staticmethod
    def _upload_status(
        upload: dict[str, object],
    ) -> str:
        status = str(upload["status"])

        if status in {
            "FINALIZED",
            "FAILED",
            "EXPIRED",
        }:
            return status

        expires_at = str(upload["expires_at"])
        if not expires_at:
            return status

        parsed = datetime.fromisoformat(expires_at)
        if parsed.tzinfo is None:
            parsed = parsed.replace(tzinfo=UTC)

        if parsed.astimezone(UTC) <= datetime.now(UTC):
            return "EXPIRED"

        return status
