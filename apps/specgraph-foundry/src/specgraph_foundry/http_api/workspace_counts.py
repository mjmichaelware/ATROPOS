"""Counting everything on a project workspace screen.

Twenty-odd aggregate queries in one connection so the numbers on a screen are
consistent with each other rather than each being true at a different moment.
"""

from __future__ import annotations

import json

from .workspace_helpers import count, latest_row


def build_counts(
    service,
    connection,
    project_id: str,
) -> dict[str, object]:
    """Returns the `counts` block of the project workspace."""
    counts = {
        "documents": count(
            connection,
            """
            SELECT COUNT(*) AS value
            FROM source_documents
            WHERE project_id = ?
            """,
            (project_id,),
        ),
        "sections": count(
            connection,
            """
            SELECT COUNT(*) AS value
            FROM source_sections AS section
            JOIN source_documents AS document
              ON document.id =
                 section.document_id
            WHERE document.project_id = ?
            """,
            (project_id,),
        ),
        "chunks": count(
            connection,
            """
            SELECT COUNT(*) AS value
            FROM source_chunks AS chunk
            JOIN source_documents AS document
              ON document.id =
                 chunk.document_id
            WHERE document.project_id = ?
            """,
            (project_id,),
        ),
        "atoms": count(
            connection,
            """
            SELECT COUNT(*) AS value
            FROM atoms
            WHERE project_id = ?
            """,
            (project_id,),
        ),
        "dimensions": count(
            connection,
            """
            SELECT COUNT(*) AS value
            FROM atom_dimensions AS dimension
            JOIN atoms AS atom
              ON atom.id =
                 dimension.atom_id
            WHERE atom.project_id = ?
            """,
            (project_id,),
        ),
        "open_dimensions": count(
            connection,
            """
            SELECT COUNT(*) AS value
            FROM atom_dimensions AS dimension
            JOIN atoms AS atom
              ON atom.id =
                 dimension.atom_id
            WHERE atom.project_id = ?
              AND dimension.status = 'OPEN'
            """,
            (project_id,),
        ),
        "resolved_dimensions": count(
            connection,
            """
            SELECT COUNT(*) AS value
            FROM atom_dimensions AS dimension
            JOIN atoms AS atom
              ON atom.id =
                 dimension.atom_id
            WHERE atom.project_id = ?
              AND dimension.status = 'RESOLVED'
            """,
            (project_id,),
        ),
        "not_applicable_dimensions": count(
            connection,
            """
            SELECT COUNT(*) AS value
            FROM atom_dimensions AS dimension
            JOIN atoms AS atom
              ON atom.id =
                 dimension.atom_id
            WHERE atom.project_id = ?
              AND dimension.status =
                  'NOT_APPLICABLE'
            """,
            (project_id,),
        ),
        "research_tasks": count(
            connection,
            """
            SELECT COUNT(*) AS value
            FROM research_tasks
            WHERE project_id = ?
            """,
            (project_id,),
        ),
        "pending_research_tasks": count(
            connection,
            """
            SELECT COUNT(*) AS value
            FROM research_tasks
            WHERE project_id = ?
              AND status IN (
                  'PENDING',
                  'CLAIMED'
              )
            """,
            (project_id,),
        ),
        "completed_research_tasks": count(
            connection,
            """
            SELECT COUNT(*) AS value
            FROM research_tasks
            WHERE project_id = ?
              AND status = 'COMPLETE'
            """,
            (project_id,),
        ),
        "failed_research_tasks": count(
            connection,
            """
            SELECT COUNT(*) AS value
            FROM research_tasks
            WHERE project_id = ?
              AND status = 'FAILED'
            """,
            (project_id,),
        ),
        "authority_relations": count(
            connection,
            """
            SELECT COUNT(*) AS value
            FROM authority_relations
            WHERE project_id = ?
            """,
            (project_id,),
        ),
        "plans": count(
            connection,
            """
            SELECT COUNT(*) AS value
            FROM plan_versions
            WHERE project_id = ?
            """,
            (project_id,),
        ),
        "verified_plans": count(
            connection,
            """
            SELECT COUNT(*) AS value
            FROM plan_versions
            WHERE project_id = ?
              AND status = 'VERIFIED'
            """,
            (project_id,),
        ),
        "bindings": count(
            connection,
            """
            SELECT COUNT(*) AS value
            FROM integration_bindings
            WHERE project_id = ?
            """,
            (project_id,),
        ),
        "enabled_bindings": count(
            connection,
            """
            SELECT COUNT(*) AS value
            FROM integration_bindings
            WHERE project_id = ?
              AND enabled IS TRUE
            """,
            (project_id,),
        ),
        "exports": count(
            connection,
            """
            SELECT COUNT(*) AS value
            FROM exports
            WHERE project_id = ?
            """,
            (project_id,),
        ),
        "verified_exports": count(
            connection,
            """
            SELECT COUNT(*) AS value
            FROM exports
            WHERE project_id = ?
              AND status = 'VERIFIED'
            """,
            (project_id,),
        ),
        "execution_runs": count(
            connection,
            """
            SELECT COUNT(*) AS value
            FROM execution_runs
            WHERE project_id = ?
            """,
            (project_id,),
        ),
        "verified_execution_runs": count(
            connection,
            """
            SELECT COUNT(*) AS value
            FROM execution_runs
            WHERE project_id = ?
              AND status = 'VERIFIED'
            """,
            (project_id,),
        ),
        "providers": count(
            connection,
            """
            SELECT COUNT(*) AS value
            FROM provider_configs
            WHERE project_id = ?
            """,
            (project_id,),
        ),
        "ready_providers": count(
            connection,
            """
            SELECT COUNT(*) AS value
            FROM provider_configs
            WHERE project_id = ?
              AND enabled IS TRUE
              AND status = 'READY'
            """,
            (project_id,),
        ),
        "renderers": count(
            connection,
            """
            SELECT COUNT(*) AS value
            FROM renderer_configs
            WHERE project_id = ?
            """,
            (project_id,),
        ),
        "enabled_renderers": count(
            connection,
            """
            SELECT COUNT(*) AS value
            FROM renderer_configs
            WHERE project_id = ?
              AND enabled IS TRUE
            """,
            (project_id,),
        ),
        "route_decisions": count(
            connection,
            """
            SELECT COUNT(*) AS value
            FROM route_decisions
            WHERE project_id = ?
            """,
            (project_id,),
        ),
        "operations": count(
            connection,
            """
            SELECT COUNT(*) AS value
            FROM operations
            WHERE project_id = ?
            """,
            (project_id,),
        ),
        "active_operations": count(
            connection,
            """
            SELECT COUNT(*) AS value
            FROM operations
            WHERE project_id = ?
              AND state IN (
                  'QUEUED',
                  'CLAIMED',
                  'RUNNING',
                  'CANCEL_REQUESTED'
              )
            """,
            (project_id,),
        ),
    }

    return counts
