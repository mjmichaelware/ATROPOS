"""The execution database schema.

A 167-line DDL string and the service that runs against it change for different
reasons -- the schema when the shape of a run changes, the service when its
behaviour does. Kept together, every reader of `ExecutionService` scrolled the
whole schema to reach the first method.
"""

from __future__ import annotations

EXECUTION_SCHEMA = """
PRAGMA foreign_keys = ON;

CREATE TABLE IF NOT EXISTS execution_runs (
    id TEXT PRIMARY KEY,
    project_id TEXT NOT NULL
        REFERENCES projects(id)
        ON DELETE CASCADE,
    plan_version_id TEXT NOT NULL
        REFERENCES plan_versions(id)
        ON DELETE CASCADE,
    export_id TEXT
        REFERENCES exports(id)
        ON DELETE SET NULL,
    runtime_system TEXT NOT NULL,
    runtime_run_id TEXT NOT NULL,
    status TEXT NOT NULL,
    input_fingerprint TEXT NOT NULL,
    created_at TEXT NOT NULL,
    started_at TEXT NOT NULL,
    completed_at TEXT,
    verified_at TEXT,
    UNIQUE(
        runtime_system,
        runtime_run_id
    )
);

CREATE TABLE IF NOT EXISTS execution_run_nodes (
    id TEXT PRIMARY KEY,
    run_id TEXT NOT NULL
        REFERENCES execution_runs(id)
        ON DELETE CASCADE,
    graph_node_id TEXT NOT NULL
        REFERENCES graph_nodes(id)
        ON DELETE CASCADE,
    atom_id TEXT NOT NULL
        REFERENCES atoms(id)
        ON DELETE CASCADE,
    stage TEXT NOT NULL,
    sequence_number INTEGER NOT NULL,
    title TEXT NOT NULL,
    status TEXT NOT NULL,
    lease_owner TEXT,
    lease_expires_at TEXT,
    attempt_count INTEGER NOT NULL DEFAULT 0,
    accepted_receipt_id TEXT,
    created_at TEXT NOT NULL,
    updated_at TEXT NOT NULL,
    UNIQUE(
        run_id,
        graph_node_id
    )
);

CREATE TABLE IF NOT EXISTS execution_attempts (
    id TEXT PRIMARY KEY,
    run_node_id TEXT NOT NULL
        REFERENCES execution_run_nodes(id)
        ON DELETE CASCADE,
    worker_id TEXT NOT NULL,
    status TEXT NOT NULL,
    lease_expires_at TEXT NOT NULL,
    started_at TEXT NOT NULL,
    completed_at TEXT,
    error_message TEXT
);

CREATE TABLE IF NOT EXISTS execution_receipts (
    id TEXT PRIMARY KEY,
    run_id TEXT NOT NULL
        REFERENCES execution_runs(id)
        ON DELETE CASCADE,
    run_node_id TEXT NOT NULL
        REFERENCES execution_run_nodes(id)
        ON DELETE CASCADE,
    attempt_id TEXT NOT NULL
        REFERENCES execution_attempts(id)
        ON DELETE CASCADE,
    actor_system TEXT NOT NULL,
    actor_id TEXT NOT NULL,
    outcome TEXT NOT NULL,
    summary TEXT NOT NULL,
    evidence_json TEXT NOT NULL,
    evidence_sha256 TEXT NOT NULL,
    validation_status TEXT NOT NULL,
    created_at TEXT NOT NULL,
    UNIQUE(
        run_node_id,
        evidence_sha256
    )
);

CREATE TABLE IF NOT EXISTS execution_validation_findings (
    id TEXT PRIMARY KEY,
    run_id TEXT NOT NULL
        REFERENCES execution_runs(id)
        ON DELETE CASCADE,
    run_node_id TEXT
        REFERENCES execution_run_nodes(id)
        ON DELETE CASCADE,
    receipt_id TEXT
        REFERENCES execution_receipts(id)
        ON DELETE CASCADE,
    gate_code TEXT NOT NULL,
    severity TEXT NOT NULL,
    message TEXT NOT NULL,
    created_at TEXT NOT NULL
);

CREATE TABLE IF NOT EXISTS execution_events (
    id TEXT PRIMARY KEY,
    run_id TEXT NOT NULL
        REFERENCES execution_runs(id)
        ON DELETE CASCADE,
    run_node_id TEXT
        REFERENCES execution_run_nodes(id)
        ON DELETE CASCADE,
    event_type TEXT NOT NULL,
    actor_id TEXT,
    payload_json TEXT NOT NULL DEFAULT '{}',
    created_at TEXT NOT NULL
);

CREATE INDEX IF NOT EXISTS idx_execution_runs_project
    ON execution_runs(
        project_id,
        created_at
    );

CREATE INDEX IF NOT EXISTS idx_execution_runs_plan
    ON execution_runs(
        plan_version_id,
        status
    );

CREATE INDEX IF NOT EXISTS idx_execution_nodes_run
    ON execution_run_nodes(
        run_id,
        status,
        sequence_number
    );

CREATE INDEX IF NOT EXISTS idx_execution_attempts_node
    ON execution_attempts(
        run_node_id,
        status
    );

CREATE INDEX IF NOT EXISTS idx_execution_receipts_node
    ON execution_receipts(
        run_node_id,
        validation_status
    );

CREATE INDEX IF NOT EXISTS idx_execution_findings_run
    ON execution_validation_findings(
        run_id,
        severity
    );

CREATE INDEX IF NOT EXISTS idx_execution_events_run
    ON execution_events(
        run_id,
        created_at
    );
"""
