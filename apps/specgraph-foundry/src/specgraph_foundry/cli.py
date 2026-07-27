import argparse
import json
import uuid
from pathlib import Path

from .api import Api
from .atoms import AtomService
from .config import Settings
from .database import Database
from .doctor import inspect
from .ingestion import IngestionService
from .research import ResearchService
from .planning import PlanningService
from .exports import ExportService
from .execution import ExecutionService
from .routing import RoutingService
from .services import GraphService, ProjectService


def output(value: object) -> None:
    print(
        json.dumps(
            value,
            indent=2,
            sort_keys=True,
        )
    )


def build_parser() -> argparse.ArgumentParser:
    settings = Settings.from_environment()

    parser = argparse.ArgumentParser(
        prog="specgraph"
    )

    commands = parser.add_subparsers(
        dest="command",
        required=True,
    )

    for name in (
        "init",
        "doctor",
        "demo",
        "list-projects",
    ):
        commands.add_parser(name)

    create_project = commands.add_parser(
        "create-project"
    )
    create_project.add_argument("slug")
    create_project.add_argument("name")
    create_project.add_argument(
        "--description",
        default="",
    )

    ingest_file = commands.add_parser(
        "ingest-file"
    )
    ingest_file.add_argument("project_id")
    ingest_file.add_argument("path", type=Path)
    ingest_file.add_argument("--title")
    ingest_file.add_argument(
        "--chunk-bytes",
        type=int,
        default=32768,
    )

    document = commands.add_parser("document")
    document.add_argument("document_id")
    document.add_argument(
        "--include-chunks",
        action="store_true",
    )

    verify = commands.add_parser(
        "verify-document"
    )
    verify.add_argument("document_id")

    extract = commands.add_parser(
        "extract-document"
    )
    extract.add_argument("document_id")

    list_atoms = commands.add_parser(
        "list-atoms"
    )
    list_atoms.add_argument("document_id")

    atom = commands.add_parser("atom")
    atom.add_argument("atom_id")

    research_tasks = commands.add_parser(
        "research-tasks"
    )
    research_tasks.add_argument("project_id")
    research_tasks.add_argument("--status")

    claim = commands.add_parser(
        "claim-research"
    )
    claim.add_argument("project_id")
    claim.add_argument("worker_id")
    claim.add_argument(
        "--lease-seconds",
        type=int,
        default=900,
    )

    heartbeat = commands.add_parser(
        "research-heartbeat"
    )
    heartbeat.add_argument("task_id")
    heartbeat.add_argument("worker_id")
    heartbeat.add_argument(
        "--lease-seconds",
        type=int,
        default=900,
    )

    evidence = commands.add_parser(
        "add-evidence"
    )
    evidence.add_argument("task_id")
    evidence.add_argument("worker_id")
    evidence.add_argument("source_uri")
    evidence.add_argument("source_title")
    evidence.add_argument("excerpt")
    evidence.add_argument(
        "--publisher",
        default="",
    )
    evidence.add_argument(
        "--evidence-type",
        default="OTHER",
    )
    evidence.add_argument(
        "--reliability",
        type=float,
        default=0.5,
    )

    complete = commands.add_parser(
        "complete-research"
    )
    complete.add_argument("task_id")
    complete.add_argument("worker_id")
    complete.add_argument("conclusion")
    complete.add_argument("applicability")
    complete.add_argument(
        "confidence",
        type=float,
    )
    complete.add_argument(
        "evidence_ids",
        nargs="+",
    )

    fail = commands.add_parser(
        "fail-research"
    )
    fail.add_argument("task_id")
    fail.add_argument("worker_id")
    fail.add_argument("error_message")
    fail.add_argument(
        "--terminal",
        action="store_true",
    )

    task = commands.add_parser(
        "research-task"
    )
    task.add_argument("task_id")

    matrix = commands.add_parser(
        "gap-matrix"
    )
    matrix.add_argument("project_id")


    relation = commands.add_parser(
        "add-relation"
    )
    relation.add_argument("project_id")
    relation.add_argument("from_atom_id")
    relation.add_argument("to_atom_id")
    relation.add_argument("relation_type")
    relation.add_argument(
        "--rationale",
        default="",
    )
    relation.add_argument(
        "--confidence",
        type=float,
        default=1.0,
    )
    relation.add_argument(
        "--inferred",
        action="store_true",
    )

    relations = commands.add_parser(
        "list-relations"
    )
    relations.add_argument("project_id")

    synthesize = commands.add_parser(
        "synthesize-plan"
    )
    synthesize.add_argument("project_id")
    synthesize.add_argument(
        "--allow-open-research",
        action="store_true",
    )

    plans = commands.add_parser(
        "list-plans"
    )
    plans.add_argument("project_id")

    plan = commands.add_parser("plan")
    plan.add_argument("plan_id")

    verify_plan = commands.add_parser(
        "verify-plan"
    )
    verify_plan.add_argument("plan_id")


    bind = commands.add_parser(
        "bind-integration"
    )
    bind.add_argument("project_id")
    bind.add_argument("system_name")
    bind.add_argument("binding_type")
    bind.add_argument("config_json")
    bind.add_argument(
        "--disabled",
        action="store_true",
    )

    bindings = commands.add_parser(
        "list-bindings"
    )
    bindings.add_argument("project_id")

    export_plan = commands.add_parser(
        "export-plan"
    )
    export_plan.add_argument("plan_id")
    export_plan.add_argument(
        "--output-root",
        type=Path,
    )

    exports = commands.add_parser(
        "list-exports"
    )
    exports.add_argument("project_id")

    export_record = commands.add_parser(
        "export-record"
    )
    export_record.add_argument("export_id")

    verify_export = commands.add_parser(
        "verify-export"
    )
    verify_export.add_argument("export_id")


    start_execution = commands.add_parser(
        "start-execution"
    )
    start_execution.add_argument("plan_id")
    start_execution.add_argument(
        "runtime_system"
    )
    start_execution.add_argument(
        "runtime_run_id"
    )
    start_execution.add_argument(
        "--export-id"
    )

    execution_runs = commands.add_parser(
        "list-execution-runs"
    )
    execution_runs.add_argument(
        "project_id"
    )

    execution_run = commands.add_parser(
        "execution-run"
    )
    execution_run.add_argument("run_id")

    claim_execution = commands.add_parser(
        "claim-execution-node"
    )
    claim_execution.add_argument("run_id")
    claim_execution.add_argument(
        "worker_id"
    )
    claim_execution.add_argument(
        "--run-node-id"
    )
    claim_execution.add_argument(
        "--lease-seconds",
        type=int,
        default=900,
    )

    execution_heartbeat = commands.add_parser(
        "execution-heartbeat"
    )
    execution_heartbeat.add_argument(
        "run_node_id"
    )
    execution_heartbeat.add_argument(
        "worker_id"
    )
    execution_heartbeat.add_argument(
        "--lease-seconds",
        type=int,
        default=900,
    )

    submit_receipt = commands.add_parser(
        "submit-execution-receipt"
    )
    submit_receipt.add_argument(
        "run_node_id"
    )
    submit_receipt.add_argument(
        "worker_id"
    )
    submit_receipt.add_argument(
        "actor_system"
    )
    submit_receipt.add_argument("outcome")
    submit_receipt.add_argument("summary")
    submit_receipt.add_argument(
        "evidence_json"
    )

    verify_execution = commands.add_parser(
        "verify-execution-run"
    )
    verify_execution.add_argument("run_id")


    set_policy = commands.add_parser(
        "set-routing-policy"
    )
    set_policy.add_argument("project_id")
    set_policy.add_argument(
        "--disable-offline",
        action="store_true",
    )
    set_policy.add_argument(
        "--enable-paid-emergency",
        action="store_true",
    )
    set_policy.add_argument(
        "--max-paid-decisions",
        type=int,
        default=1,
    )

    routing_policy = commands.add_parser(
        "routing-policy"
    )
    routing_policy.add_argument("project_id")

    configure_provider = commands.add_parser(
        "configure-provider"
    )
    configure_provider.add_argument(
        "project_id"
    )
    configure_provider.add_argument("name")
    configure_provider.add_argument(
        "provider_class"
    )
    configure_provider.add_argument(
        "cost_class"
    )
    configure_provider.add_argument(
        "priority",
        type=int,
    )
    configure_provider.add_argument(
        "territories_json"
    )
    configure_provider.add_argument(
        "--metadata-json",
        default="{}",
    )
    configure_provider.add_argument(
        "--disabled",
        action="store_true",
    )

    providers = commands.add_parser(
        "list-providers"
    )
    providers.add_argument("project_id")

    provider_health = commands.add_parser(
        "provider-health"
    )
    provider_health.add_argument(
        "provider_id"
    )
    provider_health.add_argument("status")
    provider_health.add_argument(
        "--latency-ms",
        type=float,
    )
    provider_health.add_argument(
        "--error-message",
        default="",
    )
    provider_health.add_argument(
        "--cooldown-seconds",
        type=int,
    )

    configure_renderer = commands.add_parser(
        "configure-renderer"
    )
    configure_renderer.add_argument(
        "project_id"
    )
    configure_renderer.add_argument("name")
    configure_renderer.add_argument(
        "renderer_type"
    )
    configure_renderer.add_argument(
        "priority",
        type=int,
    )
    configure_renderer.add_argument(
        "territories_json"
    )
    configure_renderer.add_argument(
        "--metadata-json",
        default="{}",
    )
    configure_renderer.add_argument(
        "--disabled",
        action="store_true",
    )

    renderers = commands.add_parser(
        "list-renderers"
    )
    renderers.add_argument("project_id")

    select_renderer = commands.add_parser(
        "select-renderer"
    )
    select_renderer.add_argument(
        "project_id"
    )
    select_renderer.add_argument(
        "territory"
    )

    unlock_paid = commands.add_parser(
        "unlock-paid-route"
    )
    unlock_paid.add_argument("project_id")
    unlock_paid.add_argument("actor_id")
    unlock_paid.add_argument("reason")
    unlock_paid.add_argument(
        "--ttl-seconds",
        type=int,
        default=900,
    )
    unlock_paid.add_argument(
        "--max-decisions",
        type=int,
    )
    unlock_paid.add_argument(
        "--provider-id"
    )

    route_capability = commands.add_parser(
        "route-capability"
    )
    route_capability.add_argument(
        "project_id"
    )
    route_capability.add_argument(
        "territory"
    )
    route_capability.add_argument(
        "--offline-capable",
        action="store_true",
    )

    route_decision = commands.add_parser(
        "route-decision"
    )
    route_decision.add_argument(
        "decision_id"
    )

    server = commands.add_parser("serve")
    server.add_argument(
        "--host",
        default=settings.host,
    )
    server.add_argument(
        "--port",
        type=int,
        default=settings.port,
    )

    return parser


def main() -> int:
    settings = Settings.from_environment()
    args = build_parser().parse_args()

    database = Database(
        settings.database_path,
        database_url=settings.database_url,
        owner_id=settings.database_owner_id,
    )
    database.initialize()

    projects = ProjectService(database)
    ingestion = IngestionService(database)
    atoms = AtomService(database)
    research = ResearchService(database)
    planning = PlanningService(database)
    exports = ExportService(database)
    execution = ExecutionService(database)
    routing = RoutingService(database)
    graphs = GraphService(database)

    if args.command == "init":
        output(database.health())
        return 0

    if args.command == "doctor":
        output(inspect())
        return 0

    if args.command == "serve":
        Api(database).serve(
            args.host,
            args.port,
        )
        return 0

    if args.command == "list-projects":
        output({"items": projects.list()})
        return 0

    if args.command == "create-project":
        output(
            projects.create(
                args.slug,
                args.name,
                args.description,
            )
        )
        return 0

    if args.command == "ingest-file":
        output(
            ingestion.ingest_file(
                project_id=args.project_id,
                path=args.path,
                title=args.title,
                chunk_bytes=args.chunk_bytes,
            )
        )
        return 0

    if args.command == "document":
        output(
            ingestion.get_document(
                args.document_id,
                include_chunk_content=(
                    args.include_chunks
                ),
            )
        )
        return 0

    if args.command == "verify-document":
        output(
            ingestion.verify_document(
                args.document_id
            )
        )
        return 0

    if args.command == "extract-document":
        output(
            atoms.extract_document(
                args.document_id
            )
        )
        return 0

    if args.command == "list-atoms":
        output(
            {
                "items": atoms.list_atoms(
                    args.document_id
                )
            }
        )
        return 0

    if args.command == "atom":
        output(atoms.get_atom(args.atom_id))
        return 0

    if args.command == "research-tasks":
        output(
            {
                "items": atoms.list_research_tasks(
                    args.project_id,
                    args.status,
                )
            }
        )
        return 0

    if args.command == "claim-research":
        output(
            {
                "task": research.claim_task(
                    args.project_id,
                    args.worker_id,
                    args.lease_seconds,
                )
            }
        )
        return 0

    if args.command == "research-heartbeat":
        output(
            research.heartbeat(
                args.task_id,
                args.worker_id,
                args.lease_seconds,
            )
        )
        return 0

    if args.command == "add-evidence":
        output(
            research.add_evidence(
                task_id=args.task_id,
                worker_id=args.worker_id,
                source_uri=args.source_uri,
                source_title=args.source_title,
                excerpt=args.excerpt,
                publisher=args.publisher,
                evidence_type=args.evidence_type,
                reliability=args.reliability,
            )
        )
        return 0

    if args.command == "complete-research":
        output(
            research.complete_task(
                task_id=args.task_id,
                worker_id=args.worker_id,
                conclusion=args.conclusion,
                applicability=args.applicability,
                confidence=args.confidence,
                evidence_ids=args.evidence_ids,
            )
        )
        return 0

    if args.command == "fail-research":
        output(
            research.fail_task(
                task_id=args.task_id,
                worker_id=args.worker_id,
                error_message=args.error_message,
                retryable=not args.terminal,
            )
        )
        return 0

    if args.command == "research-task":
        output(
            research.get_task(
                args.task_id
            )
        )
        return 0

    if args.command == "gap-matrix":
        output(
            research.gap_matrix(
                args.project_id
            )
        )
        return 0


    if args.command == "add-relation":
        output(
            planning.add_relation(
                project_id=args.project_id,
                from_atom_id=args.from_atom_id,
                to_atom_id=args.to_atom_id,
                relation_type=args.relation_type,
                rationale=args.rationale,
                confidence=args.confidence,
                inferred=args.inferred,
            )
        )
        return 0

    if args.command == "list-relations":
        output(
            {
                "items": planning.list_relations(
                    args.project_id
                )
            }
        )
        return 0

    if args.command == "synthesize-plan":
        output(
            planning.synthesize(
                project_id=args.project_id,
                allow_open_research=(
                    args.allow_open_research
                ),
            )
        )
        return 0

    if args.command == "list-plans":
        output(
            {
                "items": planning.list_plans(
                    args.project_id
                )
            }
        )
        return 0

    if args.command == "plan":
        output(
            planning.get_plan(
                args.plan_id
            )
        )
        return 0

    if args.command == "verify-plan":
        output(
            planning.verify_plan(
                args.plan_id
            )
        )
        return 0


    if args.command == "bind-integration":
        try:
            config = json.loads(
                args.config_json
            )
        except json.JSONDecodeError as error:
            raise SystemExit(
                "config_json must be valid JSON"
            ) from error

        if not isinstance(config, dict):
            raise SystemExit(
                "config_json must be a JSON object"
            )

        output(
            exports.bind_integration(
                project_id=args.project_id,
                system_name=args.system_name,
                binding_type=args.binding_type,
                config=config,
                enabled=not args.disabled,
            )
        )
        return 0

    if args.command == "list-bindings":
        output(
            {
                "items": exports.list_bindings(
                    args.project_id
                )
            }
        )
        return 0

    if args.command == "export-plan":
        output(
            exports.export_plan(
                args.plan_id,
                args.output_root,
            )
        )
        return 0

    if args.command == "list-exports":
        output(
            {
                "items": exports.list_exports(
                    args.project_id
                )
            }
        )
        return 0

    if args.command == "export-record":
        output(
            exports.get_export(
                args.export_id
            )
        )
        return 0

    if args.command == "verify-export":
        output(
            exports.verify_export(
                args.export_id
            )
        )
        return 0


    if args.command == "start-execution":
        output(
            execution.start_run(
                plan_id=args.plan_id,
                runtime_system=(
                    args.runtime_system
                ),
                runtime_run_id=(
                    args.runtime_run_id
                ),
                export_id=args.export_id,
            )
        )
        return 0

    if args.command == "list-execution-runs":
        output(
            {
                "items": execution.list_runs(
                    args.project_id
                )
            }
        )
        return 0

    if args.command == "execution-run":
        output(
            execution.get_run(
                args.run_id
            )
        )
        return 0

    if args.command == "claim-execution-node":
        output(
            {
                "claim": execution.claim_node(
                    run_id=args.run_id,
                    worker_id=args.worker_id,
                    run_node_id=(
                        args.run_node_id
                    ),
                    lease_seconds=(
                        args.lease_seconds
                    ),
                )
            }
        )
        return 0

    if args.command == "execution-heartbeat":
        output(
            execution.heartbeat(
                run_node_id=(
                    args.run_node_id
                ),
                worker_id=args.worker_id,
                lease_seconds=(
                    args.lease_seconds
                ),
            )
        )
        return 0

    if args.command == "submit-execution-receipt":
        try:
            evidence = json.loads(
                args.evidence_json
            )
        except json.JSONDecodeError as error:
            raise SystemExit(
                "evidence_json must be valid JSON"
            ) from error

        if not isinstance(evidence, dict):
            raise SystemExit(
                "evidence_json must be "
                "a JSON object"
            )

        output(
            execution.submit_receipt(
                run_node_id=(
                    args.run_node_id
                ),
                worker_id=args.worker_id,
                actor_system=(
                    args.actor_system
                ),
                outcome=args.outcome,
                summary=args.summary,
                evidence=evidence,
            )
        )
        return 0

    if args.command == "verify-execution-run":
        output(
            execution.verify_run(
                args.run_id
            )
        )
        return 0


    if args.command == "set-routing-policy":
        output(
            routing.set_policy(
                project_id=args.project_id,
                allow_offline_degraded=(
                    not args.disable_offline
                ),
                paid_emergency_enabled=(
                    args.enable_paid_emergency
                ),
                max_paid_decisions_per_unlock=(
                    args.max_paid_decisions
                ),
            )
        )
        return 0

    if args.command == "routing-policy":
        output(
            routing.get_policy(
                args.project_id
            )
        )
        return 0

    if args.command == "configure-provider":
        try:
            territories = json.loads(
                args.territories_json
            )
            metadata = json.loads(
                args.metadata_json
            )
        except json.JSONDecodeError as error:
            raise SystemExit(
                "territories and metadata must "
                "be valid JSON"
            ) from error

        if not isinstance(territories, list):
            raise SystemExit(
                "territories_json must be a "
                "JSON list"
            )

        if not isinstance(metadata, dict):
            raise SystemExit(
                "metadata_json must be a "
                "JSON object"
            )

        output(
            routing.configure_provider(
                project_id=args.project_id,
                name=args.name,
                provider_class=(
                    args.provider_class
                ),
                cost_class=args.cost_class,
                territories=[
                    str(item)
                    for item in territories
                ],
                priority=args.priority,
                metadata=metadata,
                enabled=not args.disabled,
            )
        )
        return 0

    if args.command == "list-providers":
        output(
            {
                "items": routing.list_providers(
                    args.project_id
                )
            }
        )
        return 0

    if args.command == "provider-health":
        output(
            routing.record_health(
                provider_id=args.provider_id,
                status=args.status,
                latency_ms=args.latency_ms,
                error_message=(
                    args.error_message
                ),
                cooldown_seconds=(
                    args.cooldown_seconds
                ),
            )
        )
        return 0

    if args.command == "configure-renderer":
        try:
            territories = json.loads(
                args.territories_json
            )
            metadata = json.loads(
                args.metadata_json
            )
        except json.JSONDecodeError as error:
            raise SystemExit(
                "territories and metadata must "
                "be valid JSON"
            ) from error

        if not isinstance(territories, list):
            raise SystemExit(
                "territories_json must be a "
                "JSON list"
            )

        if not isinstance(metadata, dict):
            raise SystemExit(
                "metadata_json must be a "
                "JSON object"
            )

        output(
            routing.configure_renderer(
                project_id=args.project_id,
                name=args.name,
                renderer_type=(
                    args.renderer_type
                ),
                territories=[
                    str(item)
                    for item in territories
                ],
                priority=args.priority,
                metadata=metadata,
                enabled=not args.disabled,
            )
        )
        return 0

    if args.command == "list-renderers":
        output(
            {
                "items": routing.list_renderers(
                    args.project_id
                )
            }
        )
        return 0

    if args.command == "select-renderer":
        output(
            {
                "renderer": routing.select_renderer(
                    args.project_id,
                    args.territory,
                )
            }
        )
        return 0

    if args.command == "unlock-paid-route":
        output(
            routing.grant_paid_unlock(
                project_id=args.project_id,
                actor_id=args.actor_id,
                reason=args.reason,
                ttl_seconds=args.ttl_seconds,
                max_decisions=(
                    args.max_decisions
                ),
                provider_id=(
                    args.provider_id
                ),
            )
        )
        return 0

    if args.command == "route-capability":
        output(
            routing.route(
                project_id=args.project_id,
                territory=args.territory,
                offline_capable=(
                    args.offline_capable
                ),
            )
        )
        return 0

    if args.command == "route-decision":
        output(
            routing.get_decision(
                args.decision_id
            )
        )
        return 0

    suffix = uuid.uuid4().hex[:8]

    project = projects.create(
        f"demo-{suffix}",
        "SpecGraph Demonstration",
    )

    document = ingestion.ingest_text(
        project_id=str(project["id"]),
        title="Demo authority",
        content=(
            "# Contract\n"
            "Contracts must exist before implementation.\n\n"
            "## Verification\n"
            "Implementation must pass independent verification.\n"
        ),
        chunk_bytes=48,
    )

    extraction = atoms.extract_document(
        str(document["id"])
    )

    graph = graphs.create(
        str(project["id"]),
        "Demo Execution DAG",
        "EXECUTION",
        True,
    )

    contract = graphs.add_node(
        str(graph["id"]),
        "contract",
        "BATCH",
        "Define contract",
    )

    implementation = graphs.add_node(
        str(graph["id"]),
        "implementation",
        "BATCH",
        "Implement service",
    )

    verification = graphs.add_node(
        str(graph["id"]),
        "verification",
        "GATE",
        "Verify service",
    )

    graphs.add_edge(
        str(graph["id"]),
        str(contract["id"]),
        str(implementation["id"]),
        "MUST_PRECEDE",
    )

    graphs.add_edge(
        str(graph["id"]),
        str(implementation["id"]),
        str(verification["id"]),
        "MUST_PRECEDE",
    )

    output(
        {
            "project": project,
            "document": document,
            "extraction": extraction,
            "document_verification": (
                ingestion.verify_document(
                    str(document["id"])
                )
            ),
            "gap_matrix": research.gap_matrix(
                str(project["id"])
            ),
            "graph": graphs.get(
                str(graph["id"])
            ),
            "ready_nodes": graphs.ready_nodes(
                str(graph["id"])
            ),
        }
    )

    return 0
