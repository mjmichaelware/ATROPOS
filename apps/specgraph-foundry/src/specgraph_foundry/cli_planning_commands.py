"""Relations, synthesis, verification, bindings.

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

    return False




def register(commands, settings) -> None:
    """Adds this group's subcommands to the parser.

    Registration sits beside the handler for the same command so the two
    cannot drift; they were previously 500 lines apart.
    """
    plan = commands.add_parser("plan")
    plan.add_argument("plan_id")

    verify_plan = commands.add_parser(
        "verify-plan"
    )
    verify_plan.add_argument("plan_id")
