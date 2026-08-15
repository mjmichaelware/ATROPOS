"""The dimensions an atom can have, and the rules that classify one.

The sixteen dimensions and the kind/modality rules that decide what a sentence
is. Together they are the vocabulary of atomization -- what the extractor is
allowed to produce -- and they change when the *meaning* of an atom changes,
which is far less often than the extractor does.
"""

from __future__ import annotations

import re

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
