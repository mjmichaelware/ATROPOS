import hashlib
import mimetypes
import re
import sqlite3
import uuid
from bisect import bisect_right
from datetime import UTC, datetime
from pathlib import Path

from .database import Database
from .errors import (
    ConflictError,
    NotFoundError,
    ValidationError,
)


HEADING_PATTERN = re.compile(
    r"^\s{0,3}(#{1,6})\s+(.+?)\s*$"
)


def utc_now() -> str:
    return datetime.now(UTC).isoformat()


def new_id(prefix: str) -> str:
    return str(uuid.uuid4())


def line_count(raw: bytes) -> int:
    count = raw.count(b"\n")

    if not raw.endswith(b"\n"):
        count += 1

    return max(count, 1)


def line_starts(raw: bytes) -> list[int]:
    starts = [0]

    for index, byte in enumerate(raw):
        if byte == 10 and index + 1 < len(raw):
            starts.append(index + 1)

    return starts


def line_number(
    starts: list[int],
    byte_offset: int,
) -> int:
    return bisect_right(
        starts,
        byte_offset,
    )


def ending_line_number(
    starts: list[int],
    byte_end: int,
) -> int:
    return line_number(
        starts,
        max(0, byte_end - 1),
    )


def safe_utf8_end(
    raw: bytes,
    start: int,
    desired_end: int,
) -> int:
    end = min(desired_end, len(raw))

    while (
        end > start
        and end < len(raw)
        and raw[end] & 0b11000000 == 0b10000000
    ):
        end -= 1

    if end == start:
        end = min(start + 1, len(raw))

        while (
            end < len(raw)
            and raw[end] & 0b11000000 == 0b10000000
        ):
            end += 1

    return end


def detect_sections(
    raw: bytes,
) -> list[dict[str, object]]:
    text = raw.decode("utf-8", errors="strict")
    starts = line_starts(raw)
    headings: list[dict[str, object]] = []
    byte_offset = 0

    for line in text.splitlines(keepends=True):
        stripped = line.rstrip("\r\n")
        match = HEADING_PATTERN.match(stripped)

        if match:
            headings.append(
                {
                    "byte_start": byte_offset,
                    "title": match.group(2).strip(),
                    "heading_level": len(match.group(1)),
                }
            )

        byte_offset += len(
            line.encode("utf-8")
        )

    if not headings:
        return [
            {
                "ordinal": 0,
                "title": "Document",
                "heading_level": None,
                "byte_start": 0,
                "byte_end": len(raw),
                "line_start": 1,
                "line_end": ending_line_number(
                    starts,
                    len(raw),
                ),
            }
        ]

    sections: list[dict[str, object]] = []
    ordinal = 0

    if int(headings[0]["byte_start"]) > 0:
        first_start = int(
            headings[0]["byte_start"]
        )

        sections.append(
            {
                "ordinal": ordinal,
                "title": "Preamble",
                "heading_level": None,
                "byte_start": 0,
                "byte_end": first_start,
                "line_start": 1,
                "line_end": ending_line_number(
                    starts,
                    first_start,
                ),
            }
        )
        ordinal += 1

    for index, heading in enumerate(headings):
        start = int(heading["byte_start"])

        if index + 1 < len(headings):
            end = int(
                headings[index + 1]["byte_start"]
            )
        else:
            end = len(raw)

        sections.append(
            {
                "ordinal": ordinal,
                "title": str(heading["title"]),
                "heading_level": int(
                    heading["heading_level"]
                ),
                "byte_start": start,
                "byte_end": end,
                "line_start": line_number(
                    starts,
                    start,
                ),
                "line_end": ending_line_number(
                    starts,
                    end,
                ),
            }
        )
        ordinal += 1

    return sections


def build_chunks(
    raw: bytes,
    sections: list[dict[str, object]],
    chunk_bytes: int,
) -> list[dict[str, object]]:
    if chunk_bytes < 8:
        raise ValidationError(
            "chunk_bytes must be at least 8"
        )

    starts = line_starts(raw)
    chunks: list[dict[str, object]] = []
    ordinal = 0

    for section in sections:
        section_start = int(
            section["byte_start"]
        )
        section_end = int(
            section["byte_end"]
        )
        cursor = section_start

        while cursor < section_end:
            desired_end = min(
                cursor + chunk_bytes,
                section_end,
            )

            if desired_end < section_end:
                newline = raw.rfind(
                    b"\n",
                    cursor,
                    desired_end,
                )

                if newline >= cursor:
                    end = newline + 1
                else:
                    end = safe_utf8_end(
                        raw,
                        cursor,
                        desired_end,
                    )
            else:
                end = section_end

            if end <= cursor:
                raise ValidationError(
                    "chunker failed to advance"
                )

            chunk_raw = raw[cursor:end]
            chunk_text = chunk_raw.decode(
                "utf-8",
                errors="strict",
            )

            chunks.append(
                {
                    "ordinal": ordinal,
                    "section_ordinal": int(
                        section["ordinal"]
                    ),
                    "sha256": hashlib.sha256(
                        chunk_raw
                    ).hexdigest(),
                    "byte_start": cursor,
                    "byte_end": end,
                    "line_start": line_number(
                        starts,
                        cursor,
                    ),
                    "line_end": ending_line_number(
                        starts,
                        end,
                    ),
                    "content": chunk_text,
                }
            )

            ordinal += 1
            cursor = end

    return chunks


def verify_chunk_coverage(
    raw: bytes,
    chunks: list[dict[str, object]],
) -> dict[str, object]:
    if not chunks:
        raise ValidationError(
            "document produced no chunks"
        )

    expected_start = 0
    reconstructed = bytearray()

    for expected_ordinal, chunk in enumerate(chunks):
        ordinal = int(chunk["ordinal"])
        start = int(chunk["byte_start"])
        end = int(chunk["byte_end"])

        if ordinal != expected_ordinal:
            raise ValidationError(
                "chunk ordinals are not contiguous"
            )

        if start != expected_start:
            raise ValidationError(
                "chunk coverage contains a gap "
                "or overlap"
            )

        content_bytes = str(
            chunk["content"]
        ).encode("utf-8")

        if len(content_bytes) != end - start:
            raise ValidationError(
                "chunk byte coordinates do not "
                "match content"
            )

        digest = hashlib.sha256(
            content_bytes
        ).hexdigest()

        if digest != str(chunk["sha256"]):
            raise ValidationError(
                "chunk checksum mismatch"
            )

        reconstructed.extend(content_bytes)
        expected_start = end

    if expected_start != len(raw):
        raise ValidationError(
            "chunk coverage does not reach "
            "document end"
        )

    reconstructed_bytes = bytes(reconstructed)

    if reconstructed_bytes != raw:
        raise ValidationError(
            "chunk reconstruction differs "
            "from source bytes"
        )

    return {
        "valid": True,
        "covered_bytes": len(
            reconstructed_bytes
        ),
        "chunk_count": len(chunks),
        "coverage_sha256": hashlib.sha256(
            reconstructed_bytes
        ).hexdigest(),
    }


class IngestionService:
    def __init__(
        self,
        database: Database,
    ) -> None:
        self.database = database

    def ingest_file(
        self,
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

        return self.ingest_bytes(
            project_id=project_id,
            title=title or path.name,
            raw=raw,
            media_type=media_type,
            chunk_bytes=chunk_bytes,
        )

    def ingest_text(
        self,
        project_id: str,
        title: str,
        content: str,
        media_type: str = "text/plain",
        chunk_bytes: int = 32768,
    ) -> dict[str, object]:
        return self.ingest_bytes(
            project_id=project_id,
            title=title,
            raw=content.encode("utf-8"),
            media_type=media_type,
            chunk_bytes=chunk_bytes,
        )

    def ingest_uploaded_bytes(
        self,
        project_id: str,
        title: str,
        raw: bytes,
        *,
        media_type: str,
        source_upload_id: str,
        chunk_bytes: int = 32768,
    ) -> dict[str, object]:
        return self.ingest_bytes(
            project_id=project_id,
            title=title,
            raw=raw,
            media_type=media_type,
            chunk_bytes=chunk_bytes,
            source_upload_id=source_upload_id,
        )

    def ingest_bytes(
        self,
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
            with self.database.connect() as connection:
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

        return self.get_document(
            document_id
        )

    def list_documents(
        self,
        project_id: str,
    ) -> list[dict[str, object]]:
        with self.database.connect() as connection:
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
        self,
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

        with self.database.connect() as connection:
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
        self,
        document_id: str,
        include_chunk_content: bool = False,
    ) -> dict[str, object]:
        with self.database.connect() as connection:
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
        self,
        document_id: str,
    ) -> dict[str, object]:
        with self.database.connect() as connection:
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
        self,
        document_id: str,
    ) -> bytes:
        with self.database.connect() as connection:
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
