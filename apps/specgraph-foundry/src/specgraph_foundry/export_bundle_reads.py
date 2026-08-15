"""Reading everything an export bundle is built from.

Nine queries against nine tables, run inside one connection so the bundle is a
consistent snapshot rather than nine reads that could disagree.

Its own module because gathering and formatting fail differently: a query that
returns the wrong rows produces a complete, well-formed, wrong bundle, while a
builder that mishandles them produces an obviously broken one. Reading them
together also makes the snapshot boundary visible -- it was previously the first
170 lines of a 412-line function.
"""

from __future__ import annotations

import sqlite3

from .database import Database
from .errors import NotFoundError
from .export_bindings import normalize_binding


def load_project_records(
    database: Database,
    project_id: str,
) -> dict[str, object]:
    """Every row the bundle builders need, from one connection."""
    with database.connect() as connection:
        project_row = connection.execute(
            """
            SELECT *
            FROM projects
            WHERE id = ?
            """,
            (project_id,),
        ).fetchone()

        if project_row is None:
            raise NotFoundError(
                f"project not found: {project_id}"
            )

        documents = [
            dict(row)
            for row in connection.execute(
                """
                SELECT
                    id,
                    project_id,
                    title,
                    media_type,
                    sha256,
                    byte_count,
                    line_count,
                    created_at
                FROM source_documents
                WHERE project_id = ?
                ORDER BY created_at, id
                """,
                (project_id,),
            ).fetchall()
        ]

        sections = [
            dict(row)
            for row in connection.execute(
                """
                SELECT section.*
                FROM source_sections
                AS section
                JOIN source_documents
                AS document
                  ON document.id =
                     section.document_id
                WHERE document.project_id = ?
                ORDER BY
                    section.document_id,
                    section.ordinal
                """,
                (project_id,),
            ).fetchall()
        ]

        atoms = [
            dict(row)
            for row in connection.execute(
                """
                SELECT *
                FROM atoms
                WHERE project_id = ?
                ORDER BY
                    document_id,
                    ordinal,
                    id
                """,
                (project_id,),
            ).fetchall()
        ]

        dimensions = [
            dict(row)
            for row in connection.execute(
                """
                SELECT dimension.*
                FROM atom_dimensions
                AS dimension
                JOIN atoms AS atom
                  ON atom.id =
                     dimension.atom_id
                WHERE atom.project_id = ?
                ORDER BY
                    dimension.atom_id,
                    dimension.dimension
                """,
                (project_id,),
            ).fetchall()
        ]

        claims = [
            dict(row)
            for row in connection.execute(
                """
                SELECT claim.*
                FROM research_claims
                AS claim
                JOIN atoms AS atom
                  ON atom.id =
                     claim.atom_id
                WHERE atom.project_id = ?
                ORDER BY
                    claim.atom_id,
                    claim.dimension,
                    claim.id
                """,
                (project_id,),
            ).fetchall()
        ]

        evidence = [
            dict(row)
            for row in connection.execute(
                """
                SELECT evidence.*
                FROM research_evidence
                AS evidence
                JOIN atoms AS atom
                  ON atom.id =
                     evidence.atom_id
                WHERE atom.project_id = ?
                ORDER BY
                    evidence.atom_id,
                    evidence.dimension,
                    evidence.id
                """,
                (project_id,),
            ).fetchall()
        ]

        claim_evidence = [
            dict(row)
            for row in connection.execute(
                """
                SELECT
                    relation.claim_id,
                    relation.evidence_id
                FROM research_claim_evidence
                AS relation
                JOIN research_claims
                AS claim
                  ON claim.id =
                     relation.claim_id
                JOIN atoms AS atom
                  ON atom.id =
                     claim.atom_id
                WHERE atom.project_id = ?
                ORDER BY
                    relation.claim_id,
                    relation.evidence_id
                """,
                (project_id,),
            ).fetchall()
        ]

        bindings = [
            normalize_binding(
                dict(row)
            )
            for row in connection.execute(
                """
                SELECT *
                FROM integration_bindings
                WHERE project_id = ?
                  AND enabled IS TRUE
                ORDER BY
                    system_name,
                    binding_type,
                    id
                """,
                (project_id,),
            ).fetchall()
        ]

    return {
        "project_row": project_row,
        "documents": documents,
        "sections": sections,
        "atoms": atoms,
        "dimensions": dimensions,
        "claims": claims,
        "evidence": evidence,
        "claim_evidence": claim_evidence,
        "bindings": bindings,
    }
