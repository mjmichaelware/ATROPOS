"""The research database schema."""

from __future__ import annotations

RESEARCH_SCHEMA = """
PRAGMA foreign_keys = ON;

CREATE TABLE IF NOT EXISTS research_evidence (
    id TEXT PRIMARY KEY,
    project_id TEXT NOT NULL
        REFERENCES projects(id)
        ON DELETE CASCADE,
    task_id TEXT NOT NULL
        REFERENCES research_tasks(id)
        ON DELETE CASCADE,
    atom_id TEXT NOT NULL
        REFERENCES atoms(id)
        ON DELETE CASCADE,
    dimension TEXT NOT NULL,
    source_uri TEXT NOT NULL,
    source_title TEXT NOT NULL,
    publisher TEXT NOT NULL DEFAULT '',
    evidence_type TEXT NOT NULL,
    excerpt TEXT NOT NULL,
    content_sha256 TEXT NOT NULL,
    reliability REAL NOT NULL,
    retrieved_at TEXT NOT NULL,
    created_at TEXT NOT NULL,
    CHECK(reliability >= 0.0 AND reliability <= 1.0),
    UNIQUE(task_id, source_uri, content_sha256)
);

CREATE TABLE IF NOT EXISTS research_claims (
    id TEXT PRIMARY KEY,
    project_id TEXT NOT NULL
        REFERENCES projects(id)
        ON DELETE CASCADE,
    task_id TEXT NOT NULL UNIQUE
        REFERENCES research_tasks(id)
        ON DELETE CASCADE,
    atom_id TEXT NOT NULL
        REFERENCES atoms(id)
        ON DELETE CASCADE,
    dimension TEXT NOT NULL,
    conclusion TEXT NOT NULL,
    applicability TEXT NOT NULL,
    confidence REAL NOT NULL,
    status TEXT NOT NULL,
    created_at TEXT NOT NULL,
    updated_at TEXT NOT NULL,
    CHECK(confidence >= 0.0 AND confidence <= 1.0)
);

CREATE TABLE IF NOT EXISTS research_claim_evidence (
    claim_id TEXT NOT NULL
        REFERENCES research_claims(id)
        ON DELETE CASCADE,
    evidence_id TEXT NOT NULL
        REFERENCES research_evidence(id)
        ON DELETE CASCADE,
    PRIMARY KEY(claim_id, evidence_id)
);

CREATE TABLE IF NOT EXISTS research_task_events (
    id TEXT PRIMARY KEY,
    task_id TEXT NOT NULL
        REFERENCES research_tasks(id)
        ON DELETE CASCADE,
    event_type TEXT NOT NULL,
    worker_id TEXT,
    payload_json TEXT NOT NULL DEFAULT '{}',
    created_at TEXT NOT NULL
);

CREATE INDEX IF NOT EXISTS idx_research_evidence_task
    ON research_evidence(task_id, created_at);

CREATE INDEX IF NOT EXISTS idx_research_claims_atom
    ON research_claims(atom_id, dimension);

CREATE INDEX IF NOT EXISTS idx_research_events_task
    ON research_task_events(task_id, created_at);
"""
