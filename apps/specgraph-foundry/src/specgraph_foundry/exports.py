import hashlib
import json
import os
import shutil
import sqlite3
import uuid
from datetime import UTC, datetime
from pathlib import Path
from typing import Any

from .database import Database
from .sensitive_keys import SENSITIVE_KEY_FRAGMENTS, contains_sensitive_key
from .export_creation import export_plan
from .export_proof import (
    build_export_proof_summary,
    canonical_json_bytes,
    parse_checksums_file,
    sha256_bytes,
    sha256_file,
    verify_export_proof_summary,
)
from .export_schema import EXPORT_SCHEMA, EXPORT_TYPE
from .primitives import new_id, utc_now
from .export_bindings import (
    bind_integration,
    get_binding,
    list_bindings,
    normalize_binding,
)
from .export_queries import find_export, get_export, list_export_artifacts, list_exports
from .export_bundle import build_bundle
from .export_verification import verify_export
from .export_handoff import build_handoff
from .export_markdown import build_execution_plan_section, build_markdown
from .export_traceability import build_traceability
from .errors import (
    ConflictError,
    NotFoundError,
    ValidationError,
)
from .planning import PlanningService
from .rendering import markdown_to_plain_text
from .research import ResearchService

























class ExportService:
    def __init__(
        self,
        database: Database,
    ) -> None:
        self.database = database
        self.research = ResearchService(
            database
        )
        self.planning = PlanningService(
            database
        )
        self.ensure_schema()

    def ensure_schema(self) -> None:
        with self.database.connect() as connection:
            connection.executescript(
                EXPORT_SCHEMA
            )

    def bind_integration(
        self,
        project_id: str,
        system_name: str,
        binding_type: str,
        config: dict[str, object],
        enabled: bool = True,
    ) -> dict[str, object]:
        """Delegates to :func:`export_bindings.bind_integration`."""
        return bind_integration(
            self.database,
            project_id,
            system_name,
            binding_type,
            config,
            enabled,
        )


    def get_binding(
        self,
        binding_id: str,
    ) -> dict[str, object]:
        """Delegates to :func:`export_bindings.get_binding`."""
        return get_binding(
            self.database,
            binding_id,
        )


    def list_bindings(
        self,
        project_id: str,
    ) -> list[dict[str, object]]:
        """Delegates to :func:`export_bindings.list_bindings`."""
        return list_bindings(
            self.database,
            project_id,
        )


    def export_plan(
        self,
        plan_id: str,
        output_root: Path | None = None,
    ) -> dict[str, object]:
        """Delegates to :func:`export_creation.export_plan`."""
        return export_plan(
            self.database,
            self.planning,
            plan_id,
            output_root,
        )


    def verify_export(
        self,
        export_id: str,
    ) -> dict[str, object]:
        """Delegates to :func:`export_verification.verify_export`."""
        return verify_export(
            self.database,
            export_id,
        )


    def get_export(
        self,
        export_id: str,
        include_findings: bool = True,
    ) -> dict[str, object]:
        """Delegates to :func:`export_queries.get_export`."""
        return get_export(
            self.database,
            export_id,
            include_findings,
        )


    def list_exports(
        self,
        project_id: str,
    ) -> list[dict[str, object]]:
        """Delegates to :func:`export_queries.list_exports`."""
        return list_exports(
            self.database,
            project_id,
        )


    def _build_bundle(
        self,
        project_id: str,
        plan: dict[str, object],
    ) -> dict[str, object]:
        """Delegates to :func:`export_bundle.build_bundle`."""
        return build_bundle(
            self.database,
            self.planning,
            project_id,
            plan,
        )


    @staticmethod
    def _build_traceability(
        atoms: list[dict[str, object]],
        documents: list[
            dict[str, object]
        ],
        dimensions: list[
            dict[str, object]
        ],
        claims: list[
            dict[str, object]
        ],
        evidence: list[
            dict[str, object]
        ],
        claim_evidence: list[
            dict[str, object]
        ],
        plan_bindings: list[
            dict[str, object]
        ],
        relations: list[
            dict[str, object]
        ],
    ) -> list[dict[str, object]]:
        """Delegates to :func:`export_traceability.build_traceability`."""
        return build_traceability(
            atoms,
            documents,
            dimensions,
            claims,
            evidence,
            claim_evidence,
            plan_bindings,
            relations,
        )


    @staticmethod
    def _build_handoff(
        project: dict[str, object],
        plan: dict[str, object],
        traceability: list[
            dict[str, object]
        ],
        bindings: list[
            dict[str, object]
        ],
    ) -> dict[str, object]:
        """Delegates to :func:`export_handoff.build_handoff`."""
        return build_handoff(
            project,
            plan,
            traceability,
            bindings,
        )


    @staticmethod
    def _build_execution_plan_section(
        plan: dict[str, object],
    ) -> list[str]:
        # atropos_handoff.json already carries the full DAG (nodes, edges,
        # ready_node_ids) as structured JSON, but the human/LLM-readable
        # blueprint previously only mapped each atom to the graph node it
        # produced - it never rendered the graph itself: what every node
        # actually is, what depends on what, or what can start right now.
        # A build agent working from the PDF/text alone had no execution
        # order to follow, only a flat list of requirements. This section
        # is that missing DAG walk-through.
        """Delegates to :func:`export_markdown.build_execution_plan_section`."""
        return build_execution_plan_section(
            plan,
        )


    @staticmethod
    def _build_markdown(
        project: dict[str, object],
        plan: dict[str, object],
        atoms: list[
            dict[str, object]
        ],
        traceability: list[
            dict[str, object]
        ],
        bindings: list[
            dict[str, object]
        ],
    ) -> str:
        """Delegates to :func:`export_markdown.build_markdown`."""
        return build_markdown(
            project,
            plan,
            atoms,
            traceability,
            bindings,
        )


    def _find_export(
        self,
        plan_id: str,
        fingerprint: str,
    ) -> sqlite3.Row | None:
        """Delegates to :func:`export_queries.find_export`."""
        return find_export(
            self.database,
            plan_id,
            fingerprint,
        )


    @staticmethod
    @staticmethod
    def _normalize_binding(
        record: dict[str, object],
    ) -> dict[str, object]:
        """Delegates to :func:`export_bindings.normalize_binding`."""
        return normalize_binding(record)


    @staticmethod
    def list_export_artifacts(
        directory: Path,
    ) -> list[dict[str, object]]:
        """Delegates to :func:`export_queries.list_export_artifacts`."""
        return list_export_artifacts(
            self.database,
            directory,
        )

