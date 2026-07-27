from ..atoms import AtomService
from ..database import Database
from ..research import ResearchService
from ..services import ProjectService
from .pagination import WORKSPACE_PREVIEW_LIMIT


class ResearchWorkspaceService:
    def __init__(
        self,
        database: Database,
    ) -> None:
        self.database = database
        self.projects = ProjectService(database)
        self.atoms = AtomService(database)
        self.research = ResearchService(database)

    def get(
        self,
        project_id: str,
    ) -> dict[str, object]:
        project = self.projects.get(project_id)
        matrix = self.research.gap_matrix(
            project_id
        )
        task_summaries = (
            self.atoms.list_research_tasks(
                project_id
            )
        )
        task_preview = task_summaries[
            :WORKSPACE_PREVIEW_LIMIT
        ]
        atom_preview = self._preview_atoms(
            matrix
        )

        counts = {
            "atoms": int(
                matrix["summary"]["atom_count"]
            ),
            "dimensions": int(
                matrix["summary"][
                    "total_dimensions"
                ]
            ),
            "open_dimensions": int(
                matrix["summary"][
                    "open_dimensions"
                ]
            ),
            "resolved_dimensions": int(
                matrix["summary"][
                    "resolved_dimensions"
                ]
            ),
            "not_applicable_dimensions": int(
                matrix["summary"][
                    "not_applicable_dimensions"
                ]
            ),
            "ready_atoms": int(
                matrix["summary"]["ready_atoms"]
            ),
            "tasks": len(task_summaries),
            "pending_tasks": (
                self._count_status(
                    task_summaries,
                    {"PENDING", "CLAIMED"},
                )
            ),
            "completed_tasks": (
                self._count_status(
                    task_summaries,
                    {"COMPLETE"},
                )
            ),
            "failed_tasks": (
                self._count_status(
                    task_summaries,
                    {"FAILED"},
                )
            ),
            "evidence": self._evidence_count(
                project_id
            ),
        }

        return {
            "project": project,
            "counts": counts,
            "gap_matrix": {
                "project": matrix["project"],
                "summary": matrix["summary"],
                "atoms": atom_preview,
                "atoms_count": len(
                    matrix["atoms"]
                ),
                "atoms_has_more": len(
                    matrix["atoms"]
                )
                > len(atom_preview),
                "atoms_route": (
                    f"/v1/projects/{project_id}/gap-matrix"
                ),
            },
            "tasks": task_preview,
            "tasks_count": len(task_summaries),
            "tasks_has_more": len(task_summaries)
            > len(task_preview),
            "tasks_route": (
                f"/v1/projects/{project_id}/research-tasks"
            ),
        }

    def _evidence_count(
        self,
        project_id: str,
    ) -> int:
        with self.database.connect() as connection:
            row = connection.execute(
                """
                SELECT COUNT(*) AS value
                FROM research_evidence
                WHERE project_id = ?
                """,
                (project_id,),
            ).fetchone()

        return (
            int(row["value"])
            if row is not None
            else 0
        )

    @staticmethod
    def _preview_atoms(
        matrix: dict[str, object],
    ) -> list[dict[str, object]]:
        atoms = matrix.get("atoms")
        if not isinstance(atoms, list):
            return []

        return atoms[:WORKSPACE_PREVIEW_LIMIT]

    @staticmethod
    def _count_status(
        tasks: list[dict[str, object]],
        statuses: set[str],
    ) -> int:
        return sum(
            str(
                item.get("status", "")
            )
            in statuses
            for item in tasks
        )
