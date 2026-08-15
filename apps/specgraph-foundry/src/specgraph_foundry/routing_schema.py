"""The routing database schema."""

from __future__ import annotations

ROUTING_SCHEMA = """
PRAGMA foreign_keys = ON;

CREATE TABLE IF NOT EXISTS project_policies (
    id TEXT PRIMARY KEY,
    project_id TEXT NOT NULL UNIQUE
        REFERENCES projects(id)
        ON DELETE CASCADE,
    route_law_json TEXT NOT NULL,
    allow_offline_degraded INTEGER NOT NULL DEFAULT 1,
    paid_emergency_enabled INTEGER NOT NULL DEFAULT 0,
    max_paid_decisions_per_unlock INTEGER NOT NULL DEFAULT 1,
    created_at TEXT NOT NULL,
    updated_at TEXT NOT NULL,
    CHECK(max_paid_decisions_per_unlock > 0)
);

CREATE TABLE IF NOT EXISTS provider_configs (
    id TEXT PRIMARY KEY,
    project_id TEXT NOT NULL
        REFERENCES projects(id)
        ON DELETE CASCADE,
    name TEXT NOT NULL,
    provider_class TEXT NOT NULL,
    cost_class TEXT NOT NULL,
    territories_json TEXT NOT NULL,
    priority INTEGER NOT NULL,
    enabled INTEGER NOT NULL DEFAULT 1,
    status TEXT NOT NULL DEFAULT 'UNKNOWN',
    cooldown_until TEXT,
    metadata_json TEXT NOT NULL DEFAULT '{}',
    created_at TEXT NOT NULL,
    updated_at TEXT NOT NULL,
    UNIQUE(project_id, name)
);

CREATE TABLE IF NOT EXISTS renderer_configs (
    id TEXT PRIMARY KEY,
    project_id TEXT NOT NULL
        REFERENCES projects(id)
        ON DELETE CASCADE,
    name TEXT NOT NULL,
    renderer_type TEXT NOT NULL,
    territories_json TEXT NOT NULL,
    priority INTEGER NOT NULL,
    enabled INTEGER NOT NULL DEFAULT 1,
    status TEXT NOT NULL DEFAULT 'READY',
    metadata_json TEXT NOT NULL DEFAULT '{}',
    created_at TEXT NOT NULL,
    updated_at TEXT NOT NULL,
    UNIQUE(project_id, name)
);

CREATE TABLE IF NOT EXISTS provider_health_events (
    id TEXT PRIMARY KEY,
    provider_id TEXT NOT NULL
        REFERENCES provider_configs(id)
        ON DELETE CASCADE,
    status TEXT NOT NULL,
    latency_ms REAL,
    error_message TEXT,
    cooldown_until TEXT,
    created_at TEXT NOT NULL
);

CREATE TABLE IF NOT EXISTS paid_route_unlocks (
    id TEXT PRIMARY KEY,
    project_id TEXT NOT NULL
        REFERENCES projects(id)
        ON DELETE CASCADE,
    provider_id TEXT
        REFERENCES provider_configs(id)
        ON DELETE CASCADE,
    actor_id TEXT NOT NULL,
    reason TEXT NOT NULL,
    max_decisions INTEGER NOT NULL,
    used_count INTEGER NOT NULL DEFAULT 0,
    expires_at TEXT NOT NULL,
    created_at TEXT NOT NULL,
    CHECK(max_decisions > 0),
    CHECK(used_count >= 0),
    CHECK(used_count <= max_decisions)
);

CREATE TABLE IF NOT EXISTS route_decisions (
    id TEXT PRIMARY KEY,
    project_id TEXT NOT NULL
        REFERENCES projects(id)
        ON DELETE CASCADE,
    territory TEXT NOT NULL,
    decision_type TEXT NOT NULL,
    selected_provider_id TEXT
        REFERENCES provider_configs(id)
        ON DELETE SET NULL,
    paid_unlock_id TEXT
        REFERENCES paid_route_unlocks(id)
        ON DELETE SET NULL,
    retry_at TEXT,
    rationale TEXT NOT NULL,
    input_json TEXT NOT NULL,
    considered_json TEXT NOT NULL,
    created_at TEXT NOT NULL
);

CREATE INDEX IF NOT EXISTS idx_provider_configs_project
    ON provider_configs(
        project_id,
        provider_class,
        enabled,
        priority
    );

CREATE INDEX IF NOT EXISTS idx_renderer_configs_project
    ON renderer_configs(
        project_id,
        enabled,
        priority
    );

CREATE INDEX IF NOT EXISTS idx_provider_health_provider
    ON provider_health_events(
        provider_id,
        created_at
    );

CREATE INDEX IF NOT EXISTS idx_paid_unlocks_project
    ON paid_route_unlocks(
        project_id,
        expires_at
    );

CREATE INDEX IF NOT EXISTS idx_route_decisions_project
    ON route_decisions(
        project_id,
        created_at
    );
"""
