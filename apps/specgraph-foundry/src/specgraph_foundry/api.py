import json
from http.server import BaseHTTPRequestHandler, ThreadingHTTPServer
from pathlib import Path
from urllib.parse import urlparse

from .atoms import AtomService
from .database import Database
from .errors import ConflictError, NotFoundError, ValidationError
from .execution import ExecutionService
from .exports import ExportService
from .http_api.pagination import (
    CursorCodec,
    CursorScope,
    PageWindow,
    pagination_headers,
    parse_pagination_query,
)
from .ingestion import IngestionService
from .planning import PlanningService
from .research import ResearchService
from .routing import RoutingService
from .services import ProjectService


from .api_server import serve
from . import (
    api_routes_documents,
    api_routes_execution,
    api_routes_planning,
    api_routes_projects,
    api_routes_research,
    api_routes_routing,
)

#: Consulted in order; the first family that recognises the path serves it.
ROUTE_FAMILIES = (
    api_routes_projects,
    api_routes_documents,
    api_routes_research,
    api_routes_planning,
    api_routes_execution,
    api_routes_routing,
)

class Api:
    def __init__(
        self,
        database: Database,
        cursor_signing_key: str | None = None,
    ) -> None:
        self.database = database
        self.cursor_signing_key = (
            cursor_signing_key
        )
        self.projects = ProjectService(database)
        self.ingestion = IngestionService(database)
        self.atoms = AtomService(database)
        self.research = ResearchService(database)
        self.planning = PlanningService(database)
        self.exports = ExportService(database)
        self.execution = ExecutionService(database)
        self.routing = RoutingService(database)
        self.response_headers: dict[str, str] = {}

    def dispatch(
        self,
        method: str,
        raw_path: str,
        payload: dict[str, object],
    ) -> tuple[int, dict[str, object]]:
        self.response_headers = {}
        parts = [
            part
            for part in urlparse(raw_path).path.split("/")
            if part
        ]

        try:
            for family in ROUTE_FAMILIES:
                served = family.match(
                    self,
                    method,
                    parts,
                    raw_path=raw_path,
                    payload=payload,
                )

                if served is not None:
                    return served















































            return 404, {
                "error": "ROUTE_NOT_FOUND",
                "message": (
                    f"no route for {method} {raw_path}"
                ),
            }

        except ValidationError as error:
            return 400, {
                "error": "VALIDATION_ERROR",
                "message": str(error),
            }
        except NotFoundError as error:
            return 404, {
                "error": "NOT_FOUND",
                "message": str(error),
            }
        except ConflictError as error:
            return 409, {
                "error": "CONFLICT",
                "message": str(error),
            }
        except (TypeError, ValueError) as error:
            return 400, {
                "error": "INVALID_VALUE",
                "message": str(error),
            }

    def _documents_page(
        self,
        project_id: str,
        raw_path: str,
    ) -> PageWindow:
        request = parse_pagination_query(
            raw_path
        )
        scope = CursorScope(
            collection="project_documents",
            owner_id=self.database.owner_id or "",
            parent_id=project_id,
        )
        boundary = self._decode_cursor(
            request.cursor,
            scope=scope,
        )
        page = self.ingestion.list_documents_page(
            project_id=project_id,
            limit=request.limit,
            boundary=boundary,
        )
        window = PageWindow(
            items=page[0],
            has_more=page[1],
            boundary=page[2],
        )
        self._set_page_headers(
            page=window,
            scope=scope,
            limit=request.limit,
        )
        return window

    def _projects_page(
        self,
        raw_path: str,
    ) -> PageWindow:
        request = parse_pagination_query(
            raw_path
        )
        scope = CursorScope(
            collection="projects",
            owner_id=self.database.owner_id or "",
            parent_id="projects",
        )
        boundary = self._decode_cursor(
            request.cursor,
            scope=scope,
        )
        page = self.projects.list_page(
            limit=request.limit,
            boundary=boundary,
        )
        window = PageWindow(
            items=page[0],
            has_more=page[1],
            boundary=page[2],
        )
        self._set_page_headers(
            page=window,
            scope=scope,
            limit=request.limit,
        )
        return window

    def _atoms_page(
        self,
        document_id: str,
        raw_path: str,
    ) -> PageWindow:
        request = parse_pagination_query(
            raw_path
        )
        scope = CursorScope(
            collection="document_atoms",
            owner_id=self.database.owner_id or "",
            parent_id=document_id,
        )
        boundary = self._decode_cursor(
            request.cursor,
            scope=scope,
        )
        page = self.atoms.list_atoms_page(
            document_id=document_id,
            limit=request.limit,
            boundary=boundary,
        )
        window = PageWindow(
            items=page[0],
            has_more=page[1],
            boundary=page[2],
        )
        self._set_page_headers(
            page=window,
            scope=scope,
            limit=request.limit,
        )
        return window

    def _research_tasks_page(
        self,
        project_id: str,
        raw_path: str,
    ) -> PageWindow:
        request = parse_pagination_query(
            raw_path
        )
        scope = CursorScope(
            collection="project_research_tasks",
            owner_id=self.database.owner_id or "",
            parent_id=project_id,
        )
        boundary = self._decode_cursor(
            request.cursor,
            scope=scope,
        )
        page = (
            self.atoms.list_research_tasks_page(
                project_id=project_id,
                limit=request.limit,
                boundary=boundary,
            )
        )
        window = PageWindow(
            items=page[0],
            has_more=page[1],
            boundary=page[2],
        )
        self._set_page_headers(
            page=window,
            scope=scope,
            limit=request.limit,
        )
        return window

    def _relations_page(
        self,
        project_id: str,
        raw_path: str,
    ) -> PageWindow:
        request = parse_pagination_query(
            raw_path
        )
        scope = CursorScope(
            collection="project_relations",
            owner_id=self.database.owner_id or "",
            parent_id=project_id,
        )
        boundary = self._decode_cursor(
            request.cursor,
            scope=scope,
        )
        page = self.planning.list_relations_page(
            project_id=project_id,
            limit=request.limit,
            boundary=boundary,
        )
        window = PageWindow(
            items=page[0],
            has_more=page[1],
            boundary=page[2],
        )
        self._set_page_headers(
            page=window,
            scope=scope,
            limit=request.limit,
        )
        return window

    def _decode_cursor(
        self,
        cursor: str | None,
        *,
        scope: CursorScope,
    ) -> dict[str, object] | None:
        if cursor is None:
            return None

        return CursorCodec(
            self.cursor_signing_key
        ).decode(
            cursor,
            scope=scope,
        )

    def _set_page_headers(
        self,
        *,
        page: PageWindow,
        scope: CursorScope,
        limit: int,
    ) -> None:
        next_cursor = None

        if page.has_more and page.boundary is not None:
            next_cursor = CursorCodec(
                self.cursor_signing_key
            ).encode(
                scope,
                page.boundary,
            )

        self.response_headers = pagination_headers(
            limit=limit,
            count=len(page.items),
            has_more=page.has_more,
            next_cursor=next_cursor,
        )

    def serve(self, host: str, port: int) -> None:
        """Delegates to :func:`api_server.serve`."""
        return serve(
            self,
            host,
            port,
        )

