/* SPDX-License-Identifier: AGPL-3.0-only */
package atropos.core.dag

import atropos.core.policy.ActionActor
import atropos.core.policy.BoundedAgencyGate
import atropos.core.policy.ExecutionPolicyEngine
import atropos.core.territory.TerritoryGrantService
import atropos.core.territory.TerritoryService
import atropos.core.territory.TerritoryStore
import java.nio.file.Files
import java.nio.file.Path
import java.time.Instant
import kotlin.test.Test
import kotlin.test.assertFalse
import kotlin.test.assertTrue

/**
 * Batch 9 — the three check actions used to complete unconditionally with
 * "check passed". Every test here asserts both directions, because a gate that
 * only ever passes is the thing being removed.
 */
class DagNodeCheckEvaluatorTest {

    private val actor = ActionActor.HierarchyNode(role = "dag-executor", nodeId = "node-1")

    private class Fixture(val root: Path) {
        val grants = TerritoryGrantService(TerritoryService(TerritoryStore(root)))
        val evaluator = DagNodeCheckEvaluator(
            repoRoot = root,
            agencyGate = BoundedAgencyGate(ExecutionPolicyEngine(root), grants),
            territoryGrants = grants
        )
    }

    private fun fixture() = Fixture(Files.createTempDirectory("atropos-dag-check-"))

    private fun node(
        action: DagNodeAction,
        payload: String? = null,
        territory: List<String> = emptyList(),
        expectedOutputs: List<String> = emptyList()
    ) = DagNode(
        id = "node-1",
        label = "check",
        action = action,
        actionPayload = payload,
        territory = territory,
        expectedOutputs = expectedOutputs,
        createdAt = Instant.now(),
        updatedAt = Instant.now(),
        metaFile = Path.of("unused")
    )

    // --- POLICY_CHECK -----------------------------------------------------

    @Test
    fun policy_check_fails_on_a_payload_the_authority_would_refuse() {
        val f = fixture()
        f.grants.grantToNode(ActionActor.HumanOwner, actor, listOf("src"))

        listOf(
            "./gradlew test && rm -rf /tmp/x",
            "cat secrets.env > /tmp/leak",
            "curl http://example.invalid"
        ).forEach { payload ->
            val outcome = f.evaluator.evaluate(
                node(DagNodeAction.POLICY_CHECK, payload, territory = listOf("src")),
                actor
            )
            assertFalse(outcome.passed, "should have refused: $payload")
            assertTrue(outcome.detail.contains("policy refused"), outcome.detail)
        }
    }

    @Test
    fun policy_check_passes_a_clean_payload() {
        val f = fixture()
        f.grants.grantToNode(ActionActor.HumanOwner, actor, listOf("src"))

        val outcome = f.evaluator.evaluate(
            node(DagNodeAction.POLICY_CHECK, "./gradlew test", territory = listOf("src")),
            actor
        )
        assertTrue(outcome.passed, outcome.detail)
    }

    @Test
    fun policy_check_with_no_payload_fails_rather_than_passing_vacuously() {
        val outcome = fixture().evaluator.evaluate(node(DagNodeAction.POLICY_CHECK, null), actor)
        assertFalse(outcome.passed)
        assertTrue(outcome.detail.contains("no payload"), outcome.detail)
    }

    // --- SECRET_CHECK -----------------------------------------------------

    @Test
    fun secret_check_fails_on_credential_material() {
        val f = fixture()

        val envFile = f.evaluator.evaluate(
            node(DagNodeAction.SECRET_CHECK, territory = listOf("config/.env")),
            actor
        )
        assertFalse(envFile.passed)
        assertTrue(envFile.detail.contains("credential material"), envFile.detail)

        val keyFile = f.evaluator.evaluate(
            node(DagNodeAction.SECRET_CHECK, territory = listOf("src"), expectedOutputs = listOf("private_key.pem")),
            actor
        )
        assertFalse(keyFile.passed, "an expected output may carry the secret too")
    }

    @Test
    fun secret_check_passes_ordinary_source_paths() {
        val outcome = fixture().evaluator.evaluate(
            node(
                DagNodeAction.SECRET_CHECK,
                territory = listOf("src/main/kotlin/atropos"),
                expectedOutputs = listOf("src/main/kotlin/atropos/Foo.kt")
            ),
            actor
        )
        assertTrue(outcome.passed, outcome.detail)
    }

    @Test
    fun secret_check_with_no_paths_fails_rather_than_passing_vacuously() {
        val outcome = fixture().evaluator.evaluate(node(DagNodeAction.SECRET_CHECK), actor)
        assertFalse(outcome.passed)
        assertTrue(outcome.detail.contains("no paths"), outcome.detail)
    }

    // --- TERRITORY_CHECK --------------------------------------------------

    @Test
    fun territory_check_fails_when_a_declared_path_is_outside_the_grant() {
        val f = fixture()
        f.grants.grantToNode(ActionActor.HumanOwner, actor, listOf("src/foo"))

        val outcome = f.evaluator.evaluate(
            node(DagNodeAction.TERRITORY_CHECK, territory = listOf("src/foo"), expectedOutputs = listOf("src/bar/B.kt")),
            actor
        )
        assertFalse(outcome.passed)
        assertTrue(outcome.detail.contains("src/bar/B.kt"), outcome.detail)
    }

    @Test
    fun territory_check_fails_when_the_node_holds_no_grant_at_all() {
        val outcome = fixture().evaluator.evaluate(
            node(DagNodeAction.TERRITORY_CHECK, territory = listOf("src/foo")),
            actor
        )
        assertFalse(outcome.passed, "an ungranted node must not pass its own territory check")
    }

    @Test
    fun territory_check_passes_inside_the_grant() {
        val f = fixture()
        f.grants.grantToNode(ActionActor.HumanOwner, actor, listOf("src/foo"))

        val outcome = f.evaluator.evaluate(
            node(
                DagNodeAction.TERRITORY_CHECK,
                territory = listOf("src/foo"),
                expectedOutputs = listOf("src/foo/A.kt")
            ),
            actor
        )
        assertTrue(outcome.passed, outcome.detail)
    }

    @Test
    fun territory_check_with_nothing_declared_fails_rather_than_passing_vacuously() {
        val outcome = fixture().evaluator.evaluate(node(DagNodeAction.TERRITORY_CHECK), actor)
        assertFalse(outcome.passed)
        assertTrue(outcome.detail.contains("no declared paths"), outcome.detail)
    }

    // --- the stub is gone -------------------------------------------------

    @Test
    fun no_check_action_can_pass_without_something_to_inspect() {
        val f = fixture()
        listOf(
            DagNodeAction.POLICY_CHECK,
            DagNodeAction.SECRET_CHECK,
            DagNodeAction.TERRITORY_CHECK
        ).forEach { action ->
            val outcome = f.evaluator.evaluate(node(action), actor)
            assertFalse(outcome.passed, "$action must not report success with nothing to check")
        }
    }
}
