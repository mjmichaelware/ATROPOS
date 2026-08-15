"""Reading a durable export back: verification, download and manifest.

Read-only. Verification belongs here rather than with the writer because it must
work on an export this process did not create.
"""

from __future__ import annotations

from .durable_export_helpers import manifest_row
from .durable_export_helpers import mark_invalid
from .durable_export_helpers import mark_verified
from .durable_export_helpers import stored_artifacts
from ..export_proof import sha256_file
from .durable_export_models import ArtifactLimitExceededError
from .durable_export_models import ArtifactNotVerifiedError
from .durable_export_models import MANIFEST_VERSION
from .durable_export_models import *  # noqa: F401,F403
from .durable_export_helpers import *  # noqa: F401,F403
from ..errors import NotFoundError, ValidationError
import tempfile
from .artifact_storage import ArtifactIntegrityError
from .artifact_storage import ArtifactStorageUnavailableError
from .artifact_storage import StoredArtifact
from .artifact_storage import artifact_object_path
from .artifact_storage import media_type_for_name
from .artifact_storage import sha256_bytes
from .artifact_storage import validate_artifact_name
from .concurrency import canonical_json_bytes
from .idempotency import utc_now
from .operation_models import OperationCancelled
from collections.abc import Callable
from pathlib import Path
import json


def verify_export(
    service,
    *,
    owner_id: str,
    authorization: str,
    export_id: str,
    on_progress: Callable[[int, int], None] | None = None,
) -> dict[str, object]:
    manifest = manifest_row(service, 
        owner_id=owner_id,
        export_id=export_id,
    )
    artifacts = stored_artifacts(service, 
        manifest,
    )
    total_steps = max(1, len(artifacts))
    completed_steps = 0

    try:
        for artifact in artifacts:
            service.storage.verified_download(
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
        mark_invalid(service, export_id)
        raise
    except OperationCancelled:
        mark_invalid(service, export_id)
        raise

    mark_verified(service, export_id)
    return {
        "export_id": export_id,
        "valid": True,
        "status": "VERIFIED",
        "finding_count": 0,
        "findings": [],
        "verified_at": utc_now(),
        "artifact_manifest": manifest_summary(service, export_id),
    }


def get_export(
    service,
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
            manifest_row(service, 
                owner_id=owner_id,
                export_id=export_id,
            )
        except NotFoundError:
            pass
    export = service.exports.get_export(export_id)
    export["output_path"] = None
    export["artifacts"] = []
    try:
        export["artifact_manifest"] = manifest_summary(service, 
            export_id
        )
    except NotFoundError:
        export["artifact_manifest"] = None
    return export


def download(
    service,
    *,
    owner_id: str,
    authorization: str,
    export_id: str,
) -> dict[str, object]:
    try:
        manifest = manifest_row(service, 
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
        signed = service.storage.signed_download(
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
        "expires_in": service.storage.settings.download_ttl_seconds,
        "artifacts": artifacts,
    }


def read_artifacts(
    service,
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
        if total > service.storage.settings.max_artifact_bytes:
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


def build_manifest(
    service,
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


def manifest_summary(
    service,
    export_id: str,
) -> dict[str, object]:
    with service.database.connect() as connection:
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
