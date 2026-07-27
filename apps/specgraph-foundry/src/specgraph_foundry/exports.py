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
from .errors import (
    ConflictError,
    NotFoundError,
    ValidationError,
)
from .planning import PlanningService
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

SENSITIVE_KEY_FRAGMENTS = (
    "api_key",
    "apikey",
    "access_key",
    "secret",
    "password",
    "passwd",
    "token",
    "credential",
    "private_key",
    "client_secret",
)


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


def contains_sensitive_key(
    value: object,
) -> bool:
    if isinstance(value, dict):
        for key, nested in value.items():
            normalized = str(key).casefold()

            if any(
                fragment in normalized
                for fragment
                in SENSITIVE_KEY_FRAGMENTS
            ):
                return True

            if contains_sensitive_key(nested):
                return True

    elif isinstance(value, list):
        return any(
            contains_sensitive_key(item)
            for item in value
        )

    return False


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
        system_name = system_name.strip()
        binding_type = binding_type.strip().upper()

        if not system_name:
            raise ValidationError(
                "system_name is required"
            )

        if not binding_type:
            raise ValidationError(
                "binding_type is required"
            )

        if not isinstance(config, dict):
            raise ValidationError(
                "integration config must be an object"
            )

        if contains_sensitive_key(config):
            raise ValidationError(
                "integration bindings must not "
                "contain secrets or credentials"
            )

        binding_id = new_id("binding")
        timestamp = utc_now()
        config_json = json.dumps(
            config,
            sort_keys=True,
            separators=(",", ":"),
            ensure_ascii=False,
        )

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

            existing = connection.execute(
                """
                SELECT id
                FROM integration_bindings
                WHERE project_id = ?
                  AND system_name = ?
                  AND binding_type = ?
                """,
                (
                    project_id,
                    system_name,
                    binding_type,
                ),
            ).fetchone()

            if existing is not None:
                binding_id = str(
                    existing["id"]
                )

                connection.execute(
                    """
                    UPDATE integration_bindings
                    SET config_json = ?,
                        enabled = ?,
                        updated_at = ?
                    WHERE id = ?
                    """,
                    (
                        config_json,
                        enabled,
                        timestamp,
                        binding_id,
                    ),
                )
            else:
                connection.execute(
                    """
                    INSERT INTO integration_bindings(
                        id,
                        project_id,
                        system_name,
                        binding_type,
                        config_json,
                        enabled,
                        created_at,
                        updated_at
                    )
                    VALUES(?,?,?,?,?,?,?,?)
                    """,
                    (
                        binding_id,
                        project_id,
                        system_name,
                        binding_type,
                        config_json,
                        enabled,
                        timestamp,
                        timestamp,
                    ),
                )

        return self.get_binding(binding_id)

    def get_binding(
        self,
        binding_id: str,
    ) -> dict[str, object]:
        with self.database.connect() as connection:
            row = connection.execute(
                """
                SELECT *
                FROM integration_bindings
                WHERE id = ?
                """,
                (binding_id,),
            ).fetchone()

        if row is None:
            raise NotFoundError(
                f"integration binding not found: "
                f"{binding_id}"
            )

        return self._normalize_binding(
            dict(row)
        )

    def list_bindings(
        self,
        project_id: str,
    ) -> list[dict[str, object]]:
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

            rows = connection.execute(
                """
                SELECT *
                FROM integration_bindings
                WHERE project_id = ?
                ORDER BY
                    system_name,
                    binding_type,
                    id
                """,
                (project_id,),
            ).fetchall()

        return [
            self._normalize_binding(
                dict(row)
            )
            for row in rows
        ]

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
        export = self.get_export(
            export_id,
            include_findings=False,
        )

        output_directory = Path(
            str(export["output_path"])
        )

        findings: list[
            dict[str, object]
        ] = []

        if not output_directory.is_dir():
            findings.append(
                {
                    "severity": "ERROR",
                    "code": (
                        "EXPORT_DIRECTORY_MISSING"
                    ),
                    "message": (
                        "Export directory does not exist."
                    ),
                    "artifact_path": str(
                        output_directory
                    ),
                }
            )
        else:
            manifest_path = (
                output_directory
                / "manifest.json"
            )

            if not manifest_path.is_file():
                findings.append(
                    {
                        "severity": "ERROR",
                        "code": (
                            "MANIFEST_MISSING"
                        ),
                        "message": (
                            "manifest.json is missing."
                        ),
                        "artifact_path": (
                            "manifest.json"
                        ),
                    }
                )
            else:
                actual_manifest_sha = (
                    sha256_file(
                        manifest_path
                    )
                )

                if (
                    actual_manifest_sha
                    != export[
                        "manifest_sha256"
                    ]
                ):
                    findings.append(
                        {
                            "severity": "ERROR",
                            "code": (
                                "MANIFEST_CHECKSUM_"
                                "MISMATCH"
                            ),
                            "message": (
                                "manifest.json checksum "
                                "does not match the "
                                "database record."
                            ),
                            "artifact_path": (
                                "manifest.json"
                            ),
                        }
                    )

                try:
                    manifest = json.loads(
                        manifest_path.read_text(
                            encoding="utf-8"
                        )
                    )
                except (
                    UnicodeDecodeError,
                    json.JSONDecodeError,
                ):
                    manifest = None

                    findings.append(
                        {
                            "severity": "ERROR",
                            "code": (
                                "MANIFEST_INVALID"
                            ),
                            "message": (
                                "manifest.json is not "
                                "valid UTF-8 JSON."
                            ),
                            "artifact_path": (
                                "manifest.json"
                            ),
                        }
                    )

                if isinstance(
                    manifest,
                    dict,
                ):
                    expected_artifacts = (
                        manifest.get(
                            "artifacts",
                            {},
                        )
                    )

                    if not isinstance(
                        expected_artifacts,
                        dict,
                    ):
                        findings.append(
                            {
                                "severity": "ERROR",
                                "code": (
                                    "MANIFEST_ARTIFACTS_"
                                    "INVALID"
                                ),
                                "message": (
                                    "Manifest artifact "
                                    "map is invalid."
                                ),
                                "artifact_path": (
                                    "manifest.json"
                                ),
                            }
                        )
                    else:
                        for (
                            relative_path,
                            expected,
                        ) in sorted(
                            expected_artifacts.items()
                        ):
                            artifact = (
                                output_directory
                                / relative_path
                            )

                            if not artifact.is_file():
                                findings.append(
                                    {
                                        "severity": (
                                            "ERROR"
                                        ),
                                        "code": (
                                            "ARTIFACT_"
                                            "MISSING"
                                        ),
                                        "message": (
                                            "Manifest "
                                            "artifact is "
                                            "missing."
                                        ),
                                        "artifact_path": (
                                            relative_path
                                        ),
                                    }
                                )
                                continue

                            if not isinstance(
                                expected,
                                dict,
                            ):
                                findings.append(
                                    {
                                        "severity": (
                                            "ERROR"
                                        ),
                                        "code": (
                                            "ARTIFACT_"
                                            "METADATA_"
                                            "INVALID"
                                        ),
                                        "message": (
                                            "Artifact "
                                            "metadata is "
                                            "invalid."
                                        ),
                                        "artifact_path": (
                                            relative_path
                                        ),
                                    }
                                )
                                continue

                            actual_sha = (
                                sha256_file(
                                    artifact
                                )
                            )

                            actual_size = (
                                artifact.stat()
                                .st_size
                            )

                            if (
                                actual_sha
                                != expected.get(
                                    "sha256"
                                )
                            ):
                                findings.append(
                                    {
                                        "severity": (
                                            "ERROR"
                                        ),
                                        "code": (
                                            "ARTIFACT_"
                                            "CHECKSUM_"
                                            "MISMATCH"
                                        ),
                                        "message": (
                                            "Artifact "
                                            "checksum does "
                                            "not match its "
                                            "manifest."
                                        ),
                                        "artifact_path": (
                                            relative_path
                                        ),
                                    }
                                )

                            if (
                                actual_size
                                != expected.get(
                                    "bytes"
                                )
                            ):
                                findings.append(
                                    {
                                        "severity": (
                                            "ERROR"
                                        ),
                                        "code": (
                                            "ARTIFACT_SIZE_"
                                            "MISMATCH"
                                        ),
                                        "message": (
                                            "Artifact size "
                                            "does not match "
                                            "its manifest."
                                        ),
                                        "artifact_path": (
                                            relative_path
                                        ),
                                    }
                                )

                        expected_names = set(
                            expected_artifacts
                        ) | {
                            "manifest.json",
                            "checksums.sha256",
                        }

                        actual_names = {
                            str(
                                path.relative_to(
                                    output_directory
                                )
                            )
                            for path
                            in output_directory.rglob(
                                "*"
                            )
                            if path.is_file()
                        }

                        unexpected = (
                            actual_names
                            - expected_names
                        )

                        for relative_path in sorted(
                            unexpected
                        ):
                            findings.append(
                                {
                                    "severity": (
                                        "ERROR"
                                    ),
                                    "code": (
                                        "UNEXPECTED_"
                                        "ARTIFACT"
                                    ),
                                    "message": (
                                        "Export contains "
                                        "an undeclared "
                                        "artifact."
                                    ),
                                    "artifact_path": (
                                        relative_path
                                    ),
                                }
                            )

            checksum_path = (
                output_directory
                / "checksums.sha256"
            )

            if not checksum_path.is_file():
                findings.append(
                    {
                        "severity": "ERROR",
                        "code": (
                            "CHECKSUM_FILE_MISSING"
                        ),
                        "message": (
                            "checksums.sha256 is missing."
                        ),
                        "artifact_path": (
                            "checksums.sha256"
                        ),
                    }
                )

        valid = not any(
            finding["severity"] == "ERROR"
            for finding in findings
        )

        timestamp = utc_now()

        with self.database.connect() as connection:
            connection.execute(
                """
                DELETE FROM
                    export_verification_findings
                WHERE export_id = ?
                """,
                (export_id,),
            )

            for finding in findings:
                connection.execute(
                    """
                    INSERT INTO
                        export_verification_findings(
                            id,
                            export_id,
                            severity,
                            code,
                            message,
                            artifact_path,
                            created_at
                        )
                    VALUES(?,?,?,?,?,?,?)
                    """,
                    (
                        new_id(
                            "export-finding"
                        ),
                        export_id,
                        finding[
                            "severity"
                        ],
                        finding["code"],
                        finding[
                            "message"
                        ],
                        finding[
                            "artifact_path"
                        ],
                        timestamp,
                    ),
                )

            connection.execute(
                """
                UPDATE exports
                SET status = ?,
                    verified_at = ?
                WHERE id = ?
                """,
                (
                    (
                        "VERIFIED"
                        if valid
                        else "INVALID"
                    ),
                    timestamp,
                    export_id,
                ),
            )

        return {
            "export_id": export_id,
            "valid": valid,
            "status": (
                "VERIFIED"
                if valid
                else "INVALID"
            ),
            "finding_count": len(
                findings
            ),
            "findings": findings,
            "verified_at": timestamp,
        }

    def get_export(
        self,
        export_id: str,
        include_findings: bool = True,
    ) -> dict[str, object]:
        with self.database.connect() as connection:
            row = connection.execute(
                """
                SELECT *
                FROM exports
                WHERE id = ?
                """,
                (export_id,),
            ).fetchone()

            if row is None:
                raise NotFoundError(
                    f"export not found: {export_id}"
                )

            findings = []

            if include_findings:
                findings = [
                    dict(item)
                    for item
                    in connection.execute(
                        """
                        SELECT *
                        FROM
                            export_verification_findings
                        WHERE export_id = ?
                        ORDER BY
                            severity,
                            code,
                            id
                        """,
                        (export_id,),
                    ).fetchall()
                ]

        result = dict(row)
        result["findings"] = findings
        result["artifacts"] = (
            self._list_export_artifacts(
                Path(
                    str(
                        result[
                            "output_path"
                        ]
                    )
                )
            )
        )

        return result

    def list_exports(
        self,
        project_id: str,
    ) -> list[dict[str, object]]:
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

            rows = connection.execute(
                """
                SELECT *
                FROM exports
                WHERE project_id = ?
                ORDER BY created_at DESC, id
                """,
                (project_id,),
            ).fetchall()

        return [
            dict(row)
            for row in rows
        ]

    def _build_bundle(
        self,
        project_id: str,
        plan: dict[str, object],
    ) -> dict[str, object]:
        with self.database.connect() as connection:
            project_row = connection.execute(
                """
                SELECT *
                FROM projects
                WHERE id = ?
                """,
                (project_id,),
            ).fetchone()

            if project_row is None:
                raise NotFoundError(
                    f"project not found: {project_id}"
                )

            documents = [
                dict(row)
                for row in connection.execute(
                    """
                    SELECT
                        id,
                        project_id,
                        title,
                        media_type,
                        sha256,
                        byte_count,
                        line_count,
                        created_at
                    FROM source_documents
                    WHERE project_id = ?
                    ORDER BY created_at, id
                    """,
                    (project_id,),
                ).fetchall()
            ]

            sections = [
                dict(row)
                for row in connection.execute(
                    """
                    SELECT section.*
                    FROM source_sections
                    AS section
                    JOIN source_documents
                    AS document
                      ON document.id =
                         section.document_id
                    WHERE document.project_id = ?
                    ORDER BY
                        section.document_id,
                        section.ordinal
                    """,
                    (project_id,),
                ).fetchall()
            ]

            atoms = [
                dict(row)
                for row in connection.execute(
                    """
                    SELECT *
                    FROM atoms
                    WHERE project_id = ?
                    ORDER BY
                        document_id,
                        ordinal,
                        id
                    """,
                    (project_id,),
                ).fetchall()
            ]

            dimensions = [
                dict(row)
                for row in connection.execute(
                    """
                    SELECT dimension.*
                    FROM atom_dimensions
                    AS dimension
                    JOIN atoms AS atom
                      ON atom.id =
                         dimension.atom_id
                    WHERE atom.project_id = ?
                    ORDER BY
                        dimension.atom_id,
                        dimension.dimension
                    """,
                    (project_id,),
                ).fetchall()
            ]

            claims = [
                dict(row)
                for row in connection.execute(
                    """
                    SELECT claim.*
                    FROM research_claims
                    AS claim
                    JOIN atoms AS atom
                      ON atom.id =
                         claim.atom_id
                    WHERE atom.project_id = ?
                    ORDER BY
                        claim.atom_id,
                        claim.dimension,
                        claim.id
                    """,
                    (project_id,),
                ).fetchall()
            ]

            evidence = [
                dict(row)
                for row in connection.execute(
                    """
                    SELECT evidence.*
                    FROM research_evidence
                    AS evidence
                    JOIN atoms AS atom
                      ON atom.id =
                         evidence.atom_id
                    WHERE atom.project_id = ?
                    ORDER BY
                        evidence.atom_id,
                        evidence.dimension,
                        evidence.id
                    """,
                    (project_id,),
                ).fetchall()
            ]

            claim_evidence = [
                dict(row)
                for row in connection.execute(
                    """
                    SELECT
                        relation.claim_id,
                        relation.evidence_id
                    FROM research_claim_evidence
                    AS relation
                    JOIN research_claims
                    AS claim
                      ON claim.id =
                         relation.claim_id
                    JOIN atoms AS atom
                      ON atom.id =
                         claim.atom_id
                    WHERE atom.project_id = ?
                    ORDER BY
                        relation.claim_id,
                        relation.evidence_id
                    """,
                    (project_id,),
                ).fetchall()
            ]

            bindings = [
                self._normalize_binding(
                    dict(row)
                )
                for row in connection.execute(
                    """
                    SELECT *
                    FROM integration_bindings
                    WHERE project_id = ?
                      AND enabled IS TRUE
                    ORDER BY
                        system_name,
                        binding_type,
                        id
                    """,
                    (project_id,),
                ).fetchall()
            ]

        project = dict(project_row)
        relations = (
            self.planning.list_relations(
                project_id
            )
        )

        sources_payload = {
            "documents": documents,
            "sections": sections,
        }

        authority_payload = {
            "relations": relations,
            "graph": plan[
                "authority_graph"
            ],
        }

        execution_payload = {
            "plan": {
                key: value
                for key, value in plan.items()
                if key
                not in {
                    "authority_graph",
                    "execution_graph",
                    "bindings",
                    "findings",
                    "ready_nodes",
                }
            },
            "graph": plan[
                "execution_graph"
            ],
            "bindings": plan[
                "bindings"
            ],
            "findings": plan[
                "findings"
            ],
            "ready_nodes": plan[
                "ready_nodes"
            ],
        }

        research_payload = {
            "dimensions": dimensions,
            "claims": claims,
            "evidence": evidence,
            "claim_evidence": (
                claim_evidence
            ),
        }

        traceability = (
            self._build_traceability(
                atoms=atoms,
                documents=documents,
                dimensions=dimensions,
                claims=claims,
                evidence=evidence,
                claim_evidence=(
                    claim_evidence
                ),
                plan_bindings=list(
                    plan["bindings"]
                ),
                relations=relations,
            )
        )

        handoff = self._build_handoff(
            project=project,
            plan=plan,
            traceability=traceability,
            bindings=bindings,
        )

        markdown = self._build_markdown(
            project=project,
            plan=plan,
            atoms=atoms,
            traceability=traceability,
            bindings=bindings,
        )

        artifact_values: dict[
            str,
            bytes,
        ] = {
            "project.json": (
                canonical_json_bytes(
                    project
                )
            ),
            "sources.json": (
                canonical_json_bytes(
                    sources_payload
                )
            ),
            "atoms.json": (
                canonical_json_bytes(
                    {"atoms": atoms}
                )
            ),
            "research.json": (
                canonical_json_bytes(
                    research_payload
                )
            ),
            "authority_graph.json": (
                canonical_json_bytes(
                    authority_payload
                )
            ),
            "execution_graph.json": (
                canonical_json_bytes(
                    execution_payload
                )
            ),
            "traceability.json": (
                canonical_json_bytes(
                    {
                        "items": (
                            traceability
                        )
                    }
                )
            ),
            "integration_bindings.json": (
                canonical_json_bytes(
                    {"bindings": bindings}
                )
            ),
            "atropos_handoff.json": (
                canonical_json_bytes(
                    handoff
                )
            ),
            "implementation_blueprint.md": (
                markdown.encode("utf-8")
            ),
        }

        artifact_metadata = {
            name: {
                "sha256": sha256_bytes(
                    content
                ),
                "bytes": len(content),
            }
            for name, content
            in sorted(
                artifact_values.items()
            )
        }

        bundle_fingerprint = (
            sha256_bytes(
                canonical_json_bytes(
                    artifact_metadata
                )
            )
        )

        manifest = {
            "schema": (
                "specgraph.export.manifest.v1"
            ),
            "export_type": EXPORT_TYPE,
            "project_id": project_id,
            "plan_id": plan["id"],
            "plan_input_fingerprint": (
                plan["input_fingerprint"]
            ),
            "bundle_fingerprint": (
                bundle_fingerprint
            ),
            "compiler_version_fingerprint": (
                "specgraph-v1"
            ),
            "artifact_count": len(
                artifact_values
            ),
            "artifacts": (
                artifact_metadata
            ),
        }

        return {
            "project": project,
            "artifacts": artifact_values,
            "artifact_checksums": {
                name: metadata[
                    "sha256"
                ]
                for name, metadata
                in artifact_metadata.items()
            },
            "bundle_fingerprint": (
                bundle_fingerprint
            ),
            "manifest": manifest,
        }

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
        document_by_id = {
            str(document["id"]): document
            for document in documents
        }

        dimensions_by_atom: dict[
            str,
            list[dict[str, object]],
        ] = {}

        for dimension in dimensions:
            dimensions_by_atom.setdefault(
                str(dimension["atom_id"]),
                [],
            ).append(dimension)

        claims_by_atom: dict[
            str,
            list[dict[str, object]],
        ] = {}

        for claim in claims:
            claims_by_atom.setdefault(
                str(claim["atom_id"]),
                [],
            ).append(claim)

        evidence_by_id = {
            str(item["id"]): item
            for item in evidence
        }

        evidence_ids_by_claim: dict[
            str,
            list[str],
        ] = {}

        for relation in claim_evidence:
            evidence_ids_by_claim.setdefault(
                str(relation["claim_id"]),
                [],
            ).append(
                str(
                    relation[
                        "evidence_id"
                    ]
                )
            )

        bindings_by_atom: dict[
            str,
            list[dict[str, object]],
        ] = {}

        for binding in plan_bindings:
            bindings_by_atom.setdefault(
                str(binding["atom_id"]),
                [],
            ).append(binding)

        outgoing_by_atom: dict[
            str,
            list[dict[str, object]],
        ] = {}

        incoming_by_atom: dict[
            str,
            list[dict[str, object]],
        ] = {}

        for relation in relations:
            outgoing_by_atom.setdefault(
                str(
                    relation[
                        "from_atom_id"
                    ]
                ),
                [],
            ).append(relation)

            incoming_by_atom.setdefault(
                str(
                    relation[
                        "to_atom_id"
                    ]
                ),
                [],
            ).append(relation)

        results = []

        for atom in atoms:
            atom_id = str(atom["id"])
            document = document_by_id.get(
                str(atom["document_id"])
            )

            atom_claims = []

            for claim in claims_by_atom.get(
                atom_id,
                [],
            ):
                claim_result = dict(claim)
                evidence_ids = (
                    evidence_ids_by_claim.get(
                        str(claim["id"]),
                        [],
                    )
                )

                claim_result[
                    "evidence"
                ] = [
                    evidence_by_id[
                        evidence_id
                    ]
                    for evidence_id
                    in evidence_ids
                    if evidence_id
                    in evidence_by_id
                ]

                atom_claims.append(
                    claim_result
                )

            results.append(
                {
                    "atom_id": atom_id,
                    "statement": atom[
                        "canonical_statement"
                    ],
                    "kind": atom["kind"],
                    "modality": atom[
                        "modality"
                    ],
                    "source": {
                        "document_id": atom[
                            "document_id"
                        ],
                        "document_title": (
                            document["title"]
                            if document
                            else None
                        ),
                        "document_sha256": (
                            document["sha256"]
                            if document
                            else None
                        ),
                        "exact_quote": atom[
                            "exact_quote"
                        ],
                        "byte_start": atom[
                            "byte_start"
                        ],
                        "byte_end": atom[
                            "byte_end"
                        ],
                        "line_start": atom[
                            "line_start"
                        ],
                        "line_end": atom[
                            "line_end"
                        ],
                        "quote_sha256": atom[
                            "source_sha256"
                        ],
                    },
                    "dimensions": (
                        dimensions_by_atom.get(
                            atom_id,
                            [],
                        )
                    ),
                    "claims": atom_claims,
                    "plan_nodes": (
                        bindings_by_atom.get(
                            atom_id,
                            [],
                        )
                    ),
                    "outgoing_relations": (
                        outgoing_by_atom.get(
                            atom_id,
                            [],
                        )
                    ),
                    "incoming_relations": (
                        incoming_by_atom.get(
                            atom_id,
                            [],
                        )
                    ),
                }
            )

        return results

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
        execution_graph = plan[
            "execution_graph"
        ]

        ready_node_ids = [
            node["id"]
            for node in plan[
                "ready_nodes"
            ]
        ]

        requirements = []

        for item in traceability:
            requirements.append(
                {
                    "atom_id": item[
                        "atom_id"
                    ],
                    "statement": item[
                        "statement"
                    ],
                    "kind": item["kind"],
                    "modality": item[
                        "modality"
                    ],
                    "source": item[
                        "source"
                    ],
                    "plan_nodes": item[
                        "plan_nodes"
                    ],
                }
            )

        return {
            "schema": (
                "specgraph.atropos.handoff.v1"
            ),
            "producer": (
                "specgraph-foundry"
            ),
            "project": {
                "id": project["id"],
                "slug": project["slug"],
                "name": project["name"],
            },
            "plan": {
                "id": plan["id"],
                "status": plan["status"],
                "input_fingerprint": (
                    plan[
                        "input_fingerprint"
                    ]
                ),
                "authority_graph_id": (
                    plan[
                        "authority_graph_id"
                    ]
                ),
                "execution_graph_id": (
                    plan[
                        "execution_graph_id"
                    ]
                ),
            },
            "execution": {
                "graph_id": (
                    execution_graph["id"]
                ),
                "nodes": (
                    execution_graph["nodes"]
                ),
                "edges": (
                    execution_graph["edges"]
                ),
                "ready_node_ids": (
                    ready_node_ids
                ),
            },
            "requirements": requirements,
            "integration_bindings": (
                bindings
            ),
            "routing_law": [
                "LOCAL_TOOLCHAIN",
                "FREE_READY_PROVIDER",
                "FREE_FALLBACK_PROVIDER",
                "COOLDOWN_QUEUE",
                "OFFLINE_DEGRADED_MODE",
                (
                    "PAID_EMERGENCY_ONLY_"
                    "BY_EXPLICIT_UNLOCK"
                ),
            ],
            "execution_contract": {
                "authority_owner": (
                    "specgraph-foundry"
                ),
                "runtime_owner": (
                    "atropos"
                ),
                "source_authority_is_immutable": (
                    True
                ),
                "execution_graph_must_be_acyclic": (
                    True
                ),
                "implementation_requires_"
                "verification": True,
            },
        }

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
        lines = [
            (
                f"# Implementation Blueprint: "
                f"{project['name']}"
            ),
            "",
            "## Identity",
            "",
            f"- Project ID: `{project['id']}`",
            f"- Project slug: `{project['slug']}`",
            f"- Plan ID: `{plan['id']}`",
            (
                "- Plan fingerprint: "
                f"`{plan['input_fingerprint']}`"
            ),
            f"- Plan status: `{plan['status']}`",
            "",
            "## Scope",
            "",
            (
                f"- Atomic requirements: "
                f"{len(atoms)}"
            ),
            (
                f"- Execution nodes: "
                f"{plan['node_count']}"
            ),
            (
                f"- Execution edges: "
                f"{plan['edge_count']}"
            ),
            (
                f"- Open research dimensions: "
                f"{plan['open_dimension_count']}"
            ),
            "",
            "## Atomic Requirements",
            "",
        ]

        for index, item in enumerate(
            traceability,
            start=1,
        ):
            source = item["source"]

            lines.extend(
                [
                    (
                        f"### {index}. "
                        f"{item['statement']}"
                    ),
                    "",
                    (
                        f"- Atom: "
                        f"`{item['atom_id']}`"
                    ),
                    (
                        f"- Kind: "
                        f"`{item['kind']}`"
                    ),
                    (
                        f"- Modality: "
                        f"`{item['modality']}`"
                    ),
                    (
                        "- Source document: "
                        f"`{source['document_id']}`"
                    ),
                    (
                        "- Source bytes: "
                        f"`{source['byte_start']}:"
                        f"{source['byte_end']}`"
                    ),
                    (
                        "- Source lines: "
                        f"`{source['line_start']}:"
                        f"{source['line_end']}`"
                    ),
                    (
                        "- Exact quote: "
                        f"{json.dumps(source['exact_quote'])}"
                    ),
                    "",
                    "Execution stages:",
                    "",
                ]
            )

            for binding in item[
                "plan_nodes"
            ]:
                lines.append(
                    (
                        f"- `{binding['stage']}` "
                        f"→ `{binding['graph_node_id']}`"
                    )
                )

            lines.append("")

        lines.extend(
            [
                "## Integration Bindings",
                "",
            ]
        )

        if bindings:
            for binding in bindings:
                lines.append(
                    (
                        f"- `{binding['system_name']}` "
                        f"as "
                        f"`{binding['binding_type']}`"
                    )
                )
        else:
            lines.append(
                "- No enabled integration bindings."
            )

        lines.extend(
            [
                "",
                "## Verification Contract",
                "",
                (
                    "- Every implementation node must "
                    "have a predecessor contract node."
                ),
                (
                    "- Every implementation node must "
                    "have a successor verification node."
                ),
                (
                    "- Execution dependencies must "
                    "remain acyclic."
                ),
                (
                    "- Source quotes and coordinates "
                    "must remain traceable."
                ),
                (
                    "- Runtime evidence must not "
                    "replace source authority."
                ),
                "",
            ]
        )

        return "\n".join(lines)

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
    def _normalize_binding(
        record: dict[str, object],
    ) -> dict[str, object]:
        config_json = record.pop(
            "config_json",
            "{}",
        )

        record["config"] = json.loads(
            str(config_json)
        )

        record["enabled"] = bool(
            record["enabled"]
        )

        return record

    @staticmethod
    def _list_export_artifacts(
        directory: Path,
    ) -> list[dict[str, object]]:
        if not directory.is_dir():
            return []

        artifacts = []

        for path in sorted(
            directory.rglob("*")
        ):
            if not path.is_file():
                continue

            artifacts.append(
                {
                    "path": str(
                        path.relative_to(
                            directory
                        )
                    ),
                    "bytes": (
                        path.stat().st_size
                    ),
                    "sha256": (
                        sha256_file(path)
                    ),
                }
            )

        return artifacts
