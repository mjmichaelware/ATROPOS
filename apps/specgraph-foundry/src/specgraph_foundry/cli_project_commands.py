"""Projects, health and the server.

`main` dispatched 49 commands in a 716-line function. The branches never
interacted, so reading one meant scrolling past the other 48. One module per
domain, each answering only for its own commands.

Returns True when it handled the command, so `main` tries each group in turn
and falls through to its usage error.
"""

from __future__ import annotations

from .api import Api
from .cli_output import output
from .doctor import inspect
import json


def handle(
    args=None,
    atoms=None,
    database=None,
    execution=None,
    exports=None,
    graphs=None,
    ingestion=None,
    planning=None,
    projects=None,
    research=None,
    routing=None,
    settings=None,
) -> bool:
    """Runs the command if this group owns it. Returns whether it did."""
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

    if args.command == "extract-document":
        output(
            atoms.extract_document(
                args.document_id
            )
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

    if args.command == "list-execution-runs":
        output(
            {
                "items": execution.list_runs(
                    args.project_id
                )
            }
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

    if args.command == "list-providers":
        output(
            {
                "items": routing.list_providers(
                    args.project_id
                )
            }
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

    return False


def register(commands, settings) -> None:
    """Adds this group's subcommands to the parser.

    Registration sits beside the handler for the same command so the two
    cannot drift -- a flag added here and unread there was previously a
    500-line scroll apart.
    """
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

    create_project = commands.add_parser(
        "create-project"
    )
    create_project.add_argument("slug")
    create_project.add_argument("name")
    create_project.add_argument(
        "--description",
        default="",
    )

    for name in (
        "init",
        "doctor",
        "demo",
        "list-projects",
    ):
        commands.add_parser(name)

    extract = commands.add_parser(
        "extract-document"
    )
    extract.add_argument("document_id")

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

    exports = commands.add_parser(
        "list-exports"
    )
    exports.add_argument("project_id")

    export_record = commands.add_parser(
        "export-record"
    )
    export_record.add_argument("export_id")

    execution_runs = commands.add_parser(
        "list-execution-runs"
    )
    execution_runs.add_argument(
        "project_id"
    )

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

    providers = commands.add_parser(
        "list-providers"
    )
    providers.add_argument("project_id")

    renderers = commands.add_parser(
        "list-renderers"
    )
    renderers.add_argument("project_id")


def register(commands, settings) -> None:
    """Adds this group's subcommands to the parser.

    Registration sits beside the handler for the same command so the two
    cannot drift; they were previously 500 lines apart.
    """
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

    create_project = commands.add_parser(
        "create-project"
    )
    create_project.add_argument("slug")
    create_project.add_argument("name")
    create_project.add_argument(
        "--description",
        default="",
    )

    for name in (
        "init",
        "doctor",
        "demo",
        "list-projects",
    ):
        commands.add_parser(name)

    extract = commands.add_parser(
        "extract-document"
    )
    extract.add_argument("document_id")

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

    exports = commands.add_parser(
        "list-exports"
    )
    exports.add_argument("project_id")

    export_record = commands.add_parser(
        "export-record"
    )
    export_record.add_argument("export_id")

    execution_runs = commands.add_parser(
        "list-execution-runs"
    )
    execution_runs.add_argument(
        "project_id"
    )

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

    providers = commands.add_parser(
        "list-providers"
    )
    providers.add_argument("project_id")

    renderers = commands.add_parser(
        "list-renderers"
    )
    renderers.add_argument("project_id")
