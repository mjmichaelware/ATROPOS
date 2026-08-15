"""Checking a bundle's artifacts against the manifest that describes them.

The per-artifact half of verification: every file the manifest names is present,
every digest matches, no file is there that the manifest does not name, and the
proof summary agrees with itself.

Separate from :mod:`export_verification` because that module answers "can this
export be verified at all" -- directory present, manifest readable, checksums
file present -- while this one answers "is its content what it claims". The
first is about reaching the bundle; the second about trusting it, and they were
258 lines apart inside one function.

Appends to `findings` rather than raising: an operator needs every mismatch, not
the first one.
"""

from __future__ import annotations

import json
from pathlib import Path

from .export_proof import sha256_file, verify_export_proof_summary


def check_artifacts(
    manifest: object,
    output_directory: Path,
    findings: list[dict[str, object]],
) -> None:
    """Records a finding for every artifact that does not match the manifest."""
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
