"""The atom database schema."""

from __future__ import annotations

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
