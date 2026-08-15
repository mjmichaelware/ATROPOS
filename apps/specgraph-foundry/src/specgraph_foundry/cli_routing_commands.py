"""Policy, providers, renderers, paid unlocks.

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

    return False




def register(commands, settings) -> None:
    """Adds this group's subcommands to the parser.

    Registration sits beside the handler for the same command so the two
    cannot drift; they were previously 500 lines apart.
    """
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
