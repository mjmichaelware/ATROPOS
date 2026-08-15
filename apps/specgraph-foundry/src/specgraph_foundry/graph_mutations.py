"""Creating a graph and adding to it.

Every write, including the acyclicity check that refuses an edge closing a
loop. That check lives with the writes because it is the only thing standing
between a valid graph and one that cannot be executed.
"""

from __future__ import annotations

from .graph_guards import require_graph
from .graph_guards import require_node
from .errors import NotFoundError
from .errors import ValidationError
from .routing import new_id
from .routing import utc_now
from collections import defaultdict
import sqlite3
import json


def create(
    service,
    project_id: str,
    name: str,
    kind: str,
    enforce_acyclic: bool,
) -> dict[str, object]:
    if kind not in service.GRAPH_KINDS:
        raise ValidationError(
            f"invalid graph kind: {kind}"
        )

    graph_id = new_id("graph")

    with service.database.connect() as connection:
        project = connection.execute(
            """
            SELECT id
            FROM projects
            WHERE id = ?
            """,
            (project_id,),
        ).fetchone()

        if project is None:
            raise NotFoundError(
                f"project not found: {project_id}"
            )

        connection.execute(
            """
            INSERT INTO graphs(
                id,
                project_id,
                name,
                kind,
                enforce_acyclic,
                created_at
            )
            VALUES(?,?,?,?,?,?)
            """,
            (
                graph_id,
                project_id,
                name.strip(),
                kind,
                enforce_acyclic,
                utc_now(),
            ),
        )

    return service.get(graph_id)


def add_node(
    service,
    graph_id: str,
    node_key: str,
    node_type: str,
    title: str,
) -> dict[str, object]:
    node_id = new_id("node")

    with service.database.connect() as connection:
        require_graph(
            connection,
            graph_id,
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
                created_at
            )
            VALUES(?,?,?,?,?,?,?)
            """,
            (
                node_id,
                graph_id,
                node_key,
                node_type,
                title,
                "PENDING",
                utc_now(),
            ),
        )

    return service.get_node(node_id)


def add_edge(
    service,
    graph_id: str,
    from_node_id: str,
    to_node_id: str,
    edge_type: str,
) -> dict[str, object]:
    if from_node_id == to_node_id:
        raise ValidationError(
            "self edges are forbidden"
        )

    edge_id = new_id("edge")

    with service.database.connect() as connection:
        graph = require_graph(
            connection,
            graph_id,
        )

        require_node(
            connection,
            graph_id,
            from_node_id,
        )

        require_node(
            connection,
            graph_id,
            to_node_id,
        )

        if (
            bool(graph["enforce_acyclic"])
            and creates_cycle(service, 
                connection,
                graph_id,
                from_node_id,
                to_node_id,
            )
        ):
            raise ValidationError(
                "edge would create a cycle"
            )

        connection.execute(
            """
            INSERT INTO graph_edges(
                id,
                graph_id,
                from_node_id,
                to_node_id,
                edge_type,
                created_at
            )
            VALUES(?,?,?,?,?,?)
            """,
            (
                edge_id,
                graph_id,
                from_node_id,
                to_node_id,
                edge_type,
                utc_now(),
            ),
        )

    return {
        "id": edge_id,
        "graph_id": graph_id,
        "from_node_id": from_node_id,
        "to_node_id": to_node_id,
        "edge_type": edge_type,
    }


def set_status(
    service,
    node_id: str,
    status: str,
) -> dict[str, object]:
    if status not in service.NODE_STATUSES:
        raise ValidationError(
            f"invalid node status: {status}"
        )

    with service.database.connect() as connection:
        cursor = connection.execute(
            """
            UPDATE graph_nodes
            SET status = ?
            WHERE id = ?
            """,
            (status, node_id),
        )

        if cursor.rowcount != 1:
            raise NotFoundError(
                f"node not found: {node_id}"
            )

    return service.get_node(node_id)


def creates_cycle(
    service,
    connection: sqlite3.Connection,
    graph_id: str,
    from_node_id: str,
    to_node_id: str,
) -> bool:
    rows = connection.execute(
        """
        SELECT from_node_id, to_node_id
        FROM graph_edges
        WHERE graph_id = ?
        """,
        (graph_id,),
    ).fetchall()

    adjacency: dict[str, set[str]] = defaultdict(set)

    for row in rows:
        adjacency[row["from_node_id"]].add(
            row["to_node_id"]
        )

    adjacency[from_node_id].add(to_node_id)

    stack = [to_node_id]
    visited: set[str] = set()

    while stack:
        current = stack.pop()

        if current == from_node_id:
            return True

        if current in visited:
            continue

        visited.add(current)
        stack.extend(
            adjacency.get(current, set())
        )

    return False
