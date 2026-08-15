"""Whether a project is ready to move to the next phase.

The judgement the workspace screen renders as a checklist: what exists, what is
still open, and what blocks progress. Its own module because it is the only
part of the workspace that decides something rather than reporting it.
"""

from __future__ import annotations

import json

from .workspace_helpers import count, latest_row


def readiness_report(
    counts: dict[str, int],
) -> dict[str, object]:
    has_source = (
        counts["documents"] > 0
    )

    has_atoms = (
        counts["atoms"] > 0
    )

    research_complete = (
        has_atoms
        and counts["dimensions"] > 0
        and counts["open_dimensions"] == 0
    )

    plan_verified = (
        counts["verified_plans"] > 0
    )

    binding_configured = (
        counts["enabled_bindings"] > 0
    )

    export_verified = (
        counts["verified_exports"] > 0
    )

    execution_verified = (
        counts[
            "verified_execution_runs"
        ]
        > 0
    )

    if execution_verified:
        overall = "VERIFIED"
        next_action = None

    elif (
        export_verified
        and binding_configured
    ):
        overall = "READY_TO_EXECUTE"
        next_action = "START_EXECUTION"

    elif export_verified:
        overall = (
            "INTEGRATION_BINDING_REQUIRED"
        )
        next_action = (
            "CONFIGURE_INTEGRATION"
        )

    elif plan_verified:
        overall = "READY_TO_EXPORT"
        next_action = "EXPORT_PLAN"

    elif research_complete:
        overall = "READY_TO_PLAN"
        next_action = "SYNTHESIZE_PLAN"

    elif has_atoms:
        overall = "RESEARCH_REQUIRED"
        next_action = (
            "COMPLETE_OPEN_DIMENSIONS"
        )

    elif has_source:
        overall = "EXTRACTION_REQUIRED"
        next_action = "EXTRACT_ATOMS"

    else:
        overall = "SOURCE_REQUIRED"
        next_action = "INGEST_SOURCE"

    stages = [
        {
            "name": "SOURCE",
            "status": (
                "COMPLETE"
                if has_source
                else "REQUIRED"
            ),
            "count": counts["documents"],
        },
        {
            "name": "ATOMS",
            "status": (
                "COMPLETE"
                if has_atoms
                else (
                    "READY"
                    if has_source
                    else "BLOCKED"
                )
            ),
            "count": counts["atoms"],
        },
        {
            "name": "RESEARCH",
            "status": (
                "COMPLETE"
                if research_complete
                else (
                    "PENDING"
                    if has_atoms
                    else "BLOCKED"
                )
            ),
            "open_dimensions": (
                counts["open_dimensions"]
            ),
        },
        {
            "name": "PLANNING",
            "status": (
                "VERIFIED"
                if plan_verified
                else (
                    "READY"
                    if research_complete
                    else "BLOCKED"
                )
            ),
            "count": counts["plans"],
        },
        {
            "name": "INTEGRATION",
            "status": (
                "CONFIGURED"
                if binding_configured
                else (
                    "READY"
                    if plan_verified
                    else "BLOCKED"
                )
            ),
            "count": (
                counts["enabled_bindings"]
            ),
        },
        {
            "name": "EXPORT",
            "status": (
                "VERIFIED"
                if export_verified
                else (
                    "READY"
                    if plan_verified
                    else "BLOCKED"
                )
            ),
            "count": counts["exports"],
        },
        {
            "name": "EXECUTION",
            "status": (
                "VERIFIED"
                if execution_verified
                else (
                    "READY"
                    if (
                        export_verified
                        and binding_configured
                    )
                    else "BLOCKED"
                )
            ),
            "count": (
                counts["execution_runs"]
            ),
        },
    ]

    return {
        "status": overall,
        "next_action": next_action,
        "source_ready": has_source,
        "atoms_ready": has_atoms,
        "research_ready": (
            research_complete
        ),
        "plan_verified": plan_verified,
        "integration_configured": (
            binding_configured
        ),
        "export_verified": (
            export_verified
        ),
        "execution_verified": (
            execution_verified
        ),
        "stages": stages,
    }
