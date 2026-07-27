from typing import Any

from ..database import Database
from ..errors import NotFoundError


class ProjectWorkspaceService:
    def __init__(
        self,
        database: Database,
    ) -> None:
        self.database = database

    def get(
        self,
        project_id: str,
    ) -> dict[str, object]:
        with self.database.connect() as connection:
            project = connection.execute(
                """
                SELECT
                    id,
                    slug,
                    name,
                    description,
                    created_at
                FROM projects
                WHERE id = ?
                """,
                (project_id,),
            ).fetchone()

            if project is None:
                raise NotFoundError(
                    f"project not found: {project_id}"
                )

            counts = {
                "documents": self._count(
                    connection,
                    """
                    SELECT COUNT(*) AS value
                    FROM source_documents
                    WHERE project_id = ?
                    """,
                    (project_id,),
                ),
                "sections": self._count(
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
                "chunks": self._count(
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
                "atoms": self._count(
                    connection,
                    """
                    SELECT COUNT(*) AS value
                    FROM atoms
                    WHERE project_id = ?
                    """,
                    (project_id,),
                ),
                "dimensions": self._count(
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
                "open_dimensions": self._count(
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
                "resolved_dimensions": self._count(
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
                "not_applicable_dimensions": self._count(
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
                "research_tasks": self._count(
                    connection,
                    """
                    SELECT COUNT(*) AS value
                    FROM research_tasks
                    WHERE project_id = ?
                    """,
                    (project_id,),
                ),
                "pending_research_tasks": self._count(
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
                "completed_research_tasks": self._count(
                    connection,
                    """
                    SELECT COUNT(*) AS value
                    FROM research_tasks
                    WHERE project_id = ?
                      AND status = 'COMPLETE'
                    """,
                    (project_id,),
                ),
                "failed_research_tasks": self._count(
                    connection,
                    """
                    SELECT COUNT(*) AS value
                    FROM research_tasks
                    WHERE project_id = ?
                      AND status = 'FAILED'
                    """,
                    (project_id,),
                ),
                "authority_relations": self._count(
                    connection,
                    """
                    SELECT COUNT(*) AS value
                    FROM authority_relations
                    WHERE project_id = ?
                    """,
                    (project_id,),
                ),
                "plans": self._count(
                    connection,
                    """
                    SELECT COUNT(*) AS value
                    FROM plan_versions
                    WHERE project_id = ?
                    """,
                    (project_id,),
                ),
                "verified_plans": self._count(
                    connection,
                    """
                    SELECT COUNT(*) AS value
                    FROM plan_versions
                    WHERE project_id = ?
                      AND status = 'VERIFIED'
                    """,
                    (project_id,),
                ),
                "bindings": self._count(
                    connection,
                    """
                    SELECT COUNT(*) AS value
                    FROM integration_bindings
                    WHERE project_id = ?
                    """,
                    (project_id,),
                ),
                "enabled_bindings": self._count(
                    connection,
                    """
                    SELECT COUNT(*) AS value
                    FROM integration_bindings
                    WHERE project_id = ?
                      AND enabled IS TRUE
                    """,
                    (project_id,),
                ),
                "exports": self._count(
                    connection,
                    """
                    SELECT COUNT(*) AS value
                    FROM exports
                    WHERE project_id = ?
                    """,
                    (project_id,),
                ),
                "verified_exports": self._count(
                    connection,
                    """
                    SELECT COUNT(*) AS value
                    FROM exports
                    WHERE project_id = ?
                      AND status = 'VERIFIED'
                    """,
                    (project_id,),
                ),
                "execution_runs": self._count(
                    connection,
                    """
                    SELECT COUNT(*) AS value
                    FROM execution_runs
                    WHERE project_id = ?
                    """,
                    (project_id,),
                ),
                "verified_execution_runs": self._count(
                    connection,
                    """
                    SELECT COUNT(*) AS value
                    FROM execution_runs
                    WHERE project_id = ?
                      AND status = 'VERIFIED'
                    """,
                    (project_id,),
                ),
                "providers": self._count(
                    connection,
                    """
                    SELECT COUNT(*) AS value
                    FROM provider_configs
                    WHERE project_id = ?
                    """,
                    (project_id,),
                ),
                "ready_providers": self._count(
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
                "renderers": self._count(
                    connection,
                    """
                    SELECT COUNT(*) AS value
                    FROM renderer_configs
                    WHERE project_id = ?
                    """,
                    (project_id,),
                ),
                "enabled_renderers": self._count(
                    connection,
                    """
                    SELECT COUNT(*) AS value
                    FROM renderer_configs
                    WHERE project_id = ?
                      AND enabled IS TRUE
                    """,
                    (project_id,),
                ),
                "route_decisions": self._count(
                    connection,
                    """
                    SELECT COUNT(*) AS value
                    FROM route_decisions
                    WHERE project_id = ?
                    """,
                    (project_id,),
                ),
                "operations": self._count(
                    connection,
                    """
                    SELECT COUNT(*) AS value
                    FROM operations
                    WHERE project_id = ?
                    """,
                    (project_id,),
                ),
                "active_operations": self._count(
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

            operations = [
                dict(row)
                for row in connection.execute(
                    """
                    SELECT
                        id,
                        operation_type,
                        state,
                        phase,
                        progress_current,
                        progress_total,
                        attempt_count,
                        created_at,
                        updated_at,
                        finished_at
                    FROM operations
                    WHERE project_id = ?
                    ORDER BY created_at DESC, id DESC
                    LIMIT 5
                    """,
                    (project_id,),
                ).fetchall()
            ]

            latest = {
                "document": self._latest(
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
                "plan": self._latest(
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
                "export": self._latest(
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
                "execution_run": self._latest(
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
                "route_decision": self._latest(
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

        readiness = self._readiness(counts)

        return {
            "project": dict(project),
            "readiness": readiness,
            "counts": counts,
            "latest": latest,
            "operations": operations,
            "operations_count": counts["operations"],
            "operations_has_more": counts["operations"] > len(operations),
            "operations_route": (
                f"/v1/projects/{project_id}/operations"
            ),
        }

    def readiness(
        self,
        project_id: str,
    ) -> dict[str, object]:
        workspace = self.get(project_id)

        return {
            "project": workspace["project"],
            "readiness": workspace["readiness"],
            "counts": workspace["counts"],
        }

    @staticmethod
    def _count(
        connection: Any,
        sql: str,
        parameters: tuple[object, ...],
    ) -> int:
        row = connection.execute(
            sql,
            parameters,
        ).fetchone()

        if row is None:
            return 0

        return int(row["value"])

    @staticmethod
    def _latest(
        connection: Any,
        sql: str,
        parameters: tuple[object, ...],
    ) -> dict[str, object] | None:
        row = connection.execute(
            sql,
            parameters,
        ).fetchone()

        return (
            dict(row)
            if row is not None
            else None
        )

    @staticmethod
    def _readiness(
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
