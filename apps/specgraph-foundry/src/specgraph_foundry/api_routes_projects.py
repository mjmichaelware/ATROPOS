"""Project-scoped routes.

Seventeen routes hang off /v1/projects -- more than any other family -- so the
table is split in two and consulted in order.
"""

from __future__ import annotations

from . import api_routes_projects_assets, api_routes_projects_core

#: Consulted in order; the first half that recognises the path serves it.
HALVES = (api_routes_projects_core, api_routes_projects_assets)


def match(api, method, parts, raw_path=None, payload=None):
    """Serves the request if this family owns the path, else returns None."""
    for half in HALVES:
        served = half.match(api, method, parts, raw_path=raw_path, payload=payload)

        if served is not None:
            return served

    return None
