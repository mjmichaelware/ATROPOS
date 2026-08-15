"""Documents as a service seam over ingestion."""

from __future__ import annotations

import hashlib
import json
import re
import sqlite3
import uuid
from collections import defaultdict
from datetime import UTC, datetime

from .database import Database
from .errors import (
    ConflictError,
    NotFoundError,
    ValidationError,
)


def utc_now() -> str:
    return datetime.now(UTC).isoformat()


def new_id(prefix: str) -> str:
    return str(uuid.uuid4())


class DocumentService:
    def __init__(self, database: Database) -> None:
        self.database = database

    def ingest(
        self,
        project_id: str,
        title: str,
        content: str,
    ) -> dict[str, object]:
        title = title.strip()

        if not title:
            raise ValidationError(
                "document title is required"
            )

        if not content:
            raise ValidationError(
                "document content is required"
            )

        encoded = content.encode("utf-8")
        digest = hashlib.sha256(encoded).hexdigest()
        line_count = content.count("\n")

        if not content.endswith("\n"):
            line_count += 1

        document_id = new_id("document")

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
                INSERT INTO source_documents(
                    id,
                    project_id,
                    title,
                    sha256,
                    byte_count,
                    line_count,
                    content,
                    created_at
                )
                VALUES(?,?,?,?,?,?,?,?)
                """,
                (
                    document_id,
                    project_id,
                    title,
                    digest,
                    len(encoded),
                    line_count,
                    content,
                    utc_now(),
                ),
            )

        return {
            "id": document_id,
            "project_id": project_id,
            "title": title,
            "sha256": digest,
            "byte_count": len(encoded),
            "line_count": line_count,
        }
