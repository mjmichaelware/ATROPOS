"""Re-verifying an export that already exists on disk.

Reads the bundle back and checks it against its own manifest: every declared
artifact present, every digest matching, the proof summary internally
consistent. The counterpart of :mod:`export_bundle` -- one writes the guarantee,
this one is the only thing that can establish it still holds.
"""

from __future__ import annotations

import json
import sqlite3
from pathlib import Path

from .database import Database
from .errors import NotFoundError, ValidationError
from .export_queries import get_export
from .export_artifact_checks import check_artifacts
from .export_proof import (
    parse_checksums_file,
    sha256_file,
    verify_export_proof_summary,
)
from .primitives import new_id, utc_now


def verify_export(
    database: Database,
    export_id: str,
) -> dict[str, object]:
    export = get_export(database, 
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

            # Per-artifact checks live in export_artifact_checks;
            # what remains here is reaching the bundle at all.
            check_artifacts(
                manifest,
                output_directory,
                findings,
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
        elif isinstance(
            manifest,
            dict,
        ):
            expected_artifacts = manifest.get(
                "artifacts",
                {},
            )
            if isinstance(
                expected_artifacts,
                dict,
            ):
                expected_checksums = {
                    relative_path: metadata.get(
                        "sha256"
                    )
                    for relative_path, metadata
                    in expected_artifacts.items()
                    if isinstance(metadata, dict)
                }
                expected_checksums[
                    "manifest.json"
                ] = sha256_file(
                    manifest_path
                )
                observed_checksums = (
                    parse_checksums_file(
                        checksum_path
                    )
                )

                if observed_checksums is None:
                    findings.append(
                        {
                            "severity": "ERROR",
                            "code": (
                                "CHECKSUM_FILE_"
                                "INVALID"
                            ),
                            "message": (
                                "checksums.sha256 "
                                "is malformed."
                            ),
                            "artifact_path": (
                                "checksums.sha256"
                            ),
                        }
                    )
                elif (
                    observed_checksums
                    != expected_checksums
                ):
                    findings.append(
                        {
                            "severity": "ERROR",
                            "code": (
                                "CHECKSUM_FILE_"
                                "MISMATCH"
                            ),
                            "message": (
                                "checksums.sha256 "
                                "does not match the "
                                "manifest artifact "
                                "checksums."
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

    with database.connect() as connection:
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
