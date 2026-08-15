"""Ingesting a document: bytes in, sections and chunks stored.

Every entry point -- file, text, uploaded bytes -- funnels into one write path,
so there is a single place where a document's identity and coverage are decided.
"""

from __future__ import annotations

from .document_chunking import line_count
import mimetypes

from .document_chunking import build_chunks
from .document_chunking import detect_sections
from .document_chunking import verify_chunk_coverage
from .document_queries import get_document
from pathlib import Path
import hashlib
import json
import sqlite3

from .database import Database
from .errors import ConflictError, NotFoundError, ValidationError
from .primitives import new_id, utc_now



def ingest_file(
    database: Database,
    project_id: str,
    path: Path,
    title: str | None = None,
    chunk_bytes: int = 32768,
) -> dict[str, object]:
    if not path.is_file():
        raise ValidationError(
            f"file not found: {path}"
        )

    raw = path.read_bytes()
    media_type = (
        mimetypes.guess_type(path.name)[0]
        or "text/plain"
    )

    return ingest_bytes(
        database,
        project_id=project_id,
        title=title or path.name,
        raw=raw,
        media_type=media_type,
        chunk_bytes=chunk_bytes,
    )


def ingest_text(
    database: Database,
    project_id: str,
    title: str,
    content: str,
    media_type: str = "text/plain",
    chunk_bytes: int = 32768,
) -> dict[str, object]:
    return ingest_bytes(
        database,
        project_id=project_id,
        title=title,
        raw=content.encode("utf-8"),
        media_type=media_type,
        chunk_bytes=chunk_bytes,
    )


def ingest_uploaded_bytes(
    database: Database,
    project_id: str,
    title: str,
    raw: bytes,
    *,
    media_type: str,
    source_upload_id: str,
    chunk_bytes: int = 32768,
) -> dict[str, object]:
    return ingest_bytes(
        database,
        project_id=project_id,
        title=title,
        raw=raw,
        media_type=media_type,
        chunk_bytes=chunk_bytes,
        source_upload_id=source_upload_id,
    )


def ingest_bytes(
    database: Database,
    project_id: str,
    title: str,
    raw: bytes,
    media_type: str,
    chunk_bytes: int,
    source_upload_id: str | None = None,
) -> dict[str, object]:
    title = title.strip()

    if not title:
        raise ValidationError(
            "document title is required"
        )

    if not raw:
        raise ValidationError(
            "document is empty"
        )

    try:
        content = raw.decode(
            "utf-8",
            errors="strict",
        )
    except UnicodeDecodeError as error:
        raise ValidationError(
            "source must be valid UTF-8 text"
        ) from error

    digest = hashlib.sha256(
        raw
    ).hexdigest()

    sections = detect_sections(raw)
    chunks = build_chunks(
        raw,
        sections,
        chunk_bytes,
    )
    coverage = verify_chunk_coverage(
        raw,
        chunks,
    )

    run_id = new_id("ingestion")
    document_id = new_id("document")
    created_at = utc_now()

    try:
        with database.connect() as connection:
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

            if source_upload_id is None:
                duplicate = connection.execute(
                    """
                    SELECT id
                    FROM source_documents
                    WHERE project_id = ?
                      AND sha256 = ?
                      AND source_upload_id IS NULL
                    LIMIT 1
                    """,
                    (project_id, digest),
                ).fetchone()

                if duplicate is not None:
                    raise ConflictError(
                        "identical document already "
                        "exists in this project"
                    )

            connection.execute(
                """
                INSERT INTO ingestion_runs(
                    id,
                    project_id,
                    status,
                    chunk_bytes,
                    created_at
                )
                VALUES(?,?,?,?,?)
                """,
                (
                    run_id,
                    project_id,
                    "RUNNING",
                    chunk_bytes,
                    created_at,
                ),
            )

            connection.execute(
                """
                INSERT INTO source_documents(
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
                )
                VALUES(?,?,?,?,?,?,?,?,?,?)
                """,
                (
                    document_id,
                    project_id,
                    title,
                    media_type,
                    digest,
                    len(raw),
                    line_count(raw),
                    source_upload_id,
                    content,
                    created_at,
                ),
            )

            section_ids: dict[int, str] = {}

            for section in sections:
                section_id = new_id("section")
                section_ordinal = int(
                    section["ordinal"]
                )
                section_ids[
                    section_ordinal
                ] = section_id

                connection.execute(
                    """
                    INSERT INTO source_sections(
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
                    )
                    VALUES(?,?,?,?,?,?,?,?,?,?)
                    """,
                    (
                        section_id,
                        document_id,
                        section_ordinal,
                        str(section["title"]),
                        section[
                            "heading_level"
                        ],
                        int(
                            section[
                                "byte_start"
                            ]
                        ),
                        int(
                            section[
                                "byte_end"
                            ]
                        ),
                        int(
                            section[
                                "line_start"
                            ]
                        ),
                        int(
                            section[
                                "line_end"
                            ]
                        ),
                        created_at,
                    ),
                )

            for chunk in chunks:
                connection.execute(
                    """
                    INSERT INTO source_chunks(
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
                    )
                    VALUES(?,?,?,?,?,?,?,?,?,?,?)
                    """,
                    (
                        new_id("chunk"),
                        document_id,
                        section_ids[
                            int(
                                chunk[
                                    "section_ordinal"
                                ]
                            )
                        ],
                        int(chunk["ordinal"]),
                        str(chunk["sha256"]),
                        int(
                            chunk["byte_start"]
                        ),
                        int(
                            chunk["byte_end"]
                        ),
                        int(
                            chunk["line_start"]
                        ),
                        int(
                            chunk["line_end"]
                        ),
                        str(chunk["content"]),
                        created_at,
                    ),
                )

            connection.execute(
                """
                UPDATE ingestion_runs
                SET document_id = ?,
                    status = 'COMPLETE',
                    section_count = ?,
                    chunk_count = ?,
                    covered_bytes = ?,
                    coverage_sha256 = ?,
                    completed_at = ?
                WHERE id = ?
                """,
                (
                    document_id,
                    len(sections),
                    len(chunks),
                    int(
                        coverage[
                            "covered_bytes"
                        ]
                    ),
                    str(
                        coverage[
                            "coverage_sha256"
                        ]
                    ),
                    utc_now(),
                    run_id,
                ),
            )

    except sqlite3.IntegrityError as error:
        raise ConflictError(
            "identical document already "
            "exists in this project"
        ) from error

    return get_document(database, 
        document_id
    )
