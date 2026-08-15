"""Extracting atoms from an ingested document.

Reads a document's sections, produces a statement per requirement, classifies
each into a dimension, and writes the atoms plus their research tasks. The
largest job in this package and the one everything downstream depends on: a plan
can only be as good as what this produced.
"""

from __future__ import annotations

from .atom_constants import EXTRACTOR_VERSION

import hashlib
import uuid
import json
import sqlite3

from .database import Database
from .errors import ConflictError, NotFoundError, ValidationError
from .atom_queries import get_extraction
from .atom_statements import extract_statements, research_question
from .atom_vocabulary import DIMENSIONS
from .primitives import new_id, utc_now



def extract_document(
    database: Database,
    document_id: str,
) -> dict[str, object]:
    with database.connect() as connection:
        document = connection.execute(
            """
            SELECT
                id,
                project_id,
                sha256,
                byte_count,
                line_count,
                content
            FROM source_documents
            WHERE id = ?
            """,
            (document_id,),
        ).fetchone()

        if document is None:
            raise NotFoundError(
                f"document not found: {document_id}"
            )

        # Every run for this (document, extractor, source) triple is
        # considered, not only completed ones. A run left RUNNING by
        # another worker still occupies the uniqueness slot, so
        # filtering to COMPLETE here meant the insert below was the
        # first thing to notice the clash - and it noticed by raising
        # a raw sqlite3 error at the caller.
        existing = connection.execute(
            """
            SELECT id, status
            FROM extraction_runs
            WHERE document_id = ?
              AND extractor_version = ?
              AND source_sha256 = ?
            """,
            (
                document_id,
                EXTRACTOR_VERSION,
                document["sha256"],
            ),
        ).fetchone()

        if existing is not None:
            if str(existing["status"]) == "COMPLETE":
                return get_extraction(database, 
                    str(existing["id"])
                )

            raise ConflictError(
                "extraction already in progress for document "
                f"{document_id}: run {existing['id']} is "
                f"{existing['status']}"
            )

        sections = [
            dict(row)
            for row in connection.execute(
                """
                SELECT
                    id,
                    byte_start,
                    byte_end
                FROM source_sections
                WHERE document_id = ?
                ORDER BY ordinal
                """,
                (document_id,),
            ).fetchall()
        ]

    raw = str(
        document["content"]
    ).encode("utf-8")

    actual_digest = hashlib.sha256(
        raw
    ).hexdigest()

    if actual_digest != str(
        document["sha256"]
    ):
        raise ValidationError(
            "stored document content does "
            "not match its source fingerprint"
        )

    from .compiler import SpecGraphCompiler
    import uuid

    def map_orthogonal_kind(domains: list[str]) -> str:
        if not domains:
            return "UNRESOLVED"
        primary = domains[0]
        if primary == "UI_UX":
            return "UX"
        if primary in {"SECURITY", "PERFORMANCE", "DATA", "API", "UX", "TEST", "OPERATIONS", "INTEGRATION", "FUNCTIONAL"}:
            return primary
        return "UNRESOLVED"

    def map_orthogonal_modality(force: str) -> str:
        if force == "MUST_NOT":
            return "PROHIBITED"
        if force in {"MUST", "SHALL", "SHOULD", "MAY", "PROHIBITED", "DECLARATIVE"}:
            return force
        return "UNRESOLVED"

    def determine_applicable_dimensions(kind: str) -> set[str]:
        applicable = {"FUNCTIONAL_CONTRACT"}
        if kind == "DATA":
            applicable.add("DATA_LIFECYCLE")
            applicable.add("MIGRATION_COMPATIBILITY")
        elif kind == "SECURITY":
            applicable.add("SECURITY_SECRETS")
        elif kind == "PERFORMANCE":
            applicable.add("PERFORMANCE_RESOURCES")
        elif kind == "API":
            applicable.add("INTEGRATION_CALL_SITES")
        elif kind == "UX":
            applicable.add("ACCESSIBILITY_UX")
        elif kind == "OPERATIONS":
            applicable.add("RESTART_RECOVERY")
            applicable.add("ROLLBACK_FAILURE_EVIDENCE")
        elif kind == "TEST":
            applicable.add("TESTS_ACCEPTANCE")
        elif kind == "INTEGRATION":
            applicable.add("INTEGRATION_CALL_SITES")
        return applicable

    compiler = SpecGraphCompiler(project_id=str(document["project_id"]))
    result = compiler.compile(filename="source.md", content=raw)
    requirements = result["requirements"]

    run_id = new_id("extraction")
    created_at = utc_now()

    with database.connect() as connection:
        try:
            connection.execute(
                """
                INSERT INTO extraction_runs(
                    id,
                    project_id,
                    document_id,
                    extractor_version,
                    source_sha256,
                    status,
                    scanned_bytes,
                    scanned_lines,
                    statement_count,
                    created_at
                )
                VALUES(?,?,?,?,?,?,?,?,?,?)
                """,
                (
                    run_id,
                    document["project_id"],
                    document_id,
                    EXTRACTOR_VERSION,
                    document["sha256"],
                    "RUNNING",
                    int(document["byte_count"]),
                    int(document["line_count"]),
                    len(requirements),
                    created_at,
                ),
            )
        except sqlite3.IntegrityError as conflict:
            # The check above runs in an earlier transaction, so two
            # workers can both pass it and race to this insert. The
            # uniqueness constraint is what actually decides the
            # winner; the loser reports a conflict rather than
            # surfacing a driver error to the caller.
            raise ConflictError(
                "extraction already in progress for document "
                f"{document_id}"
            ) from conflict

        # Persist Compiler Run
        connection.execute(
            """
            INSERT INTO compiler_runs(id, project_id, input_fingerprint, output_fingerprint, status, event_log_json, created_at)
            VALUES(?,?,?,?,?,?,?)
            """,
            (
                run_id,
                document["project_id"],
                document["sha256"],
                result["fingerprint"],
                "COMPLETE",
                json.dumps(result["event_log"]),
                created_at
            )
        )

        # Persist Dependencies
        for dep in result["dependencies"]:
            connection.execute(
                """
                INSERT INTO dependency_edges(id, project_id, from_requirement_id, to_requirement_id, rule_name, evidence, created_at)
                VALUES(?,?,?,?,?,?,?)
                """,
                (
                    str(uuid.uuid4()),
                    document["project_id"],
                    dep["from_node_id"],
                    dep["to_node_id"],
                    dep["rule"],
                    dep["evidence"],
                    created_at
                )
            )

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

        # Persist Semantic Relations in authority_relations
        ALLOWED_RELATION_TYPES = {"REFINES", "CLARIFIES", "SUPERSEDES", "CONFLICTS_WITH", "DUPLICATES"}
        for rel in result["relations"]:
            if rel["relation_type"] not in ALLOWED_RELATION_TYPES:
                continue
            from_atom_id = str(uuid.uuid5(uuid.NAMESPACE_DNS, f"{document['project_id']}:{rel['from_atom_id']}"))
            to_atom_id = str(uuid.uuid5(uuid.NAMESPACE_DNS, f"{document['project_id']}:{rel['to_atom_id']}"))

            exists = connection.execute(
                """
                SELECT 1 FROM authority_relations
                WHERE project_id = ? AND from_atom_id = ? AND to_atom_id = ? AND relation_type = ?
                """,
                (document["project_id"], from_atom_id, to_atom_id, rel["relation_type"])
            ).fetchone()

            if not exists:
                connection.execute(
                    """
                    INSERT INTO authority_relations(id, project_id, from_atom_id, to_atom_id, relation_type, rationale, confidence, inferred, created_at)
                    VALUES(?,?,?,?,?,?,?,?,?)
                    """,
                    (
                        new_id("relation"),
                        document["project_id"],
                        from_atom_id,
                        to_atom_id,
                        rel["relation_type"],
                        rel["rationale"],
                        float(rel.get("confidence", 1.0)),
                        bool(rel.get("inferred", True)),
                        created_at
                    )
                )

        # Persist Dependencies as REQUIRES relations in authority_relations
        for dep in result["dependencies"]:
            from_atom_id = str(uuid.uuid5(uuid.NAMESPACE_DNS, f"{document['project_id']}:{dep['from_node_id']}"))
            to_atom_id = str(uuid.uuid5(uuid.NAMESPACE_DNS, f"{document['project_id']}:{dep['to_node_id']}"))

            exists = connection.execute(
                """
                SELECT 1 FROM authority_relations
                WHERE project_id = ? AND from_atom_id = ? AND to_atom_id = ? AND relation_type = 'REQUIRES'
                """,
                (document["project_id"], from_atom_id, to_atom_id)
            ).fetchone()

            if not exists:
                connection.execute(
                    """
                    INSERT INTO authority_relations(id, project_id, from_atom_id, to_atom_id, relation_type, rationale, confidence, inferred, created_at)
                    VALUES(?,?,?,?,?,?,?,?,?)
                    """,
                    (
                        new_id("relation"),
                        document["project_id"],
                        from_atom_id,
                        to_atom_id,
                        "REQUIRES",
                        dep["evidence"],
                        1.0,
                        True,
                        created_at
                    )
                )

        connection.execute(
            """
            UPDATE extraction_runs
            SET status = 'COMPLETE',
                atom_count = ?,
                dimension_count = ?,
                research_task_count = ?,
                completed_at = ?
            WHERE id = ?
            """,
            (
                atom_count,
                dimension_count,
                task_count,
                utc_now(),
                run_id,
            ),
        )

    return get_extraction(database, 
        run_id
    )

    return get_extraction(database, 
        run_id
    )
