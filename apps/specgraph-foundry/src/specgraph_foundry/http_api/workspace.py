from .workspace_counts import build_counts
from .workspace_latest import build_latest
from .workspace_readiness import readiness_report
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

            counts = build_counts(self, connection, project_id)

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

            latest = build_latest(self, connection, project_id)

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

        """Delegates to :func:`workspace_readiness.readiness_report`."""
        return readiness_report(
            self,
            counts,
        )
