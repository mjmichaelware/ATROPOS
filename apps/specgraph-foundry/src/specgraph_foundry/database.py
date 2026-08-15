from .core_schema import SCHEMA
from .postgres_adapter import (  # re-exported: database is the module
    ISO_DATETIME_PATTERN,        # callers import these from.
    JSON_COLUMNS,
    PostgresConnection,
    PostgresCursor,
    PostgresRow,
    UUID_PATTERN,
    adapt_postgres_parameters,
    adapt_postgres_scalar,
    normalize_postgres_row,
    normalize_postgres_value,
    parameter_count,
    postgres_json_parameter_indexes,
    split_sql_list,
    translate_qmark_sql,
)
import json
import re
import sqlite3
import uuid
from datetime import date, datetime
from decimal import Decimal
from pathlib import Path
from types import TracebackType
from typing import Any




class ManagedConnection(sqlite3.Connection):
    def __exit__(
        self,
        exception_type: type[BaseException] | None,
        exception: BaseException | None,
        traceback: TracebackType | None,
    ) -> bool:
        try:
            return bool(
                super().__exit__(
                    exception_type,
                    exception,
                    traceback,
                )
            )
        finally:
            self.close()




























class Database:
    REQUIRED_POSTGRES_TABLES = {
        "projects",
        "source_documents",
        "source_sections",
        "source_chunks",
        "document_derivations",
        "atoms",
        "research_tasks",
        "plan_versions",
        "exports",
        "execution_runs",
        "provider_configs",
        "idempotency_records",
        "operations",
        "source_uploads",
        "storage_objects",
        "artifact_manifests",
    }

    def __init__(
        self,
        path: Path,
        database_url: str | None = None,
        owner_id: str | None = None,
    ) -> None:
        self.path = path
        self.database_url = (
            database_url.strip()
            if database_url
            else None
        )
        self.owner_id = (
            owner_id.strip()
            if owner_id
            else None
        )

    @property
    def is_postgres(self) -> bool:
        return self.database_url is not None

    @property
    def backend(self) -> str:
        return (
            "postgresql"
            if self.is_postgres
            else "sqlite"
        )

    def connect(
        self,
    ) -> ManagedConnection | PostgresConnection:
        if self.database_url is not None:
            return PostgresConnection(
                self.database_url
            )

        self.path.parent.mkdir(
            parents=True,
            exist_ok=True,
        )

        connection = sqlite3.connect(
            self.path,
            factory=ManagedConnection,
        )

        connection.row_factory = sqlite3.Row
        connection.execute(
            "PRAGMA foreign_keys = ON"
        )
        connection.execute(
            "PRAGMA journal_mode = WAL"
        )

        return connection

    def initialize(self) -> None:
        if self.is_postgres:
            self._validate_postgres_schema()
            return

        with self.connect() as connection:
            connection.executescript(SCHEMA)

            columns = {
                row["name"]
                for row in connection.execute(
                    """
                    PRAGMA table_info(
                        source_documents
                    )
                    """
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

            if "source_upload_id" not in columns:
                connection.execute(
                    """
                    ALTER TABLE source_documents
                    ADD COLUMN source_upload_id TEXT
                    REFERENCES source_uploads(id)
                    ON DELETE SET NULL
                    """
                )

            self._drop_operations_fingerprint_uniqueness(
                connection
            )

    @staticmethod
    def _drop_operations_fingerprint_uniqueness(
        connection: object,
    ) -> None:
        """Rebuild ``operations`` without the fingerprint uniqueness slot.

        Earlier schemas declared
        ``UNIQUE(owner_id, operation_type, fingerprint)``. A fingerprint
        identifies a *request*, not a lifetime: once an operation has
        finished, submitting the same request again is a new operation.
        The constraint made that permanently impossible, so a caller who
        ran a plan, got a result, and asked for it again was refused by
        the database with no way to proceed.

        SQLite cannot drop a table constraint in place, so the table is
        rebuilt and the rows copied across. `CREATE TABLE IF NOT EXISTS`
        above leaves an existing table untouched, which is why a
        pre-existing database still carries the old shape at this point.
        """
        schema = connection.execute(  # type: ignore[attr-defined]
            """
            SELECT sql
            FROM sqlite_master
            WHERE type = 'table'
              AND name = 'operations'
            """
        ).fetchone()

        if schema is None:
            return

        if "UNIQUE(owner_id, operation_type, fingerprint)" not in str(
            schema["sql"]
        ):
            return

        columns = [
            str(row["name"])
            for row in connection.execute(  # type: ignore[attr-defined]
                "PRAGMA table_info(operations)"
            ).fetchall()
        ]
        column_list = ", ".join(columns)

        rebuilt = (
            str(schema["sql"])
            .replace(
                "CREATE TABLE operations",
                "CREATE TABLE operations_rebuilt",
                1,
            )
            .replace(
                ",\n            UNIQUE(owner_id, operation_type, fingerprint)",
                "",
            )
            .replace(
                ", UNIQUE(owner_id, operation_type, fingerprint)",
                "",
            )
        )

        if "UNIQUE(owner_id, operation_type, fingerprint)" in rebuilt:
            # The declaration is present in a layout this rewrite does not
            # recognise. Rebuilding on a guess could silently drop a
            # different constraint, so the old table is left intact and
            # the caller keeps the schema it already had.
            return

        connection.executescript(  # type: ignore[attr-defined]
            f"""
            {rebuilt};
            INSERT INTO operations_rebuilt ({column_list})
                SELECT {column_list} FROM operations;
            DROP TABLE operations;
            ALTER TABLE operations_rebuilt RENAME TO operations;
            CREATE INDEX IF NOT EXISTS idx_operations_owner
                ON operations(owner_id, project_id, created_at, id);
            CREATE INDEX IF NOT EXISTS idx_operations_claim
                ON operations(state, next_attempt_at, created_at, id);
            """
        )

    def _validate_postgres_schema(self) -> None:
        with self.connect() as connection:
            rows = connection.execute(
                """
                SELECT tablename
                FROM pg_catalog.pg_tables
                WHERE schemaname = 'public'
                ORDER BY tablename
                """
            ).fetchall()

        tables = {
            str(row["tablename"])
            for row in rows
        }

        missing = (
            self.REQUIRED_POSTGRES_TABLES
            - tables
        )

        if missing:
            raise RuntimeError(
                "hosted PostgreSQL schema is missing: "
                + ", ".join(sorted(missing))
            )

    def health(self) -> dict[str, object]:
        if self.is_postgres:
            with self.connect() as connection:
                identity = connection.execute(
                    """
                    SELECT
                        current_database()
                            AS database_name,
                        current_user
                            AS database_user,
                        version()
                            AS server_version
                    """
                ).fetchone()

                rows = connection.execute(
                    """
                    SELECT tablename
                    FROM pg_catalog.pg_tables
                    WHERE schemaname = 'public'
                    ORDER BY tablename
                    """
                ).fetchall()

            return {
                "backend": "postgresql",
                "database": identity[
                    "database_name"
                ],
                "database_user": identity[
                    "database_user"
                ],
                "server_version": identity[
                    "server_version"
                ],
                "owner_id_configured": bool(
                    self.owner_id
                ),
                "tables": [
                    row["tablename"]
                    for row in rows
                ],
            }

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
            "backend": "sqlite",
            "database": str(self.path),
            "integrity": integrity,
            "tables": tables,
        }
