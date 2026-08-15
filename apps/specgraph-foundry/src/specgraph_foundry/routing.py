import json
import sqlite3
import uuid
from datetime import UTC, datetime, timedelta

from .database import Database
from .routing_decisions import get_decision, record_decision
from .routing_guards import (
    is_cooling,
    normalize_policy,
    normalize_provider,
    normalize_renderer,
    require_project,
)
from .routing_paid_unlocks import consume_paid_unlock, get_paid_unlock, grant_paid_unlock
from .routing_policy import get_policy, set_policy
from .routing_providers import configure_provider, get_provider, list_providers, record_health
from .routing_renderers import configure_renderer, get_renderer, list_renderers, select_renderer
from .routing_route import route, select_provider
from .routing_schema import ROUTING_SCHEMA
from .routing_vocabulary import (  # re-exported: routing is the public
    CANONICAL_ROUTE_LAW,          # module for this area, and callers
    CLASS_COST_LAW,               # import these from it.
    COST_CLASSES,
    PROVIDER_CLASSES,
    PROVIDER_STATUSES,
    normalize_territories,
)
from .errors import ConflictError, NotFoundError, ValidationError









SENSITIVE_KEY_FRAGMENTS = (
    "api_key",
    "apikey",
    "access_key",
    "secret",
    "password",
    "passwd",
    "token",
    "credential",
    "private_key",
    "client_secret",
)


def utc_now_datetime() -> datetime:
    return datetime.now(UTC)


def utc_now() -> str:
    return utc_now_datetime().isoformat()


def parse_time(value: str) -> datetime:
    parsed = datetime.fromisoformat(value)

    if parsed.tzinfo is None:
        parsed = parsed.replace(tzinfo=UTC)

    return parsed.astimezone(UTC)


def new_id(prefix: str) -> str:
    return str(uuid.uuid4())


def canonical_json(value: object) -> str:
    return json.dumps(
        value,
        sort_keys=True,
        separators=(",", ":"),
        ensure_ascii=False,
    )


def contains_sensitive_key(value: object) -> bool:
    if isinstance(value, dict):
        for key, nested in value.items():
            normalized = str(key).casefold()

            if any(
                fragment in normalized
                for fragment
                in SENSITIVE_KEY_FRAGMENTS
            ):
                return True

            if contains_sensitive_key(nested):
                return True

    elif isinstance(value, list):
        return any(
            contains_sensitive_key(item)
            for item in value
        )

    return False




class RoutingService:
    def __init__(
        self,
        database: Database,
    ) -> None:
        self.database = database
        self.ensure_schema()

    def ensure_schema(self) -> None:
        with self.database.connect() as connection:
            connection.executescript(
                ROUTING_SCHEMA
            )

    def set_policy(
        self,
        project_id: str,
        allow_offline_degraded: bool = True,
        paid_emergency_enabled: bool = False,
        max_paid_decisions_per_unlock: int = 1,
    ) -> dict[str, object]:
        """Delegates to :func:`routing_policy.set_policy`."""
        return set_policy(
            self.database,
            project_id,
            allow_offline_degraded,
            paid_emergency_enabled,
            max_paid_decisions_per_unlock,
        )


    def get_policy(
        self,
        project_id: str,
    ) -> dict[str, object]:
        """Delegates to :func:`routing_policy.get_policy`."""
        return get_policy(
            self.database,
            project_id,
        )


    def configure_provider(
        self,
        project_id: str,
        name: str,
        provider_class: str,
        cost_class: str,
        territories: list[str],
        priority: int,
        metadata: dict[str, object] | None = None,
        enabled: bool = True,
    ) -> dict[str, object]:
        """Delegates to :func:`routing_providers.configure_provider`."""
        return configure_provider(
            self.database,
            project_id,
            name,
            provider_class,
            cost_class,
            territories,
            priority,
            metadata,
            enabled,
        )


    def get_provider(
        self,
        provider_id: str,
    ) -> dict[str, object]:
        """Delegates to :func:`routing_providers.get_provider`."""
        return get_provider(
            self.database,
            provider_id,
        )


    def list_providers(
        self,
        project_id: str,
    ) -> list[dict[str, object]]:
        """Delegates to :func:`routing_providers.list_providers`."""
        return list_providers(
            self.database,
            project_id,
        )


    def record_health(
        self,
        provider_id: str,
        status: str,
        latency_ms: float | None = None,
        error_message: str = "",
        cooldown_seconds: int | None = None,
    ) -> dict[str, object]:
        """Delegates to :func:`routing_providers.record_health`."""
        return record_health(
            self.database,
            provider_id,
            status,
            latency_ms,
            error_message,
            cooldown_seconds,
        )


    def configure_renderer(
        self,
        project_id: str,
        name: str,
        renderer_type: str,
        territories: list[str],
        priority: int,
        metadata: dict[str, object] | None = None,
        enabled: bool = True,
    ) -> dict[str, object]:
        """Delegates to :func:`routing_renderers.configure_renderer`."""
        return configure_renderer(
            self.database,
            project_id,
            name,
            renderer_type,
            territories,
            priority,
            metadata,
            enabled,
        )


    def get_renderer(
        self,
        renderer_id: str,
    ) -> dict[str, object]:
        """Delegates to :func:`routing_renderers.get_renderer`."""
        return get_renderer(
            self.database,
            renderer_id,
        )


    def list_renderers(
        self,
        project_id: str,
    ) -> list[dict[str, object]]:
        """Delegates to :func:`routing_renderers.list_renderers`."""
        return list_renderers(
            self.database,
            project_id,
        )


    def select_renderer(
        self,
        project_id: str,
        territory: str,
    ) -> dict[str, object] | None:
        """Delegates to :func:`routing_renderers.select_renderer`."""
        return select_renderer(
            self.database,
            project_id,
            territory,
        )


    def grant_paid_unlock(
        self,
        project_id: str,
        actor_id: str,
        reason: str,
        ttl_seconds: int = 900,
        max_decisions: int | None = None,
        provider_id: str | None = None,
    ) -> dict[str, object]:
        """Delegates to :func:`routing_paid_unlocks.grant_paid_unlock`."""
        return grant_paid_unlock(
            self.database,
            project_id,
            actor_id,
            reason,
            ttl_seconds,
            max_decisions,
            provider_id,
        )


    def get_paid_unlock(
        self,
        unlock_id: str,
    ) -> dict[str, object]:
        """Delegates to :func:`routing_paid_unlocks.get_paid_unlock`."""
        return get_paid_unlock(
            self.database,
            unlock_id,
        )


    def route(
        self,
        project_id: str,
        territory: str,
        offline_capable: bool = False,
    ) -> dict[str, object]:
        """Delegates to :func:`routing_route.route`."""
        return route(
            self.database,
            project_id,
            territory,
            offline_capable,
        )


    def get_decision(
        self,
        decision_id: str,
    ) -> dict[str, object]:
        """Delegates to :func:`routing_decisions.get_decision`."""
        return get_decision(
            self.database,
            decision_id,
        )


    def _record_decision(
        self,
        project_id: str,
        territory: str,
        decision_type: str,
        selected_provider: (
            dict[str, object] | None
        ),
        paid_unlock_id: str | None,
        retry_at: str | None,
        rationale: str,
        offline_capable: bool,
        considered: list[
            dict[str, object]
        ],
    ) -> dict[str, object]:
        """Delegates to :func:`routing_decisions.record_decision`."""
        return record_decision(
            self.database,
            project_id,
            territory,
            decision_type,
            selected_provider,
            paid_unlock_id,
            retry_at,
            rationale,
            offline_capable,
            considered,
        )


    def _consume_paid_unlock(
        self,
        project_id: str,
        provider_id: str,
    ) -> dict[str, object] | None:
        """Delegates to :func:`routing_paid_unlocks.consume_paid_unlock`."""
        return consume_paid_unlock(
            self.database,
            project_id,
            provider_id,
        )


    @staticmethod
    def _select_provider(
        providers: list[
            dict[str, object]
        ],
        provider_class: str,
        allowed_statuses: set[str],
        now: datetime,
    ) -> dict[str, object] | None:
        """Delegates to :func:`routing_route.select_provider`."""
        return select_provider(
            providers,
            provider_class,
            allowed_statuses,
            now,
        )


    @staticmethod
    def _is_cooling(
        provider: dict[str, object],
        now: datetime,
    ) -> bool:
        """Delegates to :func:`routing_guards.is_cooling`."""
        return is_cooling(
            provider,
            now,
        )


    @staticmethod
    def _require_project(
        connection: sqlite3.Connection,
        project_id: str,
    ) -> None:
        """Delegates to :func:`routing_guards.require_project`."""
        return require_project(
            connection,
            project_id,
        )


    @staticmethod
    def _normalize_policy(
        record: dict[str, object],
    ) -> dict[str, object]:
        """Delegates to :func:`routing_guards.normalize_policy`."""
        return normalize_policy(
            record,
        )


    @staticmethod
    def _normalize_provider(
        record: dict[str, object],
    ) -> dict[str, object]:
        """Delegates to :func:`routing_guards.normalize_provider`."""
        return normalize_provider(
            record,
        )


    @staticmethod
    def _normalize_renderer(
        record: dict[str, object],
    ) -> dict[str, object]:
        """Delegates to :func:`routing_guards.normalize_renderer`."""
        return normalize_renderer(
            record,
        )

