"""The export database schema.

Separated for the same reason as the execution schema: DDL changes when the
shape of an export changes, the service when its behaviour does.
"""

from __future__ import annotations

EXPORT_SCHEMA = """
PRAGMA foreign_keys = ON;

CREATE TABLE IF NOT EXISTS integration_bindings (
    id TEXT PRIMARY KEY,
    project_id TEXT NOT NULL
        REFERENCES projects(id)
        ON DELETE CASCADE,
    system_name TEXT NOT NULL,
    binding_type TEXT NOT NULL,
    config_json TEXT NOT NULL DEFAULT '{}',
    enabled INTEGER NOT NULL DEFAULT 1,
    created_at TEXT NOT NULL,
    updated_at TEXT NOT NULL,
    UNIQUE(
        project_id,
        system_name,
        binding_type
    )
);

CREATE TABLE IF NOT EXISTS exports (
    id TEXT PRIMARY KEY,
    project_id TEXT NOT NULL
        REFERENCES projects(id)
        ON DELETE CASCADE,
    plan_version_id TEXT NOT NULL
        REFERENCES plan_versions(id)
        ON DELETE CASCADE,
    export_type TEXT NOT NULL,
    bundle_fingerprint TEXT NOT NULL,
    output_path TEXT NOT NULL,
    manifest_sha256 TEXT NOT NULL,
    status TEXT NOT NULL,
    artifact_count INTEGER NOT NULL,
    created_at TEXT NOT NULL,
    verified_at TEXT,
    UNIQUE(
        plan_version_id,
        export_type,
        bundle_fingerprint
    )
);

CREATE TABLE IF NOT EXISTS export_verification_findings (
    id TEXT PRIMARY KEY,
    export_id TEXT NOT NULL
        REFERENCES exports(id)
        ON DELETE CASCADE,
    severity TEXT NOT NULL,
    code TEXT NOT NULL,
    message TEXT NOT NULL,
    artifact_path TEXT,
    created_at TEXT NOT NULL
);

CREATE INDEX IF NOT EXISTS idx_integration_bindings_project
    ON integration_bindings(
        project_id,
        enabled
    );

CREATE INDEX IF NOT EXISTS idx_exports_project
    ON exports(
        project_id,
        created_at
    );

CREATE INDEX IF NOT EXISTS idx_exports_plan
    ON exports(
        plan_version_id,
        export_type
    );

CREATE INDEX IF NOT EXISTS idx_export_findings_export
    ON export_verification_findings(
        export_id,
        severity
    );
"""


#: Stamped into every manifest, and checked when one is read back.
EXPORT_TYPE = "SPECGRAPH_HANDOFF_V1"
