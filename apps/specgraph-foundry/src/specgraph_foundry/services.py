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


class ProjectService:
    SLUG_PATTERN = re.compile(
        r"^[a-z0-9]+(?:-[a-z0-9]+)*$"
    )

    def __init__(self, database: Database) -> None:
        self.database = database

    def create(
        self,
        slug: str,
        name: str,
        description: str = "",
    ) -> dict[str, object]:
        slug = slug.strip()
        name = name.strip()

        if not self.SLUG_PATTERN.fullmatch(slug):
            raise ValidationError(
                "invalid project slug"
            )

        if not name:
            raise ValidationError(
                "project name is required"
            )

        project_id = new_id("project")

        try:
            with self.database.connect() as connection:
                if self.database.is_postgres:
                    owner_id = self.database.owner_id

                    if not owner_id:
                        raise ValidationError(
                            "SPECGRAPH_OWNER_ID is "
                            "required in PostgreSQL mode"
                        )

                    try:
                        uuid.UUID(owner_id)
                    except ValueError as error:
                        raise ValidationError(
                            "SPECGRAPH_OWNER_ID must be "
                            "a valid Supabase Auth UUID"
                        ) from error

                    connection.execute(
                        """
                        INSERT INTO projects(
                            id,
                            owner_id,
                            slug,
                            name,
                            description,
                            created_at
                        )
                        VALUES(?,?,?,?,?,?)
                        """,
                        (
                            project_id,
                            owner_id,
                            slug,
                            name,
                            description.strip(),
                            utc_now(),
                        ),
                    )
                else:
                    connection.execute(
                        """
                        INSERT INTO projects(
                            id,
                            slug,
                            name,
                            description,
                            created_at
                        )
                        VALUES(?,?,?,?,?)
                        """,
                        (
                            project_id,
                            slug,
                            name,
                            description.strip(),
                            utc_now(),
                        ),
                    )
        except sqlite3.IntegrityError as error:
            raise ConflictError(
                f"project already exists: {slug}"
            ) from error

        return self.get(project_id)

    def get(
        self,
        project_id: str,
    ) -> dict[str, object]:
        with self.database.connect() as connection:
            row = connection.execute(
                """
                SELECT *
                FROM projects
                WHERE id = ?
                """,
                (project_id,),
            ).fetchone()

        if row is None:
            raise NotFoundError(
                f"project not found: {project_id}"
            )

        return dict(row)

    def list(self) -> list[dict[str, object]]:
        with self.database.connect() as connection:
            rows = connection.execute(
                """
                SELECT *
                FROM projects
                ORDER BY created_at, id
                """
            ).fetchall()

        return [dict(row) for row in rows]

    def list_page(
        self,
        limit: int,
        boundary: dict[str, object] | None = None,
    ) -> tuple[
        list[dict[str, object]],
        bool,
        dict[str, object] | None,
    ]:
        parameters: list[object] = []
        predicate = ""

        if boundary is not None:
            predicate = """
                WHERE (
                    created_at > ?
                    OR (
                        created_at = ?
                        AND id > ?
                    )
                )
            """
            created_at = str(
                boundary.get("created_at", "")
            )
            parameters.extend(
                [
                    created_at,
                    created_at,
                    str(boundary.get("id", "")),
                ]
            )

        parameters.append(limit + 1)

        with self.database.connect() as connection:
            rows = connection.execute(
                f"""
                SELECT *
                FROM projects
                {predicate}
                ORDER BY created_at, id
                LIMIT ?
                """,
                tuple(parameters),
            ).fetchall()

        items = [
            dict(row)
            for row in rows[:limit]
        ]
        has_more = len(rows) > limit
        boundary_item = (
            {
                "created_at": str(
                    items[-1]["created_at"]
                ),
                "id": str(items[-1]["id"]),
            }
            if items and has_more
            else None
        )

        return items, has_more, boundary_item


class DocumentService:
    def __init__(self, database: Database) -> None:
        self.database = database

    def ingest(
        self,
        project_id: str,
        title: str,
        content: str,
    ) -> dict[str, object]:
        title = title.strip()

        if not title:
            raise ValidationError(
                "document title is required"
            )

        if not content:
            raise ValidationError(
                "document content is required"
            )

        encoded = content.encode("utf-8")
        digest = hashlib.sha256(encoded).hexdigest()
        line_count = content.count("\n")

        if not content.endswith("\n"):
            line_count += 1

        document_id = new_id("document")

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
                INSERT INTO source_documents(
                    id,
                    project_id,
                    title,
                    sha256,
                    byte_count,
                    line_count,
                    content,
                    created_at
                )
                VALUES(?,?,?,?,?,?,?,?)
                """,
                (
                    document_id,
                    project_id,
                    title,
                    digest,
                    len(encoded),
                    line_count,
                    content,
                    utc_now(),
                ),
            )

        return {
            "id": document_id,
            "project_id": project_id,
            "title": title,
            "sha256": digest,
            "byte_count": len(encoded),
            "line_count": line_count,
        }


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
