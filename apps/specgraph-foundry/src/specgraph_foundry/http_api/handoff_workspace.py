import json

from ..database import Database
from ..execution import ExecutionService
from ..exports import ExportService
from ..routing import RoutingService
from ..services import ProjectService
from .pagination import WORKSPACE_PREVIEW_LIMIT


class HandoffWorkspaceService:
    def __init__(
        self,
        database: Database,
    ) -> None:
        self.database = database
        self.projects = ProjectService(database)
        self.exports = ExportService(database)
        self.execution = ExecutionService(database)
        self.routing = RoutingService(database)

    def get(
        self,
        project_id: str,
    ) -> dict[str, object]:
        project = self.projects.get(project_id)
        bindings = self.exports.list_bindings(
            project_id
        )
        export_summaries = self.exports.list_exports(
            project_id
        )
        export_summaries = [
            {
                **export,
                "artifact_manifest": (
                    self._artifact_manifest_summary(
                        str(export["id"])
                    )
                ),
            }
            for export in export_summaries
        ]
        run_summaries = self.execution.list_runs(
            project_id
        )
        providers = self.routing.list_providers(
            project_id
        )
        renderers = self.routing.list_renderers(
            project_id
        )
        policy = self.routing.get_policy(
            project_id
        )

        return {
            "project": project,
            "counts": {
                "bindings": len(bindings),
                "enabled_bindings": (
                    self._count_true(
                        bindings,
                        "enabled",
                    )
                ),
                "exports": len(export_summaries),
                "verified_exports": (
                    self._count_status(
                        export_summaries,
                        {"VERIFIED"},
                    )
                ),
                "invalid_exports": (
                    self._count_status(
                        export_summaries,
                        {"INVALID"},
                    )
                ),
                "execution_runs": len(
                    run_summaries
                ),
                "verified_execution_runs": (
                    self._count_status(
                        run_summaries,
                        {"VERIFIED"},
                    )
                ),
                "rejected_execution_runs": (
                    self._count_status(
                        run_summaries,
                        {"REJECTED", "INVALID"},
                    )
                ),
                "receipts": self._count_query(
                    """
                    SELECT COUNT(*) AS value
                    FROM execution_receipts AS receipt
                    JOIN execution_runs AS run
                      ON run.id = receipt.run_id
                    WHERE run.project_id = ?
                    """,
                    project_id,
                ),
                "execution_findings": self._count_query(
                    """
                    SELECT COUNT(*) AS value
                    FROM execution_validation_findings AS finding
                    JOIN execution_runs AS run
                      ON run.id = finding.run_id
                    WHERE run.project_id = ?
                    """,
                    project_id,
                ),
                "providers": len(providers),
                "ready_providers": (
                    self._count_status(
                        providers,
                        {"READY"},
                    )
                ),
                "renderers": len(renderers),
                "enabled_renderers": (
                    self._count_true(
                        renderers,
                        "enabled",
                    )
                ),
            },
            "bindings": bindings[
                :WORKSPACE_PREVIEW_LIMIT
            ],
            "bindings_count": len(bindings),
            "bindings_has_more": len(bindings)
            > WORKSPACE_PREVIEW_LIMIT,
            "bindings_route": (
                f"/v1/projects/{project_id}/bindings"
            ),
            "exports": export_summaries[
                :WORKSPACE_PREVIEW_LIMIT
            ],
            "exports_count": len(
                export_summaries
            ),
            "exports_has_more": len(
                export_summaries
            )
            > WORKSPACE_PREVIEW_LIMIT,
            "exports_route": (
                f"/v1/projects/{project_id}/exports"
            ),
            "execution_runs": run_summaries[
                :WORKSPACE_PREVIEW_LIMIT
            ],
            "execution_runs_count": len(
                run_summaries
            ),
            "execution_runs_has_more": len(
                run_summaries
            )
            > WORKSPACE_PREVIEW_LIMIT,
            "execution_runs_route": (
                f"/v1/projects/{project_id}/execution-runs"
            ),
            "providers": providers[
                :WORKSPACE_PREVIEW_LIMIT
            ],
            "providers_count": len(providers),
            "providers_has_more": len(providers)
            > WORKSPACE_PREVIEW_LIMIT,
            "providers_route": (
                f"/v1/projects/{project_id}/providers"
            ),
            "renderers": renderers[
                :WORKSPACE_PREVIEW_LIMIT
            ],
            "renderers_count": len(renderers),
            "renderers_has_more": len(renderers)
            > WORKSPACE_PREVIEW_LIMIT,
            "renderers_route": (
                f"/v1/projects/{project_id}/renderers"
            ),
            "routing_policy": policy,
            "latest_export": (
                {
                    **export_summaries[0],
                    "detail_route": (
                        f"/v1/exports/{export_summaries[0]['id']}"
                    ),
                }
                if export_summaries
                else None
            ),
            "latest_execution_run": (
                {
                    **run_summaries[0],
                    "detail_route": (
                        f"/v1/execution-runs/{run_summaries[0]['id']}"
                    ),
                }
                if run_summaries
                else None
            ),
        }

    def _count_query(
        self,
        sql: str,
        project_id: str,
    ) -> int:
        with self.database.connect() as connection:
            row = connection.execute(
                sql,
                (project_id,),
            ).fetchone()

        return (
            int(row["value"])
            if row is not None
            else 0
        )

    def _artifact_manifest_summary(
        self,
        export_id: str,
    ) -> dict[str, object] | None:
        with self.database.connect() as connection:
            row = connection.execute(
                """
                SELECT *
                FROM artifact_manifests
                WHERE export_id = ?
                """,
                (export_id,),
            ).fetchone()

        if row is None:
            return None

        manifest = json.loads(
            str(row["manifest_json"])
        )
        artifacts = [
            {
                "name": item["name"],
                "media_type": item["media_type"],
                "byte_length": item["byte_length"],
                "sha256": item["sha256"],
            }
            for item in manifest["artifacts"][
                :WORKSPACE_PREVIEW_LIMIT
            ]
        ]
        return {
            "id": str(row["id"]),
            "state": str(row["state"]),
            "aggregate_sha256": str(
                row["aggregate_sha256"]
            ),
            "artifact_count": int(
                row["artifact_count"]
            ),
            "artifacts": artifacts,
            "artifacts_has_more": int(
                row["artifact_count"]
            )
            > len(artifacts),
        }

    @staticmethod
    def _count_status(
        items: list[dict[str, object]],
        statuses: set[str],
    ) -> int:
        return sum(
            str(item.get("status", ""))
            in statuses
            for item in items
        )

    @staticmethod
    def _count_true(
        items: list[dict[str, object]],
        key: str,
    ) -> int:
        return sum(
            item.get(key) is True
            for item in items
        )
