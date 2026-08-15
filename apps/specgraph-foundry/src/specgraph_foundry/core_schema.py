"""The core database schema."""

from __future__ import annotations

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
    CHECK(progress_current <= progress_total)
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
