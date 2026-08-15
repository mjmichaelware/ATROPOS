"""Acyclicity: refusing an edge that would close a loop.

The rule that makes an execution graph executable at all. Its own module because
it is pure graph reasoning -- it has no opinion about plans, atoms or stages, and
mixing it into the synthesizer made both harder to read.
"""

from __future__ import annotations

from collections import defaultdict
from collections import deque
import json
import sqlite3

from .database import Database
from .errors import ConflictError, NotFoundError, ValidationError
from .primitives import new_id, utc_now



def validate_dependency_acyclic(
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


def graph_has_cycle(
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


def insert_edge(
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
