import base64
import hashlib
import json
import re
import sqlite3
import uuid
from datetime import UTC, datetime

from .database import Database
from .atom_bundle import encoded_file, export_atoms_bundle, render_atoms_markdown
from .atom_extraction import extract_document
from .atom_queries import get_atom, get_extraction, list_atoms, list_atoms_page
from .atom_research_tasks import (
    list_research_tasks,
    list_research_tasks_page,
    normalize_task,
)
from .atom_constants import EXTRACTOR_VERSION
from .atom_schema import ATOM_SCHEMA
from .atom_research_questions import research_question
from .atom_statements import extract_statements
from .atom_vocabulary import DIMENSIONS, KIND_RULES
from .rendering import (
    markdown_to_plain_text,
    render_markdown_pdf,
)
from .errors import (
    ConflictError,
    NotFoundError,
    ValidationError,
)

















def utc_now() -> str:
    return datetime.now(UTC).isoformat()


def new_id(prefix: str) -> str:
    return str(uuid.uuid4())












class AtomService:
    def __init__(
        self,
        database: Database,
    ) -> None:
        self.database = database
        self.ensure_schema()

    def ensure_schema(self) -> None:
        with self.database.connect() as connection:
            connection.executescript(
                ATOM_SCHEMA
            )

    def extract_document(
        self,
        document_id: str,
    ) -> dict[str, object]:
        """Delegates to :func:`atom_extraction.extract_document`."""
        return extract_document(
            self.database,
            document_id,
        )


    def get_extraction(
        self,
        extraction_run_id: str,
    ) -> dict[str, object]:
        """Delegates to :func:`atom_queries.get_extraction`."""
        return get_extraction(
            self.database,
            extraction_run_id,
        )


    def list_atoms(
        self,
        document_id: str,
    ) -> list[dict[str, object]]:
        """Delegates to :func:`atom_queries.list_atoms`."""
        return list_atoms(
            self.database,
            document_id,
        )


    def list_atoms_page(
        self,
        document_id: str,
        limit: int,
        boundary: dict[str, object] | None = None,
    ) -> tuple[
        list[dict[str, object]],
        bool,
        dict[str, object] | None,
    ]:
        """Delegates to :func:`atom_queries.list_atoms_page`."""
        return list_atoms_page(
            self.database,
            document_id,
            limit,
            boundary,
        )


    def get_atom(
        self,
        atom_id: str,
    ) -> dict[str, object]:
        """Delegates to :func:`atom_queries.get_atom`."""
        return get_atom(
            self.database,
            atom_id,
        )


    def list_research_tasks(
        self,
        project_id: str,
        status: str | None = None,
    ) -> list[dict[str, object]]:
        """Delegates to :func:`atom_research_tasks.list_research_tasks`."""
        return list_research_tasks(
            self.database,
            project_id,
            status,
        )


    def list_research_tasks_page(
        self,
        project_id: str,
        limit: int,
        boundary: dict[str, object] | None = None,
    ) -> tuple[
        list[dict[str, object]],
        bool,
        dict[str, object] | None,
    ]:
        """Delegates to :func:`atom_research_tasks.list_research_tasks_page`."""
        return list_research_tasks_page(
            self.database,
            project_id,
            limit,
            boundary,
        )


    def export_atoms_bundle(
        self,
        document_id: str,
    ) -> dict[str, object]:
        """Delegates to :func:`atom_bundle.export_atoms_bundle`."""
        return export_atoms_bundle(
            self.database,
            document_id,
        )


    @staticmethod
    def _render_atoms_markdown(
        document: dict[str, object],
        atoms: list[dict[str, object]],
    ) -> str:
        """Delegates to :func:`atom_bundle.render_atoms_markdown`."""
        return render_atoms_markdown(
            document,
            atoms,
        )


    @staticmethod
    def _encoded_file(
        payload: bytes,
        media_type: str,
    ) -> dict[str, object]:
        """Delegates to :func:`atom_bundle.encoded_file`."""
        return encoded_file(
            payload,
            media_type,
        )


    @staticmethod
    def _normalize_task(
        task: dict[str, object],
    ) -> dict[str, object]:
        """Delegates to :func:`atom_research_tasks.normalize_task`."""
        return normalize_task(
            task,
        )

