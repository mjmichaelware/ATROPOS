import json
import sqlite3
import uuid
from datetime import UTC, datetime, timedelta

from .database import Database
from .errors import (
    ConflictError,
    NotFoundError,
    ValidationError,
)


ROUTING_SCHEMA = """
PRAGMA foreign_keys = ON;

CREATE TABLE IF NOT EXISTS project_policies (
    id TEXT PRIMARY KEY,
    project_id TEXT NOT NULL UNIQUE
        REFERENCES projects(id)
        ON DELETE CASCADE,
    route_law_json TEXT NOT NULL,
    allow_offline_degraded INTEGER NOT NULL DEFAULT 1,
    paid_emergency_enabled INTEGER NOT NULL DEFAULT 0,
    max_paid_decisions_per_unlock INTEGER NOT NULL DEFAULT 1,
    created_at TEXT NOT NULL,
    updated_at TEXT NOT NULL,
    CHECK(max_paid_decisions_per_unlock > 0)
);

CREATE TABLE IF NOT EXISTS provider_configs (
    id TEXT PRIMARY KEY,
    project_id TEXT NOT NULL
        REFERENCES projects(id)
        ON DELETE CASCADE,
    name TEXT NOT NULL,
    provider_class TEXT NOT NULL,
    cost_class TEXT NOT NULL,
    territories_json TEXT NOT NULL,
    priority INTEGER NOT NULL,
    enabled INTEGER NOT NULL DEFAULT 1,
    status TEXT NOT NULL DEFAULT 'UNKNOWN',
    cooldown_until TEXT,
    metadata_json TEXT NOT NULL DEFAULT '{}',
    created_at TEXT NOT NULL,
    updated_at TEXT NOT NULL,
    UNIQUE(project_id, name)
);

CREATE TABLE IF NOT EXISTS renderer_configs (
    id TEXT PRIMARY KEY,
    project_id TEXT NOT NULL
        REFERENCES projects(id)
        ON DELETE CASCADE,
    name TEXT NOT NULL,
    renderer_type TEXT NOT NULL,
    territories_json TEXT NOT NULL,
    priority INTEGER NOT NULL,
    enabled INTEGER NOT NULL DEFAULT 1,
    status TEXT NOT NULL DEFAULT 'READY',
    metadata_json TEXT NOT NULL DEFAULT '{}',
    created_at TEXT NOT NULL,
    updated_at TEXT NOT NULL,
    UNIQUE(project_id, name)
);

CREATE TABLE IF NOT EXISTS provider_health_events (
    id TEXT PRIMARY KEY,
    provider_id TEXT NOT NULL
        REFERENCES provider_configs(id)
        ON DELETE CASCADE,
    status TEXT NOT NULL,
    latency_ms REAL,
    error_message TEXT,
    cooldown_until TEXT,
    created_at TEXT NOT NULL
);

CREATE TABLE IF NOT EXISTS paid_route_unlocks (
    id TEXT PRIMARY KEY,
    project_id TEXT NOT NULL
        REFERENCES projects(id)
        ON DELETE CASCADE,
    provider_id TEXT
        REFERENCES provider_configs(id)
        ON DELETE CASCADE,
    actor_id TEXT NOT NULL,
    reason TEXT NOT NULL,
    max_decisions INTEGER NOT NULL,
    used_count INTEGER NOT NULL DEFAULT 0,
    expires_at TEXT NOT NULL,
    created_at TEXT NOT NULL,
    CHECK(max_decisions > 0),
    CHECK(used_count >= 0),
    CHECK(used_count <= max_decisions)
);

CREATE TABLE IF NOT EXISTS route_decisions (
    id TEXT PRIMARY KEY,
    project_id TEXT NOT NULL
        REFERENCES projects(id)
        ON DELETE CASCADE,
    territory TEXT NOT NULL,
    decision_type TEXT NOT NULL,
    selected_provider_id TEXT
        REFERENCES provider_configs(id)
        ON DELETE SET NULL,
    paid_unlock_id TEXT
        REFERENCES paid_route_unlocks(id)
        ON DELETE SET NULL,
    retry_at TEXT,
    rationale TEXT NOT NULL,
    input_json TEXT NOT NULL,
    considered_json TEXT NOT NULL,
    created_at TEXT NOT NULL
);

CREATE INDEX IF NOT EXISTS idx_provider_configs_project
    ON provider_configs(
        project_id,
        provider_class,
        enabled,
        priority
    );

CREATE INDEX IF NOT EXISTS idx_renderer_configs_project
    ON renderer_configs(
        project_id,
        enabled,
        priority
    );

CREATE INDEX IF NOT EXISTS idx_provider_health_provider
    ON provider_health_events(
        provider_id,
        created_at
    );

CREATE INDEX IF NOT EXISTS idx_paid_unlocks_project
    ON paid_route_unlocks(
        project_id,
        expires_at
    );

CREATE INDEX IF NOT EXISTS idx_route_decisions_project
    ON route_decisions(
        project_id,
        created_at
    );
"""


CANONICAL_ROUTE_LAW = [
    "LOCAL_TOOLCHAIN",
    "FREE_READY_PROVIDER",
    "FREE_FALLBACK_PROVIDER",
    "COOLDOWN_QUEUE",
    "OFFLINE_DEGRADED_MODE",
    "PAID_EMERGENCY_ONLY_BY_EXPLICIT_UNLOCK",
]

PROVIDER_CLASSES = {
    "LOCAL_TOOLCHAIN",
    "FREE_READY_PROVIDER",
    "FREE_FALLBACK_PROVIDER",
    "PAID_EMERGENCY",
}

COST_CLASSES = {
    "LOCAL",
    "FREE",
    "PAID",
}

PROVIDER_STATUSES = {
    "UNKNOWN",
    "READY",
    "DEGRADED",
    "DOWN",
    "COOLDOWN",
}

CLASS_COST_LAW = {
    "LOCAL_TOOLCHAIN": "LOCAL",
    "FREE_READY_PROVIDER": "FREE",
    "FREE_FALLBACK_PROVIDER": "FREE",
    "PAID_EMERGENCY": "PAID",
}

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


def normalize_territories(
    territories: list[str],
) -> list[str]:
    if not isinstance(territories, list):
        raise ValidationError(
            "territories must be a list"
        )

    normalized = sorted(
        {
            str(item).strip().upper()
            for item in territories
            if str(item).strip()
        }
    )

    if not normalized:
        raise ValidationError(
            "at least one territory is required"
        )

    return normalized


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
        if max_paid_decisions_per_unlock < 1:
            raise ValidationError(
                "max paid decisions must be positive"
            )

        timestamp = utc_now()
        policy_id = new_id("policy")

        with self.database.connect() as connection:
            self._require_project(
                connection,
                project_id,
            )

            existing = connection.execute(
                """
                SELECT id
                FROM project_policies
                WHERE project_id = ?
                """,
                (project_id,),
            ).fetchone()

            if existing is None:
                connection.execute(
                    """
                    INSERT INTO project_policies(
                        id,
                        project_id,
                        route_law_json,
                        allow_offline_degraded,
                        paid_emergency_enabled,
                        max_paid_decisions_per_unlock,
                        created_at,
                        updated_at
                    )
                    VALUES(?,?,?,?,?,?,?,?)
                    """,
                    (
                        policy_id,
                        project_id,
                        canonical_json(
                            CANONICAL_ROUTE_LAW
                        ),
                        allow_offline_degraded,
                        paid_emergency_enabled,
                        max_paid_decisions_per_unlock,
                        timestamp,
                        timestamp,
                    ),
                )
            else:
                policy_id = str(
                    existing["id"]
                )

                connection.execute(
                    """
                    UPDATE project_policies
                    SET route_law_json = ?,
                        allow_offline_degraded = ?,
                        paid_emergency_enabled = ?,
                        max_paid_decisions_per_unlock = ?,
                        updated_at = ?
                    WHERE id = ?
                    """,
                    (
                        canonical_json(
                            CANONICAL_ROUTE_LAW
                        ),
                        allow_offline_degraded,
                        paid_emergency_enabled,
                        max_paid_decisions_per_unlock,
                        timestamp,
                        policy_id,
                    ),
                )

        return self.get_policy(project_id)

    def get_policy(
        self,
        project_id: str,
    ) -> dict[str, object]:
        with self.database.connect() as connection:
            self._require_project(
                connection,
                project_id,
            )

            row = connection.execute(
                """
                SELECT *
                FROM project_policies
                WHERE project_id = ?
                """,
                (project_id,),
            ).fetchone()

        if row is None:
            return self.set_policy(
                project_id=project_id,
                allow_offline_degraded=True,
                paid_emergency_enabled=False,
                max_paid_decisions_per_unlock=1,
            )

        return self._normalize_policy(
            dict(row)
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
        name = name.strip()
        provider_class = (
            provider_class.strip().upper()
        )
        cost_class = (
            cost_class.strip().upper()
        )
        metadata = metadata or {}

        if not name:
            raise ValidationError(
                "provider name is required"
            )

        if provider_class not in PROVIDER_CLASSES:
            raise ValidationError(
                f"invalid provider class: "
                f"{provider_class}"
            )

        if cost_class not in COST_CLASSES:
            raise ValidationError(
                f"invalid cost class: "
                f"{cost_class}"
            )

        expected_cost = CLASS_COST_LAW[
            provider_class
        ]

        if cost_class != expected_cost:
            raise ValidationError(
                f"{provider_class} requires "
                f"{expected_cost} cost class"
            )

        if priority < 0:
            raise ValidationError(
                "priority cannot be negative"
            )

        if not isinstance(metadata, dict):
            raise ValidationError(
                "metadata must be an object"
            )

        if contains_sensitive_key(metadata):
            raise ValidationError(
                "provider configuration must not "
                "contain secrets or credentials"
            )

        normalized_territories = (
            normalize_territories(
                territories
            )
        )

        timestamp = utc_now()
        provider_id = new_id("provider")

        with self.database.connect() as connection:
            self._require_project(
                connection,
                project_id,
            )

            existing = connection.execute(
                """
                SELECT id
                FROM provider_configs
                WHERE project_id = ?
                  AND name = ?
                """,
                (
                    project_id,
                    name,
                ),
            ).fetchone()

            if existing is None:
                connection.execute(
                    """
                    INSERT INTO provider_configs(
                        id,
                        project_id,
                        name,
                        provider_class,
                        cost_class,
                        territories_json,
                        priority,
                        enabled,
                        status,
                        metadata_json,
                        created_at,
                        updated_at
                    )
                    VALUES(?,?,?,?,?,?,?,?,?,?,?,?)
                    """,
                    (
                        provider_id,
                        project_id,
                        name,
                        provider_class,
                        cost_class,
                        canonical_json(
                            normalized_territories
                        ),
                        priority,
                        enabled,
                        "UNKNOWN",
                        canonical_json(metadata),
                        timestamp,
                        timestamp,
                    ),
                )
            else:
                provider_id = str(
                    existing["id"]
                )

                connection.execute(
                    """
                    UPDATE provider_configs
                    SET provider_class = ?,
                        cost_class = ?,
                        territories_json = ?,
                        priority = ?,
                        enabled = ?,
                        metadata_json = ?,
                        updated_at = ?
                    WHERE id = ?
                    """,
                    (
                        provider_class,
                        cost_class,
                        canonical_json(
                            normalized_territories
                        ),
                        priority,
                        enabled,
                        canonical_json(metadata),
                        timestamp,
                        provider_id,
                    ),
                )

        return self.get_provider(
            provider_id
        )

    def get_provider(
        self,
        provider_id: str,
    ) -> dict[str, object]:
        with self.database.connect() as connection:
            row = connection.execute(
                """
                SELECT *
                FROM provider_configs
                WHERE id = ?
                """,
                (provider_id,),
            ).fetchone()

        if row is None:
            raise NotFoundError(
                f"provider not found: {provider_id}"
            )

        return self._normalize_provider(
            dict(row)
        )

    def list_providers(
        self,
        project_id: str,
    ) -> list[dict[str, object]]:
        with self.database.connect() as connection:
            self._require_project(
                connection,
                project_id,
            )

            rows = connection.execute(
                """
                SELECT *
                FROM provider_configs
                WHERE project_id = ?
                ORDER BY
                    priority,
                    name,
                    id
                """,
                (project_id,),
            ).fetchall()

        return [
            self._normalize_provider(
                dict(row)
            )
            for row in rows
        ]

    def record_health(
        self,
        provider_id: str,
        status: str,
        latency_ms: float | None = None,
        error_message: str = "",
        cooldown_seconds: int | None = None,
    ) -> dict[str, object]:
        status = status.strip().upper()

        if status not in PROVIDER_STATUSES:
            raise ValidationError(
                f"invalid provider status: {status}"
            )

        if (
            latency_ms is not None
            and latency_ms < 0
        ):
            raise ValidationError(
                "latency cannot be negative"
            )

        if (
            cooldown_seconds is not None
            and cooldown_seconds < 1
        ):
            raise ValidationError(
                "cooldown must be positive"
            )

        if (
            status == "COOLDOWN"
            and cooldown_seconds is None
        ):
            raise ValidationError(
                "COOLDOWN status requires "
                "cooldown_seconds"
            )

        cooldown_until = None

        if cooldown_seconds is not None:
            cooldown_until = (
                utc_now_datetime()
                + timedelta(
                    seconds=cooldown_seconds
                )
            ).isoformat()

        timestamp = utc_now()

        with self.database.connect() as connection:
            provider = connection.execute(
                """
                SELECT *
                FROM provider_configs
                WHERE id = ?
                """,
                (provider_id,),
            ).fetchone()

            if provider is None:
                raise NotFoundError(
                    f"provider not found: "
                    f"{provider_id}"
                )

            connection.execute(
                """
                UPDATE provider_configs
                SET status = ?,
                    cooldown_until = ?,
                    updated_at = ?
                WHERE id = ?
                """,
                (
                    status,
                    cooldown_until,
                    timestamp,
                    provider_id,
                ),
            )

            connection.execute(
                """
                INSERT INTO provider_health_events(
                    id,
                    provider_id,
                    status,
                    latency_ms,
                    error_message,
                    cooldown_until,
                    created_at
                )
                VALUES(?,?,?,?,?,?,?)
                """,
                (
                    new_id("provider-health"),
                    provider_id,
                    status,
                    latency_ms,
                    (
                        error_message.strip()
                        or None
                    ),
                    cooldown_until,
                    timestamp,
                ),
            )

        return self.get_provider(
            provider_id
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
        name = name.strip()
        renderer_type = (
            renderer_type.strip().upper()
        )
        metadata = metadata or {}

        if not name:
            raise ValidationError(
                "renderer name is required"
            )

        if not renderer_type:
            raise ValidationError(
                "renderer type is required"
            )

        if priority < 0:
            raise ValidationError(
                "priority cannot be negative"
            )

        if not isinstance(metadata, dict):
            raise ValidationError(
                "metadata must be an object"
            )

        if contains_sensitive_key(metadata):
            raise ValidationError(
                "renderer configuration must not "
                "contain secrets or credentials"
            )

        normalized_territories = (
            normalize_territories(
                territories
            )
        )

        timestamp = utc_now()
        renderer_id = new_id("renderer")

        with self.database.connect() as connection:
            self._require_project(
                connection,
                project_id,
            )

            existing = connection.execute(
                """
                SELECT id
                FROM renderer_configs
                WHERE project_id = ?
                  AND name = ?
                """,
                (
                    project_id,
                    name,
                ),
            ).fetchone()

            if existing is None:
                connection.execute(
                    """
                    INSERT INTO renderer_configs(
                        id,
                        project_id,
                        name,
                        renderer_type,
                        territories_json,
                        priority,
                        enabled,
                        status,
                        metadata_json,
                        created_at,
                        updated_at
                    )
                    VALUES(?,?,?,?,?,?,?,?,?,?,?)
                    """,
                    (
                        renderer_id,
                        project_id,
                        name,
                        renderer_type,
                        canonical_json(
                            normalized_territories
                        ),
                        priority,
                        enabled,
                        "READY",
                        canonical_json(metadata),
                        timestamp,
                        timestamp,
                    ),
                )
            else:
                renderer_id = str(
                    existing["id"]
                )

                connection.execute(
                    """
                    UPDATE renderer_configs
                    SET renderer_type = ?,
                        territories_json = ?,
                        priority = ?,
                        enabled = ?,
                        metadata_json = ?,
                        updated_at = ?
                    WHERE id = ?
                    """,
                    (
                        renderer_type,
                        canonical_json(
                            normalized_territories
                        ),
                        priority,
                        enabled,
                        canonical_json(metadata),
                        timestamp,
                        renderer_id,
                    ),
                )

        return self.get_renderer(
            renderer_id
        )

    def get_renderer(
        self,
        renderer_id: str,
    ) -> dict[str, object]:
        with self.database.connect() as connection:
            row = connection.execute(
                """
                SELECT *
                FROM renderer_configs
                WHERE id = ?
                """,
                (renderer_id,),
            ).fetchone()

        if row is None:
            raise NotFoundError(
                f"renderer not found: {renderer_id}"
            )

        return self._normalize_renderer(
            dict(row)
        )

    def list_renderers(
        self,
        project_id: str,
    ) -> list[dict[str, object]]:
        with self.database.connect() as connection:
            self._require_project(
                connection,
                project_id,
            )

            rows = connection.execute(
                """
                SELECT *
                FROM renderer_configs
                WHERE project_id = ?
                ORDER BY
                    priority,
                    name,
                    id
                """,
                (project_id,),
            ).fetchall()

        return [
            self._normalize_renderer(
                dict(row)
            )
            for row in rows
        ]

    def select_renderer(
        self,
        project_id: str,
        territory: str,
    ) -> dict[str, object] | None:
        territory = territory.strip().upper()

        if not territory:
            raise ValidationError(
                "territory is required"
            )

        renderers = self.list_renderers(
            project_id
        )

        eligible = [
            renderer
            for renderer in renderers
            if renderer["enabled"]
            and renderer["status"] == "READY"
            and (
                territory
                in renderer["territories"]
                or "*"
                in renderer["territories"]
            )
        ]

        if not eligible:
            return None

        eligible.sort(
            key=lambda item: (
                int(item["priority"]),
                str(item["name"]),
                str(item["id"]),
            )
        )

        return eligible[0]

    def grant_paid_unlock(
        self,
        project_id: str,
        actor_id: str,
        reason: str,
        ttl_seconds: int = 900,
        max_decisions: int | None = None,
        provider_id: str | None = None,
    ) -> dict[str, object]:
        actor_id = actor_id.strip()
        reason = reason.strip()

        if not actor_id:
            raise ValidationError(
                "actor_id is required"
            )

        if len(reason) < 12:
            raise ValidationError(
                "paid unlock reason must be specific"
            )

        if ttl_seconds < 30:
            raise ValidationError(
                "paid unlock TTL must be at "
                "least 30 seconds"
            )

        policy = self.get_policy(
            project_id
        )

        if not policy[
            "paid_emergency_enabled"
        ]:
            raise ConflictError(
                "paid emergency routing is disabled "
                "by project policy"
            )

        allowed_decisions = (
            max_decisions
            if max_decisions is not None
            else int(
                policy[
                    "max_paid_decisions_per_unlock"
                ]
            )
        )

        if allowed_decisions < 1:
            raise ValidationError(
                "max decisions must be positive"
            )

        expires_at = (
            utc_now_datetime()
            + timedelta(
                seconds=ttl_seconds
            )
        ).isoformat()

        unlock_id = new_id("paid-unlock")
        timestamp = utc_now()

        with self.database.connect() as connection:
            self._require_project(
                connection,
                project_id,
            )

            if provider_id is not None:
                provider = connection.execute(
                    """
                    SELECT *
                    FROM provider_configs
                    WHERE id = ?
                      AND project_id = ?
                    """,
                    (
                        provider_id,
                        project_id,
                    ),
                ).fetchone()

                if provider is None:
                    raise ValidationError(
                        "paid provider does not belong "
                        "to the project"
                    )

                if (
                    provider["provider_class"]
                    != "PAID_EMERGENCY"
                ):
                    raise ValidationError(
                        "unlock provider must be a "
                        "PAID_EMERGENCY provider"
                    )

            connection.execute(
                """
                INSERT INTO paid_route_unlocks(
                    id,
                    project_id,
                    provider_id,
                    actor_id,
                    reason,
                    max_decisions,
                    used_count,
                    expires_at,
                    created_at
                )
                VALUES(?,?,?,?,?,?,?,?,?)
                """,
                (
                    unlock_id,
                    project_id,
                    provider_id,
                    actor_id,
                    reason,
                    allowed_decisions,
                    0,
                    expires_at,
                    timestamp,
                ),
            )

        return self.get_paid_unlock(
            unlock_id
        )

    def get_paid_unlock(
        self,
        unlock_id: str,
    ) -> dict[str, object]:
        with self.database.connect() as connection:
            row = connection.execute(
                """
                SELECT *
                FROM paid_route_unlocks
                WHERE id = ?
                """,
                (unlock_id,),
            ).fetchone()

        if row is None:
            raise NotFoundError(
                f"paid unlock not found: {unlock_id}"
            )

        result = dict(row)
        result["active"] = (
            int(result["used_count"])
            < int(result["max_decisions"])
            and parse_time(
                str(result["expires_at"])
            )
            > utc_now_datetime()
        )

        return result

    def route(
        self,
        project_id: str,
        territory: str,
        offline_capable: bool = False,
    ) -> dict[str, object]:
        territory = territory.strip().upper()

        if not territory:
            raise ValidationError(
                "territory is required"
            )

        policy = self.get_policy(
            project_id
        )

        providers = self.list_providers(
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

        local = self._select_provider(
            capable,
            provider_class=(
                "LOCAL_TOOLCHAIN"
            ),
            allowed_statuses={"READY"},
            now=now,
        )

        if local is not None:
            return self._record_decision(
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

        free_ready = self._select_provider(
            capable,
            provider_class=(
                "FREE_READY_PROVIDER"
            ),
            allowed_statuses={"READY"},
            now=now,
        )

        if free_ready is not None:
            return self._record_decision(
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

        free_fallback = self._select_provider(
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
            return self._record_decision(
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
                and self._is_cooling(
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

            return self._record_decision(
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
            return self._record_decision(
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

        paid_provider = self._select_provider(
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
            unlock = self._consume_paid_unlock(
                project_id=project_id,
                provider_id=str(
                    paid_provider["id"]
                ),
            )

            if unlock is not None:
                return self._record_decision(
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

            return self._record_decision(
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

        return self._record_decision(
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

    def get_decision(
        self,
        decision_id: str,
    ) -> dict[str, object]:
        with self.database.connect() as connection:
            row = connection.execute(
                """
                SELECT *
                FROM route_decisions
                WHERE id = ?
                """,
                (decision_id,),
            ).fetchone()

        if row is None:
            raise NotFoundError(
                f"route decision not found: "
                f"{decision_id}"
            )

        result = dict(row)
        result["input"] = json.loads(
            str(
                result.pop(
                    "input_json"
                )
            )
        )
        result["considered"] = json.loads(
            str(
                result.pop(
                    "considered_json"
                )
            )
        )

        if (
            result[
                "selected_provider_id"
            ]
            is not None
        ):
            result["selected_provider"] = (
                self.get_provider(
                    str(
                        result[
                            "selected_provider_id"
                        ]
                    )
                )
            )
        else:
            result[
                "selected_provider"
            ] = None

        return result

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
        decision_id = new_id(
            "route-decision"
        )

        with self.database.connect() as connection:
            connection.execute(
                """
                INSERT INTO route_decisions(
                    id,
                    project_id,
                    territory,
                    decision_type,
                    selected_provider_id,
                    paid_unlock_id,
                    retry_at,
                    rationale,
                    input_json,
                    considered_json,
                    created_at
                )
                VALUES(?,?,?,?,?,?,?,?,?,?,?)
                """,
                (
                    decision_id,
                    project_id,
                    territory,
                    decision_type,
                    (
                        selected_provider["id"]
                        if selected_provider
                        else None
                    ),
                    paid_unlock_id,
                    retry_at,
                    rationale,
                    canonical_json(
                        {
                            "territory": (
                                territory
                            ),
                            "offline_capable": (
                                offline_capable
                            ),
                        }
                    ),
                    canonical_json(
                        considered
                    ),
                    utc_now(),
                ),
            )

        return self.get_decision(
            decision_id
        )

    def _consume_paid_unlock(
        self,
        project_id: str,
        provider_id: str,
    ) -> dict[str, object] | None:
        now = utc_now()

        with self.database.connect() as connection:
            connection.execute(
                "BEGIN IMMEDIATE"
            )

            row = connection.execute(
                """
                SELECT *
                FROM paid_route_unlocks
                WHERE project_id = ?
                  AND expires_at > ?
                  AND used_count
                      < max_decisions
                  AND (
                      provider_id IS NULL
                      OR provider_id = ?
                  )
                ORDER BY
                    CASE
                        WHEN provider_id = ?
                        THEN 0
                        ELSE 1
                    END,
                    created_at,
                    id
                LIMIT 1
                """,
                (
                    project_id,
                    now,
                    provider_id,
                    provider_id,
                ),
            ).fetchone()

            if row is None:
                return None

            connection.execute(
                """
                UPDATE paid_route_unlocks
                SET used_count = used_count + 1
                WHERE id = ?
                """,
                (row["id"],),
            )

            unlock_id = str(row["id"])

        return self.get_paid_unlock(
            unlock_id
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
                and not RoutingService._is_cooling(
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

    @staticmethod
    def _is_cooling(
        provider: dict[str, object],
        now: datetime,
    ) -> bool:
        if provider["status"] == "COOLDOWN":
            return True

        cooldown_until = provider[
            "cooldown_until"
        ]

        return (
            cooldown_until is not None
            and parse_time(
                str(cooldown_until)
            )
            > now
        )

    @staticmethod
    def _require_project(
        connection: sqlite3.Connection,
        project_id: str,
    ) -> None:
        row = connection.execute(
            """
            SELECT id
            FROM projects
            WHERE id = ?
            """,
            (project_id,),
        ).fetchone()

        if row is None:
            raise NotFoundError(
                f"project not found: {project_id}"
            )

    @staticmethod
    def _normalize_policy(
        record: dict[str, object],
    ) -> dict[str, object]:
        record["route_law"] = json.loads(
            str(
                record.pop(
                    "route_law_json"
                )
            )
        )
        record[
            "allow_offline_degraded"
        ] = bool(
            record[
                "allow_offline_degraded"
            ]
        )
        record[
            "paid_emergency_enabled"
        ] = bool(
            record[
                "paid_emergency_enabled"
            ]
        )

        return record

    @staticmethod
    def _normalize_provider(
        record: dict[str, object],
    ) -> dict[str, object]:
        record["territories"] = json.loads(
            str(
                record.pop(
                    "territories_json"
                )
            )
        )
        record["metadata"] = json.loads(
            str(
                record.pop(
                    "metadata_json"
                )
            )
        )
        record["enabled"] = bool(
            record["enabled"]
        )

        return record

    @staticmethod
    def _normalize_renderer(
        record: dict[str, object],
    ) -> dict[str, object]:
        record["territories"] = json.loads(
            str(
                record.pop(
                    "territories_json"
                )
            )
        )
        record["metadata"] = json.loads(
            str(
                record.pop(
                    "metadata_json"
                )
            )
        )
        record["enabled"] = bool(
            record["enabled"]
        )

        return record
