"""Reading documents back, verifying coverage, and reconstruction."""

from __future__ import annotations

from .document_chunking import verify_chunk_coverage
import hashlib
import json
import sqlite3

from .database import Database
from .errors import ConflictError, NotFoundError, ValidationError
from .primitives import new_id, utc_now



def list_documents(
    database: Database,
    project_id: str,
) -> list[dict[str, object]]:
    with database.connect() as connection:
        rows = connection.execute(
            """
            SELECT
                id,
                project_id,
                title,
                media_type,
                sha256,
                byte_count,
                line_count,
                source_upload_id,
                created_at
            FROM source_documents
            WHERE project_id = ?
            ORDER BY created_at, id
            """,
            (project_id,),
        ).fetchall()

    return [
        dict(row)
        for row in rows
    ]


def list_documents_page(
    database: Database,
    project_id: str,
    limit: int,
    boundary: dict[str, object] | None = None,
) -> tuple[
    list[dict[str, object]],
    bool,
    dict[str, object] | None,
]:
    parameters: list[object] = [project_id]
    predicate = ""

    if boundary is not None:
        predicate = """
            AND (
                created_at > ?
                OR (
                    created_at = ?
                    AND id > ?
                )
            )
        """
        parameters.extend(
            [
                str(
                    boundary.get(
                        "created_at",
                        "",
                    )
                ),
                str(
                    boundary.get(
                        "created_at",
                        "",
                    )
                ),
                str(
                    boundary.get(
                        "id",
                        "",
                    )
                ),
            ]
        )

    parameters.append(limit + 1)

    with database.connect() as connection:
        rows = connection.execute(
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
                created_at
            FROM source_documents
            WHERE project_id = ?
            {predicate}
            ORDER BY created_at, id
            LIMIT ?
            """,
            tuple(parameters),
        ).fetchall()

    items = [
        dict(row)
        for row in rows[:limit]
    ]
    has_more = len(rows) > limit
    boundary_item = (
        {
            "created_at": str(
                items[-1]["created_at"]
            ),
            "id": str(items[-1]["id"]),
        }
        if items and has_more
        else None
    )

    return (
        items,
        has_more,
        boundary_item,
    )


def get_document(
    database: Database,
    document_id: str,
    include_chunk_content: bool = False,
) -> dict[str, object]:
    with database.connect() as connection:
        document = connection.execute(
            """
            SELECT
                id,
                project_id,
                title,
                media_type,
                sha256,
                byte_count,
                line_count,
                source_upload_id,
                created_at
            FROM source_documents
            WHERE id = ?
            """,
            (document_id,),
        ).fetchone()

        if document is None:
            raise NotFoundError(
                f"document not found: {document_id}"
            )

        sections = connection.execute(
            """
            SELECT *
            FROM source_sections
            WHERE document_id = ?
            ORDER BY ordinal
            """,
            (document_id,),
        ).fetchall()

        if include_chunk_content:
            chunk_query = """
                SELECT *
                FROM source_chunks
                WHERE document_id = ?
                ORDER BY ordinal
            """
        else:
            chunk_query = """
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
                    created_at
                FROM source_chunks
                WHERE document_id = ?
                ORDER BY ordinal
            """

        chunks = connection.execute(
            chunk_query,
            (document_id,),
        ).fetchall()

        run = connection.execute(
            """
            SELECT *
            FROM ingestion_runs
            WHERE document_id = ?
            ORDER BY created_at DESC
            LIMIT 1
            """,
            (document_id,),
        ).fetchone()

    result = dict(document)
    result["sections"] = [
        dict(row)
        for row in sections
    ]
    result["chunks"] = [
        dict(row)
        for row in chunks
    ]
    result["ingestion"] = (
        dict(run)
        if run is not None
        else None
    )

    return result


def verify_document(
    database: Database,
    document_id: str,
) -> dict[str, object]:
    with database.connect() as connection:
        document = connection.execute(
            """
            SELECT
                sha256,
                byte_count,
                content
            FROM source_documents
            WHERE id = ?
            """,
            (document_id,),
        ).fetchone()

        if document is None:
            raise NotFoundError(
                f"document not found: {document_id}"
            )

        chunk_rows = connection.execute(
            """
            SELECT
                ordinal,
                sha256,
                byte_start,
                byte_end,
                line_start,
                line_end,
                content
            FROM source_chunks
            WHERE document_id = ?
            ORDER BY ordinal
            """,
            (document_id,),
        ).fetchall()

    raw = str(
        document["content"]
    ).encode("utf-8")

    chunks = [
        dict(row)
        for row in chunk_rows
    ]

    coverage = verify_chunk_coverage(
        raw,
        chunks,
    )

    expected_sha256 = str(
        document["sha256"]
    )

    valid = (
        coverage["coverage_sha256"]
        == expected_sha256
        and coverage["covered_bytes"]
        == int(document["byte_count"])
    )

    return {
        "document_id": document_id,
        "valid": valid,
        "expected_sha256": expected_sha256,
        "coverage_sha256": coverage[
            "coverage_sha256"
        ],
        "expected_bytes": int(
            document["byte_count"]
        ),
        "covered_bytes": coverage[
            "covered_bytes"
        ],
        "chunk_count": coverage[
            "chunk_count"
        ],
    }


def reconstruct(
    database: Database,
    document_id: str,
) -> bytes:
    with database.connect() as connection:
        rows = connection.execute(
            """
            SELECT content
            FROM source_chunks
            WHERE document_id = ?
            ORDER BY ordinal
            """,
            (document_id,),
        ).fetchall()

    if not rows:
        raise NotFoundError(
            f"document not found: {document_id}"
        )

    return b"".join(
        str(row["content"]).encode(
            "utf-8"
        )
        for row in rows
    )
