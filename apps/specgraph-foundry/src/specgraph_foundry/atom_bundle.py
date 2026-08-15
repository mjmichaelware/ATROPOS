"""Exporting atoms as a downloadable bundle.

The only place atoms are rendered for something other than the pipeline, which
is why the markdown lives here rather than beside the queries.
"""

from __future__ import annotations

from .rendering import render_markdown_pdf
from .rendering import markdown_to_plain_text

import json
import sqlite3

from .database import Database
from .errors import NotFoundError, ValidationError
import base64



def export_atoms_bundle(
    database: Database,
    document_id: str,
) -> dict[str, object]:
    """Render a document's extracted atoms as a downloadable bundle.

    Returns the same content in two encodings so a caller can hand a
    reviewer either one without re-rendering: ``text`` for diffing and
    grepping, ``pdf`` for circulation. Both are base64 so the bundle
    survives a JSON transport unchanged.

    A document that genuinely produced zero atoms is reported as such
    in the rendered body. An empty file is indistinguishable from a
    broken extraction, and a reviewer who cannot tell the difference
    will assume the wrong one.
    """
    with database.connect() as connection:
        document = connection.execute(
            """
            SELECT
                id,
                title,
                sha256
            FROM source_documents
            WHERE id = ?
            """,
            (document_id,),
        ).fetchone()

        if document is None:
            raise NotFoundError(
                f"document not found: {document_id}"
            )

        rows = connection.execute(
            """
            SELECT
                ordinal,
                kind,
                modality,
                status,
                canonical_statement
            FROM atoms
            WHERE document_id = ?
            ORDER BY ordinal
            """,
            (document_id,),
        ).fetchall()

    atoms = [
        dict(row)
        for row in rows
    ]
    markdown = render_atoms_markdown(
        dict(document),
        atoms,
    )

    return {
        "document_id": document_id,
        "atom_count": len(atoms),
        "text": encoded_file(
            markdown_to_plain_text(markdown).encode("utf-8"),
            "text/plain",
        ),
        "pdf": encoded_file(
            render_markdown_pdf(markdown),
            "application/pdf",
        ),
    }


def render_atoms_markdown(
    document: dict[str, object],
    atoms: list[dict[str, object]],
) -> str:
    title = str(
        document.get("title")
        or document.get("id")
    )
    lines = [
        f"# Extracted atoms: {title}",
        "",
        f"Document: {document.get('id')}",
        f"Source sha256: {document.get('sha256')}",
        f"Atoms: {len(atoms)}",
        "",
    ]

    if not atoms:
        lines.append(
            "No candidate statements were found in this document. "
            "The extraction completed successfully and produced zero "
            "atoms, which is a result rather than a failure."
        )
        return "\n".join(lines) + "\n"

    for atom in atoms:
        # The ordinal is written without a number sign: the plain-text
        # rendering strips markdown headings but leaves inline text
        # alone, and a stray marker would read as a heading.
        lines.append(
            f"{atom['ordinal']}. {atom['canonical_statement']}"
        )
        lines.append(
            f"   kind={atom['kind']} "
            f"modality={atom['modality']} "
            f"status={atom['status']}"
        )
        lines.append("")

    return "\n".join(lines) + "\n"


def encoded_file(
    payload: bytes,
    media_type: str,
) -> dict[str, object]:
    return {
        "base64": base64.b64encode(payload).decode("ascii"),
        "byte_length": len(payload),
        "media_type": media_type,
    }
