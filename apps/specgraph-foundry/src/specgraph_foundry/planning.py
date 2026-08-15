import hashlib
import json
import sqlite3
import uuid
from collections import defaultdict, deque
from datetime import UTC, datetime

from .database import Database
from .plan_graph_rules import graph_has_cycle, insert_edge, validate_dependency_acyclic
from .plan_guards import existing_plan, fingerprint, require_atom, require_project
from .plan_relations import add_relation, get_relation, list_relations, list_relations_page
from .export_titles import sanitize_export_title
from .plan_queries import get_plan, list_plans
from .plan_synthesis import synthesize
from .plan_verification import verify_plan
from .planning_schema import PLANNING_SCHEMA
from .relation_types import RELATION_TYPES
from .stages import STAGES
from .errors import (
    ConflictError,
    NotFoundError,
    ValidationError,
)
from .services import GraphService










def utc_now() -> str:
    return datetime.now(UTC).isoformat()


def new_id(prefix: str) -> str:
    return str(uuid.uuid4())


class PlanningService:
    def __init__(self, database: Database) -> None:
        self.database = database
        self.graphs = GraphService(database)
        self.ensure_schema()

    def ensure_schema(self) -> None:
        with self.database.connect() as connection:
            connection.executescript(
                PLANNING_SCHEMA
            )

    def add_relation(
        self,
        project_id: str,
        from_atom_id: str,
        to_atom_id: str,
        relation_type: str,
        rationale: str = "",
        confidence: float = 1.0,
        inferred: bool = False,
    ) -> dict[str, object]:
        """Delegates to :func:`plan_relations.add_relation`."""
        return add_relation(
            self.database,
            project_id,
            from_atom_id,
            to_atom_id,
            relation_type,
            rationale,
            confidence,
            inferred,
        )


    def get_relation(
        self,
        relation_id: str,
    ) -> dict[str, object]:
        """Delegates to :func:`plan_relations.get_relation`."""
        return get_relation(
            self.database,
            relation_id,
        )


    def list_relations(
        self,
        project_id: str,
    ) -> list[dict[str, object]]:
        """Delegates to :func:`plan_relations.list_relations`."""
        return list_relations(
            self.database,
            project_id,
        )


    def list_relations_page(
        self,
        project_id: str,
        limit: int,
        boundary: dict[str, object] | None = None,
    ) -> tuple[
        list[dict[str, object]],
        bool,
        dict[str, object] | None,
    ]:
        """Delegates to :func:`plan_relations.list_relations_page`."""
        return list_relations_page(
            self.database,
            project_id,
            limit,
            boundary,
        )


    def synthesize(
        self,
        project_id: str,
        allow_open_research: bool = False,
    ) -> dict[str, object]:
        """Delegates to :func:`plan_synthesis.synthesize`."""
        return synthesize(
            self.database,
            self.graphs,
            project_id,
            allow_open_research,
        )


    def verify_plan(
        self,
        plan_id: str,
    ) -> dict[str, object]:
        """Delegates to :func:`plan_verification.verify_plan`."""
        return verify_plan(
            self.database,
            plan_id,
        )


    def get_plan(
        self,
        plan_id: str,
    ) -> dict[str, object]:
        """Delegates to :func:`plan_queries.get_plan`."""
        return get_plan(
            self.database,
            self.graphs,
            plan_id,
        )


    def list_plans(
        self,
        project_id: str,
    ) -> list[dict[str, object]]:
        """Delegates to :func:`plan_queries.list_plans`."""
        return list_plans(
            self.database,
            self.graphs,
            project_id,
        )


    def _existing_plan(
        self,
        project_id: str,
        fingerprint: str,
        allow_open_research: bool,
    ) -> sqlite3.Row | None:
        """Delegates to :func:`plan_guards.existing_plan`."""
        return existing_plan(
            self.database,
            project_id,
            fingerprint,
            allow_open_research,
        )


    @staticmethod
    def _fingerprint(
        atoms: list[dict[str, object]],
        relations: list[
            dict[str, object]
        ],
        dimensions: list[
            dict[str, object]
        ],
    ) -> str:
        """Delegates to :func:`plan_guards.fingerprint`."""
        return fingerprint(
            atoms,
            relations,
            dimensions,
        )


    @staticmethod
    def _validate_dependency_acyclic(
        atoms: list[dict[str, object]],
        relations: list[
            dict[str, object]
        ],
    ) -> None:
        """Delegates to :func:`plan_graph_rules.validate_dependency_acyclic`."""
        return validate_dependency_acyclic(
            atoms,
            relations,
        )


    @staticmethod
    def _graph_has_cycle(
        node_ids: set[str],
        edges: list[dict[str, object]],
    ) -> bool:
        """Delegates to :func:`plan_graph_rules.graph_has_cycle`."""
        return graph_has_cycle(
            node_ids,
            edges,
        )


    @staticmethod
    def _insert_edge(
        connection: sqlite3.Connection,
        graph_id: str,
        from_node_id: str,
        to_node_id: str,
        edge_type: str,
        rationale: str,
    ) -> None:
        """Delegates to :func:`plan_graph_rules.insert_edge`."""
        return insert_edge(
            connection,
            graph_id,
            from_node_id,
            to_node_id,
            edge_type,
            rationale,
        )


    @staticmethod
    def _require_project(
        connection: sqlite3.Connection,
        project_id: str,
    ) -> None:
        """Delegates to :func:`plan_guards.require_project`."""
        return require_project(
            connection,
            project_id,
        )


    @staticmethod
    def _require_atom(
        connection: sqlite3.Connection,
        project_id: str,
        atom_id: str,
    ) -> None:
        """Delegates to :func:`plan_guards.require_atom`."""
        return require_atom(
            connection,
            project_id,
            atom_id,
        )

