"""Extracting atoms from an ingested document.

Reads a document's sections, produces a statement per requirement, classifies
each into a dimension, and writes the atoms plus their research tasks. The
largest job in this package and the one everything downstream depends on: a plan
can only be as good as what this produced.
"""

from __future__ import annotations

from .atom_persistence import persist_requirements
from .research import RESEARCH_SCHEMA
from .atom_constants import EXTRACTOR_VERSION

import hashlib
import uuid
import json
import sqlite3

from .database import Database
from .errors import ConflictError, NotFoundError, ValidationError
from .atom_queries import get_extraction
from .atom_research_questions import research_question
from .atom_statements import extract_statements
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

    # Every domain the compiler can produce, mapped to a kind.
    #
    # This table used to accept nine names and the compiler emits twenty, so
    # fourteen domains resolved to UNRESOLVED and their atoms collapsed to the
    # baseline FUNCTIONAL_CONTRACT. RECOVERY and TESTABILITY were the visible
    # cases -- "MUST recover after a restart" and "the tests MUST verify" both
    # classified correctly upstream and then lost the classification here.
    #
    # Kept exhaustive rather than permissive: an unlisted domain still returns
    # UNRESOLVED, which is the honest answer for a vocabulary this has not been
    # taught. What changed is that the vocabulary it has been taught is now the
    # whole of DOMAINS rather than half of it.
    DOMAIN_TO_KIND = {
        "SECURITY": "SECURITY",
        "PRIVACY": "SECURITY",
        "COMPLIANCE": "SECURITY",
        "PERFORMANCE": "PERFORMANCE",
        "DATA": "DATA",
        "API": "API",
        "UI_UX": "UX",
        "ACCESSIBILITY": "UX",
        "TESTABILITY": "TEST",
        "INTEGRATION": "INTEGRATION",
        "RECOVERY": "OPERATIONS",
        "RELIABILITY": "OPERATIONS",
        "SAFETY": "OPERATIONS",
        "DEPLOYMENT": "OPERATIONS",
        "OBSERVABILITY": "OBSERVABILITY",
        "PLATFORM": "PLATFORM",
        "GOVERNANCE": "GOVERNANCE",
        "ARCHITECTURE": "ARCHITECTURE",
        "FUNCTIONAL_BEHAVIOR": "FUNCTIONAL",
        "DOCUMENTATION": "FUNCTIONAL",
    }

    def map_orthogonal_kind(domains: list[str]) -> str:
        if not domains:
            return "UNRESOLVED"
        return DOMAIN_TO_KIND.get(domains[0], "UNRESOLVED")

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
        # Six of the sixteen dimensions were unreachable: no kind assigned them,
        # so no atom could ever carry them however the source was written.
        elif kind == "OBSERVABILITY":
            applicable.add("OBSERVABILITY_PROVENANCE")
        elif kind == "PLATFORM":
            applicable.add("PLATFORM_ENVIRONMENT")
        elif kind == "GOVERNANCE":
            applicable.add("TERRITORY_CAPABILITIES")
        elif kind == "ARCHITECTURE":
            applicable.add("DEPENDENCY_CONTRACT")
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

        # Per-requirement persistence lives in atom_persistence.
        (
            atom_count,
            dimension_count,
            task_count,
        ) = persist_requirements(
            connection,
            document,
            document_id,
            requirements,
            run_id,
            created_at,
            RESEARCH_SCHEMA,
            determine_applicable_dimensions,
            map_orthogonal_kind,
            map_orthogonal_modality,
        )

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
