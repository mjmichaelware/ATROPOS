/* SPDX-License-Identifier: AGPL-3.0-only */
package atropos.core.territory

import atropos.core.auth.SwarmNode
import atropos.core.auth.SwarmSpec
import atropos.core.director.DirectorService
import atropos.core.policy.ActionActor
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

/**
 * `SUP.TERR.SUBAGENT-SPAWN`: every child is territory-bounded at birth, and
 * the bounds come from the attested topology rather than from the code that
 * wanted to spawn.
 */
class SubagentSpawnServiceTest {

    private fun spec(maxDepth: Int = 2) = SwarmSpec(
        nodes = listOf(
            SwarmNode("reviewer", "auditor", listOf("src/main/kotlin/atropos/core/verifier")),
            SwarmNode("builder", "worker", listOf("src/main/kotlin/atropos/core/factory"))
        ),
        maxDepth = maxDepth,
        escalationPath = listOf("director"),
        coordinationCostBound = null
    )

    private fun grants() = TerritoryGrantService(TerritoryService(director = DirectorService()))

    @Test
    fun `a declared node within depth and territory is spawned with its grant`() {
        val service = SubagentSpawnService(grants(), spec())

        val result = service.spawn(
            parent = ActionActor.HumanOwner,
            childName = "reviewer",
            childRole = "auditor",
            requestedPrefixes = listOf("src/main/kotlin/atropos/core/verifier"),
            depth = 1
        )

        assertTrue(result is SpawnResult.Spawned)
        assertEquals("auditor:reviewer", result.agent.actor.identity)
        assertTrue(result.agent.territory.isNotEmpty())
        assertTrue(result.agent.territoryClaim().contains("src/main/kotlin/atropos/core/verifier"))
    }

    @Test
    fun `a spawn with no declared territory is refused`() {
        val result = SubagentSpawnService(grants(), spec()).spawn(
            parent = ActionActor.HumanOwner,
            childName = "reviewer",
            childRole = "auditor",
            requestedPrefixes = emptyList(),
            depth = 1
        )

        assertFalse(result.permitted)
        assertTrue((result as SpawnResult.Refused).reason.contains("no bounds"))
    }

    @Test
    fun `depth beyond the attested maximum is refused`() {
        val result = SubagentSpawnService(grants(), spec(maxDepth = 1)).spawn(
            parent = ActionActor.HumanOwner,
            childName = "reviewer",
            childRole = "auditor",
            requestedPrefixes = listOf("src/main/kotlin/atropos/core/verifier"),
            depth = 2
        )

        assertFalse(result.permitted)
        assertTrue((result as SpawnResult.Refused).reason.contains("maxDepth"))
    }

    @Test
    fun `a node the topology never declared cannot be created`() {
        val result = SubagentSpawnService(grants(), spec()).spawn(
            parent = ActionActor.HumanOwner,
            childName = "stowaway",
            childRole = "worker",
            requestedPrefixes = listOf("src"),
            depth = 1
        )

        assertFalse(result.permitted)
        assertTrue((result as SpawnResult.Refused).reason.contains("not declared"))
    }

    @Test
    fun `territory the topology does not grant is refused even when the parent holds it`() {
        val result = SubagentSpawnService(grants(), spec()).spawn(
            parent = ActionActor.HumanOwner,
            childName = "reviewer",
            childRole = "auditor",
            requestedPrefixes = listOf("src/main/kotlin/atropos/core/security"),
            depth = 1
        )

        assertFalse(result.permitted)
        assertTrue((result as SpawnResult.Refused).reason.contains("does not grant"))
    }

    @Test
    fun `nothing spawns when no topology is attested`() {
        val result = SubagentSpawnService(grants(), swarm = null).spawn(
            parent = ActionActor.HumanOwner,
            childName = "reviewer",
            childRole = "auditor",
            requestedPrefixes = listOf("src"),
            depth = 1
        )

        assertFalse(result.permitted)
        assertTrue((result as SpawnResult.Refused).reason.contains("no attested Swarm.md"))
    }

    @Test
    fun `refused spawns are recorded, not only permitted ones`() {
        val service = SubagentSpawnService(grants(), spec())
        service.spawn(ActionActor.HumanOwner, "stowaway", "worker", listOf("src"), 1)
        service.spawn(
            ActionActor.HumanOwner, "reviewer", "auditor",
            listOf("src/main/kotlin/atropos/core/verifier"), 1
        )

        val attempts = service.attempts()

        assertEquals(2, attempts.size)
        assertEquals(listOf(false, true), attempts.map { it.permitted })
        assertTrue(attempts.first().render().startsWith("refused"))
    }
}
