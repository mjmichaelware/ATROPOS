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
            if method == "GET" and parts == ["health"]:
                return 200, {
                    "status": "ok",
                    "service": "specgraph-foundry",
                    "database": self.database.health(),
                }

            if parts == ["v1", "projects"]:
                if method == "GET":
                    page = self._projects_page(
                        raw_path
                    )
                    return 200, {
                        "items": page.items
                    }

                if method == "POST":
                    return 201, self.projects.create(
                        str(payload.get("slug", "")),
                        str(payload.get("name", "")),
                        str(payload.get("description", "")),
                    )

            if (
                len(parts) == 3
                and parts[:2] == ["v1", "projects"]
                and method == "GET"
            ):
                return 200, self.projects.get(parts[2])

            if (
                len(parts) == 4
                and parts[:2] == ["v1", "projects"]
                and parts[3] == "documents"
            ):
                project_id = parts[2]

                if method == "GET":
                    page = self._documents_page(
                        project_id,
                        raw_path,
                    )
                    return 200, {
                        "items": page.items
                    }

                if method == "POST":
                    return 201, self.ingestion.ingest_text(
                        project_id=project_id,
                        title=str(payload.get("title", "")),
                        content=str(payload.get("content", "")),
                        media_type=str(
                            payload.get(
                                "media_type",
                                "text/plain",
                            )
                        ),
                        chunk_bytes=int(
                            payload.get(
                                "chunk_bytes",
                                32768,
                            )
                        ),
                    )

            if (
                len(parts) == 3
                and parts[:2] == ["v1", "documents"]
                and method == "GET"
            ):
                return 200, self.ingestion.get_document(
                    parts[2]
                )

            if (
                len(parts) == 4
                and parts[:2] == ["v1", "documents"]
                and parts[3] == "verify"
                and method == "GET"
            ):
                return 200, self.ingestion.verify_document(
                    parts[2]
                )

            if (
                len(parts) == 4
                and parts[:2] == ["v1", "documents"]
                and parts[3] == "extract"
                and method == "POST"
            ):
                return 200, self.atoms.extract_document(
                    parts[2]
                )

            if (
                len(parts) == 4
                and parts[:2] == ["v1", "documents"]
                and parts[3] == "atoms"
                and method == "GET"
            ):
                page = self._atoms_page(
                    parts[2],
                    raw_path,
                )
                return 200, {
                    "items": page.items
                }

            if (
                len(parts) == 3
                and parts[:2] == ["v1", "atoms"]
                and method == "GET"
            ):
                return 200, self.atoms.get_atom(parts[2])

            if (
                len(parts) == 4
                and parts[:2] == ["v1", "projects"]
                and parts[3] == "research-tasks"
                and method == "GET"
            ):
                page = self._research_tasks_page(
                    parts[2],
                    raw_path,
                )
                return 200, {
                    "items": page.items
                }

            if (
                len(parts) == 5
                and parts[:2] == ["v1", "projects"]
                and parts[3] == "research-tasks"
                and parts[4] == "claim"
                and method == "POST"
            ):
                return 200, {
                    "task": self.research.claim_task(
                        project_id=parts[2],
                        worker_id=str(
                            payload.get("worker_id", "")
                        ),
                        lease_seconds=int(
                            payload.get(
                                "lease_seconds",
                                900,
                            )
                        ),
                    )
                }

            if (
                len(parts) == 4
                and parts[:2] == ["v1", "projects"]
                and parts[3] == "gap-matrix"
                and method == "GET"
            ):
                return 200, self.research.gap_matrix(
                    parts[2]
                )

            if (
                len(parts) == 3
                and parts[:2] == ["v1", "research-tasks"]
                and method == "GET"
            ):
                return 200, self.research.get_task(parts[2])

            if (
                len(parts) == 4
                and parts[:2] == ["v1", "research-tasks"]
                and parts[3] == "heartbeat"
                and method == "POST"
            ):
                return 200, self.research.heartbeat(
                    task_id=parts[2],
                    worker_id=str(
                        payload.get("worker_id", "")
                    ),
                    lease_seconds=int(
                        payload.get(
                            "lease_seconds",
                            900,
                        )
                    ),
                )

            if (
                len(parts) == 4
                and parts[:2] == ["v1", "research-tasks"]
                and parts[3] == "evidence"
                and method == "POST"
            ):
                return 201, self.research.add_evidence(
                    task_id=parts[2],
                    worker_id=str(
                        payload.get("worker_id", "")
                    ),
                    source_uri=str(
                        payload.get("source_uri", "")
                    ),
                    source_title=str(
                        payload.get("source_title", "")
                    ),
                    excerpt=str(
                        payload.get("excerpt", "")
                    ),
                    publisher=str(
                        payload.get("publisher", "")
                    ),
                    evidence_type=str(
                        payload.get(
                            "evidence_type",
                            "OTHER",
                        )
                    ),
                    reliability=float(
                        payload.get(
                            "reliability",
                            0.5,
                        )
                    ),
                )

            if (
                len(parts) == 4
                and parts[:2] == ["v1", "research-tasks"]
                and parts[3] == "complete"
                and method == "POST"
            ):
                evidence_ids = payload.get(
                    "evidence_ids",
                    [],
                )

                if not isinstance(evidence_ids, list):
                    raise ValidationError(
                        "evidence_ids must be a list"
                    )

                return 200, self.research.complete_task(
                    task_id=parts[2],
                    worker_id=str(
                        payload.get("worker_id", "")
                    ),
                    conclusion=str(
                        payload.get("conclusion", "")
                    ),
                    applicability=str(
                        payload.get(
                            "applicability",
                            "",
                        )
                    ),
                    confidence=float(
                        payload.get("confidence", 0.0)
                    ),
                    evidence_ids=[
                        str(item)
                        for item in evidence_ids
                    ],
                )

            if (
                len(parts) == 4
                and parts[:2] == ["v1", "research-tasks"]
                and parts[3] == "fail"
                and method == "POST"
            ):
                return 200, self.research.fail_task(
                    task_id=parts[2],
                    worker_id=str(
                        payload.get("worker_id", "")
                    ),
                    error_message=str(
                        payload.get("error_message", "")
                    ),
                    retryable=bool(
                        payload.get("retryable", True)
                    ),
                )


            if (
                len(parts) == 4
                and parts[:2] == ["v1", "projects"]
                and parts[3] == "relations"
            ):
                if method == "GET":
                    page = self._relations_page(
                        parts[2],
                        raw_path,
                    )
                    return 200, {
                        "items": page.items
                    }

                if method == "POST":
                    return 201, self.planning.add_relation(
                        project_id=parts[2],
                        from_atom_id=str(
                            payload.get(
                                "from_atom_id",
                                "",
                            )
                        ),
                        to_atom_id=str(
                            payload.get(
                                "to_atom_id",
                                "",
                            )
                        ),
                        relation_type=str(
                            payload.get(
                                "relation_type",
                                "",
                            )
                        ),
                        rationale=str(
                            payload.get(
                                "rationale",
                                "",
                            )
                        ),
                        confidence=float(
                            payload.get(
                                "confidence",
                                1.0,
                            )
                        ),
                        inferred=bool(
                            payload.get(
                                "inferred",
                                False,
                            )
                        ),
                    )

            if (
                len(parts) == 4
                and parts[:2] == ["v1", "projects"]
                and parts[3] == "plans"
            ):
                if method == "GET":
                    return 200, {
                        "items": self.planning.list_plans(
                            parts[2]
                        )
                    }

                if method == "POST":
                    return 201, self.planning.synthesize(
                        project_id=parts[2],
                        allow_open_research=bool(
                            payload.get(
                                "allow_open_research",
                                False,
                            )
                        ),
                    )

            if (
                len(parts) == 3
                and parts[:2] == ["v1", "plans"]
                and method == "GET"
            ):
                return 200, self.planning.get_plan(
                    parts[2]
                )

            if (
                len(parts) == 4
                and parts[:2] == ["v1", "plans"]
                and parts[3] == "verify"
                and method == "POST"
            ):
                return 200, self.planning.verify_plan(
                    parts[2]
                )


            if (
                len(parts) == 4
                and parts[:2] == ["v1", "projects"]
                and parts[3] == "bindings"
            ):
                if method == "GET":
                    return 200, {
                        "items": self.exports.list_bindings(
                            parts[2]
                        )
                    }

                if method == "POST":
                    config = payload.get(
                        "config",
                        {},
                    )

                    if not isinstance(config, dict):
                        raise ValidationError(
                            "config must be an object"
                        )

                    return 201, self.exports.bind_integration(
                        project_id=parts[2],
                        system_name=str(
                            payload.get(
                                "system_name",
                                "",
                            )
                        ),
                        binding_type=str(
                            payload.get(
                                "binding_type",
                                "",
                            )
                        ),
                        config=config,
                        enabled=bool(
                            payload.get(
                                "enabled",
                                True,
                            )
                        ),
                    )

            if (
                len(parts) == 4
                and parts[:2] == ["v1", "projects"]
                and parts[3] == "exports"
                and method == "GET"
            ):
                return 200, {
                    "items": self.exports.list_exports(
                        parts[2]
                    )
                }

            if (
                len(parts) == 4
                and parts[:2] == ["v1", "plans"]
                and parts[3] == "exports"
                and method == "POST"
            ):
                output_root_value = payload.get(
                    "output_root"
                )

                output_root = (
                    Path(
                        str(output_root_value)
                    )
                    if output_root_value
                    else None
                )

                return 201, self.exports.export_plan(
                    parts[2],
                    output_root,
                )

            if (
                len(parts) == 3
                and parts[:2] == ["v1", "exports"]
                and method == "GET"
            ):
                return 200, self.exports.get_export(
                    parts[2]
                )

            if (
                len(parts) == 4
                and parts[:2] == ["v1", "exports"]
                and parts[3] == "verify"
                and method == "POST"
            ):
                return 200, self.exports.verify_export(
                    parts[2]
                )


            if (
                len(parts) == 4
                and parts[:2] == ["v1", "plans"]
                and parts[3] == "execution-runs"
                and method == "POST"
            ):
                export_value = payload.get(
                    "export_id"
                )

                return 201, self.execution.start_run(
                    plan_id=parts[2],
                    runtime_system=str(
                        payload.get(
                            "runtime_system",
                            "",
                        )
                    ),
                    runtime_run_id=str(
                        payload.get(
                            "runtime_run_id",
                            "",
                        )
                    ),
                    export_id=(
                        str(export_value)
                        if export_value
                        else None
                    ),
                )

            if (
                len(parts) == 4
                and parts[:2] == ["v1", "projects"]
                and parts[3] == "execution-runs"
                and method == "GET"
            ):
                return 200, {
                    "items": self.execution.list_runs(
                        parts[2]
                    )
                }

            if (
                len(parts) == 3
                and parts[:2] == ["v1", "execution-runs"]
                and method == "GET"
            ):
                return 200, self.execution.get_run(
                    parts[2]
                )

            if (
                len(parts) == 4
                and parts[:2] == ["v1", "execution-runs"]
                and parts[3] == "claim"
                and method == "POST"
            ):
                node_value = payload.get(
                    "run_node_id"
                )

                return 200, {
                    "claim": self.execution.claim_node(
                        run_id=parts[2],
                        worker_id=str(
                            payload.get(
                                "worker_id",
                                "",
                            )
                        ),
                        run_node_id=(
                            str(node_value)
                            if node_value
                            else None
                        ),
                        lease_seconds=int(
                            payload.get(
                                "lease_seconds",
                                900,
                            )
                        ),
                    )
                }

            if (
                len(parts) == 4
                and parts[:2] == ["v1", "execution-runs"]
                and parts[3] == "verify"
                and method == "POST"
            ):
                return 200, self.execution.verify_run(
                    parts[2]
                )

            if (
                len(parts) == 4
                and parts[:2] == ["v1", "execution-nodes"]
                and parts[3] == "heartbeat"
                and method == "POST"
            ):
                return 200, self.execution.heartbeat(
                    run_node_id=parts[2],
                    worker_id=str(
                        payload.get(
                            "worker_id",
                            "",
                        )
                    ),
                    lease_seconds=int(
                        payload.get(
                            "lease_seconds",
                            900,
                        )
                    ),
                )

            if (
                len(parts) == 4
                and parts[:2] == ["v1", "execution-nodes"]
                and parts[3] == "receipts"
                and method == "POST"
            ):
                evidence = payload.get(
                    "evidence",
                    {},
                )

                if not isinstance(evidence, dict):
                    raise ValidationError(
                        "evidence must be an object"
                    )

                return 201, self.execution.submit_receipt(
                    run_node_id=parts[2],
                    worker_id=str(
                        payload.get(
                            "worker_id",
                            "",
                        )
                    ),
                    actor_system=str(
                        payload.get(
                            "actor_system",
                            "",
                        )
                    ),
                    outcome=str(
                        payload.get(
                            "outcome",
                            "",
                        )
                    ),
                    summary=str(
                        payload.get(
                            "summary",
                            "",
                        )
                    ),
                    evidence=evidence,
                )


            if (
                len(parts) == 4
                and parts[:2] == ["v1", "projects"]
                and parts[3] == "routing-policy"
            ):
                if method == "GET":
                    return 200, self.routing.get_policy(
                        parts[2]
                    )

                if method == "POST":
                    return 200, self.routing.set_policy(
                        project_id=parts[2],
                        allow_offline_degraded=bool(
                            payload.get(
                                "allow_offline_degraded",
                                True,
                            )
                        ),
                        paid_emergency_enabled=bool(
                            payload.get(
                                "paid_emergency_enabled",
                                False,
                            )
                        ),
                        max_paid_decisions_per_unlock=int(
                            payload.get(
                                "max_paid_decisions_per_unlock",
                                1,
                            )
                        ),
                    )

            if (
                len(parts) == 4
                and parts[:2] == ["v1", "projects"]
                and parts[3] == "providers"
            ):
                if method == "GET":
                    return 200, {
                        "items": self.routing.list_providers(
                            parts[2]
                        )
                    }

                if method == "POST":
                    territories = payload.get(
                        "territories",
                        [],
                    )
                    metadata = payload.get(
                        "metadata",
                        {},
                    )

                    if not isinstance(territories, list):
                        raise ValidationError(
                            "territories must be a list"
                        )

                    if not isinstance(metadata, dict):
                        raise ValidationError(
                            "metadata must be an object"
                        )

                    return 201, self.routing.configure_provider(
                        project_id=parts[2],
                        name=str(
                            payload.get("name", "")
                        ),
                        provider_class=str(
                            payload.get(
                                "provider_class",
                                "",
                            )
                        ),
                        cost_class=str(
                            payload.get(
                                "cost_class",
                                "",
                            )
                        ),
                        territories=[
                            str(item)
                            for item in territories
                        ],
                        priority=int(
                            payload.get(
                                "priority",
                                100,
                            )
                        ),
                        metadata=metadata,
                        enabled=bool(
                            payload.get(
                                "enabled",
                                True,
                            )
                        ),
                    )

            if (
                len(parts) == 4
                and parts[:2] == ["v1", "providers"]
                and parts[3] == "health"
                and method == "POST"
            ):
                cooldown_value = payload.get(
                    "cooldown_seconds"
                )
                latency_value = payload.get(
                    "latency_ms"
                )

                return 200, self.routing.record_health(
                    provider_id=parts[2],
                    status=str(
                        payload.get("status", "")
                    ),
                    latency_ms=(
                        float(latency_value)
                        if latency_value is not None
                        else None
                    ),
                    error_message=str(
                        payload.get(
                            "error_message",
                            "",
                        )
                    ),
                    cooldown_seconds=(
                        int(cooldown_value)
                        if cooldown_value is not None
                        else None
                    ),
                )

            if (
                len(parts) == 4
                and parts[:2] == ["v1", "projects"]
                and parts[3] == "renderers"
            ):
                if method == "GET":
                    return 200, {
                        "items": self.routing.list_renderers(
                            parts[2]
                        )
                    }

                if method == "POST":
                    territories = payload.get(
                        "territories",
                        [],
                    )
                    metadata = payload.get(
                        "metadata",
                        {},
                    )

                    if not isinstance(territories, list):
                        raise ValidationError(
                            "territories must be a list"
                        )

                    if not isinstance(metadata, dict):
                        raise ValidationError(
                            "metadata must be an object"
                        )

                    return 201, self.routing.configure_renderer(
                        project_id=parts[2],
                        name=str(
                            payload.get("name", "")
                        ),
                        renderer_type=str(
                            payload.get(
                                "renderer_type",
                                "",
                            )
                        ),
                        territories=[
                            str(item)
                            for item in territories
                        ],
                        priority=int(
                            payload.get(
                                "priority",
                                100,
                            )
                        ),
                        metadata=metadata,
                        enabled=bool(
                            payload.get(
                                "enabled",
                                True,
                            )
                        ),
                    )

            if (
                len(parts) == 5
                and parts[:2] == ["v1", "projects"]
                and parts[3] == "renderers"
                and parts[4] == "select"
                and method == "POST"
            ):
                return 200, {
                    "renderer": self.routing.select_renderer(
                        parts[2],
                        str(
                            payload.get(
                                "territory",
                                "",
                            )
                        ),
                    )
                }

            if (
                len(parts) == 4
                and parts[:2] == ["v1", "projects"]
                and parts[3] == "paid-unlocks"
                and method == "POST"
            ):
                provider_value = payload.get(
                    "provider_id"
                )
                max_value = payload.get(
                    "max_decisions"
                )

                return 201, self.routing.grant_paid_unlock(
                    project_id=parts[2],
                    actor_id=str(
                        payload.get("actor_id", "")
                    ),
                    reason=str(
                        payload.get("reason", "")
                    ),
                    ttl_seconds=int(
                        payload.get(
                            "ttl_seconds",
                            900,
                        )
                    ),
                    max_decisions=(
                        int(max_value)
                        if max_value is not None
                        else None
                    ),
                    provider_id=(
                        str(provider_value)
                        if provider_value
                        else None
                    ),
                )

            if (
                len(parts) == 4
                and parts[:2] == ["v1", "projects"]
                and parts[3] == "route-decisions"
                and method == "POST"
            ):
                return 201, self.routing.route(
                    project_id=parts[2],
                    territory=str(
                        payload.get(
                            "territory",
                            "",
                        )
                    ),
                    offline_capable=bool(
                        payload.get(
                            "offline_capable",
                            False,
                        )
                    ),
                )

            if (
                len(parts) == 3
                and parts[:2] == ["v1", "route-decisions"]
                and method == "GET"
            ):
                return 200, self.routing.get_decision(
                    parts[2]
                )

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
        api = self

        class Handler(BaseHTTPRequestHandler):
            def do_GET(self) -> None:
                self._handle()

            def do_POST(self) -> None:
                self._handle()

            def _handle(self) -> None:
                length = int(
                    self.headers.get(
                        "content-length",
                        "0",
                    )
                )

                payload: dict[str, object] = {}

                if length:
                    try:
                        parsed = json.loads(
                            self.rfile.read(
                                length
                            ).decode("utf-8")
                        )
                    except (
                        UnicodeDecodeError,
                        json.JSONDecodeError,
                    ):
                        self._send(
                            400,
                            {
                                "error": "INVALID_JSON",
                                "message": (
                                    "body must be valid JSON"
                                ),
                            },
                        )
                        return

                    if not isinstance(parsed, dict):
                        self._send(
                            400,
                            {
                                "error": "INVALID_JSON",
                                "message": (
                                    "body must be a JSON object"
                                ),
                            },
                        )
                        return

                    payload = parsed

                status, response = api.dispatch(
                    self.command,
                    self.path,
                    payload,
                )

                self._send(status, response)

            def _send(
                self,
                status: int,
                payload: dict[str, object],
            ) -> None:
                encoded = json.dumps(
                    payload,
                    indent=2,
                    sort_keys=True,
                ).encode("utf-8")

                self.send_response(status)
                self.send_header(
                    "content-type",
                    "application/json; charset=utf-8",
                )
                self.send_header(
                    "content-length",
                    str(len(encoded)),
                )
                self.end_headers()
                self.wfile.write(encoded)

        server = ThreadingHTTPServer(
            (host, port),
            Handler,
        )

        print(
            "SpecGraph Foundry listening on "
            f"http://{host}:{port}"
        )

        try:
            server.serve_forever()
        except KeyboardInterrupt:
            print("\nStopping SpecGraph Foundry.")
        finally:
            server.server_close()
