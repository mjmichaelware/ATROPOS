"""Finding the route a request belongs to.

The table is 465 lines of pure data describing the API. Split in two by resource
family and consulted in order, so adding a route means editing the half it
belongs to rather than scrolling the whole surface.
"""

from __future__ import annotations

from . import gateway_routes_core, gateway_routes_pipeline
from .gateway_models import RouteMetadata

#: Consulted in order; the first table that recognises the path wins.
ROUTE_TABLES = (gateway_routes_core, gateway_routes_pipeline)


def route_metadata(
    method: str,
    parts: list[str],
) -> RouteMetadata | None:
    """The route for this method and path, or None when nothing matches."""
    for table in ROUTE_TABLES:
        route = table.match(method, parts)

        if route is not None:
            return route

    return None
