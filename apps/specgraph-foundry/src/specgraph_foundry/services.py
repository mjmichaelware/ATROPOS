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

# Re-exported: services is the module callers import these from.
from .document_service import DocumentService
from .graph_service import GraphService
from .project_service import ProjectService

__all__ = ["DocumentService", "GraphService", "ProjectService"]
