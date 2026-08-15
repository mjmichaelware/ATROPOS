"""Synthesizing an execution plan from an authority graph.

Turns atoms and their relations into a graph of work: three staged nodes per
atom, MUST_PRECEDE edges between them, and edges between atoms wherever a
relation says one must come first.

The largest single job in planning, and the one with the most inputs -- it reads
the project, the atoms, their open research, and every relation, then writes two
graphs and the bindings between them. Its own module so that reading "how is a
plan built" does not mean reading how one is validated, verified and queried as
well.
"""

from __future__ import annotations

import json
import sqlite3
from collections import defaultdict

from .database import Database
from .errors import ConflictError, NotFoundError, ValidationError
from .export_titles import sanitize_export_title
from .plan_node_creation import create_atom_nodes
from .plan_graph_rules import insert_edge, validate_dependency_acyclic
from .plan_guards import (
    existing_plan,
    fingerprint as compute_fingerprint,
    require_project,
)
from .plan_queries import get_plan
from .plan_verification import verify_plan
from .primitives import new_id, utc_now
from .stages import STAGES


def synthesize(
    database: Database,
    graphs: object,
    project_id: str,
    allow_open_research: bool = False,
) -> dict[str, object]:
    with database.connect() as connection:
        require_project(
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

    validate_dependency_acyclic(
        atoms,
        dependency_relations,
    )

    fingerprint = compute_fingerprint(
        atoms,
        relations,
        dimensions,
    )

    existing = existing_plan(database, 
        project_id,
        fingerprint,
        allow_open_research,
    )

    if existing is not None:
        return get_plan(database, graphs, 
            str(existing["id"])
        )

    authority_graph = graphs.create(
        project_id,
        "Authority Graph",
        "AUTHORITY",
        False,
    )

    execution_graph = graphs.create(
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

    with database.connect() as connection:
        # Node creation lives in plan_node_creation; what remains here is
        # choosing what goes into the plan.
        create_atom_nodes(
            connection,
            atoms,
            authority_graph,
            execution_graph,
            authority_nodes,
            execution_nodes,
            open_by_atom,
            allow_open_research,
            created_at,
        )

        for relation in relations:
            from_atom_id = str(
                relation["from_atom_id"]
            )
            to_atom_id = str(
                relation["to_atom_id"]
            )

            insert_edge(
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
                insert_edge(
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

    verify_plan(database, plan_id)
    return get_plan(database, graphs, plan_id)
