import hashlib
import json
import sqlite3
import uuid
from collections import defaultdict, deque
from datetime import UTC, datetime

from .database import Database
from .errors import (
    ConflictError,
    NotFoundError,
    ValidationError,
)
from .services import GraphService


PLANNING_SCHEMA = """
PRAGMA foreign_keys = ON;

CREATE TABLE IF NOT EXISTS authority_relations (
    id TEXT PRIMARY KEY,
    project_id TEXT NOT NULL
        REFERENCES projects(id)
        ON DELETE CASCADE,
    from_atom_id TEXT NOT NULL
        REFERENCES atoms(id)
        ON DELETE CASCADE,
    to_atom_id TEXT NOT NULL
        REFERENCES atoms(id)
        ON DELETE CASCADE,
    relation_type TEXT NOT NULL,
    rationale TEXT NOT NULL DEFAULT '',
    confidence REAL NOT NULL,
    inferred INTEGER NOT NULL DEFAULT 0,
    created_at TEXT NOT NULL,
    CHECK(from_atom_id <> to_atom_id),
    CHECK(confidence >= 0.0 AND confidence <= 1.0),
    UNIQUE(
        project_id,
        from_atom_id,
        to_atom_id,
        relation_type
    )
);

CREATE TABLE IF NOT EXISTS plan_versions (
    id TEXT PRIMARY KEY,
    project_id TEXT NOT NULL
        REFERENCES projects(id)
        ON DELETE CASCADE,
    authority_graph_id TEXT NOT NULL
        REFERENCES graphs(id)
        ON DELETE CASCADE,
    execution_graph_id TEXT NOT NULL
        REFERENCES graphs(id)
        ON DELETE CASCADE,
    input_fingerprint TEXT NOT NULL,
    status TEXT NOT NULL,
    allow_open_research INTEGER NOT NULL DEFAULT 0,
    atom_count INTEGER NOT NULL,
    node_count INTEGER NOT NULL,
    edge_count INTEGER NOT NULL,
    open_dimension_count INTEGER NOT NULL,
    created_at TEXT NOT NULL,
    verified_at TEXT,
    UNIQUE(
        project_id,
        input_fingerprint,
        allow_open_research
    )
);

CREATE TABLE IF NOT EXISTS plan_node_bindings (
    id TEXT PRIMARY KEY,
    plan_version_id TEXT NOT NULL
        REFERENCES plan_versions(id)
        ON DELETE CASCADE,
    graph_node_id TEXT NOT NULL
        REFERENCES graph_nodes(id)
        ON DELETE CASCADE,
    atom_id TEXT NOT NULL
        REFERENCES atoms(id)
        ON DELETE CASCADE,
    stage TEXT NOT NULL,
    sequence_number INTEGER NOT NULL,
    created_at TEXT NOT NULL,
    UNIQUE(plan_version_id, atom_id, stage),
    UNIQUE(plan_version_id, graph_node_id)
);

CREATE TABLE IF NOT EXISTS plan_verification_findings (
    id TEXT PRIMARY KEY,
    plan_version_id TEXT NOT NULL
        REFERENCES plan_versions(id)
        ON DELETE CASCADE,
    severity TEXT NOT NULL,
    code TEXT NOT NULL,
    message TEXT NOT NULL,
    entity_id TEXT,
    created_at TEXT NOT NULL
);

CREATE INDEX IF NOT EXISTS idx_authority_relations_project
    ON authority_relations(
        project_id,
        relation_type
    );

CREATE INDEX IF NOT EXISTS idx_plan_versions_project
    ON plan_versions(
        project_id,
        created_at
    );

CREATE INDEX IF NOT EXISTS idx_plan_bindings_plan
    ON plan_node_bindings(
        plan_version_id,
        sequence_number
    );

CREATE INDEX IF NOT EXISTS idx_plan_findings_plan
    ON plan_verification_findings(
        plan_version_id,
        severity
    );
"""


RELATION_TYPES = {
    "REQUIRES",
    "REFINES",
    "CONFLICTS_WITH",
    "DUPLICATES",
    "IMPLEMENTS",
    "VERIFIES",
    "RELATES_TO",
}

STAGES = (
    "CONTRACT",
    "IMPLEMENTATION",
    "VERIFICATION",
)


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
        relation_type = relation_type.strip().upper()

        if relation_type not in RELATION_TYPES:
            raise ValidationError(
                f"invalid relation type: {relation_type}"
            )

        if from_atom_id == to_atom_id:
            raise ValidationError(
                "authority relation cannot reference "
                "the same atom twice"
            )

        if not 0.0 <= confidence <= 1.0:
            raise ValidationError(
                "confidence must be between 0 and 1"
            )

        relation_id = new_id("relation")

        try:
            with self.database.connect() as connection:
                self._require_project(
                    connection,
                    project_id,
                )

                self._require_atom(
                    connection,
                    project_id,
                    from_atom_id,
                )

                self._require_atom(
                    connection,
                    project_id,
                    to_atom_id,
                )

                connection.execute(
                    """
                    INSERT INTO authority_relations(
                        id,
                        project_id,
                        from_atom_id,
                        to_atom_id,
                        relation_type,
                        rationale,
                        confidence,
                        inferred,
                        created_at
                    )
                    VALUES(?,?,?,?,?,?,?,?,?)
                    """,
                    (
                        relation_id,
                        project_id,
                        from_atom_id,
                        to_atom_id,
                        relation_type,
                        rationale.strip(),
                        confidence,
                        inferred,
                        utc_now(),
                    ),
                )

        except sqlite3.IntegrityError as error:
            raise ConflictError(
                "authority relation already exists"
            ) from error

        return self.get_relation(relation_id)

    def get_relation(
        self,
        relation_id: str,
    ) -> dict[str, object]:
        with self.database.connect() as connection:
            row = connection.execute(
                """
                SELECT *
                FROM authority_relations
                WHERE id = ?
                """,
                (relation_id,),
            ).fetchone()

        if row is None:
            raise NotFoundError(
                f"relation not found: {relation_id}"
            )

        result = dict(row)
        result["inferred"] = bool(
            result["inferred"]
        )
        return result

    def list_relations(
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
                FROM authority_relations
                WHERE project_id = ?
                ORDER BY
                    relation_type,
                    created_at,
                    id
                """,
                (project_id,),
            ).fetchall()

        results = []

        for row in rows:
            item = dict(row)
            item["inferred"] = bool(
                item["inferred"]
            )
            results.append(item)

        return results

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
        parameters: list[object] = [project_id]
        predicate = ""

        if boundary is not None:
            predicate = """
                AND (
                    relation_type > ?
                    OR (
                        relation_type = ?
                        AND (
                            created_at > ?
                            OR (
                                created_at = ?
                                AND id > ?
                            )
                        )
                    )
                )
            """
            relation_type = str(
                boundary.get("relation_type", "")
            )
            created_at = str(
                boundary.get("created_at", "")
            )
            parameters.extend(
                [
                    relation_type,
                    relation_type,
                    created_at,
                    created_at,
                    str(boundary.get("id", "")),
                ]
            )

        parameters.append(limit + 1)

        with self.database.connect() as connection:
            self._require_project(
                connection,
                project_id,
            )

            rows = connection.execute(
                f"""
                SELECT *
                FROM authority_relations
                WHERE project_id = ?
                {predicate}
                ORDER BY
                    relation_type,
                    created_at,
                    id
                LIMIT ?
                """,
                tuple(parameters),
            ).fetchall()

        items = []

        for row in rows[:limit]:
            item = dict(row)
            item["inferred"] = bool(
                item["inferred"]
            )
            items.append(item)

        has_more = len(rows) > limit
        boundary_item = (
            {
                "relation_type": str(
                    items[-1]["relation_type"]
                ),
                "created_at": str(
                    items[-1]["created_at"]
                ),
                "id": str(items[-1]["id"]),
            }
            if items and has_more
            else None
        )

        return (
            items,
            has_more,
            boundary_item,
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
                            + str(
                                atom[
                                    "canonical_statement"
                                ]
                            )
                        )
                    elif stage == "IMPLEMENTATION":
                        title = (
                            "Implement: "
                            + str(
                                atom[
                                    "canonical_statement"
                                ]
                            )
                        )
                    else:
                        title = (
                            "Verify: "
                            + str(
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

            nodes = [
                dict(row)
                for row in connection.execute(
                    """
                    SELECT *
                    FROM graph_nodes
                    WHERE graph_id = ?
                    ORDER BY node_key
                    """,
                    (
                        plan[
                            "execution_graph_id"
                        ],
                    ),
                ).fetchall()
            ]

            edges = [
                dict(row)
                for row in connection.execute(
                    """
                    SELECT *
                    FROM graph_edges
                    WHERE graph_id = ?
                    ORDER BY id
                    """,
                    (
                        plan[
                            "execution_graph_id"
                        ],
                    ),
                ).fetchall()
            ]

            bindings = [
                dict(row)
                for row in connection.execute(
                    """
                    SELECT *
                    FROM plan_node_bindings
                    WHERE plan_version_id = ?
                    ORDER BY
                        sequence_number,
                        stage
                    """,
                    (plan_id,),
                ).fetchall()
            ]

            connection.execute(
                """
                DELETE FROM
                    plan_verification_findings
                WHERE plan_version_id = ?
                """,
                (plan_id,),
            )

            findings: list[
                dict[str, object]
            ] = []

            node_ids = {
                str(node["id"])
                for node in nodes
            }

            if len(node_ids) != int(
                plan["node_count"]
            ):
                findings.append(
                    {
                        "severity": "ERROR",
                        "code": (
                            "NODE_COUNT_MISMATCH"
                        ),
                        "message": (
                            "Stored plan node count does "
                            "not match execution graph."
                        ),
                        "entity_id": plan_id,
                    }
                )

            if len(bindings) != len(nodes):
                findings.append(
                    {
                        "severity": "ERROR",
                        "code": (
                            "UNBOUND_EXECUTION_NODE"
                        ),
                        "message": (
                            "Every execution node must "
                            "have exactly one atom binding."
                        ),
                        "entity_id": plan_id,
                    }
                )

            binding_stages: dict[
                str,
                set[str],
            ] = defaultdict(set)

            for binding in bindings:
                binding_stages[
                    str(binding["atom_id"])
                ].add(
                    str(binding["stage"])
                )

                if (
                    str(
                        binding[
                            "graph_node_id"
                        ]
                    )
                    not in node_ids
                ):
                    findings.append(
                        {
                            "severity": "ERROR",
                            "code": (
                                "BINDING_NODE_MISSING"
                            ),
                            "message": (
                                "Plan binding references "
                                "a missing graph node."
                            ),
                            "entity_id": str(
                                binding["id"]
                            ),
                        }
                    )

            for atom_id, stages in (
                binding_stages.items()
            ):
                if stages != set(STAGES):
                    findings.append(
                        {
                            "severity": "ERROR",
                            "code": (
                                "ATOM_STAGE_INCOMPLETE"
                            ),
                            "message": (
                                "Every atom requires "
                                "contract, implementation, "
                                "and verification stages."
                            ),
                            "entity_id": atom_id,
                        }
                    )

            if self._graph_has_cycle(
                node_ids,
                edges,
            ):
                findings.append(
                    {
                        "severity": "ERROR",
                        "code": (
                            "EXECUTION_GRAPH_CYCLE"
                        ),
                        "message": (
                            "Execution graph contains "
                            "a dependency cycle."
                        ),
                        "entity_id": str(
                            plan[
                                "execution_graph_id"
                            ]
                        ),
                    }
                )

            if int(
                plan["open_dimension_count"]
            ) > 0:
                findings.append(
                    {
                        "severity": "WARNING",
                        "code": (
                            "OPEN_RESEARCH_DIMENSIONS"
                        ),
                        "message": (
                            "Plan contains atoms with "
                            "unresolved research dimensions."
                        ),
                        "entity_id": plan_id,
                    }
                )

            for finding in findings:
                connection.execute(
                    """
                    INSERT INTO
                        plan_verification_findings(
                            id,
                            plan_version_id,
                            severity,
                            code,
                            message,
                            entity_id,
                            created_at
                        )
                    VALUES(?,?,?,?,?,?,?)
                    """,
                    (
                        new_id("finding"),
                        plan_id,
                        finding["severity"],
                        finding["code"],
                        finding["message"],
                        finding["entity_id"],
                        utc_now(),
                    ),
                )

            error_count = sum(
                finding["severity"] == "ERROR"
                for finding in findings
            )

            if error_count:
                status = "INVALID"
            elif (
                int(
                    plan[
                        "open_dimension_count"
                    ]
                )
                > 0
                and not bool(
                    plan[
                        "allow_open_research"
                    ]
                )
            ):
                status = "BLOCKED"
            else:
                status = "VERIFIED"

            verified_at = utc_now()

            connection.execute(
                """
                UPDATE plan_versions
                SET status = ?,
                    verified_at = ?
                WHERE id = ?
                """,
                (
                    status,
                    verified_at,
                    plan_id,
                ),
            )

        return {
            "plan_id": plan_id,
            "status": status,
            "error_count": error_count,
            "finding_count": len(findings),
            "findings": findings,
            "verified_at": verified_at,
        }

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
        with self.database.connect() as connection:
            return connection.execute(
                """
                SELECT *
                FROM plan_versions
                WHERE project_id = ?
                  AND input_fingerprint = ?
                  AND allow_open_research = ?
                """,
                (
                    project_id,
                    fingerprint,
                    allow_open_research,
                ),
            ).fetchone()

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
        payload = {
            "atoms": atoms,
            "relations": relations,
            "dimensions": dimensions,
        }

        encoded = json.dumps(
            payload,
            sort_keys=True,
            separators=(",", ":"),
        ).encode("utf-8")

        return hashlib.sha256(
            encoded
        ).hexdigest()

    @staticmethod
    def _validate_dependency_acyclic(
        atoms: list[dict[str, object]],
        relations: list[
            dict[str, object]
        ],
    ) -> None:
        node_ids = {
            str(atom["id"])
            for atom in atoms
        }

        adjacency: dict[
            str,
            set[str],
        ] = defaultdict(set)

        indegree = {
            atom_id: 0
            for atom_id in node_ids
        }

        for relation in relations:
            dependent = str(
                relation["from_atom_id"]
            )
            required = str(
                relation["to_atom_id"]
            )

            if dependent not in node_ids:
                continue

            if required not in node_ids:
                continue

            if dependent not in adjacency[
                required
            ]:
                adjacency[required].add(
                    dependent
                )
                indegree[dependent] += 1

        queue = deque(
            sorted(
                atom_id
                for atom_id, count
                in indegree.items()
                if count == 0
            )
        )

        visited = 0

        while queue:
            current = queue.popleft()
            visited += 1

            for target in sorted(
                adjacency.get(
                    current,
                    set(),
                )
            ):
                indegree[target] -= 1

                if indegree[target] == 0:
                    queue.append(target)

        if visited != len(node_ids):
            raise ValidationError(
                "REQUIRES relations contain "
                "a dependency cycle"
            )

    @staticmethod
    def _graph_has_cycle(
        node_ids: set[str],
        edges: list[dict[str, object]],
    ) -> bool:
        adjacency: dict[
            str,
            set[str],
        ] = defaultdict(set)

        indegree = {
            node_id: 0
            for node_id in node_ids
        }

        for edge in edges:
            source = str(
                edge["from_node_id"]
            )
            target = str(
                edge["to_node_id"]
            )

            if (
                source not in node_ids
                or target not in node_ids
            ):
                return True

            if target not in adjacency[source]:
                adjacency[source].add(target)
                indegree[target] += 1

        queue = deque(
            node_id
            for node_id, count
            in indegree.items()
            if count == 0
        )

        visited = 0

        while queue:
            current = queue.popleft()
            visited += 1

            for target in adjacency.get(
                current,
                set(),
            ):
                indegree[target] -= 1

                if indegree[target] == 0:
                    queue.append(target)

        return visited != len(node_ids)

    @staticmethod
    def _insert_edge(
        connection: sqlite3.Connection,
        graph_id: str,
        from_node_id: str,
        to_node_id: str,
        edge_type: str,
        rationale: str,
    ) -> None:
        connection.execute(
            """
            INSERT INTO graph_edges(
                id,
                graph_id,
                from_node_id,
                to_node_id,
                edge_type,
                rationale,
                created_at
            )
            VALUES(?,?,?,?,?,?,?)
            """,
            (
                new_id("edge"),
                graph_id,
                from_node_id,
                to_node_id,
                edge_type,
                rationale,
                utc_now(),
            ),
        )

    @staticmethod
    def _require_project(
        connection: sqlite3.Connection,
        project_id: str,
    ) -> None:
        row = connection.execute(
            """
            SELECT id
            FROM projects
            WHERE id = ?
            """,
            (project_id,),
        ).fetchone()

        if row is None:
            raise NotFoundError(
                f"project not found: {project_id}"
            )

    @staticmethod
    def _require_atom(
        connection: sqlite3.Connection,
        project_id: str,
        atom_id: str,
    ) -> None:
        row = connection.execute(
            """
            SELECT id
            FROM atoms
            WHERE id = ?
              AND project_id = ?
            """,
            (
                atom_id,
                project_id,
            ),
        ).fetchone()

        if row is None:
            raise ValidationError(
                f"atom does not belong to "
                f"project: {atom_id}"
            )
