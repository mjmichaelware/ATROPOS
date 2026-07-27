from ..database import Database
from ..planning import PlanningService
from ..services import ProjectService
from .pagination import WORKSPACE_PREVIEW_LIMIT


class PlanningWorkspaceService:
    def __init__(
        self,
        database: Database,
    ) -> None:
        self.projects = ProjectService(database)
        self.planning = PlanningService(database)

    def get(
        self,
        project_id: str,
    ) -> dict[str, object]:
        project = self.projects.get(project_id)
        relations = self.planning.list_relations(
            project_id
        )
        plan_summaries = self.planning.list_plans(
            project_id
        )

        latest_plan = None
        authority_node_count = 0
        authority_edge_count = 0
        execution_node_count = 0
        execution_edge_count = 0
        ready_node_count = 0
        blocked_node_count = 0

        if plan_summaries:
            latest_full = self.planning.get_plan(
                str(plan_summaries[0]["id"])
            )
            authority_graph = latest_full.get(
                "authority_graph"
            )
            execution_graph = latest_full.get(
                "execution_graph"
            )
            ready_nodes = latest_full.get(
                "ready_nodes"
            )
            authority_node_count = self._graph_count(
                authority_graph,
                "nodes",
            )
            authority_edge_count = self._graph_count(
                authority_graph,
                "edges",
            )
            execution_node_count = self._graph_count(
                execution_graph,
                "nodes",
            )
            execution_edge_count = self._graph_count(
                execution_graph,
                "edges",
            )
            ready_node_count = (
                len(ready_nodes)
                if isinstance(ready_nodes, list)
                else 0
            )
            blocked_node_count = sum(
                str(item.get("status", ""))
                == "BLOCKED"
                for item in (
                    execution_graph.get("nodes", [])
                    if isinstance(
                        execution_graph,
                        dict,
                    )
                    else []
                )
                if isinstance(item, dict)
            )
            latest_plan = {
                **plan_summaries[0],
                "detail_route": (
                    f"/v1/plans/{plan_summaries[0]['id']}"
                ),
            }

        plan_preview = plan_summaries[
            :WORKSPACE_PREVIEW_LIMIT
        ]

        return {
            "project": project,
            "counts": {
                "authority_relations": len(
                    relations
                ),
                "plans": len(plan_summaries),
                "draft_plans": self._count_status(
                    plan_summaries,
                    {"DRAFT"},
                ),
                "blocked_plans": self._count_status(
                    plan_summaries,
                    {"BLOCKED"},
                ),
                "verified_plans": self._count_status(
                    plan_summaries,
                    {"VERIFIED"},
                ),
                "authority_nodes": authority_node_count,
                "authority_edges": authority_edge_count,
                "execution_nodes": execution_node_count,
                "execution_edges": execution_edge_count,
                "ready_nodes": ready_node_count,
                "blocked_nodes": blocked_node_count,
            },
            "relations": relations[
                :WORKSPACE_PREVIEW_LIMIT
            ],
            "relations_count": len(relations),
            "relations_has_more": len(relations)
            > WORKSPACE_PREVIEW_LIMIT,
            "relations_route": (
                f"/v1/projects/{project_id}/relations"
            ),
            "plans": plan_preview,
            "plans_count": len(plan_summaries),
            "plans_has_more": len(plan_summaries)
            > len(plan_preview),
            "plans_route": (
                f"/v1/projects/{project_id}/plans"
            ),
            "latest_plan": latest_plan,
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
    def _graph_count(
        graph: object,
        key: str,
    ) -> int:
        if not isinstance(graph, dict):
            return 0

        value = graph.get(key)
        return (
            len(value)
            if isinstance(value, list)
            else 0
        )
