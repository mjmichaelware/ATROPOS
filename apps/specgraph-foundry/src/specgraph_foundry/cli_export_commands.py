"""Creating and verifying export bundles.

`main` dispatched 49 commands in a 716-line function. The branches never
interacted, so reading one meant scrolling past the other 48. One module per
domain, each answering only for its own commands.

Returns True when it handled the command, so `main` tries each group in turn
and falls through to its usage error.
"""

from __future__ import annotations

from pathlib import Path
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
    if args.command == "export-plan":
        output(
            exports.export_plan(
                args.plan_id,
                args.output_root,
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

    return False

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




def register(commands, settings) -> None:
    """Adds this group's subcommands to the parser.

    Registration sits beside the handler for the same command so the two
    cannot drift; they were previously 500 lines apart.
    """
    export_plan = commands.add_parser(
        "export-plan"
    )
    export_plan.add_argument("plan_id")
    export_plan.add_argument(
        "--output-root",
        type=Path,
    )

    verify_export = commands.add_parser(
        "verify-export"
    )
    verify_export.add_argument("export_id")

    exports = commands.add_parser(
        "list-exports"
    )
    exports.add_argument("project_id")
    export_record = commands.add_parser(
        "export-record"
    )
    export_record.add_argument("export_id")
