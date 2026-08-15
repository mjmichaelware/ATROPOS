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






def _sanitize_export_title(value: object) -> str:
    text = str(value)
    replacements = {
        "PHASES": "BATCHES",
        "Phases": "Batches",
        "phases": "batches",
        "PHASE": "BATCH",
        "Phase": "Batch",
        "phase": "batch",
    }
    for old, new in replacements.items():
        text = text.replace(old, new)
    return text




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
        with self.database.connect() as connection:
            self._require_project(
                connection,
                project_id,
            )

            atoms = [
                dict(row)
                for row in connection.execute(
                    """
                    SELECT
                        id,
                        document_id,
                        ordinal,
                        kind,
                        modality,
                        canonical_statement
                    FROM atoms
                    WHERE project_id = ?
                    ORDER BY
                        document_id,
                        ordinal,
                        id
                    """,
                    (project_id,),
                ).fetchall()
            ]

            relations = [
                dict(row)
                for row in connection.execute(
                    """
                    SELECT *
                    FROM authority_relations
                    WHERE project_id = ?
                    ORDER BY
                        relation_type,
                        from_atom_id,
                        to_atom_id
                    """,
                    (project_id,),
                ).fetchall()
            ]

            dimensions = [
                dict(row)
                for row in connection.execute(
                    """
                    SELECT
                        dimensions.atom_id,
                        dimensions.dimension,
                        dimensions.status
                    FROM atom_dimensions
                    AS dimensions
                    JOIN atoms
                      ON atoms.id =
                         dimensions.atom_id
                    WHERE atoms.project_id = ?
                    ORDER BY
                        dimensions.atom_id,
                        dimensions.dimension
                    """,
                    (project_id,),
                ).fetchall()
            ]

        if not atoms:
            raise ValidationError(
                "project has no extracted atoms"
            )

        open_by_atom: dict[str, int] = defaultdict(int)

        for dimension in dimensions:
            if dimension["status"] == "OPEN":
                open_by_atom[
                    str(dimension["atom_id"])
                ] += 1

        dependency_relations = [
            relation
            for relation in relations
            if relation["relation_type"]
            == "REQUIRES"
        ]

        self._validate_dependency_acyclic(
            atoms,
            dependency_relations,
        )

        fingerprint = self._fingerprint(
            atoms,
            relations,
            dimensions,
        )

        existing = self._existing_plan(
            project_id,
            fingerprint,
            allow_open_research,
        )

        if existing is not None:
            return self.get_plan(
                str(existing["id"])
            )

        authority_graph = self.graphs.create(
            project_id,
            "Authority Graph",
            "AUTHORITY",
            False,
        )

        execution_graph = self.graphs.create(
            project_id,
            "Execution DAG",
            "EXECUTION",
            True,
        )

        authority_nodes: dict[str, str] = {}
        execution_nodes: dict[
            tuple[str, str],
            str,
        ] = {}

        created_at = utc_now()

        with self.database.connect() as connection:
            for sequence, atom in enumerate(atoms):
                atom_id = str(atom["id"])
                short_id = atom_id.split("-")[-1][:8]

                authority_node_id = new_id(
                    "node"
                )

                connection.execute(
                    """
                    INSERT INTO graph_nodes(
                        id,
                        graph_id,
                        node_key,
                        node_type,
                        title,
                        status,
                        payload_json,
                        created_at
                    )
                    VALUES(?,?,?,?,?,?,?,?)
                    """,
                    (
                        authority_node_id,
                        authority_graph["id"],
                        f"atom-{short_id}",
                        "ATOM",
                        atom[
                            "canonical_statement"
                        ],
                        "READY",
                        json.dumps(
                            {
                                "atom_id": atom_id,
                                "kind": atom["kind"],
                                "modality": (
                                    atom["modality"]
                                ),
                            },
                            sort_keys=True,
                        ),
                        created_at,
                    ),
                )

                authority_nodes[
                    atom_id
                ] = authority_node_id

                blocked = (
                    open_by_atom.get(
                        atom_id,
                        0,
                    )
                    > 0
                    and not allow_open_research
                )

                for stage_index, stage in enumerate(
                    STAGES
                ):
                    node_id = new_id("node")
                    node_key = (
                        f"{sequence:06d}-"
                        f"{stage.lower()}-"
                        f"{short_id}"
                    )

                    if stage == "CONTRACT":
                        title = (
                            "Specify: "
                            + _sanitize_export_title(
                                atom[
                                    "canonical_statement"
                                ]
                            )
                        )
                    elif stage == "IMPLEMENTATION":
                        title = (
                            "Implement: "
                            + _sanitize_export_title(
                                atom[
                                    "canonical_statement"
                                ]
                            )
                        )
                    else:
                        title = (
                            "Verify: "
                            + _sanitize_export_title(
                                atom[
                                    "canonical_statement"
                                ]
                            )
                        )

                    status = (
                        "BLOCKED"
                        if blocked
                        else "PENDING"
                    )

                    connection.execute(
                        """
                        INSERT INTO graph_nodes(
                            id,
                            graph_id,
                            node_key,
                            node_type,
                            title,
                            status,
                            payload_json,
                            created_at
                        )
                        VALUES(?,?,?,?,?,?,?,?)
                        """,
                        (
                            node_id,
                            execution_graph["id"],
                            node_key,
                            stage,
                            title,
                            status,
                            json.dumps(
                                {
                                    "atom_id": atom_id,
                                    "stage": stage,
                                    "open_dimensions": (
                                        open_by_atom.get(
                                            atom_id,
                                            0,
                                        )
                                    ),
                                },
                                sort_keys=True,
                            ),
                            created_at,
                        ),
                    )

                    execution_nodes[
                        (atom_id, stage)
                    ] = node_id

                self._insert_edge(
                    connection,
                    str(execution_graph["id"]),
                    execution_nodes[
                        (atom_id, "CONTRACT")
                    ],
                    execution_nodes[
                        (
                            atom_id,
                            "IMPLEMENTATION",
                        )
                    ],
                    "MUST_PRECEDE",
                    (
                        "Contract must exist before "
                        "implementation."
                    ),
                )

                self._insert_edge(
                    connection,
                    str(execution_graph["id"]),
                    execution_nodes[
                        (
                            atom_id,
                            "IMPLEMENTATION",
                        )
                    ],
                    execution_nodes[
                        (
                            atom_id,
                            "VERIFICATION",
                        )
                    ],
                    "MUST_PRECEDE",
                    (
                        "Implementation must complete "
                        "before independent verification."
                    ),
                )

            for relation in relations:
                from_atom_id = str(
                    relation["from_atom_id"]
                )
                to_atom_id = str(
                    relation["to_atom_id"]
                )

                self._insert_edge(
                    connection,
                    str(authority_graph["id"]),
                    authority_nodes[
                        from_atom_id
                    ],
                    authority_nodes[
                        to_atom_id
                    ],
                    str(
                        relation[
                            "relation_type"
                        ]
                    ),
                    str(
                        relation["rationale"]
                    ),
                )

                if (
                    relation["relation_type"]
                    == "REQUIRES"
                ):
                    self._insert_edge(
                        connection,
                        str(
                            execution_graph["id"]
                        ),
                        execution_nodes[
                            (
                                to_atom_id,
                                "VERIFICATION",
                            )
                        ],
                        execution_nodes[
                            (
                                from_atom_id,
                                "IMPLEMENTATION",
                            )
                        ],
                        "MUST_PRECEDE",
                        (
                            "Required atom must be "
                            "verified before dependent "
                            "implementation."
                        ),
                    )

            edge_count = connection.execute(
                """
                SELECT COUNT(*)
                FROM graph_edges
                WHERE graph_id = ?
                """,
                (
                    execution_graph["id"],
                ),
            ).fetchone()[0]

            node_count = len(
                execution_nodes
            )
            open_dimension_count = sum(
                open_by_atom.values()
            )

            plan_status = (
                "BLOCKED"
                if (
                    open_dimension_count > 0
                    and not allow_open_research
                )
                else "DRAFT"
            )

            plan_id = new_id("plan")

            connection.execute(
                """
                INSERT INTO plan_versions(
                    id,
                    project_id,
                    authority_graph_id,
                    execution_graph_id,
                    input_fingerprint,
                    status,
                    allow_open_research,
                    atom_count,
                    node_count,
                    edge_count,
                    open_dimension_count,
                    created_at
                )
                VALUES(?,?,?,?,?,?,?,?,?,?,?,?)
                """,
                (
                    plan_id,
                    project_id,
                    authority_graph["id"],
                    execution_graph["id"],
                    fingerprint,
                    plan_status,
                    allow_open_research,
                    len(atoms),
                    node_count,
                    edge_count,
                    open_dimension_count,
                    created_at,
                ),
            )

            for sequence, atom in enumerate(atoms):
                atom_id = str(atom["id"])

                for stage in STAGES:
                    connection.execute(
                        """
                        INSERT INTO plan_node_bindings(
                            id,
                            plan_version_id,
                            graph_node_id,
                            atom_id,
                            stage,
                            sequence_number,
                            created_at
                        )
                        VALUES(?,?,?,?,?,?,?)
                        """,
                        (
                            new_id("binding"),
                            plan_id,
                            execution_nodes[
                                (atom_id, stage)
                            ],
                            atom_id,
                            stage,
                            sequence,
                            created_at,
                        ),
                    )

        self.verify_plan(plan_id)
        return self.get_plan(plan_id)

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
        with self.database.connect() as connection:
            plan = connection.execute(
                """
                SELECT *
                FROM plan_versions
                WHERE id = ?
                """,
                (plan_id,),
            ).fetchone()

            if plan is None:
                raise NotFoundError(
                    f"plan not found: {plan_id}"
                )

            bindings = [
                dict(row)
                for row in connection.execute(
                    """
                    SELECT
                        binding.*,
                        atom.canonical_statement,
                        atom.kind,
                        atom.modality
                    FROM plan_node_bindings
                    AS binding
                    JOIN atoms AS atom
                      ON atom.id =
                         binding.atom_id
                    WHERE
                        binding.plan_version_id = ?
                    ORDER BY
                        binding.sequence_number,
                        CASE binding.stage
                            WHEN 'CONTRACT' THEN 1
                            WHEN 'IMPLEMENTATION'
                                THEN 2
                            WHEN 'VERIFICATION'
                                THEN 3
                            ELSE 4
                        END
                    """,
                    (plan_id,),
                ).fetchall()
            ]

            findings = [
                dict(row)
                for row in connection.execute(
                    """
                    SELECT *
                    FROM
                        plan_verification_findings
                    WHERE plan_version_id = ?
                    ORDER BY
                        severity,
                        code,
                        id
                    """,
                    (plan_id,),
                ).fetchall()
            ]

        result = dict(plan)
        result["allow_open_research"] = bool(
            result["allow_open_research"]
        )
        result["bindings"] = bindings
        result["findings"] = findings
        result["authority_graph"] = (
            self.graphs.get(
                str(
                    result[
                        "authority_graph_id"
                    ]
                )
            )
        )
        result["execution_graph"] = (
            self.graphs.get(
                str(
                    result[
                        "execution_graph_id"
                    ]
                )
            )
        )
        result["ready_nodes"] = (
            self.graphs.ready_nodes(
                str(
                    result[
                        "execution_graph_id"
                    ]
                )
            )
        )

        return result

    def list_plans(
        self,
        project_id: str,
    ) -> list[dict[str, object]]:
        with self.database.connect() as connection:
            self._require_project(
                connection,
                project_id,
            )

            rows = connection.execute(
                """
                SELECT *
                FROM plan_versions
                WHERE project_id = ?
                ORDER BY created_at DESC, id
                """,
                (project_id,),
            ).fetchall()

        results = []

        for row in rows:
            item = dict(row)
            item[
                "allow_open_research"
            ] = bool(
                item[
                    "allow_open_research"
                ]
            )
            results.append(item)

        return results

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

