"""Reading a graph back, and which of its nodes are ready to run."""

from __future__ import annotations

from .graph_guards import require_graph
from .errors import NotFoundError
from .errors import ValidationError
import json


def ready_nodes(
    service,
    graph_id: str,
) -> list[dict[str, object]]:
    with service.database.connect() as connection:
        graph = require_graph(
            connection,
            graph_id,
        )

        if not bool(graph["enforce_acyclic"]):
            raise ValidationError(
                "ready-node calculation requires an acyclic graph"
            )

        rows = connection.execute(
            """
            SELECT node.*
            FROM graph_nodes AS node
            WHERE node.graph_id = ?
              AND node.status IN ('PENDING', 'READY')
              AND NOT EXISTS (
                  SELECT 1
                  FROM graph_edges AS edge
                  JOIN graph_nodes AS predecessor
                    ON predecessor.id = edge.from_node_id
                  WHERE edge.graph_id = node.graph_id
                    AND edge.to_node_id = node.id
                    AND predecessor.status <> 'COMPLETE'
              )
            ORDER BY node.created_at, node.id
            """,
            (graph_id,),
        ).fetchall()

    return [
        normalize_node(dict(row))
        for row in rows
    ]


def get(
    service,
    graph_id: str,
) -> dict[str, object]:
    with service.database.connect() as connection:
        graph = require_graph(
            connection,
            graph_id,
        )

        nodes = connection.execute(
            """
            SELECT *
            FROM graph_nodes
            WHERE graph_id = ?
            ORDER BY created_at, id
            """,
            (graph_id,),
        ).fetchall()

        edges = connection.execute(
            """
            SELECT *
            FROM graph_edges
            WHERE graph_id = ?
            ORDER BY created_at, id
            """,
            (graph_id,),
        ).fetchall()

    result = dict(graph)
    result["enforce_acyclic"] = bool(
        result["enforce_acyclic"]
    )
    result["nodes"] = [
        normalize_node(dict(row))
        for row in nodes
    ]
    result["edges"] = [
        dict(row)
        for row in edges
    ]

    return result


def get_node(
    service,
    node_id: str,
) -> dict[str, object]:
    with service.database.connect() as connection:
        row = connection.execute(
            """
            SELECT *
            FROM graph_nodes
            WHERE id = ?
            """,
            (node_id,),
        ).fetchone()

    if row is None:
        raise NotFoundError(
            f"node not found: {node_id}"
        )

    return normalize_node(dict(row))


def normalize_node(
    record: dict[str, object],
) -> dict[str, object]:
    record["payload"] = json.loads(
        str(record.pop("payload_json"))
    )
    return record
