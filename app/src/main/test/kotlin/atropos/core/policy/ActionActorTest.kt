/* SPDX-License-Identifier: AGPL-3.0-only */
package atropos.core.policy

import java.nio.file.Files
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertTrue

/**
 * Batch 7 — every action must say who is asking, because territory resolves by
 * actor and an unattributed action has no territory to check against.
 */
class ActionActorTest {

    private fun gate() = BoundedAgencyGate(
        ExecutionPolicyEngine(Files.createTempDirectory("atropos-actor-"))
    )

    @Test
    fun a_hierarchy_node_without_a_role_cannot_be_constructed() {
        assertFailsWith<IllegalArgumentException> {
            ActionActor.HierarchyNode(role = "", nodeId = "node-1")
        }
        assertFailsWith<IllegalArgumentException> {
            ActionActor.HierarchyNode(role = "   ", nodeId = "node-1")
        }
    }

    @Test
    fun a_hierarchy_node_without_a_node_id_cannot_be_constructed() {
        assertFailsWith<IllegalArgumentException> {
            ActionActor.HierarchyNode(role = "worker", nodeId = "")
        }
    }

    @Test
    fun a_system_actor_without_a_service_name_cannot_be_constructed() {
        assertFailsWith<IllegalArgumentException> { ActionActor.SystemService("") }
        assertFailsWith<IllegalArgumentException> { ActionActor.SystemService("  ") }
    }

    @Test
    fun no_actor_variant_can_produce_a_blank_identity() {
        // The interface is sealed and every variant rejects blank input, so an
        // unidentifiable actor cannot reach the gate at all. This is why the
        // gate carries no runtime blank-actor branch: it would be unreachable.
        val everyKind = listOf(
            ActionActor.HumanOwner,
            ActionActor.HierarchyNode("worker", "node-1"),
            ActionActor.SystemService("daemon")
        )
        assertTrue(everyKind.none { it.identity.isBlank() })
        assertEquals(ActorKind.entries.toSet(), everyKind.map { it.kind }.toSet())
    }

    @Test
    fun an_identified_actor_still_reaches_the_policy_engine() {
        val decision = gate().evaluate(
            ActionProposal(
                id = "attributed",
                actionClass = PolicyActionClass.SHELL,
                actor = ActionActor.HumanOwner,
                command = listOf("ls", "src")
            )
        )

        assertEquals(AgencyDisposition.ALLOWED, decision.disposition)
    }

    @Test
    fun identities_are_distinct_per_actor_kind() {
        assertEquals("human-owner", ActionActor.HumanOwner.identity)
        assertEquals("worker:node-7", ActionActor.HierarchyNode("worker", "node-7").identity)
        assertEquals("system:daemon", ActionActor.SystemService("daemon").identity)

        assertEquals(ActorKind.HUMAN_OWNER, ActionActor.HumanOwner.kind)
        assertEquals(ActorKind.HIERARCHY_NODE, ActionActor.HierarchyNode("w", "n").kind)
        assertEquals(ActorKind.SYSTEM_SERVICE, ActionActor.SystemService("queue").kind)
    }

    @Test
    fun the_actor_does_not_change_an_existing_verdict() {
        val repoRoot = Files.createTempDirectory("atropos-actor-parity-")
        val gate = BoundedAgencyGate(ExecutionPolicyEngine(repoRoot))
        val actors = listOf(
            ActionActor.HumanOwner,
            ActionActor.HierarchyNode("dag-executor", "node-1"),
            ActionActor.SystemService("daemon")
        )

        // Same action, three actors, one verdict: this batch adds identity, not
        // authorisation. Territory is what will make the actor decisive.
        val dispositions = actors.map { actor ->
            gate.evaluate(
                ActionProposal(
                    id = "parity",
                    actionClass = PolicyActionClass.SHELL,
                    actor = actor,
                    command = listOf("ls")
                )
            ).disposition
        }
        assertEquals(listOf(AgencyDisposition.ALLOWED), dispositions.distinct())

        val refusals = actors.map { actor ->
            gate.evaluate(
                ActionProposal(
                    id = "parity-deny",
                    actionClass = PolicyActionClass.SHELL,
                    actor = actor,
                    command = listOf("ls", "&&", "rm")
                )
            ).disposition
        }
        assertEquals(listOf(AgencyDisposition.POLICY_BLOCKED), refusals.distinct())
    }
}
