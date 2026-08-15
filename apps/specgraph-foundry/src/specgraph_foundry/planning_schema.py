"""The planning database schema."""

from __future__ import annotations

PLANNING_SCHEMA = """
PRAGMA foreign_keys = ON;

CREATE TABLE IF NOT EXISTS authority_relations (
    id TEXT PRIMARY KEY,
    project_id TEXT NOT NULL
        REFERENCES projects(id)
        ON DELETE CASCADE,
    from_atom_id TEXT NOT NULL
        REFERENCES atoms(id)
        ON DELETE CASCADE,
    to_atom_id TEXT NOT NULL
        REFERENCES atoms(id)
        ON DELETE CASCADE,
    relation_type TEXT NOT NULL,
    rationale TEXT NOT NULL DEFAULT '',
    confidence REAL NOT NULL,
    inferred INTEGER NOT NULL DEFAULT 0,
    created_at TEXT NOT NULL,
    CHECK(from_atom_id <> to_atom_id),
    CHECK(confidence >= 0.0 AND confidence <= 1.0),
    UNIQUE(
        project_id,
        from_atom_id,
        to_atom_id,
        relation_type
    )
);

CREATE TABLE IF NOT EXISTS plan_versions (
    id TEXT PRIMARY KEY,
    project_id TEXT NOT NULL
        REFERENCES projects(id)
        ON DELETE CASCADE,
    authority_graph_id TEXT NOT NULL
        REFERENCES graphs(id)
        ON DELETE CASCADE,
    execution_graph_id TEXT NOT NULL
        REFERENCES graphs(id)
        ON DELETE CASCADE,
    input_fingerprint TEXT NOT NULL,
    status TEXT NOT NULL,
    allow_open_research INTEGER NOT NULL DEFAULT 0,
    atom_count INTEGER NOT NULL,
    node_count INTEGER NOT NULL,
    edge_count INTEGER NOT NULL,
    open_dimension_count INTEGER NOT NULL,
    created_at TEXT NOT NULL,
    verified_at TEXT,
    UNIQUE(
        project_id,
        input_fingerprint,
        allow_open_research
    )
);

CREATE TABLE IF NOT EXISTS plan_node_bindings (
    id TEXT PRIMARY KEY,
    plan_version_id TEXT NOT NULL
        REFERENCES plan_versions(id)
        ON DELETE CASCADE,
    graph_node_id TEXT NOT NULL
        REFERENCES graph_nodes(id)
        ON DELETE CASCADE,
    atom_id TEXT NOT NULL
        REFERENCES atoms(id)
        ON DELETE CASCADE,
    stage TEXT NOT NULL,
    sequence_number INTEGER NOT NULL,
    created_at TEXT NOT NULL,
    UNIQUE(plan_version_id, atom_id, stage),
    UNIQUE(plan_version_id, graph_node_id)
);

CREATE TABLE IF NOT EXISTS plan_verification_findings (
    id TEXT PRIMARY KEY,
    plan_version_id TEXT NOT NULL
        REFERENCES plan_versions(id)
        ON DELETE CASCADE,
    severity TEXT NOT NULL,
    code TEXT NOT NULL,
    message TEXT NOT NULL,
    entity_id TEXT,
    created_at TEXT NOT NULL
);

CREATE INDEX IF NOT EXISTS idx_authority_relations_project
    ON authority_relations(
        project_id,
        relation_type
    );

CREATE INDEX IF NOT EXISTS idx_plan_versions_project
    ON plan_versions(
        project_id,
        created_at
    );

CREATE INDEX IF NOT EXISTS idx_plan_bindings_plan
    ON plan_node_bindings(
        plan_version_id,
        sequence_number
    );

CREATE INDEX IF NOT EXISTS idx_plan_findings_plan
    ON plan_verification_findings(
        plan_version_id,
        severity
    );
"""
