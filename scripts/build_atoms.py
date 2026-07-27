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
    print(f"CREATED {path}")


def replace_once(path: str, old: str, new: str) -> None:
    target = ROOT / path
    content = target.read_text(encoding="utf-8")

    if old not in content:
        raise SystemExit(
            f"PATCH MARKER NOT FOUND IN {path}:\n{old}"
        )

    target.write_text(
        content.replace(old, new, 1),
        encoding="utf-8",
    )
    print(f"UPDATED {path}")


write(
    "src/specgraph_foundry/atoms.py",
    r'''
    import hashlib
    import json
    import re
    import sqlite3
    import uuid
    from datetime import UTC, datetime

    from .database import Database
    from .errors import (
        NotFoundError,
        ValidationError,
    )


    EXTRACTOR_VERSION = "deterministic-atomizer-1"

    DIMENSIONS = (
        "FUNCTIONAL_CONTRACT",
        "DEPENDENCY_CONTRACT",
        "DATA_LIFECYCLE",
        "STATE_MODEL",
        "ERROR_MODEL",
        "SECURITY_SECRETS",
        "TERRITORY_CAPABILITIES",
        "OBSERVABILITY_PROVENANCE",
        "RESTART_RECOVERY",
        "PERFORMANCE_RESOURCES",
        "PLATFORM_ENVIRONMENT",
        "ACCESSIBILITY_UX",
        "TESTS_ACCEPTANCE",
        "INTEGRATION_CALL_SITES",
        "MIGRATION_COMPATIBILITY",
        "ROLLBACK_FAILURE_EVIDENCE",
    )

    HEADING_PATTERN = re.compile(
        r"^\s{0,3}#{1,6}\s+"
    )

    PREFIX_PATTERN = re.compile(
        r"^\s*(?:(?:[-*+])\s+|(?:\d+[.)])\s+|(?:>)\s*)?"
    )

    SENTENCE_PATTERN = re.compile(
        r"[^.!?]+(?:[.!?]+(?=\s|$)|$)"
    )

    WORD_PATTERN = re.compile(
        r"\w+",
        flags=re.UNICODE,
    )

    WHITESPACE_PATTERN = re.compile(
        r"\s+"
    )

    PROHIBITED_PATTERN = re.compile(
        r"\b("
        r"must\s+not|"
        r"shall\s+not|"
        r"should\s+not|"
        r"may\s+not|"
        r"do\s+not|"
        r"does\s+not|"
        r"never|"
        r"cannot|"
        r"can't|"
        r"forbidden|"
        r"prohibited"
        r")\b",
        flags=re.IGNORECASE,
    )

    MUST_PATTERN = re.compile(
        r"\b("
        r"must|"
        r"required|"
        r"requires|"
        r"needs?\s+to|"
        r"has\s+to|"
        r"have\s+to"
        r")\b",
        flags=re.IGNORECASE,
    )

    SHALL_PATTERN = re.compile(
        r"\bshall\b",
        flags=re.IGNORECASE,
    )

    SHOULD_PATTERN = re.compile(
        r"\bshould\b",
        flags=re.IGNORECASE,
    )

    MAY_PATTERN = re.compile(
        r"\b(may|optional|optionally)\b",
        flags=re.IGNORECASE,
    )

    KIND_RULES = (
        (
            "SECURITY",
            (
                "authentication",
                "authorization",
                "credential",
                "credentials",
                "secret",
                "secrets",
                "permission",
                "permissions",
                "encrypt",
                "encryption",
                "security",
                "token",
                "oauth",
            ),
        ),
        (
            "PERFORMANCE",
            (
                "latency",
                "throughput",
                "memory",
                "performance",
                "timeout",
                "resource",
                "resources",
                "scale",
                "scaling",
                "concurrency",
            ),
        ),
        (
            "DATA",
            (
                "database",
                "schema",
                "storage",
                "persist",
                "persistence",
                "record",
                "records",
                "migration",
                "data",
            ),
        ),
        (
            "API",
            (
                "api",
                "endpoint",
                "request",
                "response",
                "http",
                "webhook",
                "route",
                "routes",
                "sdk",
            ),
        ),
        (
            "UX",
            (
                "user interface",
                "interface",
                "screen",
                "mobile",
                "accessibility",
                "keyboard",
                "reader",
                "visual",
                "layout",
                "ux",
                "ui",
            ),
        ),
        (
            "TEST",
            (
                "test",
                "tests",
                "verify",
                "verification",
                "validate",
                "validation",
                "acceptance",
                "assert",
            ),
        ),
        (
            "OPERATIONS",
            (
                "deploy",
                "deployment",
                "logging",
                "monitoring",
                "backup",
                "restart",
                "recovery",
                "rollback",
                "health",
                "runtime",
            ),
        ),
        (
            "INTEGRATION",
            (
                "integration",
                "integrate",
                "adapter",
                "provider",
                "external",
                "connector",
                "github",
                "supabase",
                "google",
            ),
        ),
    )

    ATOM_SCHEMA = """
    PRAGMA foreign_keys = ON;

    CREATE TABLE IF NOT EXISTS extraction_runs (
        id TEXT PRIMARY KEY,
        project_id TEXT NOT NULL
            REFERENCES projects(id)
            ON DELETE CASCADE,
        document_id TEXT NOT NULL
            REFERENCES source_documents(id)
            ON DELETE CASCADE,
        extractor_version TEXT NOT NULL,
        source_sha256 TEXT NOT NULL,
        status TEXT NOT NULL,
        scanned_bytes INTEGER NOT NULL DEFAULT 0,
        scanned_lines INTEGER NOT NULL DEFAULT 0,
        statement_count INTEGER NOT NULL DEFAULT 0,
        atom_count INTEGER NOT NULL DEFAULT 0,
        dimension_count INTEGER NOT NULL DEFAULT 0,
        research_task_count INTEGER NOT NULL DEFAULT 0,
        error_message TEXT,
        created_at TEXT NOT NULL,
        completed_at TEXT,
        UNIQUE(
            document_id,
            extractor_version,
            source_sha256
        )
    );

    CREATE TABLE IF NOT EXISTS atoms (
        id TEXT PRIMARY KEY,
        project_id TEXT NOT NULL
            REFERENCES projects(id)
            ON DELETE CASCADE,
        document_id TEXT NOT NULL
            REFERENCES source_documents(id)
            ON DELETE CASCADE,
        section_id TEXT
            REFERENCES source_sections(id)
            ON DELETE SET NULL,
        extraction_run_id TEXT NOT NULL
            REFERENCES extraction_runs(id)
            ON DELETE CASCADE,
        ordinal INTEGER NOT NULL,
        kind TEXT NOT NULL,
        modality TEXT NOT NULL,
        status TEXT NOT NULL,
        canonical_statement TEXT NOT NULL,
        exact_quote TEXT NOT NULL,
        byte_start INTEGER NOT NULL,
        byte_end INTEGER NOT NULL,
        line_start INTEGER NOT NULL,
        line_end INTEGER NOT NULL,
        source_sha256 TEXT NOT NULL,
        confidence REAL NOT NULL,
        created_at TEXT NOT NULL,
        CHECK(ordinal >= 0),
        CHECK(byte_start >= 0),
        CHECK(byte_end > byte_start),
        CHECK(line_start > 0),
        CHECK(line_end >= line_start),
        CHECK(confidence >= 0.0 AND confidence <= 1.0),
        UNIQUE(
            document_id,
            byte_start,
            byte_end,
            canonical_statement
        )
    );

    CREATE TABLE IF NOT EXISTS atom_dimensions (
        id TEXT PRIMARY KEY,
        atom_id TEXT NOT NULL
            REFERENCES atoms(id)
            ON DELETE CASCADE,
        dimension TEXT NOT NULL,
        applicability TEXT NOT NULL,
        status TEXT NOT NULL,
        rationale TEXT NOT NULL DEFAULT '',
        created_at TEXT NOT NULL,
        updated_at TEXT NOT NULL,
        UNIQUE(atom_id, dimension)
    );

    CREATE TABLE IF NOT EXISTS research_tasks (
        id TEXT PRIMARY KEY,
        project_id TEXT NOT NULL
            REFERENCES projects(id)
            ON DELETE CASCADE,
        atom_id TEXT NOT NULL
            REFERENCES atoms(id)
            ON DELETE CASCADE,
        dimension TEXT NOT NULL,
        question TEXT NOT NULL,
        status TEXT NOT NULL,
        priority INTEGER NOT NULL DEFAULT 100,
        attempt_count INTEGER NOT NULL DEFAULT 0,
        lease_owner TEXT,
        lease_expires_at TEXT,
        result_json TEXT,
        created_at TEXT NOT NULL,
        updated_at TEXT NOT NULL,
        UNIQUE(atom_id, dimension)
    );

    CREATE INDEX IF NOT EXISTS idx_extraction_runs_document
        ON extraction_runs(document_id, status);

    CREATE INDEX IF NOT EXISTS idx_atoms_document
        ON atoms(document_id, ordinal);

    CREATE INDEX IF NOT EXISTS idx_atoms_project
        ON atoms(project_id, kind, modality);

    CREATE INDEX IF NOT EXISTS idx_atom_dimensions_atom
        ON atom_dimensions(atom_id, dimension);

    CREATE INDEX IF NOT EXISTS idx_research_tasks_project
        ON research_tasks(project_id, status, priority);

    CREATE INDEX IF NOT EXISTS idx_research_tasks_atom
        ON research_tasks(atom_id, dimension);
    """


    def utc_now() -> str:
        return datetime.now(UTC).isoformat()


    def new_id(prefix: str) -> str:
        return f"{prefix}-{uuid.uuid4()}"


    def normalize_statement(value: str) -> str:
        return WHITESPACE_PATTERN.sub(
            " ",
            value,
        ).strip()


    def classify_modality(
        statement: str,
    ) -> tuple[str, float]:
        if PROHIBITED_PATTERN.search(statement):
            return "PROHIBITED", 0.99

        if SHALL_PATTERN.search(statement):
            return "SHALL", 0.98

        if MUST_PATTERN.search(statement):
            return "MUST", 0.98

        if SHOULD_PATTERN.search(statement):
            return "SHOULD", 0.95

        if MAY_PATTERN.search(statement):
            return "MAY", 0.92

        return "DECLARATIVE", 0.72


    def classify_kind(statement: str) -> str:
        lowered = statement.casefold()

        for kind, keywords in KIND_RULES:
            if any(
                keyword in lowered
                for keyword in keywords
            ):
                return kind

        return "FUNCTIONAL"


    def research_question(
        statement: str,
        dimension: str,
    ) -> str:
        prompts = {
            "FUNCTIONAL_CONTRACT": (
                "What exact inputs, outputs, invariants, "
                "and acceptance behavior are required?"
            ),
            "DEPENDENCY_CONTRACT": (
                "What components, services, libraries, "
                "and ordering dependencies are required?"
            ),
            "DATA_LIFECYCLE": (
                "What data is created, read, updated, "
                "retained, exported, or deleted?"
            ),
            "STATE_MODEL": (
                "What states, transitions, guards, and "
                "terminal conditions are required?"
            ),
            "ERROR_MODEL": (
                "What failures can occur and how must "
                "each failure be represented and handled?"
            ),
            "SECURITY_SECRETS": (
                "What authentication, authorization, "
                "privacy, and secret-handling rules apply?"
            ),
            "TERRITORY_CAPABILITIES": (
                "Which actor or worker is permitted to "
                "perform each related action?"
            ),
            "OBSERVABILITY_PROVENANCE": (
                "What logs, metrics, traces, evidence, "
                "and provenance must be retained?"
            ),
            "RESTART_RECOVERY": (
                "How must interrupted work resume without "
                "duplication, corruption, or lost state?"
            ),
            "PERFORMANCE_RESOURCES": (
                "What latency, throughput, memory, storage, "
                "and concurrency limits apply?"
            ),
            "PLATFORM_ENVIRONMENT": (
                "Which operating systems, runtimes, devices, "
                "and deployment environments must work?"
            ),
            "ACCESSIBILITY_UX": (
                "What interaction, accessibility, visual, "
                "mobile, and usability requirements apply?"
            ),
            "TESTS_ACCEPTANCE": (
                "Which deterministic tests and acceptance "
                "evidence prove this requirement is complete?"
            ),
            "INTEGRATION_CALL_SITES": (
                "Where is this behavior invoked, exposed, "
                "registered, or connected to other systems?"
            ),
            "MIGRATION_COMPATIBILITY": (
                "What existing data, APIs, versions, and "
                "clients must remain compatible?"
            ),
            "ROLLBACK_FAILURE_EVIDENCE": (
                "How can the change be rolled back and what "
                "evidence must be retained after failure?"
            ),
        }

        return (
            f'Requirement: "{statement}" '
            f'{prompts[dimension]}'
        )


    def extract_statements(
        raw: bytes,
        sections: list[dict[str, object]],
    ) -> list[dict[str, object]]:
        text = raw.decode(
            "utf-8",
            errors="strict",
        )

        statements: list[dict[str, object]] = []
        byte_offset = 0
        line_number = 1
        inside_fence = False

        for line in text.splitlines(
            keepends=True
        ):
            line_without_ending = line.rstrip(
                "\r\n"
            )
            stripped = line_without_ending.strip()

            if (
                stripped.startswith("```")
                or stripped.startswith("~~~")
            ):
                inside_fence = not inside_fence
                byte_offset += len(
                    line.encode("utf-8")
                )
                line_number += 1
                continue

            if (
                inside_fence
                or not stripped
                or HEADING_PATTERN.match(
                    line_without_ending
                )
            ):
                byte_offset += len(
                    line.encode("utf-8")
                )
                line_number += 1
                continue

            prefix_match = PREFIX_PATTERN.match(
                line_without_ending
            )

            content_start = (
                prefix_match.end()
                if prefix_match
                else 0
            )

            candidate_text = (
                line_without_ending[
                    content_start:
                ]
            )

            for match in SENTENCE_PATTERN.finditer(
                candidate_text
            ):
                segment = match.group(0)

                leading = (
                    len(segment)
                    - len(segment.lstrip())
                )
                trailing = (
                    len(segment)
                    - len(segment.rstrip())
                )

                start_character = (
                    content_start
                    + match.start()
                    + leading
                )

                end_character = (
                    content_start
                    + match.end()
                    - trailing
                )

                if end_character <= start_character:
                    continue

                exact_quote = (
                    line_without_ending[
                        start_character:
                        end_character
                    ]
                )

                if len(
                    WORD_PATTERN.findall(
                        exact_quote
                    )
                ) < 2:
                    continue

                canonical = normalize_statement(
                    exact_quote
                )

                local_prefix = (
                    line_without_ending[
                        :start_character
                    ]
                )

                local_statement = (
                    line_without_ending[
                        :end_character
                    ]
                )

                statement_byte_start = (
                    byte_offset
                    + len(
                        local_prefix.encode(
                            "utf-8"
                        )
                    )
                )

                statement_byte_end = (
                    byte_offset
                    + len(
                        local_statement.encode(
                            "utf-8"
                        )
                    )
                )

                section_id = None

                for section in sections:
                    if (
                        int(
                            section["byte_start"]
                        )
                        <= statement_byte_start
                        < int(
                            section["byte_end"]
                        )
                    ):
                        section_id = section["id"]
                        break

                modality, confidence = (
                    classify_modality(
                        canonical
                    )
                )

                statements.append(
                    {
                        "ordinal": len(
                            statements
                        ),
                        "section_id": section_id,
                        "kind": classify_kind(
                            canonical
                        ),
                        "modality": modality,
                        "status": "DISCOVERED",
                        "canonical_statement": (
                            canonical
                        ),
                        "exact_quote": (
                            exact_quote
                        ),
                        "byte_start": (
                            statement_byte_start
                        ),
                        "byte_end": (
                            statement_byte_end
                        ),
                        "line_start": (
                            line_number
                        ),
                        "line_end": (
                            line_number
                        ),
                        "source_sha256": (
                            hashlib.sha256(
                                raw[
                                    statement_byte_start:
                                    statement_byte_end
                                ]
                            ).hexdigest()
                        ),
                        "confidence": confidence,
                    }
                )

            byte_offset += len(
                line.encode("utf-8")
            )
            line_number += 1

        return statements


    class AtomService:
        def __init__(
            self,
            database: Database,
        ) -> None:
            self.database = database
            self.ensure_schema()

        def ensure_schema(self) -> None:
            with self.database.connect() as connection:
                connection.executescript(
                    ATOM_SCHEMA
                )

        def extract_document(
            self,
            document_id: str,
        ) -> dict[str, object]:
            with self.database.connect() as connection:
                document = connection.execute(
                    """
                    SELECT
                        id,
                        project_id,
                        sha256,
                        byte_count,
                        line_count,
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

                existing = connection.execute(
                    """
                    SELECT id
                    FROM extraction_runs
                    WHERE document_id = ?
                      AND extractor_version = ?
                      AND source_sha256 = ?
                      AND status = 'COMPLETE'
                    """,
                    (
                        document_id,
                        EXTRACTOR_VERSION,
                        document["sha256"],
                    ),
                ).fetchone()

                if existing is not None:
                    return self.get_extraction(
                        str(existing["id"])
                    )

                sections = [
                    dict(row)
                    for row in connection.execute(
                        """
                        SELECT
                            id,
                            byte_start,
                            byte_end
                        FROM source_sections
                        WHERE document_id = ?
                        ORDER BY ordinal
                        """,
                        (document_id,),
                    ).fetchall()
                ]

            raw = str(
                document["content"]
            ).encode("utf-8")

            actual_digest = hashlib.sha256(
                raw
            ).hexdigest()

            if actual_digest != str(
                document["sha256"]
            ):
                raise ValidationError(
                    "stored document content does "
                    "not match its source fingerprint"
                )

            statements = extract_statements(
                raw,
                sections,
            )

            run_id = new_id("extraction")
            created_at = utc_now()

            with self.database.connect() as connection:
                connection.execute(
                    """
                    INSERT INTO extraction_runs(
                        id,
                        project_id,
                        document_id,
                        extractor_version,
                        source_sha256,
                        status,
                        scanned_bytes,
                        scanned_lines,
                        statement_count,
                        created_at
                    )
                    VALUES(?,?,?,?,?,?,?,?,?,?)
                    """,
                    (
                        run_id,
                        document["project_id"],
                        document_id,
                        EXTRACTOR_VERSION,
                        document["sha256"],
                        "RUNNING",
                        int(
                            document["byte_count"]
                        ),
                        int(
                            document["line_count"]
                        ),
                        len(statements),
                        created_at,
                    ),
                )

                atom_count = 0
                dimension_count = 0
                task_count = 0

                for statement in statements:
                    atom_id = new_id("atom")

                    connection.execute(
                        """
                        INSERT INTO atoms(
                            id,
                            project_id,
                            document_id,
                            section_id,
                            extraction_run_id,
                            ordinal,
                            kind,
                            modality,
                            status,
                            canonical_statement,
                            exact_quote,
                            byte_start,
                            byte_end,
                            line_start,
                            line_end,
                            source_sha256,
                            confidence,
                            created_at
                        )
                        VALUES(
                            ?,?,?,?,?,?,?,?,?,?,
                            ?,?,?,?,?,?,?,?
                        )
                        """,
                        (
                            atom_id,
                            document["project_id"],
                            document_id,
                            statement["section_id"],
                            run_id,
                            statement["ordinal"],
                            statement["kind"],
                            statement["modality"],
                            statement["status"],
                            statement[
                                "canonical_statement"
                            ],
                            statement["exact_quote"],
                            statement["byte_start"],
                            statement["byte_end"],
                            statement["line_start"],
                            statement["line_end"],
                            statement["source_sha256"],
                            statement["confidence"],
                            created_at,
                        ),
                    )

                    atom_count += 1

                    for dimension in DIMENSIONS:
                        timestamp = utc_now()

                        connection.execute(
                            """
                            INSERT INTO atom_dimensions(
                                id,
                                atom_id,
                                dimension,
                                applicability,
                                status,
                                created_at,
                                updated_at
                            )
                            VALUES(?,?,?,?,?,?,?)
                            """,
                            (
                                new_id("dimension"),
                                atom_id,
                                dimension,
                                "UNKNOWN",
                                "OPEN",
                                timestamp,
                                timestamp,
                            ),
                        )

                        connection.execute(
                            """
                            INSERT INTO research_tasks(
                                id,
                                project_id,
                                atom_id,
                                dimension,
                                question,
                                status,
                                priority,
                                created_at,
                                updated_at
                            )
                            VALUES(?,?,?,?,?,?,?,?,?)
                            """,
                            (
                                new_id(
                                    "research-task"
                                ),
                                document[
                                    "project_id"
                                ],
                                atom_id,
                                dimension,
                                research_question(
                                    str(
                                        statement[
                                            "canonical_statement"
                                        ]
                                    ),
                                    dimension,
                                ),
                                "PENDING",
                                100,
                                timestamp,
                                timestamp,
                            ),
                        )

                        dimension_count += 1
                        task_count += 1

                connection.execute(
                    """
                    UPDATE extraction_runs
                    SET status = 'COMPLETE',
                        atom_count = ?,
                        dimension_count = ?,
                        research_task_count = ?,
                        completed_at = ?
                    WHERE id = ?
                    """,
                    (
                        atom_count,
                        dimension_count,
                        task_count,
                        utc_now(),
                        run_id,
                    ),
                )

            return self.get_extraction(
                run_id
            )

        def get_extraction(
            self,
            extraction_run_id: str,
        ) -> dict[str, object]:
            with self.database.connect() as connection:
                run = connection.execute(
                    """
                    SELECT *
                    FROM extraction_runs
                    WHERE id = ?
                    """,
                    (
                        extraction_run_id,
                    ),
                ).fetchone()

                if run is None:
                    raise NotFoundError(
                        "extraction run not found: "
                        f"{extraction_run_id}"
                    )

                atoms = connection.execute(
                    """
                    SELECT *
                    FROM atoms
                    WHERE extraction_run_id = ?
                    ORDER BY ordinal
                    """,
                    (
                        extraction_run_id,
                    ),
                ).fetchall()

            result = dict(run)
            result["atoms"] = [
                dict(row)
                for row in atoms
            ]

            return result

        def list_atoms(
            self,
            document_id: str,
        ) -> list[dict[str, object]]:
            with self.database.connect() as connection:
                rows = connection.execute(
                    """
                    SELECT *
                    FROM atoms
                    WHERE document_id = ?
                    ORDER BY ordinal
                    """,
                    (document_id,),
                ).fetchall()

            return [
                dict(row)
                for row in rows
            ]

        def get_atom(
            self,
            atom_id: str,
        ) -> dict[str, object]:
            with self.database.connect() as connection:
                atom = connection.execute(
                    """
                    SELECT *
                    FROM atoms
                    WHERE id = ?
                    """,
                    (atom_id,),
                ).fetchone()

                if atom is None:
                    raise NotFoundError(
                        f"atom not found: {atom_id}"
                    )

                dimensions = connection.execute(
                    """
                    SELECT *
                    FROM atom_dimensions
                    WHERE atom_id = ?
                    ORDER BY dimension
                    """,
                    (atom_id,),
                ).fetchall()

                tasks = connection.execute(
                    """
                    SELECT *
                    FROM research_tasks
                    WHERE atom_id = ?
                    ORDER BY dimension
                    """,
                    (atom_id,),
                ).fetchall()

            result = dict(atom)
            result["dimensions"] = [
                dict(row)
                for row in dimensions
            ]
            result["research_tasks"] = [
                self._normalize_task(
                    dict(row)
                )
                for row in tasks
            ]

            return result

        def list_research_tasks(
            self,
            project_id: str,
            status: str | None = None,
        ) -> list[dict[str, object]]:
            with self.database.connect() as connection:
                if status is None:
                    rows = connection.execute(
                        """
                        SELECT *
                        FROM research_tasks
                        WHERE project_id = ?
                        ORDER BY
                            priority,
                            created_at,
                            id
                        """,
                        (project_id,),
                    ).fetchall()
                else:
                    rows = connection.execute(
                        """
                        SELECT *
                        FROM research_tasks
                        WHERE project_id = ?
                          AND status = ?
                        ORDER BY
                            priority,
                            created_at,
                            id
                        """,
                        (
                            project_id,
                            status,
                        ),
                    ).fetchall()

            return [
                self._normalize_task(
                    dict(row)
                )
                for row in rows
            ]

        @staticmethod
        def _normalize_task(
            task: dict[str, object],
        ) -> dict[str, object]:
            result_json = task.get(
                "result_json"
            )

            if result_json:
                task["result"] = json.loads(
                    str(result_json)
                )
            else:
                task["result"] = None

            task.pop(
                "result_json",
                None,
            )

            return task
    ''',
)

write(
    "infra/supabase/migrations/202607120002_atoms.sql",
    r'''
    create table if not exists public.extraction_runs (
        id uuid primary key default gen_random_uuid(),
        project_id uuid not null
            references public.projects(id)
            on delete cascade,
        document_id uuid not null
            references public.source_documents(id)
            on delete cascade,
        extractor_version text not null,
        source_sha256 text not null,
        status text not null,
        scanned_bytes bigint not null default 0,
        scanned_lines bigint not null default 0,
        statement_count bigint not null default 0,
        atom_count bigint not null default 0,
        dimension_count bigint not null default 0,
        research_task_count bigint not null default 0,
        error_message text,
        created_at timestamptz not null default now(),
        completed_at timestamptz,
        unique(
            document_id,
            extractor_version,
            source_sha256
        )
    );

    create table if not exists public.atoms (
        id uuid primary key default gen_random_uuid(),
        project_id uuid not null
            references public.projects(id)
            on delete cascade,
        document_id uuid not null
            references public.source_documents(id)
            on delete cascade,
        section_id uuid
            references public.source_sections(id)
            on delete set null,
        extraction_run_id uuid not null
            references public.extraction_runs(id)
            on delete cascade,
        ordinal bigint not null,
        kind text not null,
        modality text not null,
        status text not null,
        canonical_statement text not null,
        exact_quote text not null,
        byte_start bigint not null,
        byte_end bigint not null,
        line_start bigint not null,
        line_end bigint not null,
        source_sha256 text not null,
        confidence double precision not null,
        created_at timestamptz not null default now(),
        check(byte_start >= 0),
        check(byte_end > byte_start),
        check(line_start > 0),
        check(line_end >= line_start),
        check(confidence >= 0.0 and confidence <= 1.0),
        unique(
            document_id,
            byte_start,
            byte_end,
            canonical_statement
        )
    );

    create table if not exists public.atom_dimensions (
        id uuid primary key default gen_random_uuid(),
        atom_id uuid not null
            references public.atoms(id)
            on delete cascade,
        dimension text not null,
        applicability text not null,
        status text not null,
        rationale text not null default '',
        created_at timestamptz not null default now(),
        updated_at timestamptz not null default now(),
        unique(atom_id, dimension)
    );

    create table if not exists public.research_tasks (
        id uuid primary key default gen_random_uuid(),
        project_id uuid not null
            references public.projects(id)
            on delete cascade,
        atom_id uuid not null
            references public.atoms(id)
            on delete cascade,
        dimension text not null,
        question text not null,
        status text not null,
        priority integer not null default 100,
        attempt_count integer not null default 0,
        lease_owner text,
        lease_expires_at timestamptz,
        result_json jsonb,
        created_at timestamptz not null default now(),
        updated_at timestamptz not null default now(),
        unique(atom_id, dimension)
    );

    create index if not exists idx_atoms_document
        on public.atoms(document_id, ordinal);

    create index if not exists idx_atoms_project
        on public.atoms(project_id, kind, modality);

    create index if not exists idx_research_tasks_project
        on public.research_tasks(
            project_id,
            status,
            priority
        );

    alter table public.extraction_runs
        enable row level security;

    alter table public.atoms
        enable row level security;

    alter table public.atom_dimensions
        enable row level security;

    alter table public.research_tasks
        enable row level security;
    ''',
)

write(
    "tests/test_atoms.py",
    r'''
    import tempfile
    import unittest
    from pathlib import Path

    from specgraph_foundry.atoms import (
        AtomService,
        DIMENSIONS,
    )
    from specgraph_foundry.database import (
        Database,
    )
    from specgraph_foundry.ingestion import (
        IngestionService,
    )
    from specgraph_foundry.services import (
        ProjectService,
    )


    class AtomExtractionTest(
        unittest.TestCase
    ):
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
            self.ingestion = (
                IngestionService(
                    self.database
                )
            )
            self.atoms = AtomService(
                self.database
            )

            self.project = (
                self.projects.create(
                    "atom-test",
                    "Atom Test",
                )
            )

        def tearDown(self) -> None:
            self.temp.cleanup()

        def ingest(
            self,
            content: str,
        ) -> dict[str, object]:
            return self.ingestion.ingest_text(
                project_id=str(
                    self.project["id"]
                ),
                title="Source",
                content=content,
                chunk_bytes=32,
            )

        def test_exact_utf8_coordinates(
            self,
        ) -> None:
            content = (
                "# Requirements\n"
                "The API must preserve café data.\n"
                "The client must not expose secrets.\n"
            )

            document = self.ingest(
                content
            )

            extraction = (
                self.atoms.extract_document(
                    str(document["id"])
                )
            )

            self.assertEqual(
                extraction["atom_count"],
                2,
            )

            raw = content.encode("utf-8")

            for atom in extraction["atoms"]:
                exact = raw[
                    int(atom["byte_start"]):
                    int(atom["byte_end"])
                ].decode("utf-8")

                self.assertEqual(
                    exact,
                    atom["exact_quote"],
                )

            modalities = [
                atom["modality"]
                for atom
                in extraction["atoms"]
            ]

            self.assertEqual(
                modalities,
                [
                    "MUST",
                    "PROHIBITED",
                ],
            )

            kinds = [
                atom["kind"]
                for atom
                in extraction["atoms"]
            ]

            self.assertEqual(
                kinds,
                [
                    "DATA",
                    "SECURITY",
                ],
            )

        def test_idempotent_extraction(
            self,
        ) -> None:
            document = self.ingest(
                "The service must start.\n"
            )

            first = (
                self.atoms.extract_document(
                    str(document["id"])
                )
            )

            second = (
                self.atoms.extract_document(
                    str(document["id"])
                )
            )

            self.assertEqual(
                first["id"],
                second["id"],
            )

            self.assertEqual(
                first["dimension_count"],
                len(DIMENSIONS),
            )

            self.assertEqual(
                first["research_task_count"],
                len(DIMENSIONS),
            )

            atom = self.atoms.get_atom(
                str(first["atoms"][0]["id"])
            )

            self.assertEqual(
                len(atom["dimensions"]),
                len(DIMENSIONS),
            )

            self.assertEqual(
                len(
                    atom["research_tasks"]
                ),
                len(DIMENSIONS),
            )

        def test_headings_and_code_ignored(
            self,
        ) -> None:
            content = (
                "# Heading only\n"
                "```python\n"
                "service.must_start()\n"
                "```\n"
                "- The worker should retry failures.\n"
            )

            document = self.ingest(
                content
            )

            extraction = (
                self.atoms.extract_document(
                    str(document["id"])
                )
            )

            self.assertEqual(
                extraction["atom_count"],
                1,
            )

            atom = extraction["atoms"][0]

            self.assertEqual(
                atom["canonical_statement"],
                (
                    "The worker should retry "
                    "failures."
                ),
            )

            self.assertEqual(
                atom["modality"],
                "SHOULD",
            )


    if __name__ == "__main__":
        unittest.main()
    ''',
)

replace_once(
    "src/specgraph_foundry/api.py",
    """from .ingestion import IngestionService
from .services import ProjectService
""",
    """from .atoms import AtomService
from .ingestion import IngestionService
from .services import ProjectService
""",
)

replace_once(
    "src/specgraph_foundry/api.py",
    """            self.ingestion = IngestionService(
                database
            )
""",
    """            self.ingestion = IngestionService(
                database
            )
            self.atoms = AtomService(
                database
            )
""",
)

replace_once(
    "src/specgraph_foundry/api.py",
    """                return 404, {
                    "error": "ROUTE_NOT_FOUND",
""",
    """                if (
                    len(parts) == 4
                    and parts[:2]
                    == ["v1", "documents"]
                    and parts[3] == "extract"
                    and method == "POST"
                ):
                    return 200, (
                        self.atoms
                        .extract_document(
                            parts[2]
                        )
                    )

                if (
                    len(parts) == 4
                    and parts[:2]
                    == ["v1", "documents"]
                    and parts[3] == "atoms"
                    and method == "GET"
                ):
                    return 200, {
                        "items": (
                            self.atoms.list_atoms(
                                parts[2]
                            )
                        )
                    }

                if (
                    len(parts) == 3
                    and parts[:2]
                    == ["v1", "atoms"]
                    and method == "GET"
                ):
                    return 200, (
                        self.atoms.get_atom(
                            parts[2]
                        )
                    )

                if (
                    len(parts) == 4
                    and parts[:2]
                    == ["v1", "projects"]
                    and parts[3]
                    == "research-tasks"
                    and method == "GET"
                ):
                    return 200, {
                        "items": (
                            self.atoms
                            .list_research_tasks(
                                parts[2]
                            )
                        )
                    }

                return 404, {
                    "error": "ROUTE_NOT_FOUND",
""",
)

replace_once(
    "src/specgraph_foundry/cli.py",
    """from .api import Api
from .config import Settings
""",
    """from .api import Api
from .atoms import AtomService
from .config import Settings
""",
)

replace_once(
    "src/specgraph_foundry/cli.py",
    """        verify = commands.add_parser(
            "verify-document"
        )
        verify.add_argument("document_id")

        server = commands.add_parser("serve")
""",
    """        verify = commands.add_parser(
            "verify-document"
        )
        verify.add_argument("document_id")

        extract = commands.add_parser(
            "extract-document"
        )
        extract.add_argument(
            "document_id"
        )

        list_atoms = commands.add_parser(
            "list-atoms"
        )
        list_atoms.add_argument(
            "document_id"
        )

        atom = commands.add_parser(
            "atom"
        )
        atom.add_argument(
            "atom_id"
        )

        research_tasks = commands.add_parser(
            "research-tasks"
        )
        research_tasks.add_argument(
            "project_id"
        )
        research_tasks.add_argument(
            "--status"
        )

        server = commands.add_parser("serve")
""",
)

replace_once(
    "src/specgraph_foundry/cli.py",
    """        ingestion = IngestionService(database)
        graphs = GraphService(database)
""",
    """        ingestion = IngestionService(database)
        atoms = AtomService(database)
        graphs = GraphService(database)
""",
)

replace_once(
    "src/specgraph_foundry/cli.py",
    """        suffix = uuid.uuid4().hex[:8]
""",
    """        if args.command == "extract-document":
            output(
                atoms.extract_document(
                    args.document_id
                )
            )
            return 0

        if args.command == "list-atoms":
            output(
                {
                    "items": (
                        atoms.list_atoms(
                            args.document_id
                        )
                    )
                }
            )
            return 0

        if args.command == "atom":
            output(
                atoms.get_atom(
                    args.atom_id
                )
            )
            return 0

        if args.command == "research-tasks":
            output(
                {
                    "items": (
                        atoms.list_research_tasks(
                            args.project_id,
                            args.status,
                        )
                    )
                }
            )
            return 0

        suffix = uuid.uuid4().hex[:8]
""",
)

replace_once(
    "src/specgraph_foundry/cli.py",
    """        graph = graphs.create(
            str(project["id"]),
""",
    """        extraction = atoms.extract_document(
            str(document["id"])
        )

        graph = graphs.create(
            str(project["id"]),
""",
)

replace_once(
    "src/specgraph_foundry/cli.py",
    """                "document_verification": (
                    ingestion.verify_document(
""",
    """                "extraction": extraction,
                "document_verification": (
                    ingestion.verify_document(
""",
)

readme = ROOT / "README.md"
readme_content = readme.read_text(
    encoding="utf-8"
)

readme_section = dedent(
    r'''

    ## Atomic requirement extraction

    The backend now converts ingested source text into
    source-grounded atomic statements with:

    - exact UTF-8 byte coordinates;
    - exact line coordinates;
    - exact source quotes;
    - canonical normalized statements;
    - requirement modality classification;
    - functional category classification;
    - source fingerprints;
    - 16 completeness dimensions per atom;
    - provider-neutral research task generation;
    - idempotent extraction runs.

    ```bash
    python -m specgraph_foundry extract-document DOCUMENT_ID
    python -m specgraph_foundry list-atoms DOCUMENT_ID
    python -m specgraph_foundry atom ATOM_ID
    python -m specgraph_foundry research-tasks PROJECT_ID
    ```
    '''
)

if "## Atomic requirement extraction" not in readme_content:
    readme.write_text(
        readme_content.rstrip()
        + "\n"
        + readme_section.lstrip(),
        encoding="utf-8",
    )
    print("UPDATED README.md")

print()
print("ATOMIC EXTRACTION BACKEND CREATED")
