"""Reading exports back: the record, the list, and the artifacts on disk.

Read-only, and separated for the same reason as the execution queries -- a
module that both writes exports and reads them makes it impossible to tell at a
glance which a given function does.
"""

from __future__ import annotations

import json
import sqlite3
from pathlib import Path

from .database import Database
from .errors import NotFoundError
from .export_proof import sha256_file
from .export_schema import EXPORT_TYPE


def get_export(
    database: Database,
    export_id: str,
    include_findings: bool = True,
) -> dict[str, object]:
    with database.connect() as connection:
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
        list_export_artifacts(
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
    database: Database,
    project_id: str,
) -> list[dict[str, object]]:
    with database.connect() as connection:
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


def list_export_artifacts(
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


def find_export(
    database: Database,
    plan_id: str,
    fingerprint: str,
) -> sqlite3.Row | None:
    with database.connect() as connection:
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
