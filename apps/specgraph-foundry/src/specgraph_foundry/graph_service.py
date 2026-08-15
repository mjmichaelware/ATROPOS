"""Graphs, nodes and edges.

The generic graph store both the authority graph and the execution graph are
built on. Its own module because it knows nothing about plans or atoms -- it
enforces shape (no self edges, no cycles when required) and nothing else.
"""

from __future__ import annotations

from .graph_mutations import add_edge, add_node, create, creates_cycle, set_status
from .graph_queries import get, get_node, normalize_node, ready_nodes

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
        """Delegates to :func:`graph_mutations.create`."""
        return create(
            self,
            project_id,
            name,
            kind,
            enforce_acyclic,
        )


    def add_node(
        self,
        graph_id: str,
        node_key: str,
        node_type: str,
        title: str,
    ) -> dict[str, object]:
        """Delegates to :func:`graph_mutations.add_node`."""
        return add_node(
            self,
            graph_id,
            node_key,
            node_type,
            title,
        )


    def add_edge(
        self,
        graph_id: str,
        from_node_id: str,
        to_node_id: str,
        edge_type: str,
    ) -> dict[str, object]:
        """Delegates to :func:`graph_mutations.add_edge`."""
        return add_edge(
            self,
            graph_id,
            from_node_id,
            to_node_id,
            edge_type,
        )


    def set_status(
        self,
        node_id: str,
        status: str,
    ) -> dict[str, object]:
        """Delegates to :func:`graph_mutations.set_status`."""
        return set_status(
            self,
            node_id,
            status,
        )


    def ready_nodes(
        self,
        graph_id: str,
    ) -> list[dict[str, object]]:
        """Delegates to :func:`graph_queries.ready_nodes`."""
        return ready_nodes(
            self,
            graph_id,
        )


    def get(
        self,
        graph_id: str,
    ) -> dict[str, object]:
        """Delegates to :func:`graph_queries.get`."""
        return get(
            self,
            graph_id,
        )


    def get_node(
        self,
        node_id: str,
    ) -> dict[str, object]:
        """Delegates to :func:`graph_queries.get_node`."""
        return get_node(
            self,
            node_id,
        )


    def _creates_cycle(
        self,
        connection: sqlite3.Connection,
        graph_id: str,
        from_node_id: str,
        to_node_id: str,
    ) -> bool:
        """Delegates to :func:`graph_mutations.creates_cycle`."""
        return creates_cycle(
            self,
            connection,
            graph_id,
            from_node_id,
            to_node_id,
        )




    @staticmethod
    def _normalize_node(
        record: dict[str, object],
    ) -> dict[str, object]:
        """Delegates to :func:`graph_queries.normalize_node`."""
        return normalize_node(
            record,
        )

