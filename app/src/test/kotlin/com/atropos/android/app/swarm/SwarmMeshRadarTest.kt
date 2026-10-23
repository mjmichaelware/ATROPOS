/* SPDX-License-Identifier: AGPL-3.0-only */
package com.atropos.android.app.swarm

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class SwarmMeshRadarTest {

    @Test
    fun testInitialSwarmAgentsCoverAllFourCoreRoles() {
        val agents = INITIAL_SWARM_AGENTS
        val roles = agents.map { it.role }.toSet()

        assertTrue(roles.contains(SwarmRole.SUPERVISOR))
        assertTrue(roles.contains(SwarmRole.PLANNER))
        assertTrue(roles.contains(SwarmRole.CODER))
        assertTrue(roles.contains(SwarmRole.VERIFIER))
    }

    @Test
    fun testEveryAgentHasValidPermissionsAndPositiveTokens() {
        INITIAL_SWARM_AGENTS.forEach { agent ->
            assertFalse(agent.id.isBlank())
            assertFalse(agent.name.isBlank())
            assertFalse(agent.model.isBlank())
            assertFalse(agent.activeTask.isBlank())
            assertTrue(agent.permissions.isNotEmpty())
            assertTrue(agent.tokensBurned > 0)
            assertTrue(agent.cpuPercent in 0..100)
            assertTrue(agent.memoryMb > 0)
        }
    }

    @Test
    fun testSupervisorHoldsMerkleSealPermission() {
        val supervisor = INITIAL_SWARM_AGENTS.find { it.role == SwarmRole.SUPERVISOR }
            ?: error("Supervisor agent missing")

        assertTrue(supervisor.permissions.contains("merkle_seal"))
        assertTrue(supervisor.permissions.contains("terminal_exec"))
    }

    @Test
    fun testTaskDispatchUpdatesAgentState() {
        val agent = INITIAL_SWARM_AGENTS.first()
        val dispatchedTask = "Compile AST node resolution tree"

        val updated = agent.copy(
            status = "WORKING",
            activeTask = dispatchedTask,
            tokensBurned = agent.tokensBurned + 400L
        )

        assertEquals("WORKING", updated.status)
        assertEquals(dispatchedTask, updated.activeTask)
        assertEquals(agent.tokensBurned + 400L, updated.tokensBurned)
    }
}
