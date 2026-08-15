"""Existence checks for graphs and nodes."""

from __future__ import annotations

import sqlite3
import json

from .errors import NotFoundError, ValidationError


def require_graph(
    connection: sqlite3.Connection,
    graph_id: str,
) -> sqlite3.Row:
    row = connection.execute(
        """
        SELECT *
        FROM graphs
        WHERE id = ?
        """,
        (graph_id,),
    ).fetchone()

    if row is None:
        raise NotFoundError(
            f"graph not found: {graph_id}"
        )

    return row


def require_node(
    connection: sqlite3.Connection,
    graph_id: str,
    node_id: str,
) -> None:
    row = connection.execute(
        """
        SELECT id
        FROM graph_nodes
        WHERE graph_id = ?
          AND id = ?
        """,
        (graph_id, node_id),
    ).fetchone()

    if row is None:
        raise ValidationError(
            f"node {node_id} does not belong to graph {graph_id}"
        )
