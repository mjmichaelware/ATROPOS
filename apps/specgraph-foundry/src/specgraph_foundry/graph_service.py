"""Graphs, nodes and edges.

The generic graph store both the authority graph and the execution graph are
built on. Its own module because it knows nothing about plans or atoms -- it
enforces shape (no self edges, no cycles when required) and nothing else.
"""

from __future__ import annotations

import hashlib
import json
import re
import sqlite3
import uuid
from collections import defaultdict
from datetime import UTC, datetime

from .database import Database
from .errors import (
    ConflictError,
    NotFoundError,
    ValidationError,
)


def utc_now() -> str:
    return datetime.now(UTC).isoformat()


def new_id(prefix: str) -> str:
    return str(uuid.uuid4())


class GraphService:
    GRAPH_KINDS = {
        "AUTHORITY",
        "EXECUTION",
        "RESEARCH",
        "CUSTOM",
    }

    NODE_STATUSES = {
        "PENDING",
        "READY",
        "CLAIMED",
        "RUNNING",
        "BLOCKED",
        "FAILED",
        "CANCELLED",
        "COMPLETE",
    }

    def __init__(self, database: Database) -> None:
        self.database = database

    def create(
        self,
        project_id: str,
        name: str,
        kind: str,
        enforce_acyclic: bool,
    ) -> dict[str, object]:
        if kind not in self.GRAPH_KINDS:
            raise ValidationError(
                f"invalid graph kind: {kind}"
            )

        graph_id = new_id("graph")

        with self.database.connect() as connection:
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

        return self.get(graph_id)

    def add_node(
        self,
        graph_id: str,
        node_key: str,
        node_type: str,
        title: str,
    ) -> dict[str, object]:
        node_id = new_id("node")

        with self.database.connect() as connection:
            self._require_graph(
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

        return self.get_node(node_id)

    def add_edge(
        self,
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

        with self.database.connect() as connection:
            graph = self._require_graph(
                connection,
                graph_id,
            )

            self._require_node(
                connection,
                graph_id,
                from_node_id,
            )

            self._require_node(
                connection,
                graph_id,
                to_node_id,
            )

            if (
                bool(graph["enforce_acyclic"])
                and self._creates_cycle(
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
        self,
        node_id: str,
        status: str,
    ) -> dict[str, object]:
        if status not in self.NODE_STATUSES:
            raise ValidationError(
                f"invalid node status: {status}"
            )

        with self.database.connect() as connection:
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

        return self.get_node(node_id)

    def ready_nodes(
        self,
        graph_id: str,
    ) -> list[dict[str, object]]:
        with self.database.connect() as connection:
            graph = self._require_graph(
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
            self._normalize_node(dict(row))
            for row in rows
        ]

    def get(
        self,
        graph_id: str,
    ) -> dict[str, object]:
        with self.database.connect() as connection:
            graph = self._require_graph(
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
            self._normalize_node(dict(row))
            for row in nodes
        ]
        result["edges"] = [
            dict(row)
            for row in edges
        ]

        return result

    def get_node(
        self,
        node_id: str,
    ) -> dict[str, object]:
        with self.database.connect() as connection:
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

        return self._normalize_node(dict(row))

    def _creates_cycle(
        self,
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

    @staticmethod
    def _require_graph(
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

    @staticmethod
    def _require_node(
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

    @staticmethod
    def _normalize_node(
        record: dict[str, object],
    ) -> dict[str, object]:
        record["payload"] = json.loads(
            str(record.pop("payload_json"))
        )
        return record
