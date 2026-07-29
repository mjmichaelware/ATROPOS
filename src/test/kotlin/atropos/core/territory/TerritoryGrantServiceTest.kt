/* SPDX-License-Identifier: AGPL-3.0-only */
package atropos.core.territory

import atropos.core.director.DirectorService
import atropos.core.director.DirectorStore
import atropos.core.director.ObservationKind
import atropos.core.policy.ActionActor
import atropos.core.policy.ActionProposal
import atropos.core.policy.AgencyDisposition
import atropos.core.policy.BoundedAgencyGate
import atropos.core.policy.ExecutionPolicyEngine
import atropos.core.policy.PolicyActionClass
import java.nio.file.Files
import java.nio.file.Path
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotNull
import kotlin.test.assertNull
import kotlin.test.assertTrue

/**
 * Batch 8 — territory is delegated downward at dispatch, never claimed.
 *
 * Before this batch a hierarchy node's declared territory bounded nothing: the
 * gate consulted only the policy engine, so any node could target any path the
 * engine did not already forbid.
 */
class TerritoryGrantServiceTest {

    private fun fixture(rootPrefix: String = "", director: DirectorService? = null): Pair<TerritoryGrantService, TerritoryService> {
        val root: Path = Files.createTempDirectory("atropos-territory-")
        val service = TerritoryService(TerritoryStore(root), director)
        return TerritoryGrantService(service, rootPrefix) to service
    }

    private fun node(id: String = "node-1") =
        ActionActor.HierarchyNode(role = "dag-executor", nodeId = id)

    // --- granting ---------------------------------------------------------

    @Test
    fun a_dispatcher_holding_nothing_cannot_grant_anything() {
        val (grants, _) = fixture()
        // A node is not an owner: it holds nothing until something grants to it.
        val result = grants.grantToNode(
            dispatcher = node("dispatcher"),
            node = node("child"),
            requestedPrefixes = listOf("src/main")
        )

        assertTrue(result is GrantResult.Refused, "a holder of nothing must not be able to delegate")
        assertTrue((result as GrantResult.Refused).reason.contains("holds no territory"), result.reason)
    }

    @Test
    fun the_owner_can_delegate_and_the_child_records_its_parent() {
        val (grants, service) = fixture()
        val result = grants.grantToNode(ActionActor.HumanOwner, node(), listOf("src/main/kotlin"))

        assertTrue(result is GrantResult.Granted)
        val child = (result as GrantResult.Granted).assignments.single()
        assertEquals("src/main/kotlin", child.allowedPrefix)
        assertEquals("dag-executor:node-1", child.boundActorIdentity)
        assertEquals("dag-executor:node-1", child.ownerId)
        assertNotNull(child.parentTerritoryId)
        assertEquals(grants.rootGrant().id, child.parentTerritoryId)
        assertTrue(service.getAll().any { it.id == child.id }, "the grant must be durable")
    }

    @Test
    fun a_child_may_narrow_but_never_widen() {
        val (grants, _) = fixture(rootPrefix = "src/main")

        val narrower = grants.grantToNode(ActionActor.HumanOwner, node("a"), listOf("src/main/kotlin"))
        assertTrue(narrower is GrantResult.Granted, "narrowing is the point of delegation")

        val wider = grants.grantToNode(ActionActor.HumanOwner, node("b"), listOf("src"))
        assertTrue(wider is GrantResult.Refused, "a child may not exceed its parent")
        assertTrue((wider as GrantResult.Refused).reason.contains("outside the territory"), wider.reason)

        val elsewhere = grants.grantToNode(ActionActor.HumanOwner, node("c"), listOf("docs"))
        assertTrue(elsewhere is GrantResult.Refused)
    }

    @Test
    fun a_node_declaring_no_territory_is_granted_nothing() {
        val (grants, _) = fixture()
        val result = grants.grantToNode(ActionActor.HumanOwner, node(), emptyList())

        assertTrue(result is GrantResult.Refused)
        assertTrue((result as GrantResult.Refused).reason.contains("declared no territory"), result.reason)
    }

    // --- enforcement at the gate -----------------------------------------

    private fun proposalFor(actor: ActionActor, paths: List<String>) = ActionProposal(
        id = "p",
        actionClass = PolicyActionClass.FILE_MUTATION,
        actor = actor,
        targetPaths = paths
    )

    private fun gateOver(grants: TerritoryGrantService) =
        BoundedAgencyGate(ExecutionPolicyEngine(Files.createTempDirectory("atropos-territory-gate-")), grants)

    @Test
    fun an_ungranted_node_is_refused_at_the_gate() {
        val (grants, _) = fixture()
        val decision = gateOver(grants).evaluate(proposalFor(node(), listOf("src/main/kotlin/A.kt")))

        assertEquals(AgencyDisposition.POLICY_BLOCKED, decision.disposition)
        assertTrue(decision.reason.contains("territory refusal"), decision.reason)
    }

    @Test
    fun a_granted_node_may_act_inside_its_grant_and_not_outside() {
        val (grants, _) = fixture()
        grants.grantToNode(ActionActor.HumanOwner, node(), listOf("src/foo"))
        val gate = gateOver(grants)

        assertEquals(
            AgencyDisposition.ALLOWED,
            gate.evaluate(proposalFor(node(), listOf("src/foo/A.kt"))).disposition
        )
        assertEquals(
            AgencyDisposition.POLICY_BLOCKED,
            gate.evaluate(proposalFor(node(), listOf("src/bar/B.kt"))).disposition,
            "a node granted src/foo must not reach src/bar"
        )
    }

    @Test
    fun a_grant_authorises_one_work_item_only() {
        val (grants, _) = fixture()
        grants.grantToNode(ActionActor.HumanOwner, node("node-1"), listOf("src/foo"))
        val gate = gateOver(grants)

        // A different node cannot ride on node-1's grant.
        assertEquals(
            AgencyDisposition.POLICY_BLOCKED,
            gate.evaluate(proposalFor(node("node-2"), listOf("src/foo/A.kt"))).disposition
        )
    }

    @Test
    fun every_refusal_is_recorded_as_a_violation() {
        val (grants, service) = fixture()
        gateOver(grants).evaluate(proposalFor(node(), listOf("src/secret/A.kt")))

        val violations = service.getViolations()
        assertEquals(1, violations.size)
        assertEquals("src/secret/A.kt", violations.single().filePath)
        assertTrue(violations.single().reason.contains("territory refusal"))
    }

    @Test
    fun wired_director_receives_territory_refusal_observation() {
        val directorRoot = Files.createTempDirectory("atropos-territory-director-")
        val director = DirectorService(DirectorStore(directorRoot), directorRoot)
        val (grants, _) = fixture(director = director)

        gateOver(grants).evaluate(proposalFor(node(), listOf("src/secret/A.kt")))

        val advisory = director.advisoryBeforePromotion(files = listOf("src/secret/A.kt"))
        assertEquals(false, advisory.allowed)
        assertTrue(advisory.blockingObservations.any { it.kind == ObservationKind.TERRITORY_VIOLATION })
    }

    @Test
    fun the_owner_is_not_bounded_out_of_its_own_repository() {
        val (grants, _) = fixture()
        val decision = gateOver(grants)
            .evaluate(proposalFor(ActionActor.HumanOwner, listOf("anywhere/at/all.kt")))

        assertEquals(AgencyDisposition.ALLOWED, decision.disposition)
    }

    @Test
    fun a_lifecycle_actor_is_not_territory_bounded() {
        val (grants, _) = fixture()
        val decision = gateOver(grants).evaluate(
            ActionProposal(
                id = "p",
                actionClass = PolicyActionClass.QUEUE,
                actor = ActionActor.SystemService("queue")
            )
        )
        assertEquals(AgencyDisposition.ALLOWED, decision.disposition)
    }

    @Test
    fun paths_outside_territory_are_reported_precisely() {
        val (grants, _) = fixture()
        grants.grantToNode(ActionActor.HumanOwner, node(), listOf("src/foo"))

        assertNull(grants.firstPathOutsideTerritory(node(), listOf("src/foo/A.kt", "src/foo/B.kt")))
        assertEquals(
            "src/bar/C.kt",
            grants.firstPathOutsideTerritory(node(), listOf("src/foo/A.kt", "src/bar/C.kt"))
        )
    }

    // --- persistence ------------------------------------------------------

    @Test
    fun the_bound_identity_survives_a_store_round_trip() {
        val root = Files.createTempDirectory("atropos-territory-roundtrip-")
        val service = TerritoryService(TerritoryStore(root))
        val grants = TerritoryGrantService(service)
        grants.grantToNode(ActionActor.HumanOwner, node("node-9"), listOf("src/x"))

        val reloaded = TerritoryService(TerritoryStore(root)).getAll()
        val child = reloaded.single { it.boundActorIdentity != null }
        assertEquals("dag-executor:node-9", child.boundActorIdentity)
        assertEquals("src/x", child.allowedPrefix)
        assertNotNull(child.parentTerritoryId)
    }

    @Test
    fun assignments_written_before_grant_on_dispatch_still_parse() {
        // 11 tab-separated fields, the pre-Batch-8 layout.
        val legacy = listOf(
            "terr-old", "human-owner", "owner", "src", "**/*.kt", "",
            java.time.Instant.now().toString(), "", "", "1048576", "false"
        ).joinToString("\t")

        val parsed = parseAssignmentLine(legacy)
        assertNotNull(parsed)
        assertEquals("terr-old", parsed.id)
        assertNull(parsed.boundActorIdentity, "an operator grant is bound to no work item")
    }
}
