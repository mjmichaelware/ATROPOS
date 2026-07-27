from pathlib import Path
from textwrap import dedent
from urllib.request import urlopen
import os

ROOT = Path.cwd()

if ROOT.name != "specgraph-foundry" or not (ROOT / ".git").is_dir():
    raise SystemExit(
        f"Run this inside the cloned specgraph-foundry repository. Current: {ROOT}"
    )


def write(relative_path: str, content: str, executable: bool = False) -> None:
    destination = ROOT / relative_path
    destination.parent.mkdir(parents=True, exist_ok=True)
    destination.write_text(
        dedent(content).lstrip(),
        encoding="utf-8",
    )

    if executable:
        destination.chmod(0o755)

    print(f"CREATED {relative_path}")


apache_license = urlopen(
    "https://www.apache.org/licenses/LICENSE-2.0.txt",
    timeout=30,
).read().decode("utf-8")

write("LICENSE", apache_license)
write("LICENSES/Apache-2.0.txt", apache_license)

write(
    "NOTICE",
    """
    SpecGraph Foundry
    Copyright 2026 Michael Alonzo Ware

    This product includes software developed for the SpecGraph Foundry project.

    Third-party software, assets, services, models, and generated distributions
    may carry additional notices. See THIRD_PARTY_NOTICES.md and docs/legal/.
    """,
)

write(
    "THIRD_PARTY_NOTICES.md",
    """
    # Third-Party Notices

    Pass 0 has no required third-party Python runtime dependencies.

    The following technologies are currently research candidates and are not
    bundled into this repository:

    - React Flow
    - ELK / elkjs
    - Cytoscape.js
    - Sigma.js
    - Apache ECharts
    - Mermaid
    - Graphviz
    - Supabase
    - GitHub Actions
    - GitHub Models
    - OpenCode
    - Google Vertex AI
    - Google Cloud Tasks
    - Cloud Run Jobs
    - Prefect
    - Temporal
    - Dagster

    Before any dependency is added, its exact version, upstream source,
    copyright holder, SPDX identifier, license text, attribution requirements,
    modifications, and distribution obligations must be recorded here.
    """,
)

write(
    "docs/legal/LICENSING.md",
    """
    # Licensing

    Original source code, documentation, schemas, tests, configuration,
    diagrams, and original project assets are licensed under Apache-2.0 unless
    a file explicitly states otherwise.

    Copyright 2026 Michael Alonzo Ware.

    Third-party material remains under its original license.

    User-supplied documents and generated blueprints remain subject to the
    rights of their respective owners. Processing content through SpecGraph
    Foundry does not transfer ownership.
    """,
)

write(
    "docs/legal/DEPENDENCY_LICENSE_POLICY.md",
    """
    # Dependency License Policy

    Every production dependency must pass a license-admission gate.

    Normally acceptable after verification:

    - Apache-2.0
    - MIT
    - BSD-2-Clause
    - BSD-3-Clause
    - ISC
    - 0BSD

    Requires explicit review:

    - MPL-2.0
    - EPL-2.0
    - LGPL licenses
    - dual-licensed packages
    - fonts
    - models
    - datasets
    - media assets
    - hosted API terms

    Forbidden without a deliberate project-level decision:

    - unknown-license dependencies
    - noncommercial restrictions
    - field-of-use restrictions
    - copied code without provenance
    - packages without exact versions
    - generated code copied from unidentified third-party sources
    """,
)

write(
    "docs/legal/ASSET_LICENSE_POLICY.md",
    """
    # Asset License Policy

    Every image, logo, icon, font, screenshot, fixture, sample document,
    audio file, video file, and generated media asset must have recorded
    provenance.

    Required fields:

    - asset path
    - creator
    - source
    - copyright holder
    - license identifier
    - attribution requirements
    - modification status
    - redistribution status

    Unknown-license assets are forbidden.
    """,
)

write(
    "SECURITY.md",
    """
    # Security Policy

    Security invariants:

    1. Secret values never enter Git history.
    2. Secret diagnostics report presence only.
    3. Model output is treated as untrusted input.
    4. Raw model-generated shell commands are never automatically executed.
    5. Executable actions require typed policy checks.
    6. Source documents retain immutable fingerprints.
    7. Completion requires independent evidence.
    8. Supabase service-role credentials never reach browser clients.
    9. Authentication and authorization failures fail closed.
    """,
)

write(
    "CONTRIBUTING.md",
    """
    # Contributing

    - One file has one narrow responsibility.
    - Every behavior requires a testable contract.
    - No placeholder or fake-success implementations.
    - No provider verifies its own work.
    - Source requirements retain exact provenance.
    - Research conclusions remain distinct from source authority.
    - New dependencies require license review.
    - Secrets never enter commits, fixtures, logs, or examples.

    Run the foundation gate:

    ```bash
    export PYTHONPATH="$PWD/src"
    ./scripts/test.sh
    ```
    """,
)

write(
    "CODE_OF_CONDUCT.md",
    """
    # Code of Conduct

    SpecGraph Foundry welcomes rigorous and respectful technical collaboration.

    Harassment, discrimination, malicious code, fabricated evidence,
    plagiarism, concealed licensing problems, and disclosure of private
    project data are prohibited.
    """,
)

write(
    "assets/hero.svg",
    """
    <svg xmlns="http://www.w3.org/2000/svg" width="1280" height="420" viewBox="0 0 1280 420">
      <defs>
        <linearGradient id="bg" x1="0" y1="0" x2="1" y2="1">
          <stop offset="0%" stop-color="#070b18"/>
          <stop offset="55%" stop-color="#111d3d"/>
          <stop offset="100%" stop-color="#2b1450"/>
        </linearGradient>
        <linearGradient id="edge" x1="0" y1="0" x2="1" y2="0">
          <stop offset="0%" stop-color="#62d8ff"/>
          <stop offset="50%" stop-color="#938aff"/>
          <stop offset="100%" stop-color="#ed77ff"/>
        </linearGradient>
      </defs>

      <rect width="1280" height="420" rx="28" fill="url(#bg)"/>

      <g opacity="0.12" stroke="#cbd5ff">
        <path d="M0 70H1280M0 140H1280M0 210H1280M0 280H1280M0 350H1280"/>
        <path d="M120 0V420M280 0V420M440 0V420M600 0V420M760 0V420M920 0V420M1080 0V420"/>
      </g>

      <g fill="none" stroke="url(#edge)" stroke-width="4">
        <path d="M90 305C185 305 185 145 300 145"/>
        <path d="M300 145C430 145 430 225 550 225"/>
        <path d="M300 145C430 145 430 330 550 330"/>
        <path d="M550 225C690 225 680 110 820 110"/>
        <path d="M550 225C690 225 690 240 820 240"/>
        <path d="M550 330C690 330 700 350 820 350"/>
        <path d="M820 110C960 110 950 230 1090 230"/>
        <path d="M820 240C960 240 950 230 1090 230"/>
        <path d="M820 350C960 350 960 230 1090 230"/>
      </g>

      <g fill="#101b38" stroke="#75ddff" stroke-width="3">
        <circle cx="90" cy="305" r="18"/>
        <circle cx="300" cy="145" r="22"/>
        <circle cx="550" cy="225" r="24"/>
        <circle cx="550" cy="330" r="18"/>
        <circle cx="820" cy="110" r="18"/>
        <circle cx="820" cy="240" r="22"/>
        <circle cx="820" cy="350" r="18"/>
        <circle cx="1090" cy="230" r="28"/>
      </g>

      <text x="70" y="70" fill="#ffffff" font-family="sans-serif" font-size="52" font-weight="700">
        SpecGraph Foundry
      </text>

      <text x="72" y="116" fill="#c9d4ff" font-family="sans-serif" font-size="22">
        Compile source authority into research-enriched, verifiable execution DAGs.
      </text>

      <text x="72" y="392" fill="#9eafe8" font-family="monospace" font-size="15">
        API-first • provider-independent • renderer-independent • restart-safe
      </text>
    </svg>
    """,
)

write(
    "README.md",
    """
    <p align="center">
      <img src="assets/hero.svg" alt="SpecGraph Foundry" width="100%">
    </p>

    <p align="center">
      <a href="https://github.com/mjmichaelware/specgraph-foundry/actions/workflows/ci.yml">
        <img src="https://github.com/mjmichaelware/specgraph-foundry/actions/workflows/ci.yml/badge.svg" alt="CI">
      </a>
      <a href="LICENSE">
        <img src="https://img.shields.io/badge/license-Apache--2.0-6d82ff" alt="Apache-2.0">
      </a>
      <img src="https://img.shields.io/badge/python-3.11%2B-3776ab" alt="Python 3.11+">
      <img src="https://img.shields.io/badge/architecture-API--first-9b6cff" alt="API first">
    </p>

    # SpecGraph Foundry

    **Source documents are not prompts. They are compilable authority.**

    SpecGraph Foundry is a generic platform for converting complex project
    documentation into complete, research-enriched, independently verifiable
    software blueprints.

    ## Processing pipeline

    ```mermaid
    flowchart LR
        A[Source Authorities] --> B[Immutable Ingestion]
        B --> C[Atomic Extraction]
        C --> D[Coverage Validation]
        D --> E[Gap Matrix]
        E --> F[Deep Research]
        F --> G[Evidence Reconciliation]
        G --> H[Complete Specification]
        H --> I[Authority Graph]
        I --> J[Execution DAG]
        J --> K[Implementation Workers]
        K --> L[Independent Verification]
        L --> J
    ```

    ## Two graph types

    | Graph | Purpose | Cycles |
    |---|---|---:|
    | Authority Graph | Requirements, evidence, decisions, conflicts, relationships | Allowed |
    | Execution DAG | Dependency-safe implementation order | Forbidden |

    ## Pass 0 capabilities

    - project persistence
    - source-document hashing
    - authority and execution graphs
    - execution-cycle prevention
    - dependency-ready node calculation
    - SQLite local development
    - Supabase PostgreSQL migration
    - HTTP JSON API
    - secret-presence diagnostics
    - Apache-2.0 licensing
    - tests and GitHub Actions

    ## Run

    ```bash
    cd ~/specgraph-foundry
    export PYTHONPATH="$PWD/src"

    python -m specgraph_foundry init
    python -m specgraph_foundry demo
    python -m specgraph_foundry doctor
    python -m specgraph_foundry serve
    ```

    ## Planned visualization adapters

    | Capability | Candidate |
    |---|---|
    | Editable DAG | React Flow |
    | Hierarchical layout | ELK |
    | Analytical graph | Cytoscape.js |
    | Large graph | Sigma.js |
    | Charts | Apache ECharts |
    | Markdown diagrams | Mermaid |
    | Static exports | Graphviz |

    ## Planned execution adapters

    - Supabase Queues
    - GitHub Actions
    - OpenCode
    - GitHub Models
    - Vertex AI
    - Cloud Tasks
    - Cloud Run Jobs
    - Prefect
    - Temporal
    - Dagster
    - ATROPOS

    ## Anti-stub doctrine

    SpecGraph Foundry must reject:

    - placeholder implementations
    - fake hard-coded success
    - disconnected components
    - unreachable features
    - meaningless tests
    - source-less requirements
    - provider self-verification
    - hidden unresolved decisions
    - generated boilerplate presented as completion

    ## License

    Apache-2.0.

    - `LICENSE`
    - `NOTICE`
    - `THIRD_PARTY_NOTICES.md`
    - `docs/legal/LICENSING.md`
    - `docs/legal/DEPENDENCY_LICENSE_POLICY.md`
    - `docs/legal/ASSET_LICENSE_POLICY.md`

    **Compile the truth. Research the gaps. Prove the plan.**
    """,
)

write(
    ".gitignore",
    """
    .env
    .env.*
    !.env.example
    .specgraph/
    *.sqlite
    *.sqlite3
    *.db
    __pycache__/
    *.py[cod]
    .pytest_cache/
    .coverage
    build/
    dist/
    *.egg-info/
    .venv/
    node_modules/
    *.log
    """,
)

write(
    ".env.example",
    """
    SPECGRAPH_DATABASE_PATH=.specgraph/specgraph.sqlite3
    SPECGRAPH_HOST=127.0.0.1
    SPECGRAPH_PORT=8787

    SUPABASE_URL=
    SUPABASE_ANON_KEY=
    SUPABASE_SERVICE_ROLE_KEY=

    GOOGLE_CLOUD_PROJECT=
    GOOGLE_APPLICATION_CREDENTIALS=
    GOOGLE_OAUTH_CLIENT_ID=
    GOOGLE_OAUTH_CLIENT_SECRET=

    GH_TOKEN=
    GITHUB_TOKEN=
    """,
)

write(
    "pyproject.toml",
    """
    [project]
    name = "specgraph-foundry"
    version = "0.1.0"
    description = "Source-authority compiler and execution-DAG platform"
    readme = "README.md"
    requires-python = ">=3.11"
    license = {text = "Apache-2.0"}
    authors = [
      {name = "Michael Alonzo Ware"}
    ]

    [project.scripts]
    specgraph = "specgraph_foundry.cli:main"

    [build-system]
    requires = ["setuptools>=68"]
    build-backend = "setuptools.build_meta"

    [tool.setuptools.packages.find]
    where = ["src"]
    """,
)

write(
    "src/specgraph_foundry/__init__.py",
    """
    \"\"\"SpecGraph Foundry.\"\"\"

    __version__ = "0.1.0"
    """,
)

write(
    "src/specgraph_foundry/__main__.py",
    """
    from .cli import main

    raise SystemExit(main())
    """,
)

write(
    "src/specgraph_foundry/errors.py",
    """
    class SpecGraphError(Exception):
        \"\"\"Base application error.\"\"\"


    class ValidationError(SpecGraphError):
        \"\"\"Input violates an invariant.\"\"\"


    class NotFoundError(SpecGraphError):
        \"\"\"Requested record does not exist.\"\"\"


    class ConflictError(SpecGraphError):
        \"\"\"Requested mutation conflicts with stored state.\"\"\"
    """,
)

write(
    "src/specgraph_foundry/config.py",
    """
    import os
    from dataclasses import dataclass
    from pathlib import Path


    @dataclass(frozen=True, slots=True)
    class Settings:
        database_path: Path
        host: str
        port: int

        @classmethod
        def from_environment(cls) -> "Settings":
            return cls(
                database_path=Path(
                    os.environ.get(
                        "SPECGRAPH_DATABASE_PATH",
                        ".specgraph/specgraph.sqlite3",
                    )
                ),
                host=os.environ.get(
                    "SPECGRAPH_HOST",
                    "127.0.0.1",
                ),
                port=int(
                    os.environ.get(
                        "SPECGRAPH_PORT",
                        "8787",
                    )
                ),
            )
    """,
)

write(
    "src/specgraph_foundry/database.py",
    '''
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
        sha256 TEXT NOT NULL,
        byte_count INTEGER NOT NULL,
        line_count INTEGER NOT NULL,
        content TEXT NOT NULL,
        created_at TEXT NOT NULL,
        UNIQUE(project_id, sha256)
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

        def health(self) -> dict[str, object]:
            with self.connect() as connection:
                integrity = connection.execute(
                    "PRAGMA integrity_check"
                ).fetchone()[0]

            return {
                "database": str(self.path),
                "integrity": integrity,
            }
    ''',
)

write(
    "src/specgraph_foundry/services.py",
    '''
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
        return f"{prefix}-{uuid.uuid4()}"


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
            line_count = content.count("\\n")

            if not content.endswith("\\n"):
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


    class GraphService:
        GRAPH_KINDS = {
            "AUTHORITY",
            "EXECUTION",
            "RESEARCH",
            "CUSTOM",
        }

        NODE_STATUSES = {
            "PENDING",
            "READY",
            "CLAIMED",
            "RUNNING",
            "BLOCKED",
            "FAILED",
            "CANCELLED",
            "COMPLETE",
        }

        def __init__(self, database: Database) -> None:
            self.database = database

        def create(
            self,
            project_id: str,
            name: str,
            kind: str,
            enforce_acyclic: bool,
        ) -> dict[str, object]:
            if kind not in self.GRAPH_KINDS:
                raise ValidationError(
                    f"invalid graph kind: {kind}"
                )

            graph_id = new_id("graph")

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
                    INSERT INTO graphs(
                        id,
                        project_id,
                        name,
                        kind,
                        enforce_acyclic,
                        created_at
                    )
                    VALUES(?,?,?,?,?,?)
                    """,
                    (
                        graph_id,
                        project_id,
                        name.strip(),
                        kind,
                        int(enforce_acyclic),
                        utc_now(),
                    ),
                )

            return self.get(graph_id)

        def add_node(
            self,
            graph_id: str,
            node_key: str,
            node_type: str,
            title: str,
        ) -> dict[str, object]:
            node_id = new_id("node")

            with self.database.connect() as connection:
                self._require_graph(
                    connection,
                    graph_id,
                )

                connection.execute(
                    """
                    INSERT INTO graph_nodes(
                        id,
                        graph_id,
                        node_key,
                        node_type,
                        title,
                        status,
                        created_at
                    )
                    VALUES(?,?,?,?,?,?,?)
                    """,
                    (
                        node_id,
                        graph_id,
                        node_key,
                        node_type,
                        title,
                        "PENDING",
                        utc_now(),
                    ),
                )

            return self.get_node(node_id)

        def add_edge(
            self,
            graph_id: str,
            from_node_id: str,
            to_node_id: str,
            edge_type: str,
        ) -> dict[str, object]:
            if from_node_id == to_node_id:
                raise ValidationError(
                    "self edges are forbidden"
                )

            edge_id = new_id("edge")

            with self.database.connect() as connection:
                graph = self._require_graph(
                    connection,
                    graph_id,
                )

                self._require_node(
                    connection,
                    graph_id,
                    from_node_id,
                )

                self._require_node(
                    connection,
                    graph_id,
                    to_node_id,
                )

                if (
                    bool(graph["enforce_acyclic"])
                    and self._creates_cycle(
                        connection,
                        graph_id,
                        from_node_id,
                        to_node_id,
                    )
                ):
                    raise ValidationError(
                        "edge would create a cycle"
                    )

                connection.execute(
                    """
                    INSERT INTO graph_edges(
                        id,
                        graph_id,
                        from_node_id,
                        to_node_id,
                        edge_type,
                        created_at
                    )
                    VALUES(?,?,?,?,?,?)
                    """,
                    (
                        edge_id,
                        graph_id,
                        from_node_id,
                        to_node_id,
                        edge_type,
                        utc_now(),
                    ),
                )

            return {
                "id": edge_id,
                "graph_id": graph_id,
                "from_node_id": from_node_id,
                "to_node_id": to_node_id,
                "edge_type": edge_type,
            }

        def set_status(
            self,
            node_id: str,
            status: str,
        ) -> dict[str, object]:
            if status not in self.NODE_STATUSES:
                raise ValidationError(
                    f"invalid node status: {status}"
                )

            with self.database.connect() as connection:
                cursor = connection.execute(
                    """
                    UPDATE graph_nodes
                    SET status = ?
                    WHERE id = ?
                    """,
                    (status, node_id),
                )

                if cursor.rowcount != 1:
                    raise NotFoundError(
                        f"node not found: {node_id}"
                    )

            return self.get_node(node_id)

        def ready_nodes(
            self,
            graph_id: str,
        ) -> list[dict[str, object]]:
            with self.database.connect() as connection:
                graph = self._require_graph(
                    connection,
                    graph_id,
                )

                if not bool(graph["enforce_acyclic"]):
                    raise ValidationError(
                        "ready-node calculation requires an acyclic graph"
                    )

                rows = connection.execute(
                    """
                    SELECT node.*
                    FROM graph_nodes AS node
                    WHERE node.graph_id = ?
                      AND node.status IN ('PENDING', 'READY')
                      AND NOT EXISTS (
                          SELECT 1
                          FROM graph_edges AS edge
                          JOIN graph_nodes AS predecessor
                            ON predecessor.id = edge.from_node_id
                          WHERE edge.graph_id = node.graph_id
                            AND edge.to_node_id = node.id
                            AND predecessor.status <> 'COMPLETE'
                      )
                    ORDER BY node.created_at, node.id
                    """,
                    (graph_id,),
                ).fetchall()

            return [
                self._normalize_node(dict(row))
                for row in rows
            ]

        def get(
            self,
            graph_id: str,
        ) -> dict[str, object]:
            with self.database.connect() as connection:
                graph = self._require_graph(
                    connection,
                    graph_id,
                )

                nodes = connection.execute(
                    """
                    SELECT *
                    FROM graph_nodes
                    WHERE graph_id = ?
                    ORDER BY created_at, id
                    """,
                    (graph_id,),
                ).fetchall()

                edges = connection.execute(
                    """
                    SELECT *
                    FROM graph_edges
                    WHERE graph_id = ?
                    ORDER BY created_at, id
                    """,
                    (graph_id,),
                ).fetchall()

            result = dict(graph)
            result["enforce_acyclic"] = bool(
                result["enforce_acyclic"]
            )
            result["nodes"] = [
                self._normalize_node(dict(row))
                for row in nodes
            ]
            result["edges"] = [
                dict(row)
                for row in edges
            ]

            return result

        def get_node(
            self,
            node_id: str,
        ) -> dict[str, object]:
            with self.database.connect() as connection:
                row = connection.execute(
                    """
                    SELECT *
                    FROM graph_nodes
                    WHERE id = ?
                    """,
                    (node_id,),
                ).fetchone()

            if row is None:
                raise NotFoundError(
                    f"node not found: {node_id}"
                )

            return self._normalize_node(dict(row))

        def _creates_cycle(
            self,
            connection: sqlite3.Connection,
            graph_id: str,
            from_node_id: str,
            to_node_id: str,
        ) -> bool:
            rows = connection.execute(
                """
                SELECT from_node_id, to_node_id
                FROM graph_edges
                WHERE graph_id = ?
                """,
                (graph_id,),
            ).fetchall()

            adjacency: dict[str, set[str]] = defaultdict(set)

            for row in rows:
                adjacency[row["from_node_id"]].add(
                    row["to_node_id"]
                )

            adjacency[from_node_id].add(to_node_id)

            stack = [to_node_id]
            visited: set[str] = set()

            while stack:
                current = stack.pop()

                if current == from_node_id:
                    return True

                if current in visited:
                    continue

                visited.add(current)
                stack.extend(
                    adjacency.get(current, set())
                )

            return False

        @staticmethod
        def _require_graph(
            connection: sqlite3.Connection,
            graph_id: str,
        ) -> sqlite3.Row:
            row = connection.execute(
                """
                SELECT *
                FROM graphs
                WHERE id = ?
                """,
                (graph_id,),
            ).fetchone()

            if row is None:
                raise NotFoundError(
                    f"graph not found: {graph_id}"
                )

            return row

        @staticmethod
        def _require_node(
            connection: sqlite3.Connection,
            graph_id: str,
            node_id: str,
        ) -> None:
            row = connection.execute(
                """
                SELECT id
                FROM graph_nodes
                WHERE graph_id = ?
                  AND id = ?
                """,
                (graph_id, node_id),
            ).fetchone()

            if row is None:
                raise ValidationError(
                    f"node {node_id} does not belong to graph {graph_id}"
                )

        @staticmethod
        def _normalize_node(
            record: dict[str, object],
        ) -> dict[str, object]:
            record["payload"] = json.loads(
                str(record.pop("payload_json"))
            )
            return record
    ''',
)

write(
    "src/specgraph_foundry/doctor.py",
    """
    import os
    from pathlib import Path


    SECRET_NAMES = (
        "SUPABASE_URL",
        "SUPABASE_ANON_KEY",
        "SUPABASE_SERVICE_ROLE_KEY",
        "GOOGLE_CLOUD_PROJECT",
        "GOOGLE_APPLICATION_CREDENTIALS",
        "GOOGLE_OAUTH_CLIENT_ID",
        "GOOGLE_OAUTH_CLIENT_SECRET",
        "GH_TOKEN",
        "GITHUB_TOKEN",
    )


    def inspect() -> dict[str, object]:
        candidate_files = (
            Path.cwd() / ".env",
            Path.home() / ".env",
            Path.home() / "ATROPOS" / ".env",
            Path.home() / ".config" / "atropos" / ".env",
        )

        file_secret_names: set[str] = set()
        checked_files: list[str] = []

        for path in candidate_files:
            if not path.is_file():
                continue

            checked_files.append(str(path))

            for raw_line in path.read_text(
                encoding="utf-8",
                errors="ignore",
            ).splitlines():
                line = raw_line.strip()

                if (
                    not line
                    or line.startswith("#")
                    or "=" not in line
                ):
                    continue

                name = (
                    line.split("=", 1)[0]
                    .strip()
                    .removeprefix("export ")
                    .strip()
                )

                if name:
                    file_secret_names.add(name)

        return {
            "values_exposed": False,
            "env_files_checked": checked_files,
            "secrets": {
                name: {
                    "environment": bool(
                        os.environ.get(name)
                    ),
                    "env_file": (
                        name in file_secret_names
                    ),
                }
                for name in SECRET_NAMES
            },
        }
    """,
)

write(
    "src/specgraph_foundry/api.py",
    '''
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
    from .services import ProjectService


    class Api:
        def __init__(self, database: Database) -> None:
            self.database = database
            self.projects = ProjectService(database)

        def dispatch(
            self,
            method: str,
            raw_path: str,
            payload: dict[str, object],
        ) -> tuple[int, dict[str, object]]:
            parts = [
                part
                for part in urlparse(raw_path).path.split("/")
                if part
            ]

            try:
                if (
                    method == "GET"
                    and parts == ["health"]
                ):
                    return 200, {
                        "status": "ok",
                        "service": "specgraph-foundry",
                        "database": self.database.health(),
                    }

                if parts == ["v1", "projects"]:
                    if method == "GET":
                        return 200, {
                            "items": self.projects.list()
                        }

                    if method == "POST":
                        return 201, self.projects.create(
                            str(payload.get("slug", "")),
                            str(payload.get("name", "")),
                            str(
                                payload.get(
                                    "description",
                                    "",
                                )
                            ),
                        )

                if (
                    len(parts) == 3
                    and parts[:2] == ["v1", "projects"]
                    and method == "GET"
                ):
                    return 200, self.projects.get(
                        parts[2]
                    )

                return 404, {
                    "error": "ROUTE_NOT_FOUND",
                    "message": (
                        f"no route for {method} {raw_path}"
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
        database: Database,
        host: str,
        port: int,
    ) -> None:
        api = Api(database)

        class Handler(BaseHTTPRequestHandler):
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

                payload: dict[str, object] = {}

                if length:
                    try:
                        parsed = json.loads(
                            self.rfile.read(length).decode(
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
                                "error": "INVALID_JSON",
                                "message": (
                                    "request body must be valid JSON"
                                ),
                            },
                        )
                        return

                    if not isinstance(parsed, dict):
                        self._send(
                            400,
                            {
                                "error": "INVALID_JSON",
                                "message": (
                                    "request body must be a JSON object"
                                ),
                            },
                        )
                        return

                    payload = parsed

                status, response = api.dispatch(
                    self.command,
                    self.path,
                    payload,
                )

                self._send(status, response)

            def _send(
                self,
                status: int,
                payload: dict[str, object],
            ) -> None:
                encoded = json.dumps(
                    payload,
                    indent=2,
                    sort_keys=True,
                ).encode("utf-8")

                self.send_response(status)
                self.send_header(
                    "content-type",
                    "application/json; charset=utf-8",
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
            f"SpecGraph Foundry listening on "
            f"http://{host}:{port}"
        )

        try:
            server.serve_forever()
        except KeyboardInterrupt:
            print("\\nStopping SpecGraph Foundry.")
        finally:
            server.server_close()
    ''',
)

write(
    "src/specgraph_foundry/cli.py",
    '''
    import argparse
    import json
    import uuid

    from .api import serve
    from .config import Settings
    from .database import Database
    from .doctor import inspect
    from .services import (
        DocumentService,
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


    def main() -> int:
        settings = Settings.from_environment()

        parser = argparse.ArgumentParser(
            prog="specgraph"
        )

        commands = parser.add_subparsers(
            dest="command",
            required=True,
        )

        commands.add_parser("init")
        commands.add_parser("demo")
        commands.add_parser("doctor")

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

        args = parser.parse_args()

        database = Database(
            settings.database_path
        )
        database.initialize()

        if args.command == "init":
            output(database.health())
            return 0

        if args.command == "doctor":
            output(inspect())
            return 0

        if args.command == "serve":
            serve(
                database,
                args.host,
                args.port,
            )
            return 0

        projects = ProjectService(database)
        documents = DocumentService(database)
        graphs = GraphService(database)

        suffix = uuid.uuid4().hex[:8]

        project = projects.create(
            f"demo-{suffix}",
            "SpecGraph Demonstration",
        )

        document = documents.ingest(
            str(project["id"]),
            "Demo authority",
            (
                "Contracts must exist before implementation.\\n"
                "Implementation must pass independent verification.\\n"
            ),
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
                "graph": graphs.get(
                    str(graph["id"])
                ),
                "ready_nodes": graphs.ready_nodes(
                    str(graph["id"])
                ),
            }
        )

        return 0
    ''',
)

write(
    "tests/test_core.py",
    """
    import tempfile
    import unittest
    from pathlib import Path

    from specgraph_foundry.database import Database
    from specgraph_foundry.errors import ValidationError
    from specgraph_foundry.services import (
        GraphService,
        ProjectService,
    )


    class CoreTest(unittest.TestCase):
        def setUp(self) -> None:
            self.temp = tempfile.TemporaryDirectory()
            self.database = Database(
                Path(self.temp.name) / "test.sqlite3"
            )
            self.database.initialize()

            self.projects = ProjectService(
                self.database
            )
            self.graphs = GraphService(
                self.database
            )

            self.project = self.projects.create(
                "test-project",
                "Test Project",
            )

        def tearDown(self) -> None:
            self.temp.cleanup()

        def test_dependency_readiness(self) -> None:
            graph = self.graphs.create(
                str(self.project["id"]),
                "Execution",
                "EXECUTION",
                True,
            )

            first = self.graphs.add_node(
                str(graph["id"]),
                "first",
                "BATCH",
                "First",
            )

            second = self.graphs.add_node(
                str(graph["id"]),
                "second",
                "BATCH",
                "Second",
            )

            self.graphs.add_edge(
                str(graph["id"]),
                str(first["id"]),
                str(second["id"]),
                "MUST_PRECEDE",
            )

            ready = self.graphs.ready_nodes(
                str(graph["id"])
            )

            self.assertEqual(
                [
                    node["node_key"]
                    for node in ready
                ],
                ["first"],
            )

            self.graphs.set_status(
                str(first["id"]),
                "COMPLETE",
            )

            ready = self.graphs.ready_nodes(
                str(graph["id"])
            )

            self.assertEqual(
                [
                    node["node_key"]
                    for node in ready
                ],
                ["second"],
            )

        def test_execution_cycle_is_rejected(
            self,
        ) -> None:
            graph = self.graphs.create(
                str(self.project["id"]),
                "Cycle Test",
                "EXECUTION",
                True,
            )

            first = self.graphs.add_node(
                str(graph["id"]),
                "first",
                "BATCH",
                "First",
            )

            second = self.graphs.add_node(
                str(graph["id"]),
                "second",
                "BATCH",
                "Second",
            )

            self.graphs.add_edge(
                str(graph["id"]),
                str(first["id"]),
                str(second["id"]),
                "MUST_PRECEDE",
            )

            with self.assertRaises(
                ValidationError
            ):
                self.graphs.add_edge(
                    str(graph["id"]),
                    str(second["id"]),
                    str(first["id"]),
                    "MUST_PRECEDE",
                )


    if __name__ == "__main__":
        unittest.main()
    """,
)

write(
    "scripts/check_licenses.py",
    """
    from pathlib import Path


    ROOT = Path(__file__).resolve().parents[1]

    REQUIRED = (
        "LICENSE",
        "NOTICE",
        "THIRD_PARTY_NOTICES.md",
        "LICENSES/Apache-2.0.txt",
        "docs/legal/LICENSING.md",
        "docs/legal/DEPENDENCY_LICENSE_POLICY.md",
        "docs/legal/ASSET_LICENSE_POLICY.md",
    )


    def main() -> int:
        missing = [
            path
            for path in REQUIRED
            if not (ROOT / path).is_file()
        ]

        if missing:
            print("LICENSE CHECK FAILED")

            for path in missing:
                print(f"- missing: {path}")

            return 1

        license_text = (
            ROOT / "LICENSE"
        ).read_text(encoding="utf-8")

        if "Apache License" not in license_text:
            print("LICENSE CHECK FAILED")
            print("- LICENSE is not Apache-2.0")
            return 1

        print("LICENSE CHECK PASSED")
        return 0


    if __name__ == "__main__":
        raise SystemExit(main())
    """,
)

write(
    "scripts/test.sh",
    """
    #!/data/data/com.termux/files/usr/bin/bash

    set -euo pipefail

    cd "$HOME/specgraph-foundry"

    export PYTHONPATH="$PWD/src"

    python -m compileall -q src
    python -m unittest discover -s tests -v
    python scripts/check_licenses.py
    python -m specgraph_foundry init
    python -m specgraph_foundry doctor
    """,
    executable=True,
)

write(
    ".github/workflows/ci.yml",
    """
    name: CI

    on:
      push:
        branches:
          - main
      pull_request:
      workflow_dispatch:

    permissions:
      contents: read

    jobs:
      foundation:
        runs-on: ubuntu-latest
        timeout-minutes: 10

        strategy:
          matrix:
            python-version:
              - "3.11"
              - "3.13"

        steps:
          - uses: actions/checkout@v4

          - uses: actions/setup-python@v5
            with:
              python-version: ${{ matrix.python-version }}

          - name: Compile
            env:
              PYTHONPATH: src
            run: python -m compileall -q src

          - name: Test
            env:
              PYTHONPATH: src
            run: python -m unittest discover -s tests -v

          - name: Verify licenses
            run: python scripts/check_licenses.py

          - name: CLI smoke
            env:
              PYTHONPATH: src
            run: |
              python -m specgraph_foundry init
              python -m specgraph_foundry demo
    """,
)

write(
    "infra/supabase/migrations/202607120001_core.sql",
    """
    create extension if not exists pgcrypto;

    create table if not exists public.projects (
        id uuid primary key default gen_random_uuid(),
        slug text not null unique,
        name text not null,
        description text not null default '',
        created_at timestamptz not null default now()
    );

    create table if not exists public.source_documents (
        id uuid primary key default gen_random_uuid(),
        project_id uuid not null
            references public.projects(id)
            on delete cascade,
        title text not null,
        sha256 text not null,
        byte_count bigint not null,
        line_count bigint not null,
        storage_path text,
        content text,
        created_at timestamptz not null default now(),
        unique(project_id, sha256)
    );

    create table if not exists public.graphs (
        id uuid primary key default gen_random_uuid(),
        project_id uuid not null
            references public.projects(id)
            on delete cascade,
        name text not null,
        kind text not null,
        enforce_acyclic boolean not null default false,
        created_at timestamptz not null default now()
    );

    create table if not exists public.graph_nodes (
        id uuid primary key default gen_random_uuid(),
        graph_id uuid not null
            references public.graphs(id)
            on delete cascade,
        node_key text not null,
        node_type text not null,
        title text not null,
        status text not null,
        payload_json jsonb not null default '{}'::jsonb,
        created_at timestamptz not null default now(),
        unique(graph_id, node_key)
    );

    create table if not exists public.graph_edges (
        id uuid primary key default gen_random_uuid(),
        graph_id uuid not null
            references public.graphs(id)
            on delete cascade,
        from_node_id uuid not null
            references public.graph_nodes(id)
            on delete cascade,
        to_node_id uuid not null
            references public.graph_nodes(id)
            on delete cascade,
        edge_type text not null,
        inferred boolean not null default false,
        rationale text not null default '',
        created_at timestamptz not null default now(),
        check(from_node_id <> to_node_id),
        unique(
            graph_id,
            from_node_id,
            to_node_id,
            edge_type
        )
    );

    create index if not exists idx_graph_nodes_status
        on public.graph_nodes(graph_id, status);

    create index if not exists idx_graph_edges_from
        on public.graph_edges(graph_id, from_node_id);

    create index if not exists idx_graph_edges_to
        on public.graph_edges(graph_id, to_node_id);

    alter table public.projects enable row level security;
    alter table public.source_documents enable row level security;
    alter table public.graphs enable row level security;
    alter table public.graph_nodes enable row level security;
    alter table public.graph_edges enable row level security;
    """,
)

print()
print("PASS 0 FILES CREATED")
print(f"Repository: {ROOT}")
