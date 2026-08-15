"""The research loop.

`main` dispatched 49 commands in a 716-line function. The branches never
interacted, so reading one meant scrolling past the other 48. One module per
domain, each answering only for its own commands.

Returns True when it handled the command, so `main` tries each group in turn
and falls through to its usage error.
"""

from __future__ import annotations

from .cli_output import output
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

    return False

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
    if args.command == "research-task":
        output(
            research.get_task(
                args.task_id
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
    if args.command == "research-heartbeat":
        output(
            research.heartbeat(
                args.task_id,
                args.worker_id,
                args.lease_seconds,
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




def register(commands, settings) -> None:
    """Adds this group's subcommands to the parser.

    Registration sits beside the handler for the same command so the two
    cannot drift; they were previously 500 lines apart.
    """
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
    task = commands.add_parser(
        "research-task"
    )
    task.add_argument("task_id")
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
    matrix = commands.add_parser(
        "gap-matrix"
    )
    matrix.add_argument("project_id")
