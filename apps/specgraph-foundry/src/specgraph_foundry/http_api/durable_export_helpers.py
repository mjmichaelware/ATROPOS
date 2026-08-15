"""Paths, manifest rows and digests shared by the export writer and reader."""

from __future__ import annotations

from .artifact_storage import StoredArtifact
from .idempotency import utc_now
from .durable_export_models import *  # noqa: F401,F403
import hashlib
import json
import tempfile
from pathlib import Path

from ..errors import NotFoundError, ValidationError

def object_paths(
    service,
    export_id: str,
) -> list[str]:
    with service.database.connect() as connection:
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


def manifest_row(
    service,
    *,
    owner_id: str,
    export_id: str,
):
    with service.database.connect() as connection:
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


def stored_artifacts(
    service,
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


def mark_verified(
    service,
    export_id: str,
) -> None:
    now = utc_now()
    paths = object_paths(service, export_id)
    with service.database.connect() as connection:
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


def mark_invalid(
    service,
    export_id: str,
) -> None:
    paths = object_paths(service, export_id)
    with service.database.connect() as connection:
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
