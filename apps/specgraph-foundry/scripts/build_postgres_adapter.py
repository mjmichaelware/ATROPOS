from pathlib import Path
from textwrap import dedent
import re

ROOT = Path.cwd()

if ROOT.name != "specgraph-foundry" or not (ROOT / ".git").is_dir():
    raise SystemExit(f"Wrong repository: {ROOT}")


def write(path: str, content: str) -> None:
    target = ROOT / path
    target.parent.mkdir(parents=True, exist_ok=True)
    target.write_text(
        dedent(content).lstrip(),
        encoding="utf-8",
    )
    print(f"WROTE {path}")


def replace_once(
    path: str,
    old: str,
    new: str,
    installed_marker: str,
) -> None:
    target = ROOT / path
    content = target.read_text(encoding="utf-8")

    if installed_marker in content:
        print(f"SKIPPED {path}: already installed")
        return

    if old not in content:
        raise SystemExit(
            f"PATCH MARKER NOT FOUND IN {path}:\n{old}"
        )

    target.write_text(
        content.replace(old, new, 1),
        encoding="utf-8",
    )
    print(f"UPDATED {path}")


database_path = ROOT / "src/specgraph_foundry/database.py"
database_content = database_path.read_text(
    encoding="utf-8"
)

database_content = database_content.replace(
    "from types import TracebackType\n",
    (
        "from types import TracebackType\n"
        "from typing import Any\n"
    ),
    1,
)

class_marker = "class Database:\n"

if class_marker not in database_content:
    raise SystemExit(
        "Database class marker was not found"
    )

database_prefix = database_content.split(
    class_marker,
    1,
)[0]

database_backend = r'''
def translate_qmark_sql(sql: str) -> str:
    result: list[str] = []
    single_quote = False
    double_quote = False
    index = 0

    while index < len(sql):
        character = sql[index]

        if character == "'" and not double_quote:
            if (
                single_quote
                and index + 1 < len(sql)
                and sql[index + 1] == "'"
            ):
                result.extend(["'", "'"])
                index += 2
                continue

            single_quote = not single_quote
            result.append(character)
            index += 1
            continue

        if character == '"' and not single_quote:
            double_quote = not double_quote
            result.append(character)
            index += 1
            continue

        if (
            character == "?"
            and not single_quote
            and not double_quote
        ):
            result.append("%s")
        else:
            result.append(character)

        index += 1

    return "".join(result)


class PostgresConnection:
    def __init__(
        self,
        database_url: str,
    ) -> None:
        try:
            import psycopg
            from psycopg.rows import dict_row
        except ImportError as error:
            raise RuntimeError(
                "PostgreSQL mode requires Psycopg 3. "
                "Install the optional postgres dependency."
            ) from error

        self._psycopg = psycopg
        self._connection = psycopg.connect(
            database_url,
            row_factory=dict_row,
            prepare_threshold=None,
        )

    def __enter__(
        self,
    ) -> "PostgresConnection":
        return self

    def __exit__(
        self,
        exception_type: type[BaseException] | None,
        exception: BaseException | None,
        traceback: TracebackType | None,
    ) -> bool:
        try:
            if exception_type is None:
                self._connection.commit()
            else:
                self._connection.rollback()
        finally:
            self._connection.close()

        return False

    def execute(
        self,
        sql: str,
        parameters: tuple[object, ...] = (),
    ) -> Any:
        normalized = sql.strip()

        if normalized.upper() == "BEGIN IMMEDIATE":
            translated = "BEGIN"
        else:
            translated = translate_qmark_sql(sql)

        try:
            return self._connection.execute(
                translated,
                parameters,
            )
        except self._psycopg.IntegrityError as error:
            raise sqlite3.IntegrityError(
                str(error)
            ) from error

    def executescript(
        self,
        sql: str,
    ) -> None:
        # Hosted schema is managed exclusively by
        # Supabase migrations. Service-level SQLite
        # bootstrap scripts must not mutate it.
        return None

    def close(self) -> None:
        self._connection.close()


class Database:
    REQUIRED_POSTGRES_TABLES = {
        "projects",
        "source_documents",
        "source_sections",
        "source_chunks",
        "atoms",
        "research_tasks",
        "plan_versions",
        "exports",
        "execution_runs",
        "provider_configs",
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
'''

database_path.write_text(
    database_prefix
    + dedent(database_backend).lstrip(),
    encoding="utf-8",
)
print("UPDATED src/specgraph_foundry/database.py")


replace_once(
    "src/specgraph_foundry/config.py",
    """class Settings:
    database_path: Path
    host: str
    port: int
""",
    """class Settings:
    database_path: Path
    host: str
    port: int
    database_url: str | None = None
    database_owner_id: str | None = None
""",
    "database_owner_id:",
)

replace_once(
    "src/specgraph_foundry/config.py",
    """            port=int(
                os.environ.get(
                    "SPECGRAPH_PORT",
                    "8787",
                )
            ),
""",
    """            port=int(
                os.environ.get(
                    "SPECGRAPH_PORT",
                    "8787",
                )
            ),
            database_url=(
                os.environ.get(
                    "SPECGRAPH_DATABASE_URL"
                )
                or None
            ),
            database_owner_id=(
                os.environ.get(
                    "SPECGRAPH_OWNER_ID"
                )
                or None
            ),
""",
    'os.environ.get(\n                    "SPECGRAPH_DATABASE_URL"',
)

replace_once(
    "src/specgraph_foundry/cli.py",
    """    database = Database(settings.database_path)
""",
    """    database = Database(
        settings.database_path,
        database_url=settings.database_url,
        owner_id=settings.database_owner_id,
    )
""",
    "database_url=settings.database_url",
)


services_path = (
    ROOT
    / "src/specgraph_foundry/services.py"
)

services_content = services_path.read_text(
    encoding="utf-8"
)

old_project_insert = """        try:
            with self.database.connect() as connection:
                connection.execute(
                    \"\"\"
                    INSERT INTO projects(
                        id,
                        slug,
                        name,
                        description,
                        created_at
                    )
                    VALUES(?,?,?,?,?)
                    \"\"\",
                    (
                        project_id,
                        slug,
                        name,
                        description.strip(),
                        utc_now(),
                    ),
                )
"""

new_project_insert = """        try:
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
                        \"\"\"
                        INSERT INTO projects(
                            id,
                            owner_id,
                            slug,
                            name,
                            description,
                            created_at
                        )
                        VALUES(?,?,?,?,?,?)
                        \"\"\",
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
                        \"\"\"
                        INSERT INTO projects(
                            id,
                            slug,
                            name,
                            description,
                            created_at
                        )
                        VALUES(?,?,?,?,?)
                        \"\"\",
                        (
                            project_id,
                            slug,
                            name,
                            description.strip(),
                            utc_now(),
                        ),
                    )
"""

if (
    "SPECGRAPH_OWNER_ID is "
    not in services_content
):
    if old_project_insert not in services_content:
        raise SystemExit(
            "Project insert marker not found"
        )

    services_content = services_content.replace(
        old_project_insert,
        new_project_insert,
        1,
    )

    services_path.write_text(
        services_content,
        encoding="utf-8",
    )
    print("UPDATED src/specgraph_foundry/services.py")


uuid_files = []
uuid_replacements = 0

for path in sorted(
    (
        ROOT
        / "src/specgraph_foundry"
    ).glob("*.py")
):
    content = path.read_text(
        encoding="utf-8"
    )

    updated, count = re.subn(
        r'return f"\{prefix\}-\{uuid\.uuid4\(\)\}"',
        "return str(uuid.uuid4())",
        content,
    )

    if count:
        path.write_text(
            updated,
            encoding="utf-8",
        )
        uuid_files.append(str(path.relative_to(ROOT)))
        uuid_replacements += count

if uuid_replacements:
    print(
        "UPDATED UUID GENERATORS:",
        uuid_replacements,
        uuid_files,
    )
else:
    existing = sum(
        "return str(uuid.uuid4())"
        in path.read_text(
            encoding="utf-8"
        )
        for path in (
            ROOT
            / "src/specgraph_foundry"
        ).glob("*.py")
    )

    if existing < 7:
        raise SystemExit(
            "Expected UUID generators were not found"
        )


boolean_replacements = {
    "int(enforce_acyclic)": "enforce_acyclic",
    "int(inferred)": "inferred",
    "int(allow_open_research)": (
        "allow_open_research"
    ),
    "int(enabled)": "enabled",
    "int(allow_offline_degraded)": (
        "allow_offline_degraded"
    ),
    "int(paid_emergency_enabled)": (
        "paid_emergency_enabled"
    ),
    "int(complete)": "complete",
    "int(all_complete)": "all_complete",
}

for path in sorted(
    (
        ROOT
        / "src/specgraph_foundry"
    ).glob("*.py")
):
    content = path.read_text(
        encoding="utf-8"
    )
    updated = content

    for old, new in boolean_replacements.items():
        updated = updated.replace(
            old,
            new,
        )

    if updated != content:
        path.write_text(
            updated,
            encoding="utf-8",
        )
        print(
            "UPDATED BOOLEAN PARAMETERS",
            path.relative_to(ROOT),
        )


pyproject_path = ROOT / "pyproject.toml"
pyproject = pyproject_path.read_text(
    encoding="utf-8"
)

optional_section = dedent(
    """

    [project.optional-dependencies]
    postgres = [
      "psycopg>=3.2,<4",
    ]
    """
)

if "[project.optional-dependencies]" not in pyproject:
    pyproject_path.write_text(
        pyproject.rstrip()
        + optional_section
        + "\n",
        encoding="utf-8",
    )
    print("UPDATED pyproject.toml")


write(
    "tests/test_postgres_adapter.py",
    r'''
    import os
    import re
    import unittest
    import uuid
    from pathlib import Path
    from unittest.mock import patch

    from specgraph_foundry import atoms
    from specgraph_foundry import execution
    from specgraph_foundry import exports
    from specgraph_foundry import ingestion
    from specgraph_foundry import planning
    from specgraph_foundry import research
    from specgraph_foundry import routing
    from specgraph_foundry import services
    from specgraph_foundry.config import Settings
    from specgraph_foundry.database import (
        Database,
        translate_qmark_sql,
    )


    ROOT = Path(__file__).resolve().parents[1]


    class PostgresAdapterTest(unittest.TestCase):
        def test_qmark_translation_ignores_quotes(
            self,
        ) -> None:
            sql = (
                "SELECT '?' AS literal "
                "FROM projects WHERE id = ? "
                'AND "?" = "?"'
            )

            translated = translate_qmark_sql(sql)

            self.assertEqual(
                translated,
                (
                    "SELECT '?' AS literal "
                    "FROM projects WHERE id = %s "
                    'AND "?" = "?"'
                ),
            )

        def test_environment_selects_postgres(
            self,
        ) -> None:
            with patch.dict(
                os.environ,
                {
                    "SPECGRAPH_DATABASE_URL": (
                        "postgresql://example.invalid/db"
                    ),
                    "SPECGRAPH_OWNER_ID": (
                        "11111111-1111-1111-"
                        "1111-111111111111"
                    ),
                },
                clear=False,
            ):
                settings = (
                    Settings.from_environment()
                )

            database = Database(
                settings.database_path,
                database_url=(
                    settings.database_url
                ),
                owner_id=(
                    settings.database_owner_id
                ),
            )

            self.assertTrue(
                database.is_postgres
            )
            self.assertEqual(
                database.backend,
                "postgresql",
            )

        def test_all_new_ids_are_postgres_uuids(
            self,
        ) -> None:
            modules = [
                atoms,
                execution,
                exports,
                ingestion,
                planning,
                research,
                routing,
                services,
            ]

            for module in modules:
                generated = module.new_id(
                    "ignored"
                )

                self.assertEqual(
                    str(
                        uuid.UUID(generated)
                    ),
                    generated,
                )

        def test_runtime_sql_has_no_unsupported_forms(
            self,
        ) -> None:
            forbidden = {
                "INSERT OR REPLACE",
                "INSERT OR IGNORE",
                "REPLACE INTO",
                "AUTOINCREMENT",
                "LAST_INSERT_ROWID",
            }

            failures = []

            for path in (
                ROOT
                / "src/specgraph_foundry"
            ).glob("*.py"):
                if path.name == "database.py":
                    continue

                content = path.read_text(
                    encoding="utf-8"
                ).upper()

                for token in forbidden:
                    if token in content:
                        failures.append(
                            f"{path.name}: {token}"
                        )

            self.assertEqual(
                failures,
                [],
            )

        def test_boolean_parameters_are_not_int_cast(
            self,
        ) -> None:
            pattern = re.compile(
                r"int\("
                r"(?:enforce_acyclic|inferred|"
                r"allow_open_research|enabled|"
                r"allow_offline_degraded|"
                r"paid_emergency_enabled|"
                r"complete|all_complete)"
                r"\)"
            )

            failures = []

            for path in (
                ROOT
                / "src/specgraph_foundry"
            ).glob("*.py"):
                content = path.read_text(
                    encoding="utf-8"
                )

                if pattern.search(content):
                    failures.append(path.name)

            self.assertEqual(
                failures,
                [],
            )


    if __name__ == "__main__":
        unittest.main()
    ''',
)


readme_path = ROOT / "README.md"
readme = readme_path.read_text(
    encoding="utf-8"
)

section = dedent(
    """

    ## PostgreSQL and Supabase runtime mode

    SQLite remains the default offline and mobile-local
    backend.

    A hosted PostgreSQL backend is selected when
    `SPECGRAPH_DATABASE_URL` is configured. Hosted project
    creation also requires `SPECGRAPH_OWNER_ID`, containing
    the UUID of an existing Supabase Auth user.

    ```bash
    export SPECGRAPH_DATABASE_URL='postgresql://...'
    export SPECGRAPH_OWNER_ID='00000000-0000-0000-0000-000000000000'
    python -m specgraph_foundry init
    ```

    Hosted schema creation is controlled exclusively through
    `supabase/migrations`. Runtime service constructors never
    execute their SQLite bootstrap DDL against PostgreSQL.

    Psycopg server-side prepared statements are disabled so
    the adapter remains compatible with Supavisor transaction
    pooling as well as direct and session-mode connections.
    """
)

if "## PostgreSQL and Supabase runtime mode" not in readme:
    readme_path.write_text(
        readme.rstrip()
        + "\n"
        + section,
        encoding="utf-8",
    )
    print("UPDATED README.md")

print("POSTGRESQL RUNTIME ADAPTER CREATED")
