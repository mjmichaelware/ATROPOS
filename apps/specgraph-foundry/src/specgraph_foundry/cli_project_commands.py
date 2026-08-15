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

