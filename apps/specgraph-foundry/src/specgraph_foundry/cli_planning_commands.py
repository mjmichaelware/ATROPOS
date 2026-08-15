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
    if args.command == "list-relations":
        output(
            {
                "items": planning.list_relations(
                    args.project_id
                )
            }
        )
        return 0




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
    relations = commands.add_parser(
        "list-relations"
    )
    relations.add_argument("project_id")
