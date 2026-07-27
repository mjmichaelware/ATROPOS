import base64
import hashlib
import json
import re
import sqlite3
import uuid
from datetime import UTC, datetime

from .database import Database
from .rendering import (
    markdown_to_plain_text,
    render_markdown_pdf,
)
from .errors import (
    ConflictError,
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
    return str(uuid.uuid4())


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

            # Every run for this (document, extractor, source) triple is
            # considered, not only completed ones. A run left RUNNING by
            # another worker still occupies the uniqueness slot, so
            # filtering to COMPLETE here meant the insert below was the
            # first thing to notice the clash - and it noticed by raising
            # a raw sqlite3 error at the caller.
            existing = connection.execute(
                """
                SELECT id, status
                FROM extraction_runs
                WHERE document_id = ?
                  AND extractor_version = ?
                  AND source_sha256 = ?
                """,
                (
                    document_id,
                    EXTRACTOR_VERSION,
                    document["sha256"],
                ),
            ).fetchone()

            if existing is not None:
                if str(existing["status"]) == "COMPLETE":
                    return self.get_extraction(
                        str(existing["id"])
                    )

                raise ConflictError(
                    "extraction already in progress for document "
                    f"{document_id}: run {existing['id']} is "
                    f"{existing['status']}"
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

        from .compiler import SpecGraphCompiler
        import uuid

        def map_orthogonal_kind(domains: list[str]) -> str:
            if not domains:
                return "UNRESOLVED"
            primary = domains[0]
            if primary == "UI_UX":
                return "UX"
            if primary in {"SECURITY", "PERFORMANCE", "DATA", "API", "UX", "TEST", "OPERATIONS", "INTEGRATION", "FUNCTIONAL"}:
                return primary
            return "UNRESOLVED"

        def map_orthogonal_modality(force: str) -> str:
            if force == "MUST_NOT":
                return "PROHIBITED"
            if force in {"MUST", "SHALL", "SHOULD", "MAY", "PROHIBITED", "DECLARATIVE"}:
                return force
            return "UNRESOLVED"

        def determine_applicable_dimensions(kind: str) -> set[str]:
            applicable = {"FUNCTIONAL_CONTRACT"}
            if kind == "DATA":
                applicable.add("DATA_LIFECYCLE")
                applicable.add("MIGRATION_COMPATIBILITY")
            elif kind == "SECURITY":
                applicable.add("SECURITY_SECRETS")
            elif kind == "PERFORMANCE":
                applicable.add("PERFORMANCE_RESOURCES")
            elif kind == "API":
                applicable.add("INTEGRATION_CALL_SITES")
            elif kind == "UX":
                applicable.add("ACCESSIBILITY_UX")
            elif kind == "OPERATIONS":
                applicable.add("RESTART_RECOVERY")
                applicable.add("ROLLBACK_FAILURE_EVIDENCE")
            elif kind == "TEST":
                applicable.add("TESTS_ACCEPTANCE")
            elif kind == "INTEGRATION":
                applicable.add("INTEGRATION_CALL_SITES")
            return applicable

        compiler = SpecGraphCompiler(project_id=str(document["project_id"]))
        result = compiler.compile(filename="source.md", content=raw)
        requirements = result["requirements"]

        run_id = new_id("extraction")
        created_at = utc_now()

        with self.database.connect() as connection:
            try:
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
                        int(document["byte_count"]),
                        int(document["line_count"]),
                        len(requirements),
                        created_at,
                    ),
                )
            except sqlite3.IntegrityError as conflict:
                # The check above runs in an earlier transaction, so two
                # workers can both pass it and race to this insert. The
                # uniqueness constraint is what actually decides the
                # winner; the loser reports a conflict rather than
                # surfacing a driver error to the caller.
                raise ConflictError(
                    "extraction already in progress for document "
                    f"{document_id}"
                ) from conflict

            # Persist Compiler Run
            connection.execute(
                """
                INSERT INTO compiler_runs(id, project_id, input_fingerprint, output_fingerprint, status, event_log_json, created_at)
                VALUES(?,?,?,?,?,?,?)
                """,
                (
                    run_id,
                    document["project_id"],
                    document["sha256"],
                    result["fingerprint"],
                    "COMPLETE",
                    json.dumps(result["event_log"]),
                    created_at
                )
            )

            # Persist Dependencies
            for dep in result["dependencies"]:
                connection.execute(
                    """
                    INSERT INTO dependency_edges(id, project_id, from_requirement_id, to_requirement_id, rule_name, evidence, created_at)
                    VALUES(?,?,?,?,?,?,?)
                    """,
                    (
                        str(uuid.uuid4()),
                        document["project_id"],
                        dep["from_node_id"],
                        dep["to_node_id"],
                        dep["rule"],
                        dep["evidence"],
                        created_at
                    )
                )

            atom_count = 0
            dimension_count = 0
            task_count = 0

            for idx, req in enumerate(requirements):
                atom_id = str(uuid.uuid5(uuid.NAMESPACE_DNS, f"{document['project_id']}:{req['stable_id']}"))
                kind = map_orthogonal_kind(req["domains"])
                modality = map_orthogonal_modality(req["force"])

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
                        None,  # Section is resolved structurally in DocumentIR
                        run_id,
                        idx,
                        kind,
                        modality,
                        "DISCOVERED",
                        req["canonical_statement"],
                        req["original_statement"],
                        req["coordinates"]["byte_start"],
                        req["coordinates"]["byte_end"],
                        req["coordinates"]["line_start"],
                        req["coordinates"]["line_end"],
                        document["sha256"],
                        1.0,
                        created_at,
                    ),
                )

                atom_count += 1

                # Persist Orthogonal Types
                connection.execute(
                    """
                    INSERT INTO orthogonal_types(id, requirement_id, modality, domain_kind, artifact_target, verification_method, created_at)
                    VALUES(?,?,?,?,?,?,?)
                    """,
                    (
                        str(uuid.uuid4()),
                        atom_id,
                        modality,
                        kind,
                        req.get("artifact_target", "UNSPECIFIED"),
                        req.get("verification_method", "UNSPECIFIED"),
                        created_at
                    )
                )

                # Persist Quality Findings
                for finding in req["quality_findings"]:
                    connection.execute(
                        """
                        INSERT INTO requirement_quality_findings(id, requirement_id, severity, code, message, created_at)
                        VALUES(?,?,?,?,?,?)
                        """,
                        (
                            str(uuid.uuid4()),
                            atom_id,
                            finding["severity"],
                            finding["code"],
                            finding["message"],
                            created_at
                        )
                    )

                # Ensure research tables exist
                try:
                    from .research import RESEARCH_SCHEMA
                    connection.executescript(RESEARCH_SCHEMA)
                except Exception:
                    pass

                applicable_dimensions = determine_applicable_dimensions(kind)

                for dimension in DIMENSIONS:
                    timestamp = utc_now()
                    is_app = dimension in applicable_dimensions
                    app_status = "OPEN" if is_app else "NOT_APPLICABLE"
                    app_val = "UNKNOWN" if is_app else "NOT_APPLICABLE"

                    connection.execute(
                        """
                        INSERT INTO atom_dimensions(
                            id,
                            atom_id,
                            dimension,
                            applicability,
                            status,
                            rationale,
                            created_at,
                            updated_at
                        )
                        VALUES(?,?,?,?,?,?,?,?)
                        """,
                        (
                            new_id("dimension"),
                            atom_id,
                            dimension,
                            app_val,
                            app_status,
                            "Determined by compiler orthogonal typing" if is_app else "Not applicable to this requirement type",
                            timestamp,
                            timestamp,
                        ),
                    )

                    dimension_count += 1

                    if is_app:
                        task_id = new_id("research-task")
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
                                task_id,
                                document["project_id"],
                                atom_id,
                                dimension,
                                research_question(
                                    str(req["canonical_statement"]),
                                    dimension,
                                ),
                                "PENDING",
                                100,
                                timestamp,
                                timestamp,
                            ),
                        )
                        task_count += 1
                    else:
                        # Auto-insert compiler decision claim and evidence to justify NOT_APPLICABLE status
                        claim_id = new_id("claim")
                        evidence_id = new_id("evidence")
                        dummy_task_id = new_id("compiler-task")

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
                                dummy_task_id,
                                document["project_id"],
                                atom_id,
                                dimension,
                                f"Is {dimension} applicable?",
                                "RESOLVED",
                                100,
                                timestamp,
                                timestamp,
                            )
                        )

                        connection.execute(
                            """
                            INSERT INTO research_evidence(
                                id,
                                project_id,
                                task_id,
                                atom_id,
                                dimension,
                                source_uri,
                                source_title,
                                publisher,
                                evidence_type,
                                excerpt,
                                content_sha256,
                                reliability,
                                retrieved_at,
                                created_at
                            )
                            VALUES(?,?,?,?,?,?,?,?,?,?,?,?,?,?)
                            """,
                            (
                                evidence_id,
                                document["project_id"],
                                dummy_task_id,
                                atom_id,
                                dimension,
                                "compiler://orthogonal_typing",
                                "SpecGraph Compiler",
                                "SpecGraph Compiler",
                                "OTHER",
                                f"Dimension is not applicable to requirement kind {kind}.",
                                hashlib.sha256(b"na").hexdigest(),
                                1.0,
                                timestamp,
                                timestamp,
                            )
                        )

                        connection.execute(
                            """
                            INSERT INTO research_claims(
                                id,
                                project_id,
                                task_id,
                                atom_id,
                                dimension,
                                conclusion,
                                applicability,
                                confidence,
                                status,
                                created_at,
                                updated_at
                            )
                            VALUES(?,?,?,?,?,?,?,?,?,?,?)
                            """,
                            (
                                claim_id,
                                document["project_id"],
                                dummy_task_id,
                                atom_id,
                                dimension,
                                f"Compiler determined this dimension is not applicable to {kind} requirements.",
                                "NOT_APPLICABLE",
                                1.0,
                                "ACCEPTED",
                                timestamp,
                                timestamp,
                            )
                        )

                        connection.execute(
                            """
                            INSERT INTO research_claim_evidence(claim_id, evidence_id)
                            VALUES(?,?)
                            """,
                            (claim_id, evidence_id)
                        )
                        task_count += 1

            # Persist Semantic Relations in authority_relations
            ALLOWED_RELATION_TYPES = {"REFINES", "CLARIFIES", "SUPERSEDES", "CONFLICTS_WITH", "DUPLICATES"}
            for rel in result["relations"]:
                if rel["relation_type"] not in ALLOWED_RELATION_TYPES:
                    continue
                from_atom_id = str(uuid.uuid5(uuid.NAMESPACE_DNS, f"{document['project_id']}:{rel['from_atom_id']}"))
                to_atom_id = str(uuid.uuid5(uuid.NAMESPACE_DNS, f"{document['project_id']}:{rel['to_atom_id']}"))

                exists = connection.execute(
                    """
                    SELECT 1 FROM authority_relations
                    WHERE project_id = ? AND from_atom_id = ? AND to_atom_id = ? AND relation_type = ?
                    """,
                    (document["project_id"], from_atom_id, to_atom_id, rel["relation_type"])
                ).fetchone()

                if not exists:
                    connection.execute(
                        """
                        INSERT INTO authority_relations(id, project_id, from_atom_id, to_atom_id, relation_type, rationale, confidence, inferred, created_at)
                        VALUES(?,?,?,?,?,?,?,?,?)
                        """,
                        (
                            new_id("relation"),
                            document["project_id"],
                            from_atom_id,
                            to_atom_id,
                            rel["relation_type"],
                            rel["rationale"],
                            float(rel.get("confidence", 1.0)),
                            bool(rel.get("inferred", True)),
                            created_at
                        )
                    )

            # Persist Dependencies as REQUIRES relations in authority_relations
            for dep in result["dependencies"]:
                from_atom_id = str(uuid.uuid5(uuid.NAMESPACE_DNS, f"{document['project_id']}:{dep['from_node_id']}"))
                to_atom_id = str(uuid.uuid5(uuid.NAMESPACE_DNS, f"{document['project_id']}:{dep['to_node_id']}"))

                exists = connection.execute(
                    """
                    SELECT 1 FROM authority_relations
                    WHERE project_id = ? AND from_atom_id = ? AND to_atom_id = ? AND relation_type = 'REQUIRES'
                    """,
                    (document["project_id"], from_atom_id, to_atom_id)
                ).fetchone()

                if not exists:
                    connection.execute(
                        """
                        INSERT INTO authority_relations(id, project_id, from_atom_id, to_atom_id, relation_type, rationale, confidence, inferred, created_at)
                        VALUES(?,?,?,?,?,?,?,?,?)
                        """,
                        (
                            new_id("relation"),
                            document["project_id"],
                            from_atom_id,
                            to_atom_id,
                            "REQUIRES",
                            dep["evidence"],
                            1.0,
                            True,
                            created_at
                        )
                    )

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

    def list_atoms_page(
        self,
        document_id: str,
        limit: int,
        boundary: dict[str, object] | None = None,
    ) -> tuple[
        list[dict[str, object]],
        bool,
        dict[str, object] | None,
    ]:
        parameters: list[object] = [document_id]
        predicate = ""

        if boundary is not None:
            predicate = """
                AND (
                    ordinal > ?
                    OR (
                        ordinal = ?
                        AND id > ?
                    )
                )
            """
            ordinal = int(boundary.get("ordinal", 0))
            parameters.extend(
                [
                    ordinal,
                    ordinal,
                    str(boundary.get("id", "")),
                ]
            )

        parameters.append(limit + 1)

        with self.database.connect() as connection:
            rows = connection.execute(
                f"""
                SELECT *
                FROM atoms
                WHERE document_id = ?
                {predicate}
                ORDER BY ordinal, id
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
                "ordinal": int(
                    items[-1]["ordinal"]
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

    def list_research_tasks_page(
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
                    priority > ?
                    OR (
                        priority = ?
                        AND (
                            created_at > ?
                            OR (
                                created_at = ?
                                AND id > ?
                            )
                        )
                    )
                )
            """
            priority = int(
                boundary.get("priority", 0)
            )
            created_at = str(
                boundary.get("created_at", "")
            )
            parameters.extend(
                [
                    priority,
                    priority,
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
                FROM research_tasks
                WHERE project_id = ?
                {predicate}
                ORDER BY
                    priority,
                    created_at,
                    id
                LIMIT ?
                """,
                tuple(parameters),
            ).fetchall()

        items = [
            self._normalize_task(
                dict(row)
            )
            for row in rows[:limit]
        ]
        has_more = len(rows) > limit
        boundary_item = (
            {
                "priority": int(
                    items[-1]["priority"]
                ),
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

    def export_atoms_bundle(
        self,
        document_id: str,
    ) -> dict[str, object]:
        """Render a document's extracted atoms as a downloadable bundle.

        Returns the same content in two encodings so a caller can hand a
        reviewer either one without re-rendering: ``text`` for diffing and
        grepping, ``pdf`` for circulation. Both are base64 so the bundle
        survives a JSON transport unchanged.

        A document that genuinely produced zero atoms is reported as such
        in the rendered body. An empty file is indistinguishable from a
        broken extraction, and a reviewer who cannot tell the difference
        will assume the wrong one.
        """
        with self.database.connect() as connection:
            document = connection.execute(
                """
                SELECT
                    id,
                    title,
                    sha256
                FROM source_documents
                WHERE id = ?
                """,
                (document_id,),
            ).fetchone()

            if document is None:
                raise NotFoundError(
                    f"document not found: {document_id}"
                )

            rows = connection.execute(
                """
                SELECT
                    ordinal,
                    kind,
                    modality,
                    status,
                    canonical_statement
                FROM atoms
                WHERE document_id = ?
                ORDER BY ordinal
                """,
                (document_id,),
            ).fetchall()

        atoms = [
            dict(row)
            for row in rows
        ]
        markdown = self._render_atoms_markdown(
            dict(document),
            atoms,
        )

        return {
            "document_id": document_id,
            "atom_count": len(atoms),
            "text": self._encoded_file(
                markdown_to_plain_text(markdown).encode("utf-8"),
                "text/plain",
            ),
            "pdf": self._encoded_file(
                render_markdown_pdf(markdown),
                "application/pdf",
            ),
        }

    @staticmethod
    def _render_atoms_markdown(
        document: dict[str, object],
        atoms: list[dict[str, object]],
    ) -> str:
        title = str(
            document.get("title")
            or document.get("id")
        )
        lines = [
            f"# Extracted atoms: {title}",
            "",
            f"Document: {document.get('id')}",
            f"Source sha256: {document.get('sha256')}",
            f"Atoms: {len(atoms)}",
            "",
        ]

        if not atoms:
            lines.append(
                "No candidate statements were found in this document. "
                "The extraction completed successfully and produced zero "
                "atoms, which is a result rather than a failure."
            )
            return "\n".join(lines) + "\n"

        for atom in atoms:
            # The ordinal is written without a number sign: the plain-text
            # rendering strips markdown headings but leaves inline text
            # alone, and a stray marker would read as a heading.
            lines.append(
                f"{atom['ordinal']}. {atom['canonical_statement']}"
            )
            lines.append(
                f"   kind={atom['kind']} "
                f"modality={atom['modality']} "
                f"status={atom['status']}"
            )
            lines.append("")

        return "\n".join(lines) + "\n"

    @staticmethod
    def _encoded_file(
        payload: bytes,
        media_type: str,
    ) -> dict[str, object]:
        return {
            "base64": base64.b64encode(payload).decode("ascii"),
            "byte_length": len(payload),
            "media_type": media_type,
        }

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
