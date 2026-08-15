"""Creating an export: bundle, write, record, verify.

`export_plan` is the whole write path in one place -- it builds the bundle,
writes every artifact to disk, records the export row, and immediately verifies
what it just wrote. That last step is the reason it is worth isolating: an
export that is not verified at the moment of creation is an export whose
guarantee was never established at all.
"""

from __future__ import annotations

import os
import shutil
import uuid
import json
import sqlite3
from pathlib import Path

from .database import Database
from .errors import ConflictError, NotFoundError, ValidationError
from .export_bundle import build_bundle
from .export_proof import canonical_json_bytes, sha256_bytes
from .export_queries import find_export, get_export
from .export_schema import EXPORT_TYPE
from .export_verification import verify_export
from .planning import PlanningService
from .primitives import new_id, utc_now


def export_plan(
    database: Database,
    planning: PlanningService,
    plan_id: str,
    output_root: Path | None = None,
) -> dict[str, object]:
    plan = planning.get_plan(
        plan_id
    )

    if plan["status"] != "VERIFIED":
        raise ValidationError(
            "only VERIFIED plans may be exported"
        )

    project_id = str(
        plan["project_id"]
    )

    bundle = build_bundle(database, planning, 
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

    existing = find_export(database, 
        plan_id,
        bundle_fingerprint,
    )

    if existing is not None:
        output_path = Path(
            str(existing["output_path"])
        )

        if output_path.is_dir():
            verification = (
                verify_export(database, 
                    str(existing["id"])
                )
            )

            if verification["valid"]:
                return get_export(database, 
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
        with database.connect() as connection:
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

    verify_export(database, export_id)
    return get_export(database, export_id)
