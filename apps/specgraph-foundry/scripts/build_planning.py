from pathlib import Path
from textwrap import dedent

ROOT = Path.cwd()

if ROOT.name != "specgraph-foundry" or not (ROOT / ".git").is_dir():
    raise SystemExit(f"Wrong repository: {ROOT}")


def write(path: str, content: str) -> None:
    target = ROOT / path
    target.parent.mkdir(parents=True, exist_ok=True)
    target.write_text(
        dedent(content).lstrip(),
        encoding="utf-8",
    )
    print(f"WROTE {path}")


def patch(
    path: str,
    marker: str,
    replacement: str,
    installed_marker: str,
) -> None:
    target = ROOT / path
    content = target.read_text(encoding="utf-8")

    if installed_marker in content:
        print(f"SKIPPED {path}")
        return

    if marker not in content:
        raise SystemExit(
            f"PATCH MARKER NOT FOUND IN {path}:\n{marker}"
        )

    target.write_text(
        content.replace(marker, replacement, 1),
        encoding="utf-8",
    )
    print(f"UPDATED {path}")


write(
    "src/specgraph_foundry/planning.py",
    r'''
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
        return f"{prefix}-{uuid.uuid4()}"


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
                            int(inferred),
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
                        int(
                            allow_open_research
                        ),
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
                        int(
                            allow_open_research
                        ),
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
    ''',
)

write(
    "tests/test_planning.py",
    r'''
    import tempfile
    import unittest
    from pathlib import Path

    from specgraph_foundry.atoms import (
        AtomService,
    )
    from specgraph_foundry.database import (
        Database,
    )
    from specgraph_foundry.errors import (
        ValidationError,
    )
    from specgraph_foundry.ingestion import (
        IngestionService,
    )
    from specgraph_foundry.planning import (
        PlanningService,
    )
    from specgraph_foundry.services import (
        ProjectService,
    )


    class PlanningTest(unittest.TestCase):
        def setUp(self) -> None:
            self.temp = (
                tempfile.TemporaryDirectory()
            )

            self.database = Database(
                Path(self.temp.name)
                / "test.sqlite3"
            )
            self.database.initialize()

            self.projects = ProjectService(
                self.database
            )
            self.ingestion = (
                IngestionService(
                    self.database
                )
            )
            self.atoms = AtomService(
                self.database
            )
            self.planning = (
                PlanningService(
                    self.database
                )
            )

            self.project = (
                self.projects.create(
                    "planning-test",
                    "Planning Test",
                )
            )

            document = (
                self.ingestion.ingest_text(
                    project_id=str(
                        self.project["id"]
                    ),
                    title="Authority",
                    content=(
                        "The schema must exist.\n"
                        "The API must use the schema.\n"
                    ),
                    chunk_bytes=32,
                )
            )

            extraction = (
                self.atoms.extract_document(
                    str(document["id"])
                )
            )

            self.atom_a = extraction[
                "atoms"
            ][0]

            self.atom_b = extraction[
                "atoms"
            ][1]

        def tearDown(self) -> None:
            self.temp.cleanup()

        def test_plan_has_three_stages_per_atom(
            self,
        ) -> None:
            plan = self.planning.synthesize(
                str(self.project["id"]),
                allow_open_research=True,
            )

            self.assertEqual(
                plan["atom_count"],
                2,
            )

            self.assertEqual(
                plan["node_count"],
                6,
            )

            self.assertEqual(
                len(plan["bindings"]),
                6,
            )

            self.assertEqual(
                plan["status"],
                "VERIFIED",
            )

            self.assertEqual(
                len(plan["ready_nodes"]),
                2,
            )

        def test_open_research_blocks_plan(
            self,
        ) -> None:
            plan = self.planning.synthesize(
                str(self.project["id"]),
                allow_open_research=False,
            )

            self.assertEqual(
                plan["status"],
                "BLOCKED",
            )

            self.assertGreater(
                plan[
                    "open_dimension_count"
                ],
                0,
            )

            self.assertEqual(
                plan["ready_nodes"],
                [],
            )

        def test_requires_relation_orders_atoms(
            self,
        ) -> None:
            self.planning.add_relation(
                project_id=str(
                    self.project["id"]
                ),
                from_atom_id=str(
                    self.atom_b["id"]
                ),
                to_atom_id=str(
                    self.atom_a["id"]
                ),
                relation_type="REQUIRES",
                rationale=(
                    "The API depends on the schema."
                ),
            )

            plan = self.planning.synthesize(
                str(self.project["id"]),
                allow_open_research=True,
            )

            bindings = {
                (
                    str(binding["atom_id"]),
                    str(binding["stage"]),
                ): str(
                    binding["graph_node_id"]
                )
                for binding
                in plan["bindings"]
            }

            expected_source = bindings[
                (
                    str(self.atom_a["id"]),
                    "VERIFICATION",
                )
            ]

            expected_target = bindings[
                (
                    str(self.atom_b["id"]),
                    "IMPLEMENTATION",
                )
            ]

            edges = plan[
                "execution_graph"
            ]["edges"]

            self.assertTrue(
                any(
                    edge["from_node_id"]
                    == expected_source
                    and edge["to_node_id"]
                    == expected_target
                    for edge in edges
                )
            )

        def test_dependency_cycle_rejected(
            self,
        ) -> None:
            project_id = str(
                self.project["id"]
            )

            self.planning.add_relation(
                project_id,
                str(self.atom_a["id"]),
                str(self.atom_b["id"]),
                "REQUIRES",
            )

            self.planning.add_relation(
                project_id,
                str(self.atom_b["id"]),
                str(self.atom_a["id"]),
                "REQUIRES",
            )

            with self.assertRaises(
                ValidationError
            ):
                self.planning.synthesize(
                    project_id,
                    allow_open_research=True,
                )

        def test_synthesis_is_idempotent(
            self,
        ) -> None:
            first = self.planning.synthesize(
                str(self.project["id"]),
                allow_open_research=True,
            )

            second = self.planning.synthesize(
                str(self.project["id"]),
                allow_open_research=True,
            )

            self.assertEqual(
                first["id"],
                second["id"],
            )


    if __name__ == "__main__":
        unittest.main()
    ''',
)

write(
    "supabase/migrations/20260712000500_planning.sql",
    r'''
    create table if not exists public.authority_relations (
        id uuid primary key default gen_random_uuid(),
        project_id uuid not null
            references public.projects(id)
            on delete cascade,
        from_atom_id uuid not null
            references public.atoms(id)
            on delete cascade,
        to_atom_id uuid not null
            references public.atoms(id)
            on delete cascade,
        relation_type text not null,
        rationale text not null default '',
        confidence double precision not null,
        inferred boolean not null default false,
        created_at timestamptz not null default now(),
        check(from_atom_id <> to_atom_id),
        check(confidence >= 0.0 and confidence <= 1.0),
        unique(
            project_id,
            from_atom_id,
            to_atom_id,
            relation_type
        )
    );

    create table if not exists public.plan_versions (
        id uuid primary key default gen_random_uuid(),
        project_id uuid not null
            references public.projects(id)
            on delete cascade,
        authority_graph_id uuid not null
            references public.graphs(id)
            on delete cascade,
        execution_graph_id uuid not null
            references public.graphs(id)
            on delete cascade,
        input_fingerprint text not null,
        status text not null,
        allow_open_research boolean not null default false,
        atom_count bigint not null,
        node_count bigint not null,
        edge_count bigint not null,
        open_dimension_count bigint not null,
        created_at timestamptz not null default now(),
        verified_at timestamptz,
        unique(
            project_id,
            input_fingerprint,
            allow_open_research
        )
    );

    create table if not exists public.plan_node_bindings (
        id uuid primary key default gen_random_uuid(),
        plan_version_id uuid not null
            references public.plan_versions(id)
            on delete cascade,
        graph_node_id uuid not null
            references public.graph_nodes(id)
            on delete cascade,
        atom_id uuid not null
            references public.atoms(id)
            on delete cascade,
        stage text not null,
        sequence_number bigint not null,
        created_at timestamptz not null default now(),
        unique(
            plan_version_id,
            atom_id,
            stage
        ),
        unique(
            plan_version_id,
            graph_node_id
        )
    );

    create table if not exists public.plan_verification_findings (
        id uuid primary key default gen_random_uuid(),
        plan_version_id uuid not null
            references public.plan_versions(id)
            on delete cascade,
        severity text not null,
        code text not null,
        message text not null,
        entity_id text,
        created_at timestamptz not null default now()
    );

    create index if not exists idx_authority_relations_project
        on public.authority_relations(
            project_id,
            relation_type
        );

    create index if not exists idx_plan_versions_project
        on public.plan_versions(
            project_id,
            created_at
        );

    create index if not exists idx_plan_bindings_plan
        on public.plan_node_bindings(
            plan_version_id,
            sequence_number
        );

    alter table public.authority_relations
        enable row level security;

    alter table public.plan_versions
        enable row level security;

    alter table public.plan_node_bindings
        enable row level security;

    alter table public.plan_verification_findings
        enable row level security;
    ''',
)

patch(
    "src/specgraph_foundry/api.py",
    "from .research import ResearchService\n"
    "from .services import ProjectService\n",
    "from .research import ResearchService\n"
    "from .planning import PlanningService\n"
    "from .services import ProjectService\n",
    "from .planning import PlanningService",
)

patch(
    "src/specgraph_foundry/api.py",
    "        self.research = ResearchService(database)\n",
    "        self.research = ResearchService(database)\n"
    "        self.planning = PlanningService(database)\n",
    "self.planning = PlanningService",
)

api_routes = r'''
                if (
                    len(parts) == 4
                    and parts[:2] == ["v1", "projects"]
                    and parts[3] == "relations"
                ):
                    if method == "GET":
                        return 200, {
                            "items": (
                                self.planning
                                .list_relations(
                                    parts[2]
                                )
                            )
                        }

                    if method == "POST":
                        return 201, (
                            self.planning.add_relation(
                                project_id=parts[2],
                                from_atom_id=str(
                                    payload.get(
                                        "from_atom_id",
                                        "",
                                    )
                                ),
                                to_atom_id=str(
                                    payload.get(
                                        "to_atom_id",
                                        "",
                                    )
                                ),
                                relation_type=str(
                                    payload.get(
                                        "relation_type",
                                        "",
                                    )
                                ),
                                rationale=str(
                                    payload.get(
                                        "rationale",
                                        "",
                                    )
                                ),
                                confidence=float(
                                    payload.get(
                                        "confidence",
                                        1.0,
                                    )
                                ),
                                inferred=bool(
                                    payload.get(
                                        "inferred",
                                        False,
                                    )
                                ),
                            )
                        )

                if (
                    len(parts) == 4
                    and parts[:2] == ["v1", "projects"]
                    and parts[3] == "plans"
                ):
                    if method == "GET":
                        return 200, {
                            "items": (
                                self.planning.list_plans(
                                    parts[2]
                                )
                            )
                        }

                    if method == "POST":
                        return 201, (
                            self.planning.synthesize(
                                project_id=parts[2],
                                allow_open_research=bool(
                                    payload.get(
                                        "allow_open_research",
                                        False,
                                    )
                                ),
                            )
                        )

                if (
                    len(parts) == 3
                    and parts[:2] == ["v1", "plans"]
                    and method == "GET"
                ):
                    return 200, (
                        self.planning.get_plan(
                            parts[2]
                        )
                    )

                if (
                    len(parts) == 4
                    and parts[:2] == ["v1", "plans"]
                    and parts[3] == "verify"
                    and method == "POST"
                ):
                    return 200, (
                        self.planning.verify_plan(
                            parts[2]
                        )
                    )

'''

patch(
    "src/specgraph_foundry/api.py",
    "                return 404, {\n"
    '                    "error": "ROUTE_NOT_FOUND",\n',
    api_routes
    + "                return 404, {\n"
      '                    "error": "ROUTE_NOT_FOUND",\n',
    'parts[3] == "relations"',
)

patch(
    "src/specgraph_foundry/cli.py",
    "from .research import ResearchService\n"
    "from .services import GraphService, ProjectService\n",
    "from .research import ResearchService\n"
    "from .planning import PlanningService\n"
    "from .services import GraphService, ProjectService\n",
    "from .planning import PlanningService",
)

cli_parsers = r'''
        relation = commands.add_parser(
            "add-relation"
        )
        relation.add_argument("project_id")
        relation.add_argument("from_atom_id")
        relation.add_argument("to_atom_id")
        relation.add_argument("relation_type")
        relation.add_argument(
            "--rationale",
            default="",
        )
        relation.add_argument(
            "--confidence",
            type=float,
            default=1.0,
        )
        relation.add_argument(
            "--inferred",
            action="store_true",
        )

        relations = commands.add_parser(
            "list-relations"
        )
        relations.add_argument("project_id")

        synthesize = commands.add_parser(
            "synthesize-plan"
        )
        synthesize.add_argument("project_id")
        synthesize.add_argument(
            "--allow-open-research",
            action="store_true",
        )

        plans = commands.add_parser(
            "list-plans"
        )
        plans.add_argument("project_id")

        plan = commands.add_parser("plan")
        plan.add_argument("plan_id")

        verify_plan = commands.add_parser(
            "verify-plan"
        )
        verify_plan.add_argument("plan_id")

'''

patch(
    "src/specgraph_foundry/cli.py",
    '        server = commands.add_parser("serve")\n',
    cli_parsers
    + '        server = commands.add_parser("serve")\n',
    '"synthesize-plan"',
)

patch(
    "src/specgraph_foundry/cli.py",
    "        research = ResearchService(database)\n"
    "        graphs = GraphService(database)\n",
    "        research = ResearchService(database)\n"
    "        planning = PlanningService(database)\n"
    "        graphs = GraphService(database)\n",
    "planning = PlanningService",
)

cli_commands = r'''
        if args.command == "add-relation":
            output(
                planning.add_relation(
                    project_id=args.project_id,
                    from_atom_id=(
                        args.from_atom_id
                    ),
                    to_atom_id=args.to_atom_id,
                    relation_type=(
                        args.relation_type
                    ),
                    rationale=args.rationale,
                    confidence=args.confidence,
                    inferred=args.inferred,
                )
            )
            return 0

        if args.command == "list-relations":
            output(
                {
                    "items": (
                        planning.list_relations(
                            args.project_id
                        )
                    )
                }
            )
            return 0

        if args.command == "synthesize-plan":
            output(
                planning.synthesize(
                    project_id=args.project_id,
                    allow_open_research=(
                        args.allow_open_research
                    ),
                )
            )
            return 0

        if args.command == "list-plans":
            output(
                {
                    "items": (
                        planning.list_plans(
                            args.project_id
                        )
                    )
                }
            )
            return 0

        if args.command == "plan":
            output(
                planning.get_plan(
                    args.plan_id
                )
            )
            return 0

        if args.command == "verify-plan":
            output(
                planning.verify_plan(
                    args.plan_id
                )
            )
            return 0

'''

patch(
    "src/specgraph_foundry/cli.py",
    "        suffix = uuid.uuid4().hex[:8]\n",
    cli_commands
    + "        suffix = uuid.uuid4().hex[:8]\n",
    'args.command == "synthesize-plan"',
)

readme = ROOT / "README.md"
content = readme.read_text(encoding="utf-8")

section = dedent(
    r'''

    ## Authority and execution planning

    The planning backend now provides:

    - typed atom relationships;
    - dependency-cycle rejection;
    - authority graph generation;
    - three-stage execution nodes per atom;
    - contract-before-implementation gates;
    - implementation-before-verification gates;
    - cross-atom dependency ordering;
    - unresolved-research blocking;
    - deterministic plan fingerprints;
    - idempotent plan synthesis;
    - independent structural verification;
    - stored verification findings.

    ```bash
    python -m specgraph_foundry add-relation \
      PROJECT_ID \
      DEPENDENT_ATOM_ID \
      REQUIRED_ATOM_ID \
      REQUIRES

    python -m specgraph_foundry synthesize-plan \
      PROJECT_ID

    python -m specgraph_foundry verify-plan \
      PLAN_ID
    ```
    '''
)

if "## Authority and execution planning" not in content:
    readme.write_text(
        content.rstrip()
        + "\n"
        + section.lstrip(),
        encoding="utf-8",
    )
    print("UPDATED README.md")

print("PLANNING BACKEND CREATED")
