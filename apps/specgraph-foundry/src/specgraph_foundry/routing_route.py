"""Choosing a provider for a capability.

The decision itself: eligible providers, cooldown, policy, and the paid unlock
if one is being spent. The largest job in routing and the one an operator most
often needs to explain after the fact, which is why the decision it records is
written by :mod:`routing_decisions` rather than inline here.
"""

from __future__ import annotations

from .routing_decisions import record_decision
from .routing_guards import is_cooling
from .routing_paid_unlocks import consume_paid_unlock
from .routing_policy import get_policy
from .routing_providers import list_providers
from datetime import datetime
from datetime import timedelta
import json
import sqlite3

from .database import Database
from .errors import ConflictError, NotFoundError, ValidationError
from .primitives import parse_time, utc_now_datetime
from .sensitive_keys import contains_sensitive_key



def route(
    database: Database,
    project_id: str,
    territory: str,
    offline_capable: bool = False,
) -> dict[str, object]:
    territory = territory.strip().upper()

    if not territory:
        raise ValidationError(
            "territory is required"
        )

    policy = get_policy(database, 
        project_id
    )

    providers = list_providers(database, 
        project_id
    )

    capable = [
        provider
        for provider in providers
        if provider["enabled"]
        and (
            territory
            in provider["territories"]
            or "*"
            in provider["territories"]
        )
    ]

    considered = [
        {
            "id": provider["id"],
            "name": provider["name"],
            "provider_class": (
                provider[
                    "provider_class"
                ]
            ),
            "cost_class": (
                provider["cost_class"]
            ),
            "priority": provider[
                "priority"
            ],
            "status": provider["status"],
            "cooldown_until": (
                provider[
                    "cooldown_until"
                ]
            ),
        }
        for provider in capable
    ]

    now = utc_now_datetime()

    local = select_provider(
        capable,
        provider_class=(
            "LOCAL_TOOLCHAIN"
        ),
        allowed_statuses={"READY"},
        now=now,
    )

    if local is not None:
        return record_decision(
            database,
            project_id=project_id,
            territory=territory,
            decision_type=(
                "LOCAL_TOOLCHAIN"
            ),
            selected_provider=local,
            paid_unlock_id=None,
            retry_at=None,
            rationale=(
                "Selected ready local toolchain "
                "under canonical route law."
            ),
            offline_capable=(
                offline_capable
            ),
            considered=considered,
        )

    free_ready = select_provider(
        capable,
        provider_class=(
            "FREE_READY_PROVIDER"
        ),
        allowed_statuses={"READY"},
        now=now,
    )

    if free_ready is not None:
        return record_decision(
            database,
            project_id=project_id,
            territory=territory,
            decision_type=(
                "FREE_READY_PROVIDER"
            ),
            selected_provider=(
                free_ready
            ),
            paid_unlock_id=None,
            retry_at=None,
            rationale=(
                "Selected ready free provider "
                "after local routing was "
                "unavailable."
            ),
            offline_capable=(
                offline_capable
            ),
            considered=considered,
        )

    free_fallback = select_provider(
        capable,
        provider_class=(
            "FREE_FALLBACK_PROVIDER"
        ),
        allowed_statuses={
            "READY",
            "DEGRADED",
        },
        now=now,
    )

    if free_fallback is not None:
        return record_decision(
            database,
            project_id=project_id,
            territory=territory,
            decision_type=(
                "FREE_FALLBACK_PROVIDER"
            ),
            selected_provider=(
                free_fallback
            ),
            paid_unlock_id=None,
            retry_at=None,
            rationale=(
                "Selected free fallback "
                "provider after preferred free "
                "routes were unavailable."
            ),
            offline_capable=(
                offline_capable
            ),
            considered=considered,
        )

    cooling = [
        provider
        for provider in capable
        if (
            provider[
                "provider_class"
            ]
            != "PAID_EMERGENCY"
            and is_cooling(
                provider,
                now,
            )
        )
    ]

    if cooling:
        retry_times = [
            parse_time(
                str(
                    provider[
                        "cooldown_until"
                    ]
                )
            )
            for provider in cooling
            if provider[
                "cooldown_until"
            ]
        ]

        retry_at = (
            min(retry_times).isoformat()
            if retry_times
            else (
                now
                + timedelta(minutes=5)
            ).isoformat()
        )

        return record_decision(
            database,
            project_id=project_id,
            territory=territory,
            decision_type=(
                "COOLDOWN_QUEUE"
            ),
            selected_provider=None,
            paid_unlock_id=None,
            retry_at=retry_at,
            rationale=(
                "A capable non-paid route is "
                "cooling down; work must queue "
                "before degraded or paid routing."
            ),
            offline_capable=(
                offline_capable
            ),
            considered=considered,
        )

    if (
        offline_capable
        and policy[
            "allow_offline_degraded"
        ]
    ):
        return record_decision(
            database,
            project_id=project_id,
            territory=territory,
            decision_type=(
                "OFFLINE_DEGRADED_MODE"
            ),
            selected_provider=None,
            paid_unlock_id=None,
            retry_at=None,
            rationale=(
                "No local or free provider is "
                "available; capability may run "
                "in policy-approved offline "
                "degraded mode."
            ),
            offline_capable=(
                offline_capable
            ),
            considered=considered,
        )

    paid_provider = select_provider(
        capable,
        provider_class="PAID_EMERGENCY",
        allowed_statuses={"READY"},
        now=now,
    )

    if (
        paid_provider is not None
        and policy[
            "paid_emergency_enabled"
        ]
    ):
        unlock = consume_paid_unlock(database, 
            project_id=project_id,
            provider_id=str(
                paid_provider["id"]
            ),
        )

        if unlock is not None:
            return record_decision(
                database,
                project_id=project_id,
                territory=territory,
                decision_type=(
                    "PAID_EMERGENCY_ONLY_"
                    "BY_EXPLICIT_UNLOCK"
                ),
                selected_provider=(
                    paid_provider
                ),
                paid_unlock_id=str(
                    unlock["id"]
                ),
                retry_at=None,
                rationale=(
                    "Selected paid emergency "
                    "provider using an explicit, "
                    "unexpired, capacity-limited "
                    "unlock."
                ),
                offline_capable=(
                    offline_capable
                ),
                considered=considered,
            )

        return record_decision(
            database,
            project_id=project_id,
            territory=territory,
            decision_type=(
                "BLOCKED_PAID_UNLOCK_REQUIRED"
            ),
            selected_provider=None,
            paid_unlock_id=None,
            retry_at=None,
            rationale=(
                "A paid provider is ready, but "
                "no valid explicit paid unlock "
                "exists."
            ),
            offline_capable=(
                offline_capable
            ),
            considered=considered,
        )

    return record_decision(
        database,
        project_id=project_id,
        territory=territory,
        decision_type="UNROUTABLE",
        selected_provider=None,
        paid_unlock_id=None,
        retry_at=None,
        rationale=(
            "No policy-permitted route is "
            "currently available."
        ),
        offline_capable=(
            offline_capable
        ),
        considered=considered,
    )


def select_provider(
    providers: list[
        dict[str, object]
    ],
    provider_class: str,
    allowed_statuses: set[str],
    now: datetime,
) -> dict[str, object] | None:
    eligible = [
        provider
        for provider in providers
        if (
            provider[
                "provider_class"
            ]
            == provider_class
            and provider["status"]
            in allowed_statuses
            and not is_cooling(
                provider,
                now,
            )
        )
    ]

    if not eligible:
        return None

    eligible.sort(
        key=lambda provider: (
            int(provider["priority"]),
            (
                0
                if provider["status"]
                == "READY"
                else 1
            ),
            str(provider["name"]),
            str(provider["id"]),
        )
    )

    return eligible[0]
