"""Document and atom routes.

`Api.dispatch` matched 42 routes in a 972-line try block. The blocks are
independent -- each recognises its own path and returns -- so reading one meant
scrolling past the rest. One module per resource family, each returning None
when the path is not its own.
"""

from __future__ import annotations

import json


def match(api, method, parts, raw_path=None, payload=None):
    """Serves the request if this family owns the path, else returns None."""
    if (
        len(parts) == 3
        and parts[:2] == ["v1", "documents"]
        and method == "GET"
    ):
        return 200, api.ingestion.get_document(
            parts[2]
        )

    if (
        len(parts) == 4
        and parts[:2] == ["v1", "documents"]
        and parts[3] == "verify"
        and method == "GET"
    ):
        return 200, api.ingestion.verify_document(
            parts[2]
        )

    if (
        len(parts) == 4
        and parts[:2] == ["v1", "documents"]
        and parts[3] == "extract"
        and method == "POST"
    ):
        return 200, api.atoms.extract_document(
            parts[2]
        )

    if (
        len(parts) == 4
        and parts[:2] == ["v1", "documents"]
        and parts[3] == "atoms"
        and method == "GET"
    ):
        page = api._atoms_page(
            parts[2],
            raw_path,
        )
        return 200, {
            "items": page.items
        }

    if (
        len(parts) == 5
        and parts[:2] == ["v1", "documents"]
        and parts[3] == "atoms"
        and parts[4] == "export"
        and method == "GET"
    ):
        return 200, api.atoms.export_atoms_bundle(
            parts[2]
        )

    if (
        len(parts) == 3
        and parts[:2] == ["v1", "atoms"]
        and method == "GET"
    ):
        return 200, api.atoms.get_atom(parts[2])

    return None
