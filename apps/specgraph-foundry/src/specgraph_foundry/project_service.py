"""Projects: the root every other record hangs from."""

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


class ProjectService:
    SLUG_PATTERN = re.compile(
        r"^[a-z0-9]+(?:-[a-z0-9]+)*$"
    )

    def __init__(self, database: Database) -> None:
        self.database = database

    def create(
        self,
        slug: str,
        name: str,
        description: str = "",
    ) -> dict[str, object]:
        slug = slug.strip()
        name = name.strip()

        if not self.SLUG_PATTERN.fullmatch(slug):
            raise ValidationError(
                "invalid project slug"
            )

        if not name:
            raise ValidationError(
                "project name is required"
            )

        project_id = new_id("project")

        try:
            with self.database.connect() as connection:
                if self.database.is_postgres:
                    owner_id = self.database.owner_id

                    if not owner_id:
                        raise ValidationError(
                            "SPECGRAPH_OWNER_ID is "
                            "required in PostgreSQL mode"
                        )

                    try:
                        uuid.UUID(owner_id)
                    except ValueError as error:
                        raise ValidationError(
                            "SPECGRAPH_OWNER_ID must be "
                            "a valid Supabase Auth UUID"
                        ) from error

                    connection.execute(
                        """
                        INSERT INTO projects(
                            id,
                            owner_id,
                            slug,
                            name,
                            description,
                            created_at
                        )
                        VALUES(?,?,?,?,?,?)
                        """,
                        (
                            project_id,
                            owner_id,
                            slug,
                            name,
                            description.strip(),
                            utc_now(),
                        ),
                    )
                else:
                    connection.execute(
                        """
                        INSERT INTO projects(
                            id,
                            slug,
                            name,
                            description,
                            created_at
                        )
                        VALUES(?,?,?,?,?)
                        """,
                        (
                            project_id,
                            slug,
                            name,
                            description.strip(),
                            utc_now(),
                        ),
                    )
        except sqlite3.IntegrityError as error:
            raise ConflictError(
                f"project already exists: {slug}"
            ) from error

        return self.get(project_id)

    def get(
        self,
        project_id: str,
    ) -> dict[str, object]:
        with self.database.connect() as connection:
            row = connection.execute(
                """
                SELECT *
                FROM projects
                WHERE id = ?
                """,
                (project_id,),
            ).fetchone()

        if row is None:
            raise NotFoundError(
                f"project not found: {project_id}"
            )

        return dict(row)

    def list(self) -> list[dict[str, object]]:
        with self.database.connect() as connection:
            rows = connection.execute(
                """
                SELECT *
                FROM projects
                ORDER BY created_at, id
                """
            ).fetchall()

        return [dict(row) for row in rows]

    def list_page(
        self,
        limit: int,
        boundary: dict[str, object] | None = None,
    ) -> tuple[
        list[dict[str, object]],
        bool,
        dict[str, object] | None,
    ]:
        parameters: list[object] = []
        predicate = ""

        if boundary is not None:
            predicate = """
                WHERE (
                    created_at > ?
                    OR (
                        created_at = ?
                        AND id > ?
                    )
                )
            """
            created_at = str(
                boundary.get("created_at", "")
            )
            parameters.extend(
                [
                    created_at,
                    created_at,
                    str(boundary.get("id", "")),
                ]
            )

        parameters.append(limit + 1)

        with self.database.connect() as connection:
            rows = connection.execute(
                f"""
                SELECT *
                FROM projects
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

        return items, has_more, boundary_item
