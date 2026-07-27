import json
import re
import sqlite3
import uuid
from datetime import date, datetime
from decimal import Decimal
from pathlib import Path
from types import TracebackType
from typing import Any


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
    source_upload_id TEXT
        REFERENCES source_uploads(id)
        ON DELETE SET NULL,
    content TEXT NOT NULL,
    created_at TEXT NOT NULL
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

CREATE TABLE IF NOT EXISTS idempotency_records (
    id TEXT PRIMARY KEY,
    owner_id TEXT NOT NULL,
    operation TEXT NOT NULL,
    idempotency_key_hash TEXT NOT NULL,
    canonical_request_hash TEXT NOT NULL,
    state TEXT NOT NULL,
    http_status INTEGER,
    response_body_json TEXT,
    resource_type TEXT,
    resource_id TEXT,
    created_at TEXT NOT NULL,
    updated_at TEXT NOT NULL,
    expires_at TEXT NOT NULL,
    CHECK(
        state IN (
            'IN_PROGRESS',
            'SUCCEEDED',
            'FAILED'
        )
    ),
    UNIQUE(
        owner_id,
        operation,
        idempotency_key_hash
    )
);

CREATE INDEX IF NOT EXISTS idx_idempotency_lookup
    ON idempotency_records(
        owner_id,
        operation,
        idempotency_key_hash
    );

CREATE INDEX IF NOT EXISTS idx_idempotency_expiry
    ON idempotency_records(
        state,
        expires_at
    );

CREATE TABLE IF NOT EXISTS source_uploads (
    id TEXT PRIMARY KEY,
    owner_id TEXT NOT NULL,
    project_id TEXT NOT NULL
        REFERENCES projects(id)
        ON DELETE CASCADE,
    bucket TEXT NOT NULL,
    object_path TEXT NOT NULL UNIQUE,
    original_filename TEXT NOT NULL,
    declared_media_type TEXT NOT NULL,
    expected_bytes INTEGER NOT NULL
        CHECK(expected_bytes > 0),
    expected_sha256 TEXT NOT NULL,
    status TEXT NOT NULL
        CHECK(
            status IN (
                'PENDING',
                'UPLOADED',
                'FINALIZING',
                'FINALIZED',
                'FAILED',
                'EXPIRED'
            )
        ),
    actual_bytes INTEGER
        CHECK(
            actual_bytes IS NULL
            OR actual_bytes >= 0
        ),
    actual_sha256 TEXT,
    document_id TEXT
        REFERENCES source_documents(id)
        ON DELETE SET NULL,
    failure_code TEXT,
    created_at TEXT NOT NULL,
    updated_at TEXT NOT NULL,
    expires_at TEXT NOT NULL,
    finalized_at TEXT,
    CHECK(length(expected_sha256) = 64),
    CHECK(
        actual_sha256 IS NULL
        OR length(actual_sha256) = 64
    )
);

CREATE INDEX IF NOT EXISTS idx_source_uploads_owner
    ON source_uploads(
        owner_id,
        created_at,
        id
    );

CREATE INDEX IF NOT EXISTS idx_source_uploads_project
    ON source_uploads(
        project_id,
        created_at,
        id
    );

CREATE TABLE IF NOT EXISTS document_derivations (
    id TEXT PRIMARY KEY,
    owner_id TEXT NOT NULL,
    project_id TEXT NOT NULL
        REFERENCES projects(id)
        ON DELETE CASCADE,
    source_upload_id TEXT NOT NULL UNIQUE
        REFERENCES source_uploads(id)
        ON DELETE CASCADE,
    source_document_id TEXT NOT NULL UNIQUE
        REFERENCES source_documents(id)
        ON DELETE CASCADE,
    adapter_name TEXT NOT NULL,
    adapter_version TEXT NOT NULL,
    original_media_type TEXT NOT NULL,
    detected_media_type TEXT NOT NULL,
    original_byte_count INTEGER NOT NULL
        CHECK(original_byte_count > 0),
    original_sha256 TEXT NOT NULL
        CHECK(length(original_sha256) = 64),
    derived_byte_count INTEGER NOT NULL
        CHECK(derived_byte_count > 0),
    derived_sha256 TEXT NOT NULL
        CHECK(length(derived_sha256) = 64),
    status TEXT NOT NULL
        CHECK(
            status IN (
                'SUCCEEDED',
                'FAILED'
            )
        ),
    metadata_json TEXT NOT NULL DEFAULT '{}',
    created_at TEXT NOT NULL
);

CREATE INDEX IF NOT EXISTS idx_document_derivations_owner
    ON document_derivations(
        owner_id,
        project_id,
        created_at,
        id
    );

CREATE INDEX IF NOT EXISTS idx_document_derivations_document
    ON document_derivations(
        source_document_id,
        created_at,
        id
    );

CREATE TABLE IF NOT EXISTS storage_objects (
    id TEXT PRIMARY KEY,
    owner_id TEXT NOT NULL,
    project_id TEXT NOT NULL
        REFERENCES projects(id)
        ON DELETE CASCADE,
    bucket TEXT NOT NULL,
    object_path TEXT NOT NULL,
    media_type TEXT NOT NULL,
    byte_length INTEGER NOT NULL
        CHECK(byte_length > 0),
    sha256 TEXT NOT NULL
        CHECK(length(sha256) = 64),
    state TEXT NOT NULL
        CHECK(
            state IN (
                'PENDING',
                'STORED',
                'VERIFIED',
                'INVALID'
            )
        ),
    created_at TEXT NOT NULL,
    verified_at TEXT,
    UNIQUE(bucket, object_path)
);

CREATE INDEX IF NOT EXISTS idx_storage_objects_owner
    ON storage_objects(
        owner_id,
        project_id,
        created_at,
        id
    );

CREATE TABLE IF NOT EXISTS artifact_manifests (
    id TEXT PRIMARY KEY,
    owner_id TEXT NOT NULL,
    project_id TEXT NOT NULL
        REFERENCES projects(id)
        ON DELETE CASCADE,
    export_id TEXT NOT NULL UNIQUE
        REFERENCES exports(id)
        ON DELETE CASCADE,
    manifest_version TEXT NOT NULL,
    state TEXT NOT NULL
        CHECK(
            state IN (
                'GENERATED',
                'STORED',
                'VERIFIED',
                'INVALID'
            )
        ),
    aggregate_sha256 TEXT NOT NULL
        CHECK(length(aggregate_sha256) = 64),
    total_bytes INTEGER NOT NULL
        CHECK(total_bytes >= 0),
    artifact_count INTEGER NOT NULL
        CHECK(artifact_count > 0),
    manifest_json TEXT NOT NULL,
    created_at TEXT NOT NULL,
    verified_at TEXT
);

CREATE INDEX IF NOT EXISTS idx_artifact_manifests_owner
    ON artifact_manifests(
        owner_id,
        project_id,
        created_at,
        id
    );

CREATE TABLE IF NOT EXISTS operations (
    id TEXT PRIMARY KEY,
    owner_id TEXT NOT NULL,
    project_id TEXT NOT NULL
        REFERENCES projects(id)
        ON DELETE CASCADE,
    operation_type TEXT NOT NULL,
    fingerprint TEXT NOT NULL,
    state TEXT NOT NULL
        CHECK(
            state IN (
                'QUEUED',
                'CLAIMED',
                'RUNNING',
                'SUCCEEDED',
                'FAILED',
                'CANCEL_REQUESTED',
                'CANCELLED',
                'TIMED_OUT'
            )
        ),
    phase TEXT NOT NULL,
    progress_current INTEGER NOT NULL DEFAULT 0
        CHECK(progress_current >= 0),
    progress_total INTEGER NOT NULL DEFAULT 1
        CHECK(progress_total >= 1),
    attempt_count INTEGER NOT NULL DEFAULT 0
        CHECK(attempt_count >= 0),
    max_attempts INTEGER NOT NULL
        CHECK(max_attempts BETWEEN 1 AND 10),
    worker_id TEXT,
    lease_token_hash TEXT,
    lease_expires_at TEXT,
    heartbeat_at TEXT,
    next_attempt_at TEXT NOT NULL,
    cancel_requested_at TEXT,
    started_at TEXT,
    finished_at TEXT,
    timeout_at TEXT NOT NULL,
    request_json TEXT NOT NULL,
    result_json TEXT,
    error_code TEXT,
    error_message TEXT,
    created_at TEXT NOT NULL,
    updated_at TEXT NOT NULL,
    CHECK(progress_current <= progress_total),
    UNIQUE(owner_id, operation_type, fingerprint)
);

CREATE INDEX IF NOT EXISTS idx_operations_owner
    ON operations(
        owner_id,
        project_id,
        created_at,
        id
    );

CREATE INDEX IF NOT EXISTS idx_operations_claim
    ON operations(
        state,
        next_attempt_at,
        created_at,
        id
    );

CREATE TABLE IF NOT EXISTS compiler_runs (
    id TEXT PRIMARY KEY,
    project_id TEXT NOT NULL REFERENCES projects(id) ON DELETE CASCADE,
    input_fingerprint TEXT NOT NULL,
    output_fingerprint TEXT NOT NULL,
    status TEXT NOT NULL,
    event_log_json TEXT NOT NULL,
    created_at TEXT NOT NULL
);

CREATE TABLE IF NOT EXISTS document_ir_nodes (
    id TEXT PRIMARY KEY,
    document_id TEXT NOT NULL REFERENCES source_documents(id) ON DELETE CASCADE,
    node_id TEXT NOT NULL,
    role TEXT NOT NULL,
    content TEXT NOT NULL,
    byte_start INTEGER NOT NULL,
    byte_end INTEGER NOT NULL,
    line_start INTEGER NOT NULL,
    line_end INTEGER NOT NULL,
    parent_id TEXT,
    metadata_json TEXT NOT NULL DEFAULT '{}',
    created_at TEXT NOT NULL
);

CREATE TABLE IF NOT EXISTS statement_ir (
    id TEXT PRIMARY KEY,
    document_id TEXT NOT NULL REFERENCES source_documents(id) ON DELETE CASCADE,
    statement_id TEXT NOT NULL,
    exact_quote TEXT NOT NULL,
    canonical_text TEXT NOT NULL,
    byte_start INTEGER NOT NULL,
    byte_end INTEGER NOT NULL,
    line_start INTEGER NOT NULL,
    line_end INTEGER NOT NULL,
    parent_node_id TEXT NOT NULL,
    governing_heading_id TEXT,
    governing_list_item_id TEXT,
    structural_ancestry_json TEXT NOT NULL DEFAULT '[]',
    neighboring_context_json TEXT NOT NULL DEFAULT '[]',
    completeness_state TEXT NOT NULL,
    created_at TEXT NOT NULL
);

CREATE TABLE IF NOT EXISTS discourse_roles (
    id TEXT PRIMARY KEY,
    statement_id TEXT NOT NULL,
    role TEXT NOT NULL,
    confidence REAL NOT NULL DEFAULT 1.0,
    source TEXT NOT NULL,
    created_at TEXT NOT NULL
);

CREATE TABLE IF NOT EXISTS requirement_candidacies (
    id TEXT PRIMARY KEY,
    statement_id TEXT NOT NULL,
    is_candidate INTEGER NOT NULL,
    actor TEXT NOT NULL,
    trigger_text TEXT,
    ears_pattern TEXT,
    created_at TEXT NOT NULL
);

CREATE TABLE IF NOT EXISTS atomic_decompositions (
    id TEXT PRIMARY KEY,
    parent_statement_id TEXT NOT NULL,
    child_requirement_id TEXT NOT NULL,
    relation_type TEXT NOT NULL,
    created_at TEXT NOT NULL
);

CREATE TABLE IF NOT EXISTS orthogonal_types (
    id TEXT PRIMARY KEY,
    requirement_id TEXT NOT NULL,
    modality TEXT NOT NULL,
    domain_kind TEXT NOT NULL,
    artifact_target TEXT NOT NULL,
    verification_method TEXT NOT NULL,
    created_at TEXT NOT NULL
);

CREATE TABLE IF NOT EXISTS requirement_quality_findings (
    id TEXT PRIMARY KEY,
    requirement_id TEXT NOT NULL,
    severity TEXT NOT NULL,
    code TEXT NOT NULL,
    message TEXT NOT NULL,
    created_at TEXT NOT NULL
);

CREATE TABLE IF NOT EXISTS artifact_contracts (
    id TEXT PRIMARY KEY,
    requirement_id TEXT NOT NULL,
    port_type TEXT NOT NULL,
    artifact_name TEXT NOT NULL,
    schema_version TEXT NOT NULL,
    created_at TEXT NOT NULL
);

CREATE TABLE IF NOT EXISTS dependency_edges (
    id TEXT PRIMARY KEY,
    project_id TEXT NOT NULL REFERENCES projects(id) ON DELETE CASCADE,
    from_requirement_id TEXT NOT NULL,
    to_requirement_id TEXT NOT NULL,
    rule_name TEXT NOT NULL,
    evidence TEXT NOT NULL,
    created_at TEXT NOT NULL
);

CREATE TABLE IF NOT EXISTS provider_proposals (
    id TEXT PRIMARY KEY,
    project_id TEXT NOT NULL REFERENCES projects(id) ON DELETE CASCADE,
    provider_id TEXT NOT NULL,
    model_id TEXT NOT NULL,
    proposal_type TEXT NOT NULL,
    target_id TEXT NOT NULL,
    proposed_value TEXT NOT NULL,
    confidence REAL NOT NULL,
    rationale TEXT NOT NULL,
    prompt_hash TEXT NOT NULL,
    response_hash TEXT NOT NULL,
    created_at TEXT NOT NULL
);

CREATE TABLE IF NOT EXISTS provenance_records (
    id TEXT PRIMARY KEY,
    project_id TEXT NOT NULL REFERENCES projects(id) ON DELETE CASCADE,
    entity_id TEXT NOT NULL,
    entity_type TEXT NOT NULL,
    activity_id TEXT NOT NULL,
    activity_type TEXT NOT NULL,
    agent_id TEXT NOT NULL,
    agent_type TEXT NOT NULL,
    relation_type TEXT NOT NULL,
    created_at TEXT NOT NULL
);

CREATE TABLE IF NOT EXISTS source_authorities (
    id TEXT PRIMARY KEY,
    document_id TEXT NOT NULL REFERENCES source_documents(id) ON DELETE CASCADE,
    tier INTEGER NOT NULL,
    version TEXT NOT NULL,
    effective_date TEXT NOT NULL,
    owner TEXT NOT NULL,
    is_approved INTEGER NOT NULL DEFAULT 1,
    created_at TEXT NOT NULL
);
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


JSON_COLUMNS = {
    "payload_json",
    "result_json",
    "config_json",
    "evidence_json",
    "response_body_json",
    "route_law_json",
    "territories_json",
    "metadata_json",
    "input_json",
    "considered_json",
}

UUID_PATTERN = re.compile(
    r"^[0-9a-fA-F]{8}-"
    r"[0-9a-fA-F]{4}-"
    r"[1-5][0-9a-fA-F]{3}-"
    r"[89abAB][0-9a-fA-F]{3}-"
    r"[0-9a-fA-F]{12}$"
)

ISO_DATETIME_PATTERN = re.compile(
    r"^\d{4}-\d{2}-\d{2}T"
    r"\d{2}:\d{2}:\d{2}"
    r"(?:\.\d+)?"
    r"(?:Z|[+-]\d{2}:\d{2})$"
)


class PostgresRow(dict[str, object]):
    def __getitem__(
        self,
        key: str | int,
    ) -> object:
        if isinstance(key, int):
            return tuple(self.values())[key]

        return super().__getitem__(key)


def normalize_postgres_value(
    value: object,
) -> object:
    if isinstance(value, uuid.UUID):
        return str(value)

    if isinstance(value, datetime):
        return value.isoformat()

    if isinstance(value, date):
        return value.isoformat()

    if isinstance(value, Decimal):
        if value == value.to_integral_value():
            return int(value)

        return float(value)

    if isinstance(value, (dict, list)):
        return json.dumps(
            value,
            sort_keys=True,
            separators=(",", ":"),
            ensure_ascii=False,
        )

    if isinstance(value, memoryview):
        return bytes(value)

    return value


def normalize_postgres_row(
    row: object,
) -> PostgresRow | None:
    if row is None:
        return None

    if not isinstance(row, dict):
        raise TypeError(
            "PostgreSQL row must be a mapping"
        )

    return PostgresRow(
        {
            str(key): normalize_postgres_value(
                value
            )
            for key, value in row.items()
        }
    )


def split_sql_list(
    value: str,
) -> list[str]:
    items: list[str] = []
    current: list[str] = []
    depth = 0
    single_quote = False
    double_quote = False
    index = 0

    while index < len(value):
        character = value[index]

        if character == "'" and not double_quote:
            if (
                single_quote
                and index + 1 < len(value)
                and value[index + 1] == "'"
            ):
                current.extend(["'", "'"])
                index += 2
                continue

            single_quote = not single_quote
            current.append(character)
            index += 1
            continue

        if character == '"' and not single_quote:
            double_quote = not double_quote
            current.append(character)
            index += 1
            continue

        if not single_quote and not double_quote:
            if character == "(":
                depth += 1
            elif character == ")":
                depth -= 1
            elif character == "," and depth == 0:
                items.append(
                    "".join(current).strip()
                )
                current = []
                index += 1
                continue

        current.append(character)
        index += 1

    if current:
        items.append(
            "".join(current).strip()
        )

    return items


def parameter_count(
    sql: str,
) -> int:
    single_quote = False
    double_quote = False
    count = 0
    index = 0

    while index < len(sql):
        character = sql[index]

        if character == "'" and not double_quote:
            if (
                single_quote
                and index + 1 < len(sql)
                and sql[index + 1] == "'"
            ):
                index += 2
                continue

            single_quote = not single_quote
            index += 1
            continue

        if character == '"' and not single_quote:
            double_quote = not double_quote
            index += 1
            continue

        if (
            character == "?"
            and not single_quote
            and not double_quote
        ):
            count += 1

        index += 1

    return count


def postgres_json_parameter_indexes(
    sql: str,
) -> set[int]:
    indexes: set[int] = set()

    insert = re.search(
        r"""
        INSERT\s+INTO\s+
        (?:[a-zA-Z_][a-zA-Z0-9_]*\.)?
        [a-zA-Z_][a-zA-Z0-9_]*
        \s*\((.*?)\)
        \s*VALUES\s*\((.*?)\)
        """,
        sql,
        flags=(
            re.IGNORECASE
            | re.DOTALL
            | re.VERBOSE
        ),
    )

    if insert is not None:
        columns = split_sql_list(
            insert.group(1)
        )
        values = split_sql_list(
            insert.group(2)
        )
        parameter_index = 0

        for column, expression in zip(
            columns,
            values,
            strict=False,
        ):
            column_name = (
                column.strip()
                .split(".")[-1]
                .strip('"')
                .lower()
            )

            expression_count = (
                parameter_count(expression)
            )

            if (
                column_name in JSON_COLUMNS
                and expression.strip() == "?"
            ):
                indexes.add(parameter_index)

            parameter_index += (
                expression_count
            )

    update = re.search(
        r"""
        UPDATE\s+
        (?:[a-zA-Z_][a-zA-Z0-9_]*\.)?
        [a-zA-Z_][a-zA-Z0-9_]*
        \s+SET\s+
        (.*?)
        (?=\s+WHERE\s+|\Z)
        """,
        sql,
        flags=(
            re.IGNORECASE
            | re.DOTALL
            | re.VERBOSE
        ),
    )

    if update is not None:
        prefix = sql[: update.start(1)]
        parameter_index = (
            parameter_count(prefix)
        )

        for assignment in split_sql_list(
            update.group(1)
        ):
            left, separator, right = (
                assignment.partition("=")
            )

            expression_count = (
                parameter_count(right)
            )

            if separator:
                column_name = (
                    left.strip()
                    .split(".")[-1]
                    .strip('"')
                    .lower()
                )

                if (
                    column_name
                    in JSON_COLUMNS
                    and right.strip() == "?"
                ):
                    indexes.add(
                        parameter_index
                    )

            parameter_index += (
                expression_count
            )

    return indexes


def adapt_postgres_scalar(
    value: object,
) -> object:
    if not isinstance(value, str):
        return value

    if UUID_PATTERN.fullmatch(value):
        return uuid.UUID(value)

    if ISO_DATETIME_PATTERN.fullmatch(
        value
    ):
        return datetime.fromisoformat(
            value.replace(
                "Z",
                "+00:00",
            )
        )

    return value


def adapt_postgres_parameters(
    sql: str,
    parameters: tuple[object, ...],
    json_wrapper: Any,
) -> tuple[object, ...]:
    json_indexes = (
        postgres_json_parameter_indexes(
            sql
        )
    )

    adapted: list[object] = []

    for index, value in enumerate(
        parameters
    ):
        if index in json_indexes:
            if isinstance(value, str):
                try:
                    parsed = json.loads(value)
                except json.JSONDecodeError as error:
                    raise ValueError(
                        "JSON database parameter "
                        "is not valid JSON"
                    ) from error
            elif isinstance(
                value,
                (dict, list),
            ):
                parsed = value
            else:
                parsed = value

            adapted.append(
                json_wrapper(parsed)
            )
            continue

        adapted.append(
            adapt_postgres_scalar(value)
        )

    return tuple(adapted)


class PostgresCursor:
    def __init__(
        self,
        cursor: Any,
    ) -> None:
        self._cursor = cursor

    @property
    def rowcount(self) -> int:
        return int(self._cursor.rowcount)

    def fetchone(
        self,
    ) -> PostgresRow | None:
        return normalize_postgres_row(
            self._cursor.fetchone()
        )

    def fetchall(
        self,
    ) -> list[PostgresRow]:
        return [
            row
            for raw_row
            in self._cursor.fetchall()
            if (
                row := normalize_postgres_row(
                    raw_row
                )
            )
            is not None
        ]

    def __iter__(self):
        for raw_row in self._cursor:
            row = normalize_postgres_row(
                raw_row
            )

            if row is not None:
                yield row

    def __getattr__(
        self,
        name: str,
    ) -> Any:
        return getattr(
            self._cursor,
            name,
        )


class PostgresConnection:
    def __init__(
        self,
        database_url: str,
    ) -> None:
        try:
            import psycopg
            from psycopg.rows import dict_row
            from psycopg.types.json import Jsonb
        except ImportError as error:
            raise RuntimeError(
                "PostgreSQL mode requires Psycopg 3. "
                "Install the optional postgres dependency."
            ) from error

        self._psycopg = psycopg
        self._json_wrapper = Jsonb
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
    ) -> PostgresCursor:
        normalized = sql.strip()

        if (
            normalized.upper()
            == "BEGIN IMMEDIATE"
        ):
            translated = "BEGIN"
        else:
            translated = translate_qmark_sql(
                sql
            )

        adapted = adapt_postgres_parameters(
            sql,
            tuple(parameters),
            self._json_wrapper,
        )

        try:
            cursor = self._connection.execute(
                translated,
                adapted,
            )
        except self._psycopg.IntegrityError as error:
            raise sqlite3.IntegrityError(
                str(error)
            ) from error

        return PostgresCursor(cursor)

    def executescript(
        self,
        sql: str,
    ) -> None:
        # Hosted schema is managed exclusively by
        # Supabase migrations. SQLite bootstrap DDL
        # must never mutate the hosted schema.
        return None

    def close(self) -> None:
        self._connection.close()


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
