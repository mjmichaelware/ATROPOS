"""Runs: start, claim, receipt, verify.

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

    if args.command == "execution-run":
        output(
            execution.get_run(
                args.run_id
            )
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

    return False

    if args.command == "list-execution-runs":
        output(
            {
                "items": execution.list_runs(
                    args.project_id
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




def register(commands, settings) -> None:
    """Adds this group's subcommands to the parser.

    Registration sits beside the handler for the same command so the two
    cannot drift; they were previously 500 lines apart.
    """
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

    execution_run = commands.add_parser(
        "execution-run"
    )
    execution_run.add_argument("run_id")

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

    execution_runs = commands.add_parser(
        "list-execution-runs"
    )
    execution_runs.add_argument(
        "project_id"
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
