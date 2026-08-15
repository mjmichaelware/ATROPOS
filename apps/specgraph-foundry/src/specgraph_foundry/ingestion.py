import hashlib
import mimetypes
import re
import sqlite3
import uuid
from bisect import bisect_right
from datetime import UTC, datetime
from pathlib import Path

from .database import Database
from .document_ingest import ingest_bytes, ingest_file, ingest_text, ingest_uploaded_bytes
from .document_queries import (
    get_document,
    list_documents,
    list_documents_page,
    reconstruct,
    verify_document,
)
from .document_chunking import (
    build_chunks,
    detect_sections,
    safe_utf8_end,
    verify_chunk_coverage,
)
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
        """Delegates to :func:`document_ingest.ingest_file`."""
        return ingest_file(
            self.database,
            project_id,
            path,
            title,
            chunk_bytes,
        )


    def ingest_text(
        self,
        project_id: str,
        title: str,
        content: str,
        media_type: str = "text/plain",
        chunk_bytes: int = 32768,
    ) -> dict[str, object]:
        """Delegates to :func:`document_ingest.ingest_text`."""
        return ingest_text(
            self.database,
            project_id,
            title,
            content,
            media_type,
            chunk_bytes,
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
        """Delegates to :func:`document_ingest.ingest_uploaded_bytes`."""
        return ingest_uploaded_bytes(
            self.database,
            project_id,
            title,
            raw,
            media_type=media_type,
            source_upload_id=source_upload_id,
            chunk_bytes=chunk_bytes,
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
        """Delegates to :func:`document_ingest.ingest_bytes`."""
        return ingest_bytes(
            self.database,
            project_id,
            title,
            raw,
            media_type,
            chunk_bytes,
            source_upload_id,
        )


    def list_documents(
        self,
        project_id: str,
    ) -> list[dict[str, object]]:
        """Delegates to :func:`document_queries.list_documents`."""
        return list_documents(
            self.database,
            project_id,
        )


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
        """Delegates to :func:`document_queries.list_documents_page`."""
        return list_documents_page(
            self.database,
            project_id,
            limit,
            boundary,
        )


    def get_document(
        self,
        document_id: str,
        include_chunk_content: bool = False,
    ) -> dict[str, object]:
        """Delegates to :func:`document_queries.get_document`."""
        return get_document(
            self.database,
            document_id,
            include_chunk_content,
        )


    def verify_document(
        self,
        document_id: str,
    ) -> dict[str, object]:
        """Delegates to :func:`document_queries.verify_document`."""
        return verify_document(
            self.database,
            document_id,
        )


    def reconstruct(
        self,
        document_id: str,
    ) -> bytes:
        """Delegates to :func:`document_queries.reconstruct`."""
        return reconstruct(
            self.database,
            document_id,
        )

