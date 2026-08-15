"""The most recent record of each kind on a project workspace.

What a client shows as "last activity". Separate from the counts because a
count answers how much and this answers which one.
"""

from __future__ import annotations

import json

from .workspace_helpers import count, latest_row


def build_latest(
    service,
    connection,
    project_id: str,
) -> dict[str, object]:
    """Returns the `latest` block of the project workspace."""
    latest = {
        "document": latest_row(
            connection,
            """
            SELECT
                id,
                title,
                sha256,
                byte_count,
                line_count,
                created_at
            FROM source_documents
            WHERE project_id = ?
            ORDER BY created_at DESC, id DESC
            LIMIT 1
            """,
            (project_id,),
        ),
        "plan": latest_row(
            connection,
            """
            SELECT
                id,
                status,
                atom_count,
                node_count,
                edge_count,
                open_dimension_count,
                created_at,
                verified_at
            FROM plan_versions
            WHERE project_id = ?
            ORDER BY created_at DESC, id DESC
            LIMIT 1
            """,
            (project_id,),
        ),
        "export": latest_row(
            connection,
            """
            SELECT
                id,
                plan_version_id,
                export_type,
                status,
                artifact_count,
                created_at,
                verified_at
            FROM exports
            WHERE project_id = ?
            ORDER BY created_at DESC, id DESC
            LIMIT 1
            """,
            (project_id,),
        ),
        "execution_run": latest_row(
            connection,
            """
            SELECT
                id,
                plan_version_id,
                export_id,
                runtime_system,
                runtime_run_id,
                status,
                created_at,
                started_at,
                completed_at,
                verified_at
            FROM execution_runs
            WHERE project_id = ?
            ORDER BY created_at DESC, id DESC
            LIMIT 1
            """,
            (project_id,),
        ),
        "route_decision": latest_row(
            connection,
            """
            SELECT
                id,
                territory,
                decision_type,
                selected_provider_id,
                retry_at,
                rationale,
                created_at
            FROM route_decisions
            WHERE project_id = ?
            ORDER BY created_at DESC, id DESC
            LIMIT 1
            """,
            (project_id,),
        ),
    }

    return latest
