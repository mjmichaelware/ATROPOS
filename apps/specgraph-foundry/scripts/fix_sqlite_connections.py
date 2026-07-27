from pathlib import Path
from textwrap import dedent

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


write(
    "src/specgraph_foundry/database.py",
    r'''
    import sqlite3
    from pathlib import Path
    from types import TracebackType


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
        def __init__(self, path: Path) -> None:
            self.path = path

        def connect(self) -> ManagedConnection:
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
    "tests/test_database.py",
    r'''
    import sqlite3
    import tempfile
    import unittest
    import warnings
    from pathlib import Path

    from specgraph_foundry.database import Database


    class DatabaseConnectionTest(unittest.TestCase):
        def setUp(self) -> None:
            self.temp = tempfile.TemporaryDirectory()
            self.database = Database(
                Path(self.temp.name) / "test.sqlite3"
            )
            self.database.initialize()

        def tearDown(self) -> None:
            self.temp.cleanup()

        def test_context_manager_closes_connection(
            self,
        ) -> None:
            with self.database.connect() as connection:
                result = connection.execute(
                    "SELECT 1"
                ).fetchone()[0]

            self.assertEqual(result, 1)

            with self.assertRaises(
                sqlite3.ProgrammingError
            ):
                connection.execute("SELECT 1")

        def test_repeated_connections_do_not_warn(
            self,
        ) -> None:
            with warnings.catch_warnings():
                warnings.simplefilter(
                    "error",
                    ResourceWarning,
                )

                for _ in range(100):
                    with self.database.connect() as connection:
                        connection.execute(
                            "SELECT 1"
                        ).fetchone()


    if __name__ == "__main__":
        unittest.main()
    ''',
)

print("SQLITE CONNECTION LIFECYCLE FIXED")
