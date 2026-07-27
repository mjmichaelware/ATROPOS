from pathlib import Path
from textwrap import dedent

ROOT = Path.cwd()

if ROOT.name != "specgraph-foundry" or not (ROOT / ".git").is_dir():
    raise SystemExit(f"Wrong directory: {ROOT}")


def write(path: str, content: str) -> None:
    target = ROOT / path
    target.parent.mkdir(parents=True, exist_ok=True)
    target.write_text(
        dedent(content).lstrip(),
        encoding="utf-8",
    )
    print(f"CREATED {path}")


write(
    "src/specgraph_foundry/database.py",
    r'''
    import sqlite3
    from pathlib import Path


    SCHEMA = """
    PRAGMA foreign_keys = ON;

    CREATE TABLE IF NOT EXISTS projects (
        id TEXT PRIMARY KEY,
        slug TEXT NOT NULL UNIQUE,
        name TEXT NOT NULL,
        description TEXT NOT NULL DEFAULT '',
        created_at TEXT NOT NULL
    );

    CREATE TABLE IF NOT EXISTS source_documents (
        id TEXT PRIMARY KEY,
        project_id TEXT NOT NULL
            REFERENCES projects(id)
            ON DELETE CASCADE,
        title TEXT NOT NULL,
        media_type TEXT NOT NULL DEFAULT 'text/plain',
        sha256 TEXT NOT NULL,
        byte_count INTEGER NOT NULL
            CHECK(byte_count > 0),
        line_count INTEGER NOT NULL
            CHECK(line_count > 0),
        content TEXT NOT NULL,
        created_at TEXT NOT NULL,
        UNIQUE(project_id, sha256)
    );

    CREATE TABLE IF NOT EXISTS ingestion_runs (
        id TEXT PRIMARY KEY,
        project_id TEXT NOT NULL
            REFERENCES projects(id)
            ON DELETE CASCADE,
        document_id TEXT
            REFERENCES source_documents(id)
            ON DELETE CASCADE,
        status TEXT NOT NULL,
        chunk_bytes INTEGER NOT NULL,
        section_count INTEGER NOT NULL DEFAULT 0,
        chunk_count INTEGER NOT NULL DEFAULT 0,
        covered_bytes INTEGER NOT NULL DEFAULT 0,
        coverage_sha256 TEXT,
        error_message TEXT,
        created_at TEXT NOT NULL,
        completed_at TEXT
    );

    CREATE TABLE IF NOT EXISTS source_sections (
        id TEXT PRIMARY KEY,
        document_id TEXT NOT NULL
            REFERENCES source_documents(id)
            ON DELETE CASCADE,
        ordinal INTEGER NOT NULL,
        title TEXT NOT NULL,
        heading_level INTEGER,
        byte_start INTEGER NOT NULL,
        byte_end INTEGER NOT NULL,
        line_start INTEGER NOT NULL,
        line_end INTEGER NOT NULL,
        created_at TEXT NOT NULL,
        CHECK(ordinal >= 0),
        CHECK(byte_start >= 0),
        CHECK(byte_end > byte_start),
        CHECK(line_start > 0),
        CHECK(line_end >= line_start),
        UNIQUE(document_id, ordinal)
    );

    CREATE TABLE IF NOT EXISTS source_chunks (
        id TEXT PRIMARY KEY,
        document_id TEXT NOT NULL
            REFERENCES source_documents(id)
            ON DELETE CASCADE,
        section_id TEXT
            REFERENCES source_sections(id)
            ON DELETE CASCADE,
        ordinal INTEGER NOT NULL,
        sha256 TEXT NOT NULL,
        byte_start INTEGER NOT NULL,
        byte_end INTEGER NOT NULL,
        line_start INTEGER NOT NULL,
        line_end INTEGER NOT NULL,
        content TEXT NOT NULL,
        created_at TEXT NOT NULL,
        CHECK(ordinal >= 0),
        CHECK(byte_start >= 0),
        CHECK(byte_end > byte_start),
        CHECK(line_start > 0),
        CHECK(line_end >= line_start),
        UNIQUE(document_id, ordinal)
    );

    CREATE TABLE IF NOT EXISTS graphs (
        id TEXT PRIMARY KEY,
        project_id TEXT NOT NULL
            REFERENCES projects(id)
            ON DELETE CASCADE,
        name TEXT NOT NULL,
        kind TEXT NOT NULL,
        enforce_acyclic INTEGER NOT NULL DEFAULT 0,
        created_at TEXT NOT NULL
    );

    CREATE TABLE IF NOT EXISTS graph_nodes (
        id TEXT PRIMARY KEY,
        graph_id TEXT NOT NULL
            REFERENCES graphs(id)
            ON DELETE CASCADE,
        node_key TEXT NOT NULL,
        node_type TEXT NOT NULL,
        title TEXT NOT NULL,
        status TEXT NOT NULL,
        payload_json TEXT NOT NULL DEFAULT '{}',
        created_at TEXT NOT NULL,
        UNIQUE(graph_id, node_key)
    );

    CREATE TABLE IF NOT EXISTS graph_edges (
        id TEXT PRIMARY KEY,
        graph_id TEXT NOT NULL
            REFERENCES graphs(id)
            ON DELETE CASCADE,
        from_node_id TEXT NOT NULL
            REFERENCES graph_nodes(id)
            ON DELETE CASCADE,
        to_node_id TEXT NOT NULL
            REFERENCES graph_nodes(id)
            ON DELETE CASCADE,
        edge_type TEXT NOT NULL,
        inferred INTEGER NOT NULL DEFAULT 0,
        rationale TEXT NOT NULL DEFAULT '',
        created_at TEXT NOT NULL,
        CHECK(from_node_id <> to_node_id),
        UNIQUE(
            graph_id,
            from_node_id,
            to_node_id,
            edge_type
        )
    );

    CREATE INDEX IF NOT EXISTS idx_documents_project
        ON source_documents(project_id);

    CREATE INDEX IF NOT EXISTS idx_ingestion_runs_project
        ON ingestion_runs(project_id, status);

    CREATE INDEX IF NOT EXISTS idx_sections_document
        ON source_sections(document_id, ordinal);

    CREATE INDEX IF NOT EXISTS idx_chunks_document
        ON source_chunks(document_id, ordinal);

    CREATE INDEX IF NOT EXISTS idx_nodes_graph_status
        ON graph_nodes(graph_id, status);

    CREATE INDEX IF NOT EXISTS idx_edges_graph_from
        ON graph_edges(graph_id, from_node_id);

    CREATE INDEX IF NOT EXISTS idx_edges_graph_to
        ON graph_edges(graph_id, to_node_id);
    """


    class Database:
        def __init__(self, path: Path) -> None:
            self.path = path

        def connect(self) -> sqlite3.Connection:
            self.path.parent.mkdir(
                parents=True,
                exist_ok=True,
            )

            connection = sqlite3.connect(self.path)
            connection.row_factory = sqlite3.Row
            connection.execute("PRAGMA foreign_keys = ON")
            connection.execute("PRAGMA journal_mode = WAL")
            return connection

        def initialize(self) -> None:
            with self.connect() as connection:
                connection.executescript(SCHEMA)

                columns = {
                    row["name"]
                    for row in connection.execute(
                        "PRAGMA table_info(source_documents)"
                    ).fetchall()
                }

                if "media_type" not in columns:
                    connection.execute(
                        """
                        ALTER TABLE source_documents
                        ADD COLUMN media_type TEXT
                        NOT NULL DEFAULT 'text/plain'
                        """
                    )

        def health(self) -> dict[str, object]:
            with self.connect() as connection:
                integrity = connection.execute(
                    "PRAGMA integrity_check"
                ).fetchone()[0]

                tables = [
                    row["name"]
                    for row in connection.execute(
                        """
                        SELECT name
                        FROM sqlite_master
                        WHERE type = 'table'
                        ORDER BY name
                        """
                    ).fetchall()
                ]

            return {
                "database": str(self.path),
                "integrity": integrity,
                "tables": tables,
            }
    ''',
)

write(
    "src/specgraph_foundry/ingestion.py",
    r'''
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
        return f"{prefix}-{uuid.uuid4()}"


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

        def ingest_bytes(
            self,
            project_id: str,
            title: str,
            raw: bytes,
            media_type: str,
            chunk_bytes: int,
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
                            content,
                            created_at
                        )
                        VALUES(?,?,?,?,?,?,?,?,?)
                        """,
                        (
                            document_id,
                            project_id,
                            title,
                            media_type,
                            digest,
                            len(raw),
                            line_count(raw),
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
    ''',
)

write(
    "src/specgraph_foundry/api.py",
    r'''
    import json
    from http.server import (
        BaseHTTPRequestHandler,
        ThreadingHTTPServer,
    )
    from urllib.parse import urlparse

    from .database import Database
    from .errors import (
        ConflictError,
        NotFoundError,
        ValidationError,
    )
    from .ingestion import IngestionService
    from .services import ProjectService


    class Api:
        def __init__(
            self,
            database: Database,
        ) -> None:
            self.database = database
            self.projects = ProjectService(
                database
            )
            self.ingestion = IngestionService(
                database
            )

        def dispatch(
            self,
            method: str,
            raw_path: str,
            payload: dict[str, object],
        ) -> tuple[int, dict[str, object]]:
            parts = [
                part
                for part in urlparse(
                    raw_path
                ).path.split("/")
                if part
            ]

            try:
                if (
                    method == "GET"
                    and parts == ["health"]
                ):
                    return 200, {
                        "status": "ok",
                        "service": (
                            "specgraph-foundry"
                        ),
                        "database": (
                            self.database.health()
                        ),
                    }

                if parts == [
                    "v1",
                    "projects",
                ]:
                    if method == "GET":
                        return 200, {
                            "items": (
                                self.projects.list()
                            )
                        }

                    if method == "POST":
                        return 201, (
                            self.projects.create(
                                str(
                                    payload.get(
                                        "slug",
                                        "",
                                    )
                                ),
                                str(
                                    payload.get(
                                        "name",
                                        "",
                                    )
                                ),
                                str(
                                    payload.get(
                                        "description",
                                        "",
                                    )
                                ),
                            )
                        )

                if (
                    len(parts) == 3
                    and parts[:2]
                    == ["v1", "projects"]
                    and method == "GET"
                ):
                    return 200, (
                        self.projects.get(
                            parts[2]
                        )
                    )

                if (
                    len(parts) == 4
                    and parts[:2]
                    == ["v1", "projects"]
                    and parts[3] == "documents"
                ):
                    project_id = parts[2]

                    if method == "GET":
                        return 200, {
                            "items": (
                                self.ingestion
                                .list_documents(
                                    project_id
                                )
                            )
                        }

                    if method == "POST":
                        document = (
                            self.ingestion
                            .ingest_text(
                                project_id=(
                                    project_id
                                ),
                                title=str(
                                    payload.get(
                                        "title",
                                        "",
                                    )
                                ),
                                content=str(
                                    payload.get(
                                        "content",
                                        "",
                                    )
                                ),
                                media_type=str(
                                    payload.get(
                                        "media_type",
                                        "text/plain",
                                    )
                                ),
                                chunk_bytes=int(
                                    payload.get(
                                        "chunk_bytes",
                                        32768,
                                    )
                                ),
                            )
                        )
                        return 201, document

                if (
                    len(parts) == 3
                    and parts[:2]
                    == ["v1", "documents"]
                    and method == "GET"
                ):
                    return 200, (
                        self.ingestion
                        .get_document(
                            parts[2]
                        )
                    )

                if (
                    len(parts) == 4
                    and parts[:2]
                    == ["v1", "documents"]
                    and parts[3] == "verify"
                    and method == "GET"
                ):
                    return 200, (
                        self.ingestion
                        .verify_document(
                            parts[2]
                        )
                    )

                return 404, {
                    "error": "ROUTE_NOT_FOUND",
                    "message": (
                        f"no route for "
                        f"{method} {raw_path}"
                    ),
                }

            except ValidationError as error:
                return 400, {
                    "error": "VALIDATION_ERROR",
                    "message": str(error),
                }
            except NotFoundError as error:
                return 404, {
                    "error": "NOT_FOUND",
                    "message": str(error),
                }
            except ConflictError as error:
                return 409, {
                    "error": "CONFLICT",
                    "message": str(error),
                }

        def serve(
            self,
            host: str,
            port: int,
        ) -> None:
            api = self

            class Handler(
                BaseHTTPRequestHandler
            ):
                def do_GET(self) -> None:
                    self._handle()

                def do_POST(self) -> None:
                    self._handle()

                def _handle(self) -> None:
                    length = int(
                        self.headers.get(
                            "content-length",
                            "0",
                        )
                    )

                    payload: dict[
                        str,
                        object,
                    ] = {}

                    if length:
                        try:
                            parsed = json.loads(
                                self.rfile.read(
                                    length
                                ).decode(
                                    "utf-8"
                                )
                            )
                        except (
                            UnicodeDecodeError,
                            json.JSONDecodeError,
                        ):
                            self._send(
                                400,
                                {
                                    "error": (
                                        "INVALID_JSON"
                                    ),
                                    "message": (
                                        "body must be "
                                        "valid JSON"
                                    ),
                                },
                            )
                            return

                        if not isinstance(
                            parsed,
                            dict,
                        ):
                            self._send(
                                400,
                                {
                                    "error": (
                                        "INVALID_JSON"
                                    ),
                                    "message": (
                                        "body must be "
                                        "a JSON object"
                                    ),
                                },
                            )
                            return

                        payload = parsed

                    status, response = (
                        api.dispatch(
                            self.command,
                            self.path,
                            payload,
                        )
                    )

                    self._send(
                        status,
                        response,
                    )

                def _send(
                    self,
                    status: int,
                    payload: dict[
                        str,
                        object,
                    ],
                ) -> None:
                    encoded = json.dumps(
                        payload,
                        indent=2,
                        sort_keys=True,
                    ).encode("utf-8")

                    self.send_response(status)
                    self.send_header(
                        "content-type",
                        (
                            "application/json; "
                            "charset=utf-8"
                        ),
                    )
                    self.send_header(
                        "content-length",
                        str(len(encoded)),
                    )
                    self.end_headers()
                    self.wfile.write(encoded)

            server = ThreadingHTTPServer(
                (host, port),
                Handler,
            )

            print(
                "SpecGraph Foundry listening "
                f"on http://{host}:{port}"
            )

            try:
                server.serve_forever()
            except KeyboardInterrupt:
                print(
                    "\nStopping "
                    "SpecGraph Foundry."
                )
            finally:
                server.server_close()
    ''',
)

write(
    "src/specgraph_foundry/cli.py",
    r'''
    import argparse
    import json
    import uuid
    from pathlib import Path

    from .api import Api
    from .config import Settings
    from .database import Database
    from .doctor import inspect
    from .ingestion import IngestionService
    from .services import (
        GraphService,
        ProjectService,
    )


    def output(value: object) -> None:
        print(
            json.dumps(
                value,
                indent=2,
                sort_keys=True,
            )
        )


    def build_parser() -> argparse.ArgumentParser:
        settings = Settings.from_environment()

        parser = argparse.ArgumentParser(
            prog="specgraph"
        )

        commands = parser.add_subparsers(
            dest="command",
            required=True,
        )

        commands.add_parser("init")
        commands.add_parser("doctor")
        commands.add_parser("demo")
        commands.add_parser("list-projects")

        create_project = commands.add_parser(
            "create-project"
        )
        create_project.add_argument("slug")
        create_project.add_argument("name")
        create_project.add_argument(
            "--description",
            default="",
        )

        ingest_file = commands.add_parser(
            "ingest-file"
        )
        ingest_file.add_argument("project_id")
        ingest_file.add_argument(
            "path",
            type=Path,
        )
        ingest_file.add_argument("--title")
        ingest_file.add_argument(
            "--chunk-bytes",
            type=int,
            default=32768,
        )

        document = commands.add_parser(
            "document"
        )
        document.add_argument("document_id")
        document.add_argument(
            "--include-chunks",
            action="store_true",
        )

        verify = commands.add_parser(
            "verify-document"
        )
        verify.add_argument("document_id")

        server = commands.add_parser("serve")
        server.add_argument(
            "--host",
            default=settings.host,
        )
        server.add_argument(
            "--port",
            type=int,
            default=settings.port,
        )

        return parser


    def main() -> int:
        settings = Settings.from_environment()
        args = build_parser().parse_args()

        database = Database(
            settings.database_path
        )
        database.initialize()

        projects = ProjectService(database)
        ingestion = IngestionService(database)
        graphs = GraphService(database)

        if args.command == "init":
            output(database.health())
            return 0

        if args.command == "doctor":
            output(inspect())
            return 0

        if args.command == "serve":
            Api(database).serve(
                args.host,
                args.port,
            )
            return 0

        if args.command == "list-projects":
            output(
                {
                    "items": projects.list()
                }
            )
            return 0

        if args.command == "create-project":
            output(
                projects.create(
                    args.slug,
                    args.name,
                    args.description,
                )
            )
            return 0

        if args.command == "ingest-file":
            output(
                ingestion.ingest_file(
                    project_id=(
                        args.project_id
                    ),
                    path=args.path,
                    title=args.title,
                    chunk_bytes=(
                        args.chunk_bytes
                    ),
                )
            )
            return 0

        if args.command == "document":
            output(
                ingestion.get_document(
                    args.document_id,
                    include_chunk_content=(
                        args.include_chunks
                    ),
                )
            )
            return 0

        if args.command == "verify-document":
            output(
                ingestion.verify_document(
                    args.document_id
                )
            )
            return 0

        suffix = uuid.uuid4().hex[:8]

        project = projects.create(
            f"demo-{suffix}",
            "SpecGraph Demonstration",
        )

        document = ingestion.ingest_text(
            project_id=str(project["id"]),
            title="Demo authority",
            content=(
                "# Contract\n"
                "Contracts must exist before "
                "implementation.\n\n"
                "## Verification\n"
                "Implementation must pass "
                "independent verification.\n"
            ),
            chunk_bytes=48,
        )

        graph = graphs.create(
            str(project["id"]),
            "Demo Execution DAG",
            "EXECUTION",
            True,
        )

        contract = graphs.add_node(
            str(graph["id"]),
            "contract",
            "BATCH",
            "Define contract",
        )

        implementation = graphs.add_node(
            str(graph["id"]),
            "implementation",
            "BATCH",
            "Implement service",
        )

        verification = graphs.add_node(
            str(graph["id"]),
            "verification",
            "GATE",
            "Verify service",
        )

        graphs.add_edge(
            str(graph["id"]),
            str(contract["id"]),
            str(implementation["id"]),
            "MUST_PRECEDE",
        )

        graphs.add_edge(
            str(graph["id"]),
            str(implementation["id"]),
            str(verification["id"]),
            "MUST_PRECEDE",
        )

        output(
            {
                "project": project,
                "document": document,
                "document_verification": (
                    ingestion.verify_document(
                        str(document["id"])
                    )
                ),
                "graph": graphs.get(
                    str(graph["id"])
                ),
                "ready_nodes": (
                    graphs.ready_nodes(
                        str(graph["id"])
                    )
                ),
            }
        )

        return 0
    ''',
)

write(
    "tests/test_ingestion.py",
    r'''
    import tempfile
    import unittest
    from pathlib import Path

    from specgraph_foundry.database import Database
    from specgraph_foundry.errors import (
        ConflictError,
        ValidationError,
    )
    from specgraph_foundry.ingestion import (
        IngestionService,
    )
    from specgraph_foundry.services import (
        ProjectService,
    )


    class IngestionTest(unittest.TestCase):
        def setUp(self) -> None:
            self.temp = (
                tempfile.TemporaryDirectory()
            )
            self.database = Database(
                Path(self.temp.name)
                / "test.sqlite3"
            )
            self.database.initialize()

            self.projects = ProjectService(
                self.database
            )
            self.ingestion = IngestionService(
                self.database
            )

            self.project = self.projects.create(
                "ingestion-test",
                "Ingestion Test",
            )

        def tearDown(self) -> None:
            self.temp.cleanup()

        def test_utf8_coordinates_and_coverage(
            self,
        ) -> None:
            content = (
                "# Alpha\n"
                "café\n"
                "🙂 unicode line\n"
                "## Beta\n"
                "final line\n"
            )

            document = (
                self.ingestion.ingest_text(
                    project_id=str(
                        self.project["id"]
                    ),
                    title="UTF-8 Source",
                    content=content,
                    chunk_bytes=14,
                )
            )

            self.assertEqual(
                document["byte_count"],
                len(content.encode("utf-8")),
            )

            self.assertEqual(
                [
                    section["title"]
                    for section
                    in document["sections"]
                ],
                ["Alpha", "Beta"],
            )

            verification = (
                self.ingestion
                .verify_document(
                    str(document["id"])
                )
            )

            self.assertTrue(
                verification["valid"]
            )

            reconstructed = (
                self.ingestion.reconstruct(
                    str(document["id"])
                )
            )

            self.assertEqual(
                reconstructed,
                content.encode("utf-8"),
            )

            expected_start = 0

            for chunk in document["chunks"]:
                self.assertEqual(
                    chunk["byte_start"],
                    expected_start,
                )
                expected_start = int(
                    chunk["byte_end"]
                )

            self.assertEqual(
                expected_start,
                len(content.encode("utf-8")),
            )

        def test_invalid_utf8_rejected(
            self,
        ) -> None:
            with self.assertRaises(
                ValidationError
            ):
                self.ingestion.ingest_bytes(
                    project_id=str(
                        self.project["id"]
                    ),
                    title="Invalid",
                    raw=b"\xff\xfe\xfa",
                    media_type="text/plain",
                    chunk_bytes=32,
                )

        def test_duplicate_source_rejected(
            self,
        ) -> None:
            content = "same source\n"

            self.ingestion.ingest_text(
                project_id=str(
                    self.project["id"]
                ),
                title="First",
                content=content,
            )

            with self.assertRaises(
                ConflictError
            ):
                self.ingestion.ingest_text(
                    project_id=str(
                        self.project["id"]
                    ),
                    title="Duplicate",
                    content=content,
                )


    if __name__ == "__main__":
        unittest.main()
    ''',
)

readme = ROOT / "README.md"
current = readme.read_text(encoding="utf-8")

section = dedent(
    r'''

    ## Byte-complete ingestion

    The ingestion engine now provides:

    - strict UTF-8 validation;
    - immutable SHA-256 source fingerprints;
    - exact byte counts and line counts;
    - deterministic Markdown section detection;
    - exact byte and line coordinates;
    - bounded UTF-8-safe chunks;
    - gap and overlap detection;
    - complete byte reconstruction;
    - duplicate-source rejection;
    - durable ingestion-run records.

    ```bash
    python -m specgraph_foundry create-project \
      example-project \
      "Example Project"

    python -m specgraph_foundry ingest-file \
      PROJECT_ID \
      ./source-document.md

    python -m specgraph_foundry verify-document \
      DOCUMENT_ID
    ```
    '''
)

if "## Byte-complete ingestion" not in current:
    readme.write_text(
        current.rstrip()
        + "\n"
        + section.lstrip(),
        encoding="utf-8",
    )
    print("UPDATED README.md")

print("INGESTION BACKEND CREATED")
