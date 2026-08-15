"""The question each dimension asks of a requirement.

One question per dimension: what a researcher must answer before that aspect of
a requirement is settled. Its own module because these are the prompts the whole
research stage runs on -- they are worth finding by name and editing
deliberately, not buried beside sentence segmentation.
"""

from __future__ import annotations

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
