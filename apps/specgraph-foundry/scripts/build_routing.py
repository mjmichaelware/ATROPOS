from pathlib import Path
from textwrap import dedent

ROOT = Path.cwd()

if ROOT.name != "specgraph-foundry" or not (ROOT / ".git").is_dir():
    raise SystemExit(f"Wrong repository: {ROOT}")


def write(path: str, content: str) -> None:
    target = ROOT / path
    target.parent.mkdir(parents=True, exist_ok=True)
    target.write_text(
        dedent(content).lstrip(),
        encoding="utf-8",
    )
    print(f"WROTE {path}")


def insert_after(
    path: str,
    marker: str,
    addition: str,
    installed_marker: str,
) -> None:
    target = ROOT / path
    content = target.read_text(encoding="utf-8")

    if installed_marker in content:
        print(f"SKIPPED {path}: already installed")
        return

    if marker not in content:
        raise SystemExit(
            f"PATCH MARKER NOT FOUND IN {path}:\n{marker}"
        )

    target.write_text(
        content.replace(
            marker,
            marker + addition,
            1,
        ),
        encoding="utf-8",
    )
    print(f"UPDATED {path}")


def insert_before(
    path: str,
    marker: str,
    addition: str,
    installed_marker: str,
) -> None:
    target = ROOT / path
    content = target.read_text(encoding="utf-8")

    if installed_marker in content:
        print(f"SKIPPED {path}: already installed")
        return

    if marker not in content:
        raise SystemExit(
            f"PATCH MARKER NOT FOUND IN {path}:\n{marker}"
        )

    target.write_text(
        content.replace(
            marker,
            addition + marker,
            1,
        ),
        encoding="utf-8",
    )
    print(f"UPDATED {path}")


write(
    "src/specgraph_foundry/routing.py",
    r'''
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
        return f"{prefix}-{uuid.uuid4()}"


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
                            int(
                                allow_offline_degraded
                            ),
                            int(
                                paid_emergency_enabled
                            ),
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
                            int(
                                allow_offline_degraded
                            ),
                            int(
                                paid_emergency_enabled
                            ),
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
                            int(enabled),
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
                            int(enabled),
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
                            int(enabled),
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
                            int(enabled),
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
    ''',
)

write(
    "tests/test_routing.py",
    r'''
    import tempfile
    import unittest
    from pathlib import Path

    from specgraph_foundry.database import (
        Database,
    )
    from specgraph_foundry.errors import (
        ConflictError,
        ValidationError,
    )
    from specgraph_foundry.routing import (
        CANONICAL_ROUTE_LAW,
        RoutingService,
    )
    from specgraph_foundry.services import (
        ProjectService,
    )


    class RoutingTest(unittest.TestCase):
        def setUp(self) -> None:
            self.temp = (
                tempfile.TemporaryDirectory()
            )

            self.database = Database(
                Path(self.temp.name)
                / "test.sqlite3"
            )
            self.database.initialize()

            self.projects = ProjectService(
                self.database
            )
            self.routing = RoutingService(
                self.database
            )

            self.project = (
                self.projects.create(
                    "routing-test",
                    "Routing Test",
                )
            )

            self.project_id = str(
                self.project["id"]
            )

        def tearDown(self) -> None:
            self.temp.cleanup()

        def _provider(
            self,
            name: str,
            provider_class: str,
            cost_class: str,
            priority: int,
            status: str = "READY",
        ) -> dict[str, object]:
            provider = (
                self.routing.configure_provider(
                    project_id=(
                        self.project_id
                    ),
                    name=name,
                    provider_class=(
                        provider_class
                    ),
                    cost_class=cost_class,
                    territories=[
                        "CODE_PATCH"
                    ],
                    priority=priority,
                    metadata={
                        "endpoint_alias": name
                    },
                )
            )

            return self.routing.record_health(
                str(provider["id"]),
                status,
                latency_ms=10.0,
            )

        def test_policy_uses_canonical_law(
            self,
        ) -> None:
            policy = self.routing.get_policy(
                self.project_id
            )

            self.assertEqual(
                policy["route_law"],
                CANONICAL_ROUTE_LAW,
            )

        def test_local_precedes_free(
            self,
        ) -> None:
            local = self._provider(
                "local",
                "LOCAL_TOOLCHAIN",
                "LOCAL",
                50,
            )

            self._provider(
                "free",
                "FREE_READY_PROVIDER",
                "FREE",
                0,
            )

            decision = self.routing.route(
                self.project_id,
                "CODE_PATCH",
                offline_capable=True,
            )

            self.assertEqual(
                decision["decision_type"],
                "LOCAL_TOOLCHAIN",
            )

            self.assertEqual(
                decision[
                    "selected_provider_id"
                ],
                local["id"],
            )

        def test_free_used_when_local_down(
            self,
        ) -> None:
            self._provider(
                "local",
                "LOCAL_TOOLCHAIN",
                "LOCAL",
                0,
                status="DOWN",
            )

            free = self._provider(
                "free",
                "FREE_READY_PROVIDER",
                "FREE",
                10,
            )

            decision = self.routing.route(
                self.project_id,
                "CODE_PATCH",
                offline_capable=True,
            )

            self.assertEqual(
                decision["decision_type"],
                "FREE_READY_PROVIDER",
            )

            self.assertEqual(
                decision[
                    "selected_provider_id"
                ],
                free["id"],
            )

        def test_cooldown_queues_before_offline(
            self,
        ) -> None:
            provider = (
                self.routing.configure_provider(
                    project_id=(
                        self.project_id
                    ),
                    name="cooling",
                    provider_class=(
                        "FREE_READY_PROVIDER"
                    ),
                    cost_class="FREE",
                    territories=[
                        "CODE_PATCH"
                    ],
                    priority=0,
                )
            )

            self.routing.record_health(
                str(provider["id"]),
                "COOLDOWN",
                cooldown_seconds=300,
            )

            decision = self.routing.route(
                self.project_id,
                "CODE_PATCH",
                offline_capable=True,
            )

            self.assertEqual(
                decision["decision_type"],
                "COOLDOWN_QUEUE",
            )

            self.assertIsNotNone(
                decision["retry_at"]
            )

        def test_offline_degraded_mode(
            self,
        ) -> None:
            decision = self.routing.route(
                self.project_id,
                "CODE_PATCH",
                offline_capable=True,
            )

            self.assertEqual(
                decision["decision_type"],
                "OFFLINE_DEGRADED_MODE",
            )

        def test_paid_requires_explicit_unlock(
            self,
        ) -> None:
            self.routing.set_policy(
                project_id=self.project_id,
                allow_offline_degraded=False,
                paid_emergency_enabled=True,
                max_paid_decisions_per_unlock=1,
            )

            paid = self._provider(
                "paid",
                "PAID_EMERGENCY",
                "PAID",
                0,
            )

            blocked = self.routing.route(
                self.project_id,
                "CODE_PATCH",
                offline_capable=False,
            )

            self.assertEqual(
                blocked["decision_type"],
                (
                    "BLOCKED_PAID_UNLOCK_"
                    "REQUIRED"
                ),
            )

            unlock = (
                self.routing.grant_paid_unlock(
                    project_id=(
                        self.project_id
                    ),
                    actor_id="michael",
                    reason=(
                        "Explicit emergency build "
                        "recovery authorization."
                    ),
                    ttl_seconds=300,
                    max_decisions=1,
                    provider_id=str(
                        paid["id"]
                    ),
                )
            )

            routed = self.routing.route(
                self.project_id,
                "CODE_PATCH",
                offline_capable=False,
            )

            self.assertEqual(
                routed["decision_type"],
                (
                    "PAID_EMERGENCY_ONLY_"
                    "BY_EXPLICIT_UNLOCK"
                ),
            )

            self.assertEqual(
                routed["paid_unlock_id"],
                unlock["id"],
            )

            exhausted = self.routing.route(
                self.project_id,
                "CODE_PATCH",
                offline_capable=False,
            )

            self.assertEqual(
                exhausted["decision_type"],
                (
                    "BLOCKED_PAID_UNLOCK_"
                    "REQUIRED"
                ),
            )

        def test_paid_unlock_blocked_by_policy(
            self,
        ) -> None:
            with self.assertRaises(
                ConflictError
            ):
                self.routing.grant_paid_unlock(
                    project_id=(
                        self.project_id
                    ),
                    actor_id="michael",
                    reason=(
                        "Emergency provider use "
                        "requested explicitly."
                    ),
                )

        def test_secret_metadata_rejected(
            self,
        ) -> None:
            with self.assertRaises(
                ValidationError
            ):
                self.routing.configure_provider(
                    project_id=(
                        self.project_id
                    ),
                    name="unsafe",
                    provider_class=(
                        "FREE_READY_PROVIDER"
                    ),
                    cost_class="FREE",
                    territories=[
                        "CODE_PATCH"
                    ],
                    priority=0,
                    metadata={
                        "api_key": "forbidden"
                    },
                )

        def test_renderer_is_independent(
            self,
        ) -> None:
            renderer = (
                self.routing.configure_renderer(
                    project_id=(
                        self.project_id
                    ),
                    name="json-renderer",
                    renderer_type="JSON",
                    territories=[
                        "BLUEPRINT"
                    ],
                    priority=0,
                    metadata={
                        "format": "canonical"
                    },
                )
            )

            selected = (
                self.routing.select_renderer(
                    self.project_id,
                    "BLUEPRINT",
                )
            )

            self.assertIsNotNone(selected)
            self.assertEqual(
                selected["id"],
                renderer["id"],
            )


    if __name__ == "__main__":
        unittest.main()
    ''',
)

write(
    "supabase/migrations/20260712000800_routing.sql",
    r'''
    create table if not exists public.project_policies (
        id uuid primary key default gen_random_uuid(),
        project_id uuid not null unique
            references public.projects(id)
            on delete cascade,
        route_law_json jsonb not null,
        allow_offline_degraded boolean not null
            default true,
        paid_emergency_enabled boolean not null
            default false,
        max_paid_decisions_per_unlock bigint not null
            default 1,
        created_at timestamptz not null default now(),
        updated_at timestamptz not null default now(),
        check(max_paid_decisions_per_unlock > 0)
    );

    create table if not exists public.provider_configs (
        id uuid primary key default gen_random_uuid(),
        project_id uuid not null
            references public.projects(id)
            on delete cascade,
        name text not null,
        provider_class text not null,
        cost_class text not null,
        territories_json jsonb not null,
        priority bigint not null,
        enabled boolean not null default true,
        status text not null default 'UNKNOWN',
        cooldown_until timestamptz,
        metadata_json jsonb not null
            default '{}'::jsonb,
        created_at timestamptz not null default now(),
        updated_at timestamptz not null default now(),
        unique(project_id, name)
    );

    create table if not exists public.renderer_configs (
        id uuid primary key default gen_random_uuid(),
        project_id uuid not null
            references public.projects(id)
            on delete cascade,
        name text not null,
        renderer_type text not null,
        territories_json jsonb not null,
        priority bigint not null,
        enabled boolean not null default true,
        status text not null default 'READY',
        metadata_json jsonb not null
            default '{}'::jsonb,
        created_at timestamptz not null default now(),
        updated_at timestamptz not null default now(),
        unique(project_id, name)
    );

    create table if not exists public.provider_health_events (
        id uuid primary key default gen_random_uuid(),
        provider_id uuid not null
            references public.provider_configs(id)
            on delete cascade,
        status text not null,
        latency_ms double precision,
        error_message text,
        cooldown_until timestamptz,
        created_at timestamptz not null default now()
    );

    create table if not exists public.paid_route_unlocks (
        id uuid primary key default gen_random_uuid(),
        project_id uuid not null
            references public.projects(id)
            on delete cascade,
        provider_id uuid
            references public.provider_configs(id)
            on delete cascade,
        actor_id text not null,
        reason text not null,
        max_decisions bigint not null,
        used_count bigint not null default 0,
        expires_at timestamptz not null,
        created_at timestamptz not null default now(),
        check(max_decisions > 0),
        check(used_count >= 0),
        check(used_count <= max_decisions)
    );

    create table if not exists public.route_decisions (
        id uuid primary key default gen_random_uuid(),
        project_id uuid not null
            references public.projects(id)
            on delete cascade,
        territory text not null,
        decision_type text not null,
        selected_provider_id uuid
            references public.provider_configs(id)
            on delete set null,
        paid_unlock_id uuid
            references public.paid_route_unlocks(id)
            on delete set null,
        retry_at timestamptz,
        rationale text not null,
        input_json jsonb not null,
        considered_json jsonb not null,
        created_at timestamptz not null default now()
    );

    create index if not exists idx_provider_configs_project
        on public.provider_configs(
            project_id,
            provider_class,
            enabled,
            priority
        );

    create index if not exists idx_renderer_configs_project
        on public.renderer_configs(
            project_id,
            enabled,
            priority
        );

    create index if not exists idx_paid_unlocks_project
        on public.paid_route_unlocks(
            project_id,
            expires_at
        );

    create index if not exists idx_route_decisions_project
        on public.route_decisions(
            project_id,
            created_at
        );

    alter table public.project_policies
        enable row level security;

    alter table public.provider_configs
        enable row level security;

    alter table public.renderer_configs
        enable row level security;

    alter table public.provider_health_events
        enable row level security;

    alter table public.paid_route_unlocks
        enable row level security;

    alter table public.route_decisions
        enable row level security;
    ''',
)

insert_after(
    "src/specgraph_foundry/api.py",
    "from .execution import ExecutionService\n",
    "from .routing import RoutingService\n",
    "from .routing import RoutingService",
)

insert_after(
    "src/specgraph_foundry/api.py",
    "        self.execution = ExecutionService(database)\n",
    "        self.routing = RoutingService(database)\n",
    "self.routing = RoutingService",
)

api_routes = r'''
            if (
                len(parts) == 4
                and parts[:2] == ["v1", "projects"]
                and parts[3] == "routing-policy"
            ):
                if method == "GET":
                    return 200, self.routing.get_policy(
                        parts[2]
                    )

                if method == "POST":
                    return 200, self.routing.set_policy(
                        project_id=parts[2],
                        allow_offline_degraded=bool(
                            payload.get(
                                "allow_offline_degraded",
                                True,
                            )
                        ),
                        paid_emergency_enabled=bool(
                            payload.get(
                                "paid_emergency_enabled",
                                False,
                            )
                        ),
                        max_paid_decisions_per_unlock=int(
                            payload.get(
                                "max_paid_decisions_per_unlock",
                                1,
                            )
                        ),
                    )

            if (
                len(parts) == 4
                and parts[:2] == ["v1", "projects"]
                and parts[3] == "providers"
            ):
                if method == "GET":
                    return 200, {
                        "items": self.routing.list_providers(
                            parts[2]
                        )
                    }

                if method == "POST":
                    territories = payload.get(
                        "territories",
                        [],
                    )
                    metadata = payload.get(
                        "metadata",
                        {},
                    )

                    if not isinstance(territories, list):
                        raise ValidationError(
                            "territories must be a list"
                        )

                    if not isinstance(metadata, dict):
                        raise ValidationError(
                            "metadata must be an object"
                        )

                    return 201, self.routing.configure_provider(
                        project_id=parts[2],
                        name=str(
                            payload.get("name", "")
                        ),
                        provider_class=str(
                            payload.get(
                                "provider_class",
                                "",
                            )
                        ),
                        cost_class=str(
                            payload.get(
                                "cost_class",
                                "",
                            )
                        ),
                        territories=[
                            str(item)
                            for item in territories
                        ],
                        priority=int(
                            payload.get(
                                "priority",
                                100,
                            )
                        ),
                        metadata=metadata,
                        enabled=bool(
                            payload.get(
                                "enabled",
                                True,
                            )
                        ),
                    )

            if (
                len(parts) == 4
                and parts[:2] == ["v1", "providers"]
                and parts[3] == "health"
                and method == "POST"
            ):
                cooldown_value = payload.get(
                    "cooldown_seconds"
                )
                latency_value = payload.get(
                    "latency_ms"
                )

                return 200, self.routing.record_health(
                    provider_id=parts[2],
                    status=str(
                        payload.get("status", "")
                    ),
                    latency_ms=(
                        float(latency_value)
                        if latency_value is not None
                        else None
                    ),
                    error_message=str(
                        payload.get(
                            "error_message",
                            "",
                        )
                    ),
                    cooldown_seconds=(
                        int(cooldown_value)
                        if cooldown_value is not None
                        else None
                    ),
                )

            if (
                len(parts) == 4
                and parts[:2] == ["v1", "projects"]
                and parts[3] == "renderers"
            ):
                if method == "GET":
                    return 200, {
                        "items": self.routing.list_renderers(
                            parts[2]
                        )
                    }

                if method == "POST":
                    territories = payload.get(
                        "territories",
                        [],
                    )
                    metadata = payload.get(
                        "metadata",
                        {},
                    )

                    if not isinstance(territories, list):
                        raise ValidationError(
                            "territories must be a list"
                        )

                    if not isinstance(metadata, dict):
                        raise ValidationError(
                            "metadata must be an object"
                        )

                    return 201, self.routing.configure_renderer(
                        project_id=parts[2],
                        name=str(
                            payload.get("name", "")
                        ),
                        renderer_type=str(
                            payload.get(
                                "renderer_type",
                                "",
                            )
                        ),
                        territories=[
                            str(item)
                            for item in territories
                        ],
                        priority=int(
                            payload.get(
                                "priority",
                                100,
                            )
                        ),
                        metadata=metadata,
                        enabled=bool(
                            payload.get(
                                "enabled",
                                True,
                            )
                        ),
                    )

            if (
                len(parts) == 5
                and parts[:2] == ["v1", "projects"]
                and parts[3] == "renderers"
                and parts[4] == "select"
                and method == "POST"
            ):
                return 200, {
                    "renderer": self.routing.select_renderer(
                        parts[2],
                        str(
                            payload.get(
                                "territory",
                                "",
                            )
                        ),
                    )
                }

            if (
                len(parts) == 4
                and parts[:2] == ["v1", "projects"]
                and parts[3] == "paid-unlocks"
                and method == "POST"
            ):
                provider_value = payload.get(
                    "provider_id"
                )
                max_value = payload.get(
                    "max_decisions"
                )

                return 201, self.routing.grant_paid_unlock(
                    project_id=parts[2],
                    actor_id=str(
                        payload.get("actor_id", "")
                    ),
                    reason=str(
                        payload.get("reason", "")
                    ),
                    ttl_seconds=int(
                        payload.get(
                            "ttl_seconds",
                            900,
                        )
                    ),
                    max_decisions=(
                        int(max_value)
                        if max_value is not None
                        else None
                    ),
                    provider_id=(
                        str(provider_value)
                        if provider_value
                        else None
                    ),
                )

            if (
                len(parts) == 4
                and parts[:2] == ["v1", "projects"]
                and parts[3] == "route-decisions"
                and method == "POST"
            ):
                return 201, self.routing.route(
                    project_id=parts[2],
                    territory=str(
                        payload.get(
                            "territory",
                            "",
                        )
                    ),
                    offline_capable=bool(
                        payload.get(
                            "offline_capable",
                            False,
                        )
                    ),
                )

            if (
                len(parts) == 3
                and parts[:2] == ["v1", "route-decisions"]
                and method == "GET"
            ):
                return 200, self.routing.get_decision(
                    parts[2]
                )

'''

insert_before(
    "src/specgraph_foundry/api.py",
    "            return 404, {\n",
    api_routes,
    'parts[3] == "routing-policy"',
)

insert_after(
    "src/specgraph_foundry/cli.py",
    "from .execution import ExecutionService\n",
    "from .routing import RoutingService\n",
    "from .routing import RoutingService",
)

cli_parsers = r'''
    set_policy = commands.add_parser(
        "set-routing-policy"
    )
    set_policy.add_argument("project_id")
    set_policy.add_argument(
        "--disable-offline",
        action="store_true",
    )
    set_policy.add_argument(
        "--enable-paid-emergency",
        action="store_true",
    )
    set_policy.add_argument(
        "--max-paid-decisions",
        type=int,
        default=1,
    )

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

    providers = commands.add_parser(
        "list-providers"
    )
    providers.add_argument("project_id")

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

    renderers = commands.add_parser(
        "list-renderers"
    )
    renderers.add_argument("project_id")

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

'''

insert_before(
    "src/specgraph_foundry/cli.py",
    '    server = commands.add_parser("serve")\n',
    cli_parsers,
    '"set-routing-policy"',
)

insert_after(
    "src/specgraph_foundry/cli.py",
    "    execution = ExecutionService(database)\n",
    "    routing = RoutingService(database)\n",
    "routing = RoutingService",
)

cli_commands = r'''
    if args.command == "set-routing-policy":
        output(
            routing.set_policy(
                project_id=args.project_id,
                allow_offline_degraded=(
                    not args.disable_offline
                ),
                paid_emergency_enabled=(
                    args.enable_paid_emergency
                ),
                max_paid_decisions_per_unlock=(
                    args.max_paid_decisions
                ),
            )
        )
        return 0

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

    if args.command == "list-providers":
        output(
            {
                "items": routing.list_providers(
                    args.project_id
                )
            }
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

    if args.command == "list-renderers":
        output(
            {
                "items": routing.list_renderers(
                    args.project_id
                )
            }
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

'''

insert_before(
    "src/specgraph_foundry/cli.py",
    "    suffix = uuid.uuid4().hex[:8]\n",
    cli_commands,
    'args.command == "set-routing-policy"',
)

readme_path = ROOT / "README.md"
readme = readme_path.read_text(
    encoding="utf-8"
)

section = dedent(
    r'''

    ## Policy-controlled provider routing

    Provider choice is now deterministic, persisted, and
    independent from source authority, rendering, and runtime
    execution.

    Canonical route law:

    1. `LOCAL_TOOLCHAIN`
    2. `FREE_READY_PROVIDER`
    3. `FREE_FALLBACK_PROVIDER`
    4. `COOLDOWN_QUEUE`
    5. `OFFLINE_DEGRADED_MODE`
    6. `PAID_EMERGENCY_ONLY_BY_EXPLICIT_UNLOCK`

    Provider and renderer metadata cannot contain credentials.
    Paid routing requires both project-policy authorization and
    an explicit, expiring, capacity-limited unlock.

    ```bash
    python -m specgraph_foundry set-routing-policy \
      PROJECT_ID \
      --enable-paid-emergency

    python -m specgraph_foundry configure-provider \
      PROJECT_ID \
      LOCAL_TOOLCHAIN \
      LOCAL_TOOLCHAIN \
      LOCAL \
      0 \
      '["CODE_PATCH","BUILD","TEST"]'

    python -m specgraph_foundry provider-health \
      PROVIDER_ID \
      READY

    python -m specgraph_foundry route-capability \
      PROJECT_ID \
      CODE_PATCH \
      --offline-capable
    ```
    '''
)

if "## Policy-controlled provider routing" not in readme:
    readme_path.write_text(
        readme.rstrip()
        + "\n"
        + section.lstrip(),
        encoding="utf-8",
    )
    print("UPDATED README.md")

print("POLICY AND ROUTING CONTROL PLANE CREATED")
