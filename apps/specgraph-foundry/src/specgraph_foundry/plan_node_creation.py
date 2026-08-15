"""Creating the nodes a plan is made of.

One authority node per atom, and three execution nodes per atom -- CONTRACT,
IMPLEMENTATION, VERIFICATION -- joined by MUST_PRECEDE edges so a stage cannot
start before the one it depends on.

Its own module because this is where the *shape* of a plan is decided, while
:mod:`plan_synthesis` decides what goes into one. A node blocked here for open
research stays blocked all the way to execution, so the rule lives somewhere it
can be read on its own.

Fills `authority_nodes` and `execution_nodes` in place; the caller needs both to
add the relation edges afterwards.
"""

from __future__ import annotations

import json
import sqlite3

from .export_titles import sanitize_export_title
from .plan_graph_rules import insert_edge
from .primitives import new_id
from .stages import STAGES


def create_atom_nodes(
    connection: sqlite3.Connection,
    atoms: list[dict[str, object]],
    authority_graph: dict[str, object],
    execution_graph: dict[str, object],
    authority_nodes: dict[str, str],
    execution_nodes: dict[tuple[str, str], str],
    open_by_atom: dict[str, int],
    allow_open_research: bool,
    created_at: str,
) -> None:
    """Creates every node for every atom, in the plan's own order."""
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
                    + sanitize_export_title(
                        atom[
                            "canonical_statement"
                        ]
                    )
                )
            elif stage == "IMPLEMENTATION":
                title = (
                    "Implement: "
                    + sanitize_export_title(
                        atom[
                            "canonical_statement"
                        ]
                    )
                )
            else:
                title = (
                    "Verify: "
                    + sanitize_export_title(
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

        insert_edge(
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

        insert_edge(
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
