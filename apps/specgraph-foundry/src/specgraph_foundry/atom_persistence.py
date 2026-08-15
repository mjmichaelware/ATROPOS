"""Writing one requirement's atom, its dimensions and its research tasks.

The per-requirement half of extraction: for each statement found in a document,
create the atom, decide which of the sixteen dimensions apply, and open a
research task for every dimension left open.

Separate from :mod:`atom_extraction` because that module decides *what the
document says* and this one decides *what gets stored about it*. They were 278
lines of one function, and the dimension rules -- which drive how much research
a project needs -- were unreadable in the middle of it.

Returns the three counts the caller records on the extraction row. Returning
them rather than mutating a shared accumulator keeps the caller's arithmetic
visible at the call site.
"""

from __future__ import annotations

import hashlib
import json
import uuid

from .atom_research_questions import research_question
from .atom_vocabulary import DIMENSIONS
from .primitives import new_id, utc_now


def persist_requirements(
    connection,
    document,
    document_id: str,
    requirements: list,
    run_id: str,
    created_at: str,
    research_schema: str,
    determine_applicable_dimensions,
    map_orthogonal_kind,
    map_orthogonal_modality,
) -> tuple[int, int, int]:
    """Returns (atom_count, dimension_count, task_count)."""
    RESEARCH_SCHEMA = research_schema
    atom_count = 0
    dimension_count = 0
    task_count = 0

    for idx, req in enumerate(requirements):
        atom_id = str(uuid.uuid5(uuid.NAMESPACE_DNS, f"{document['project_id']}:{req['stable_id']}"))
        kind = map_orthogonal_kind(req["domains"])
        modality = map_orthogonal_modality(req["force"])

        connection.execute(
            """
            INSERT INTO atoms(
                id,
                project_id,
                document_id,
                section_id,
                extraction_run_id,
                ordinal,
                kind,
                modality,
                status,
                canonical_statement,
                exact_quote,
                byte_start,
                byte_end,
                line_start,
                line_end,
                source_sha256,
                confidence,
                created_at
            )
            VALUES(
                ?,?,?,?,?,?,?,?,?,?,
                ?,?,?,?,?,?,?,?
            )
            """,
            (
                atom_id,
                document["project_id"],
                document_id,
                None,  # Section is resolved structurally in DocumentIR
                run_id,
                idx,
                kind,
                modality,
                "DISCOVERED",
                req["canonical_statement"],
                req["original_statement"],
                req["coordinates"]["byte_start"],
                req["coordinates"]["byte_end"],
                req["coordinates"]["line_start"],
                req["coordinates"]["line_end"],
                document["sha256"],
                1.0,
                created_at,
            ),
        )

        atom_count += 1

        # Persist Orthogonal Types
        connection.execute(
            """
            INSERT INTO orthogonal_types(id, requirement_id, modality, domain_kind, artifact_target, verification_method, created_at)
            VALUES(?,?,?,?,?,?,?)
            """,
            (
                str(uuid.uuid4()),
                atom_id,
                modality,
                kind,
                req.get("artifact_target", "UNSPECIFIED"),
                req.get("verification_method", "UNSPECIFIED"),
                created_at
            )
        )

        # Persist Quality Findings
        for finding in req["quality_findings"]:
            connection.execute(
                """
                INSERT INTO requirement_quality_findings(id, requirement_id, severity, code, message, created_at)
                VALUES(?,?,?,?,?,?)
                """,
                (
                    str(uuid.uuid4()),
                    atom_id,
                    finding["severity"],
                    finding["code"],
                    finding["message"],
                    created_at
                )
            )

        # Ensure research tables exist
        try:
            from .research import RESEARCH_SCHEMA
            connection.executescript(RESEARCH_SCHEMA)
        except Exception:
            pass

        applicable_dimensions = determine_applicable_dimensions(kind)

        for dimension in DIMENSIONS:
            timestamp = utc_now()
            is_app = dimension in applicable_dimensions
            app_status = "OPEN" if is_app else "NOT_APPLICABLE"
            app_val = "UNKNOWN" if is_app else "NOT_APPLICABLE"

            connection.execute(
                """
                INSERT INTO atom_dimensions(
                    id,
                    atom_id,
                    dimension,
                    applicability,
                    status,
                    rationale,
                    created_at,
                    updated_at
                )
                VALUES(?,?,?,?,?,?,?,?)
                """,
                (
                    new_id("dimension"),
                    atom_id,
                    dimension,
                    app_val,
                    app_status,
                    "Determined by compiler orthogonal typing" if is_app else "Not applicable to this requirement type",
                    timestamp,
                    timestamp,
                ),
            )

            dimension_count += 1

            if is_app:
                task_id = new_id("research-task")
                connection.execute(
                    """
                    INSERT INTO research_tasks(
                        id,
                        project_id,
                        atom_id,
                        dimension,
                        question,
                        status,
                        priority,
                        created_at,
                        updated_at
                    )
                    VALUES(?,?,?,?,?,?,?,?,?)
                    """,
                    (
                        task_id,
                        document["project_id"],
                        atom_id,
                        dimension,
                        research_question(
                            str(req["canonical_statement"]),
                            dimension,
                        ),
                        "PENDING",
                        100,
                        timestamp,
                        timestamp,
                    ),
                )
                task_count += 1
            else:
                # Auto-insert compiler decision claim and evidence to justify NOT_APPLICABLE status
                claim_id = new_id("claim")
                evidence_id = new_id("evidence")
                dummy_task_id = new_id("compiler-task")

                connection.execute(
                    """
                    INSERT INTO research_tasks(
                        id,
                        project_id,
                        atom_id,
                        dimension,
                        question,
                        status,
                        priority,
                        created_at,
                        updated_at
                    )
                    VALUES(?,?,?,?,?,?,?,?,?)
                    """,
                    (
                        dummy_task_id,
                        document["project_id"],
                        atom_id,
                        dimension,
                        f"Is {dimension} applicable?",
                        "RESOLVED",
                        100,
                        timestamp,
                        timestamp,
                    )
                )

                connection.execute(
                    """
                    INSERT INTO research_evidence(
                        id,
                        project_id,
                        task_id,
                        atom_id,
                        dimension,
                        source_uri,
                        source_title,
                        publisher,
                        evidence_type,
                        excerpt,
                        content_sha256,
                        reliability,
                        retrieved_at,
                        created_at
                    )
                    VALUES(?,?,?,?,?,?,?,?,?,?,?,?,?,?)
                    """,
                    (
                        evidence_id,
                        document["project_id"],
                        dummy_task_id,
                        atom_id,
                        dimension,
                        "compiler://orthogonal_typing",
                        "SpecGraph Compiler",
                        "SpecGraph Compiler",
                        "OTHER",
                        f"Dimension is not applicable to requirement kind {kind}.",
                        hashlib.sha256(b"na").hexdigest(),
                        1.0,
                        timestamp,
                        timestamp,
                    )
                )

                connection.execute(
                    """
                    INSERT INTO research_claims(
                        id,
                        project_id,
                        task_id,
                        atom_id,
                        dimension,
                        conclusion,
                        applicability,
                        confidence,
                        status,
                        created_at,
                        updated_at
                    )
                    VALUES(?,?,?,?,?,?,?,?,?,?,?)
                    """,
                    (
                        claim_id,
                        document["project_id"],
                        dummy_task_id,
                        atom_id,
                        dimension,
                        f"Compiler determined this dimension is not applicable to {kind} requirements.",
                        "NOT_APPLICABLE",
                        1.0,
                        "ACCEPTED",
                        timestamp,
                        timestamp,
                    )
                )

                connection.execute(
                    """
                    INSERT INTO research_claim_evidence(claim_id, evidence_id)
                    VALUES(?,?)
                    """,
                    (claim_id, evidence_id)
                )
                task_count += 1

    return atom_count, dimension_count, task_count
