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
                    proof_summary = manifest.get(
                        "proof_summary",
                        {},
                    )
                    if (
                        not isinstance(
                            proof_summary,
                            dict,
                        )
                        or proof_summary.get(
                            "path"
                        )
                        != "export_proof_summary.json"
                        or proof_summary.get(
                            "sha256"
                        )
                        != expected_artifacts.get(
                            "export_proof_summary.json",
                            {},
                        ).get("sha256")
                    ):
                        findings.append(
                            {
                                "severity": (
                                    "ERROR"
                                ),
                                "code": (
                                    "MANIFEST_PROOF_"
                                    "SUMMARY_INVALID"
                                ),
                                "message": (
                                    "Manifest proof "
                                    "summary pointer "
                                    "is invalid."
                                ),
                                "artifact_path": (
                                    "manifest.json"
                                ),
                            }
                        )

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

                    proof_summary_path = (
                        output_directory
                        / "export_proof_summary.json"
                    )
                    if proof_summary_path.is_file():
                        proof_findings = (
                            verify_export_proof_summary(
                                proof_summary_path
                            )
                        )
                        findings.extend(
                            proof_findings
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
