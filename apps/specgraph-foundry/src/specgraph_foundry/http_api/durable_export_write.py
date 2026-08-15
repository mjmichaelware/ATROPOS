"""Producing a durable export and recording what happened to it.

Writes the artifacts, then marks the export verified or invalid. The marking is
here rather than beside verification because a half-written export must be
marked invalid by whoever was writing it -- nobody else knows it failed.
"""

from __future__ import annotations

from .durable_export_helpers import mark_invalid
from .durable_export_helpers import mark_verified
from .durable_export_read import build_manifest, stored_artifacts
from .durable_export_models import MANIFEST_VERSION
from .durable_export_models import *  # noqa: F401,F403
from .durable_export_helpers import *  # noqa: F401,F403
from ..errors import NotFoundError, ValidationError
import tempfile
from .artifact_storage import ArtifactAlreadyExistsError
from .artifact_storage import ArtifactIntegrityError
from .artifact_storage import ArtifactStoragePermanentError
from .artifact_storage import ArtifactStorageUnavailableError
from .artifact_storage import StoredArtifact
from .durable_export_read import read_artifacts
from .idempotency import utc_now
from .operation_models import OperationCancelled
from collections.abc import Callable
from pathlib import Path
import shutil
import uuid
import json


def export_plan(
    service,
    *,
    owner_id: str,
    authorization: str,
    plan_id: str,
    on_progress: Callable[[int, int], None] | None = None,
) -> dict[str, object]:
    with tempfile.TemporaryDirectory(
        prefix="specgraph-export-"
    ) as root:
        export = service.exports.export_plan(
            plan_id,
            Path(root),
        )
        export_id = str(export["id"])
        output_path = Path(str(export["output_path"]))
        artifacts = read_artifacts(service, 
            output_path,
            owner_id=owner_id,
            project_id=str(export["project_id"]),
            export_id=export_id,
        )
        manifest = build_manifest(service, 
            export=export,
            artifacts=artifacts,
        )

        persist_initial(service, 
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
                    service.storage.upload(
                        authorization=authorization,
                        artifact=artifact,
                    )
                except ArtifactAlreadyExistsError:
                    # A previous failed attempt partially uploaded
                    # artifacts for this export_id. Verify the
                    # existing object has the right content - the
                    # outer except will fire if it doesn't match.
                    service.storage.verified_download(
                        authorization=authorization,
                        artifact=artifact,
                    )
                report()

            mark_objects(service, 
                export_id,
                "STORED",
            )

            for artifact in artifacts:
                service.storage.verified_download(
                    authorization=authorization,
                    artifact=artifact,
                )
                report()
        except (
            ArtifactIntegrityError,
            ArtifactStoragePermanentError,
            ArtifactStorageUnavailableError,
        ):
            mark_invalid(service, export_id)
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
            mark_invalid(service, export_id)
            raise

        mark_verified(service, export_id)
        shutil.rmtree(output_path, ignore_errors=True)

    return service.get_export(export_id)


def persist_initial(
    service,
    *,
    owner_id: str,
    project_id: str,
    export_id: str,
    artifacts: list[StoredArtifact],
    manifest: dict[str, object],
) -> None:
    now = utc_now()
    manifest_id = str(uuid.uuid4())

    with service.database.connect() as connection:
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
                    service.storage.settings.bucket,
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
                f"durable://{service.storage.settings.bucket}/{export_id}",
                export_id,
            ),
        )


def mark_objects(
    service,
    export_id: str,
    state: str,
) -> None:
    with service.database.connect() as connection:
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




