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
from .export_bindings import (
    bind_integration,
    get_binding,
    list_bindings,
    normalize_binding,
)
from .export_queries import get_export, list_export_artifacts, list_exports
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


EXPORT_SCHEMA = """
PRAGMA foreign_keys = ON;

CREATE TABLE IF NOT EXISTS integration_bindings (
    id TEXT PRIMARY KEY,
    project_id TEXT NOT NULL
        REFERENCES projects(id)
        ON DELETE CASCADE,
    system_name TEXT NOT NULL,
    binding_type TEXT NOT NULL,
    config_json TEXT NOT NULL DEFAULT '{}',
    enabled INTEGER NOT NULL DEFAULT 1,
    created_at TEXT NOT NULL,
    updated_at TEXT NOT NULL,
    UNIQUE(
        project_id,
        system_name,
        binding_type
    )
);

CREATE TABLE IF NOT EXISTS exports (
    id TEXT PRIMARY KEY,
    project_id TEXT NOT NULL
        REFERENCES projects(id)
        ON DELETE CASCADE,
    plan_version_id TEXT NOT NULL
        REFERENCES plan_versions(id)
        ON DELETE CASCADE,
    export_type TEXT NOT NULL,
    bundle_fingerprint TEXT NOT NULL,
    output_path TEXT NOT NULL,
    manifest_sha256 TEXT NOT NULL,
    status TEXT NOT NULL,
    artifact_count INTEGER NOT NULL,
    created_at TEXT NOT NULL,
    verified_at TEXT,
    UNIQUE(
        plan_version_id,
        export_type,
        bundle_fingerprint
    )
);

CREATE TABLE IF NOT EXISTS export_verification_findings (
    id TEXT PRIMARY KEY,
    export_id TEXT NOT NULL
        REFERENCES exports(id)
        ON DELETE CASCADE,
    severity TEXT NOT NULL,
    code TEXT NOT NULL,
    message TEXT NOT NULL,
    artifact_path TEXT,
    created_at TEXT NOT NULL
);

CREATE INDEX IF NOT EXISTS idx_integration_bindings_project
    ON integration_bindings(
        project_id,
        enabled
    );

CREATE INDEX IF NOT EXISTS idx_exports_project
    ON exports(
        project_id,
        created_at
    );

CREATE INDEX IF NOT EXISTS idx_exports_plan
    ON exports(
        plan_version_id,
        export_type
    );

CREATE INDEX IF NOT EXISTS idx_export_findings_export
    ON export_verification_findings(
        export_id,
        severity
    );
"""


EXPORT_TYPE = "SPECGRAPH_HANDOFF_V1"



def utc_now() -> str:
    return datetime.now(UTC).isoformat()


def new_id(prefix: str) -> str:
    return str(uuid.uuid4())


def canonical_json_bytes(
    value: object,
) -> bytes:
    return (
        json.dumps(
            value,
            sort_keys=True,
            separators=(",", ":"),
            ensure_ascii=False,
        )
        + "\n"
    ).encode("utf-8")


def sha256_bytes(value: bytes) -> str:
    return hashlib.sha256(value).hexdigest()


def sha256_file(path: Path) -> str:
    digest = hashlib.sha256()

    with path.open("rb") as handle:
        while True:
            block = handle.read(1024 * 1024)

            if not block:
                break

            digest.update(block)

    return digest.hexdigest()


def parse_checksums_file(
    path: Path,
) -> dict[str, str] | None:
    observed: dict[str, str] = {}
    try:
        lines = path.read_text(
            encoding="utf-8",
        ).splitlines()
    except UnicodeDecodeError:
        return None

    for line in lines:
        if not line.strip():
            continue
        parts = line.split()
        if len(parts) != 2:
            return None
        checksum, relative_path = parts
        if (
            len(checksum) != 64
            or any(
                char
                not in "0123456789abcdef"
                for char in checksum
            )
        ):
            return None
        if relative_path in observed:
            return None
        observed[relative_path] = checksum
    return observed


def build_export_proof_summary(
    project_id: str,
    plan: dict[str, object],
    artifacts: dict[str, bytes],
    traceability: list[dict[str, object]],
    authority_payload: dict[str, object],
    execution_payload: dict[str, object],
) -> dict[str, object]:
    artifact_hashes = {
        name: sha256_bytes(content)
        for name, content
        in sorted(artifacts.items())
    }
    traceability_hash = sha256_bytes(
        canonical_json_bytes(
            {"items": traceability}
        )
    )
    authority_hash = sha256_bytes(
        canonical_json_bytes(
            authority_payload
        )
    )
    execution_hash = sha256_bytes(
        canonical_json_bytes(
            execution_payload
        )
    )
    payload = {
        "schema_version": "specgraph.export.proof-summary.v1",
        "project_id": project_id,
        "plan_id": plan["id"],
        "plan_status": plan["status"],
        "plan_input_fingerprint": plan["input_fingerprint"],
        "atom_count": plan["atom_count"],
        "artifact_count": len(artifacts),
        "artifact_hashes": artifact_hashes,
        "traceability_sha256": traceability_hash,
        "authority_graph_sha256": authority_hash,
        "execution_graph_sha256": execution_hash,
        "acceptance": {
            "plan_status_verified": plan["status"] == "VERIFIED",
            "traceability_items": len(traceability),
            "artifact_hashes_present": all(
                len(value) == 64
                for value in artifact_hashes.values()
            ),
        },
        "verifier_identity": "specgraph.export.proof-summary.v1",
    }
    return {
        **payload,
        "proof_summary_sha256": sha256_bytes(
            canonical_json_bytes(payload)
        ),
    }


def verify_export_proof_summary(
    path: Path,
) -> list[dict[str, object]]:
    try:
        proof = json.loads(
            path.read_text(
                encoding="utf-8"
            )
        )
    except (
        UnicodeDecodeError,
        json.JSONDecodeError,
    ):
        return [
            {
                "severity": "ERROR",
                "code": "EXPORT_PROOF_SUMMARY_INVALID",
                "message": (
                    "export_proof_summary.json is not valid UTF-8 JSON."
                ),
                "artifact_path": "export_proof_summary.json",
            }
        ]

    if not isinstance(proof, dict):
        return [
            {
                "severity": "ERROR",
                "code": "EXPORT_PROOF_SUMMARY_INVALID",
                "message": (
                    "export_proof_summary.json must contain an object."
                ),
                "artifact_path": "export_proof_summary.json",
            }
        ]

    observed = proof.get(
        "proof_summary_sha256"
    )
    payload = {
        key: value
        for key, value
        in proof.items()
        if key != "proof_summary_sha256"
    }
    expected = sha256_bytes(
        canonical_json_bytes(payload)
    )
    if observed != expected:
        return [
            {
                "severity": "ERROR",
                "code": "EXPORT_PROOF_SUMMARY_CHECKSUM_MISMATCH",
                "message": (
                    "export_proof_summary.json internal checksum does not match."
                ),
                "artifact_path": "export_proof_summary.json",
            }
        ]
    return []




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
        plan = self.planning.get_plan(
            plan_id
        )

        if plan["status"] != "VERIFIED":
            raise ValidationError(
                "only VERIFIED plans may be exported"
            )

        project_id = str(
            plan["project_id"]
        )

        bundle = self._build_bundle(
            project_id,
            plan,
        )

        artifacts = bundle["artifacts"]
        bundle_fingerprint = str(
            bundle["bundle_fingerprint"]
        )

        root = (
            output_root
            if output_root is not None
            else Path(
                ".specgraph",
                "exports",
            )
        )

        root = root.expanduser().resolve()
        root.mkdir(
            parents=True,
            exist_ok=True,
        )

        project_slug = str(
            bundle["project"]["slug"]
        )

        directory_name = (
            f"{project_slug}-"
            f"{plan_id.split('-')[-1][:8]}-"
            f"{bundle_fingerprint[:12]}"
        )

        final_directory = (
            root / directory_name
        )

        manifest_bytes = canonical_json_bytes(
            bundle["manifest"]
        )

        manifest_sha256 = sha256_bytes(
            manifest_bytes
        )

        all_checksums = dict(
            bundle["artifact_checksums"]
        )
        all_checksums[
            "manifest.json"
        ] = manifest_sha256

        checksum_lines = [
            f"{digest}  {name}"
            for name, digest
            in sorted(
                all_checksums.items()
            )
        ]

        checksum_bytes = (
            "\n".join(checksum_lines)
            + "\n"
        ).encode("utf-8")

        existing = self._find_export(
            plan_id,
            bundle_fingerprint,
        )

        if existing is not None:
            output_path = Path(
                str(existing["output_path"])
            )

            if output_path.is_dir():
                verification = (
                    self.verify_export(
                        str(existing["id"])
                    )
                )

                if verification["valid"]:
                    return self.get_export(
                        str(existing["id"])
                    )

        temporary_directory = (
            root
            / (
                ".tmp-"
                + uuid.uuid4().hex
            )
        )

        temporary_directory.mkdir(
            parents=False,
            exist_ok=False,
        )

        try:
            for relative_path, content in (
                artifacts.items()
            ):
                target = (
                    temporary_directory
                    / relative_path
                )

                target.parent.mkdir(
                    parents=True,
                    exist_ok=True,
                )

                target.write_bytes(content)

            (
                temporary_directory
                / "manifest.json"
            ).write_bytes(
                manifest_bytes
            )

            (
                temporary_directory
                / "checksums.sha256"
            ).write_bytes(
                checksum_bytes
            )

            if final_directory.exists():
                shutil.rmtree(
                    final_directory
                )

            os.replace(
                temporary_directory,
                final_directory,
            )

        finally:
            if temporary_directory.exists():
                shutil.rmtree(
                    temporary_directory
                )

        export_id = (
            str(existing["id"])
            if existing is not None
            else new_id("export")
        )

        artifact_count = (
            len(artifacts) + 2
        )

        try:
            with self.database.connect() as connection:
                if existing is None:
                    connection.execute(
                        """
                        INSERT INTO exports(
                            id,
                            project_id,
                            plan_version_id,
                            export_type,
                            bundle_fingerprint,
                            output_path,
                            manifest_sha256,
                            status,
                            artifact_count,
                            created_at
                        )
                        VALUES(?,?,?,?,?,?,?,?,?,?)
                        """,
                        (
                            export_id,
                            project_id,
                            plan_id,
                            EXPORT_TYPE,
                            bundle_fingerprint,
                            str(final_directory),
                            manifest_sha256,
                            "CREATED",
                            artifact_count,
                            utc_now(),
                        ),
                    )
                else:
                    connection.execute(
                        """
                        UPDATE exports
                        SET output_path = ?,
                            manifest_sha256 = ?,
                            status = 'CREATED',
                            artifact_count = ?,
                            verified_at = NULL
                        WHERE id = ?
                        """,
                        (
                            str(final_directory),
                            manifest_sha256,
                            artifact_count,
                            export_id,
                        ),
                    )

        except sqlite3.IntegrityError as error:
            raise ConflictError(
                "an identical export already exists"
            ) from error

        self.verify_export(export_id)
        return self.get_export(export_id)

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
        with self.database.connect() as connection:
            return connection.execute(
                """
                SELECT *
                FROM exports
                WHERE plan_version_id = ?
                  AND export_type = ?
                  AND bundle_fingerprint = ?
                """,
                (
                    plan_id,
                    EXPORT_TYPE,
                    fingerprint,
                ),
            ).fetchone()

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

