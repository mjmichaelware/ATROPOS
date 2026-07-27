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
