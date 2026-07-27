from __future__ import annotations

import json
import shutil
import tempfile
import uuid
from dataclasses import dataclass
from datetime import UTC, datetime
from pathlib import Path
from typing import Callable

from ..database import Database
from ..errors import NotFoundError, ValidationError
from ..execution import ExecutionService
from ..exports import ExportService, canonical_json_bytes, sha256_file
from .artifact_storage import (
    ArtifactAlreadyExistsError,
    ArtifactIntegrityError,
    ArtifactStorageClient,
    ArtifactStoragePermanentError,
    ArtifactStorageUnavailableError,
    StoredArtifact,
    artifact_object_path,
    media_type_for_name,
    sha256_bytes,
    validate_artifact_name,
)
from .operations import OperationCancelled


MANIFEST_VERSION = "specgraph.artifact.manifest.v1"


class ArtifactNotVerifiedError(RuntimeError):
    pass


class ArtifactLimitExceededError(RuntimeError):
    pass


def utc_now() -> str:
    return datetime.now(UTC).isoformat()


@dataclass(frozen=True)
class DurableExportResult:
    status: int
    body: dict[str, object]


class DurableExportService:
    def __init__(
        self,
        database: Database,
        storage: ArtifactStorageClient,
    ) -> None:
        self.database = database
        self.storage = storage
        self.exports = ExportService(database)
        self.execution = ExecutionService(database)

    def export_plan(
        self,
        *,
        owner_id: str,
        authorization: str,
        plan_id: str,
        on_progress: Callable[[int, int], None] | None = None,
    ) -> dict[str, object]:
        with tempfile.TemporaryDirectory(
            prefix="specgraph-export-"
        ) as root:
            export = self.exports.export_plan(
                plan_id,
                Path(root),
            )
            export_id = str(export["id"])
            output_path = Path(str(export["output_path"]))
            artifacts = self._read_artifacts(
                output_path,
                owner_id=owner_id,
                project_id=str(export["project_id"]),
                export_id=export_id,
            )
            manifest = self._manifest(
                export=export,
                artifacts=artifacts,
            )

            self._persist_initial(
                owner_id=owner_id,
                project_id=str(export["project_id"]),
                export_id=export_id,
                artifacts=artifacts,
                manifest=manifest,
            )

            # Every artifact needs one upload call and one verified-download
            # call - each a real HTTP round trip to Supabase Storage. With
            # 12 artifacts per export that's up to 24 sequential requests,
            # which can comfortably exceed a single operation lease window
            # if nothing renews the lease in between. Reporting progress
            # after every individual request both keeps the lease alive
            # (each checkpoint call renews it) and gives real, granular
            # status instead of one static "exporting" message for however
            # long this loop takes.
            total_steps = max(1, len(artifacts) * 2)
            completed_steps = 0

            def report() -> None:
                nonlocal completed_steps
                completed_steps += 1
                if on_progress is not None:
                    on_progress(completed_steps, total_steps)

            try:
                for artifact in artifacts:
                    try:
                        self.storage.upload(
                            authorization=authorization,
                            artifact=artifact,
                        )
                    except ArtifactAlreadyExistsError:
                        # A previous failed attempt partially uploaded
                        # artifacts for this export_id. Verify the
                        # existing object has the right content - the
                        # outer except will fire if it doesn't match.
                        self.storage.verified_download(
                            authorization=authorization,
                            artifact=artifact,
                        )
                    report()

                self._mark_objects(
                    export_id,
                    "STORED",
                )

                for artifact in artifacts:
                    self.storage.verified_download(
                        authorization=authorization,
                        artifact=artifact,
                    )
                    report()
            except (
                ArtifactIntegrityError,
                ArtifactStoragePermanentError,
                ArtifactStorageUnavailableError,
            ):
                self._mark_invalid(export_id)
                raise
            except OperationCancelled:
                # report() calls context.checkpoint() after every artifact
                # now, which can raise OperationCancelled mid-loop if
                # cancellation was requested while artifacts were still
                # being uploaded/verified. Without this, that exception
                # skipped both except clauses above and left the export
                # row and manifest stuck in CREATED/GENERATED/STORED with
                # only some objects uploaded - never VERIFIED, never
                # INVALID, and not retried, since the operation itself
                # (a separate row) is already terminal (CANCELLED).
                self._mark_invalid(export_id)
                raise

            self._mark_verified(export_id)
            shutil.rmtree(output_path, ignore_errors=True)

        return self.get_export(export_id)

    def verify_export(
        self,
        *,
        owner_id: str,
        authorization: str,
        export_id: str,
        on_progress: Callable[[int, int], None] | None = None,
    ) -> dict[str, object]:
        manifest = self._manifest_row(
            owner_id=owner_id,
            export_id=export_id,
        )
        artifacts = self._stored_artifacts(
            manifest,
        )
        total_steps = max(1, len(artifacts))
        completed_steps = 0

        try:
            for artifact in artifacts:
                self.storage.verified_download(
                    authorization=authorization,
                    artifact=artifact,
                )
                completed_steps += 1
                if on_progress is not None:
                    on_progress(completed_steps, total_steps)
        except (
            ArtifactIntegrityError,
            ArtifactStorageUnavailableError,
        ):
            self._mark_invalid(export_id)
            raise
        except OperationCancelled:
            self._mark_invalid(export_id)
            raise

        self._mark_verified(export_id)
        return {
            "export_id": export_id,
            "valid": True,
            "status": "VERIFIED",
            "finding_count": 0,
            "findings": [],
            "verified_at": utc_now(),
            "artifact_manifest": self._manifest_summary(export_id),
        }

    def get_export(
        self,
        export_id: str,
        owner_id: str | None = None,
    ) -> dict[str, object]:
        if owner_id is not None:
            # Exports created before durable artifact storage/manifests
            # existed (or through any path that never persisted a manifest
            # row) have no artifact_manifests row at all - list_exports()
            # already tolerates that below. Treating "no manifest row" as
            # "not authorized" here would incorrectly 404 a real, valid
            # export instead of just skipping this best-effort ownership
            # cross-check; the actual export lookup right below applies no
            # owner filtering of its own.
            try:
                self._manifest_row(
                    owner_id=owner_id,
                    export_id=export_id,
                )
            except NotFoundError:
                pass
        export = self.exports.get_export(export_id)
        export["output_path"] = None
        export["artifacts"] = []
        try:
            export["artifact_manifest"] = self._manifest_summary(
                export_id
            )
        except NotFoundError:
            export["artifact_manifest"] = None
        return export

    def list_exports(
        self,
        project_id: str,
    ) -> list[dict[str, object]]:
        items = self.exports.list_exports(project_id)
        for item in items:
            item["output_path"] = None
            try:
                item["artifact_manifest"] = self._manifest_summary(
                    str(item["id"])
                )
            except NotFoundError:
                item["artifact_manifest"] = None
        return items

    def download(
        self,
        *,
        owner_id: str,
        authorization: str,
        export_id: str,
    ) -> dict[str, object]:
        try:
            manifest = self._manifest_row(
                owner_id=owner_id,
                export_id=export_id,
            )
        except NotFoundError:
            raise ValidationError(
                "This export was created before cloud downloads were available. "
                "Generate a new export to download the build plan."
            )
        if str(manifest["state"]) != "VERIFIED":
            raise ArtifactNotVerifiedError(
                "artifact manifest is not verified"
            )

        artifacts = []
        manifest_json = json.loads(str(manifest["manifest_json"]))

        for entry in manifest_json["artifacts"]:
            signed = self.storage.signed_download(
                authorization=authorization,
                object_path=str(entry["object_path"]),
            )
            artifacts.append(
                {
                    "name": entry["name"],
                    "media_type": entry["media_type"],
                    "byte_length": entry["byte_length"],
                    "sha256": entry["sha256"],
                    "signed_download_url": signed.url,
                    "expires_at": signed.expires_at,
                }
            )

        return {
            "export_id": export_id,
            "manifest_id": str(manifest["id"]),
            "expires_in": self.storage.settings.download_ttl_seconds,
            "artifacts": artifacts,
        }

    def assert_execution_ready(
        self,
        *,
        owner_id: str,
        export_id: str | None,
    ) -> None:
        if export_id is None:
            return
        manifest = self._manifest_row(
            owner_id=owner_id,
            export_id=export_id,
        )
        if str(manifest["state"]) != "VERIFIED":
            raise ArtifactNotVerifiedError(
                "artifact manifest is not verified"
            )

    def start_execution_run(
        self,
        *,
        owner_id: str,
        plan_id: str,
        runtime_system: str,
        runtime_run_id: str,
        export_id: str | None,
    ) -> dict[str, object]:
        self.assert_execution_ready(
            owner_id=owner_id,
            export_id=export_id,
        )
        return self.execution.start_run(
            plan_id=plan_id,
            runtime_system=runtime_system,
            runtime_run_id=runtime_run_id,
            export_id=export_id,
        )

    def _read_artifacts(
        self,
        output_path: Path,
        *,
        owner_id: str,
        project_id: str,
        export_id: str,
    ) -> list[StoredArtifact]:
        if not output_path.is_dir():
            raise ValidationError("export output directory is missing")

        artifacts: list[StoredArtifact] = []
        total = 0

        for path in sorted(output_path.rglob("*")):
            if path.is_symlink() or not path.is_file():
                raise ValidationError("export contains an invalid artifact")
            name = validate_artifact_name(
                str(path.relative_to(output_path))
            )
            data = path.read_bytes()
            total += len(data)
            if total > self.storage.settings.max_artifact_bytes:
                raise ArtifactLimitExceededError(
                    "export artifacts exceed configured limit"
                )
            object_path = artifact_object_path(
                owner_id=owner_id,
                project_id=project_id,
                export_id=export_id,
                artifact_name=name,
            )
            artifacts.append(
                StoredArtifact(
                    name=name,
                    media_type=media_type_for_name(name),
                    byte_length=len(data),
                    sha256=sha256_file(path),
                    object_path=object_path,
                    data=data,
                )
            )

        if not artifacts:
            raise ValidationError("export contains no artifacts")
        return artifacts

    def _manifest(
        self,
        *,
        export: dict[str, object],
        artifacts: list[StoredArtifact],
    ) -> dict[str, object]:
        entries = [
            {
                "name": artifact.name,
                "media_type": artifact.media_type,
                "byte_length": artifact.byte_length,
                "sha256": artifact.sha256,
                "object_path": artifact.object_path,
            }
            for artifact in sorted(
                artifacts,
                key=lambda item: item.name,
            )
        ]
        checksum_material = {
            "manifest_version": MANIFEST_VERSION,
            "export_id": str(export["id"]),
            "bundle_fingerprint": str(export["bundle_fingerprint"]),
            "artifacts": entries,
        }
        return {
            **checksum_material,
            "aggregate_sha256": sha256_bytes(
                canonical_json_bytes(checksum_material)
            ),
            "total_bytes": sum(
                artifact.byte_length
                for artifact in artifacts
            ),
            "artifact_count": len(artifacts),
        }

    def _persist_initial(
        self,
        *,
        owner_id: str,
        project_id: str,
        export_id: str,
        artifacts: list[StoredArtifact],
        manifest: dict[str, object],
    ) -> None:
        now = utc_now()
        manifest_id = str(uuid.uuid4())

        with self.database.connect() as connection:
            existing = connection.execute(
                """
                SELECT id
                FROM artifact_manifests
                WHERE export_id = ?
                """,
                (export_id,),
            ).fetchone()
            if existing is not None:
                return

            for artifact in artifacts:
                connection.execute(
                    """
                    INSERT INTO storage_objects(
                        id,
                        owner_id,
                        project_id,
                        bucket,
                        object_path,
                        media_type,
                        byte_length,
                        sha256,
                        state,
                        created_at
                    )
                    VALUES(?,?,?,?,?,?,?,?,?,?)
                    """,
                    (
                        str(uuid.uuid4()),
                        owner_id,
                        project_id,
                        self.storage.settings.bucket,
                        artifact.object_path,
                        artifact.media_type,
                        artifact.byte_length,
                        artifact.sha256,
                        "PENDING",
                        now,
                    ),
                )

            connection.execute(
                """
                INSERT INTO artifact_manifests(
                    id,
                    owner_id,
                    project_id,
                    export_id,
                    manifest_version,
                    state,
                    aggregate_sha256,
                    total_bytes,
                    artifact_count,
                    manifest_json,
                    created_at
                )
                VALUES(?,?,?,?,?,?,?,?,?,?,?)
                """,
                (
                    manifest_id,
                    owner_id,
                    project_id,
                    export_id,
                    MANIFEST_VERSION,
                    "GENERATED",
                    str(manifest["aggregate_sha256"]),
                    int(manifest["total_bytes"]),
                    int(manifest["artifact_count"]),
                    json.dumps(
                        manifest,
                        sort_keys=True,
                        separators=(",", ":"),
                        ensure_ascii=False,
                    ),
                    now,
                ),
            )
            connection.execute(
                """
                UPDATE exports
                SET output_path = ?,
                    status = 'CREATED'
                WHERE id = ?
                """,
                (
                    f"durable://{self.storage.settings.bucket}/{export_id}",
                    export_id,
                ),
            )

    def _mark_objects(
        self,
        export_id: str,
        state: str,
    ) -> None:
        with self.database.connect() as connection:
            rows = connection.execute(
                """
                SELECT manifest_json
                FROM artifact_manifests
                WHERE export_id = ?
                """,
                (export_id,),
            ).fetchone()
            if rows is None:
                return
            manifest = json.loads(str(rows["manifest_json"]))
            paths = [
                str(item["object_path"])
                for item in manifest["artifacts"]
            ]
            for object_path in paths:
                connection.execute(
                    """
                    UPDATE storage_objects
                    SET state = ?
                    WHERE object_path = ?
                    """,
                    (state, object_path),
                )
            if state == "STORED":
                connection.execute(
                    """
                    UPDATE artifact_manifests
                    SET state = 'STORED'
                    WHERE export_id = ?
                    """,
                    (export_id,),
                )

    def _mark_verified(
        self,
        export_id: str,
    ) -> None:
        now = utc_now()
        paths = self._object_paths(export_id)
        with self.database.connect() as connection:
            for object_path in paths:
                connection.execute(
                    """
                    UPDATE storage_objects
                    SET state = 'VERIFIED',
                        verified_at = ?
                    WHERE object_path = ?
                    """,
                    (now, object_path),
                )
            connection.execute(
                """
                UPDATE artifact_manifests
                SET state = 'VERIFIED',
                    verified_at = ?
                WHERE export_id = ?
                """,
                (now, export_id),
            )
            connection.execute(
                """
                UPDATE exports
                SET status = 'VERIFIED',
                    verified_at = ?
                WHERE id = ?
                """,
                (now, export_id),
            )

    def _mark_invalid(
        self,
        export_id: str,
    ) -> None:
        paths = self._object_paths(export_id)
        with self.database.connect() as connection:
            for object_path in paths:
                connection.execute(
                    """
                    UPDATE storage_objects
                    SET state = 'INVALID'
                    WHERE object_path = ?
                    """,
                    (object_path,),
                )
            connection.execute(
                """
                UPDATE artifact_manifests
                SET state = 'INVALID'
                WHERE export_id = ?
                """,
                (export_id,),
            )
            connection.execute(
                """
                UPDATE exports
                SET status = 'INVALID'
                WHERE id = ?
                """,
                (export_id,),
            )

    def _object_paths(
        self,
        export_id: str,
    ) -> list[str]:
        with self.database.connect() as connection:
            row = connection.execute(
                """
                SELECT manifest_json
                FROM artifact_manifests
                WHERE export_id = ?
                """,
                (export_id,),
            ).fetchone()
        if row is None:
            return []
        manifest = json.loads(str(row["manifest_json"]))
        return [
            str(item["object_path"])
            for item in manifest["artifacts"]
        ]

    def _manifest_row(
        self,
        *,
        owner_id: str,
        export_id: str,
    ):
        with self.database.connect() as connection:
            row = connection.execute(
                """
                SELECT *
                FROM artifact_manifests
                WHERE export_id = ?
                  AND owner_id = ?
                """,
                (export_id, owner_id),
            ).fetchone()
        if row is None:
            raise NotFoundError(
                f"artifact manifest not found: {export_id}"
            )
        return row

    def _manifest_summary(
        self,
        export_id: str,
    ) -> dict[str, object]:
        with self.database.connect() as connection:
            row = connection.execute(
                """
                SELECT *
                FROM artifact_manifests
                WHERE export_id = ?
                """,
                (export_id,),
            ).fetchone()
        if row is None:
            raise NotFoundError(
                f"artifact manifest not found: {export_id}"
            )
        manifest = json.loads(str(row["manifest_json"]))
        preview = [
            {
                key: item[key]
                for key in (
                    "name",
                    "media_type",
                    "byte_length",
                    "sha256",
                )
            }
            for item in manifest["artifacts"][:5]
        ]
        return {
            "id": str(row["id"]),
            "state": str(row["state"]),
            "manifest_version": str(row["manifest_version"]),
            "aggregate_sha256": str(row["aggregate_sha256"]),
            "total_bytes": int(row["total_bytes"]),
            "artifact_count": int(row["artifact_count"]),
            "artifacts_preview": preview,
            "artifacts_has_more": int(row["artifact_count"]) > len(preview),
            "verified_at": row["verified_at"],
        }

    def _stored_artifacts(
        self,
        manifest_row,
    ) -> list[StoredArtifact]:
        manifest = json.loads(str(manifest_row["manifest_json"]))
        return [
            StoredArtifact(
                name=str(item["name"]),
                media_type=str(item["media_type"]),
                byte_length=int(item["byte_length"]),
                sha256=str(item["sha256"]),
                object_path=str(item["object_path"]),
                data=b"",
            )
            for item in manifest["artifacts"]
        ]
