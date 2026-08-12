/* SPDX-License-Identifier: AGPL-3.0-only */
package atropos.cli.commands

import atropos.core.agent.AgentService
import atropos.core.agent.GoalContinuationService
import atropos.core.agent.SupervisedSessionStore
import java.nio.file.Paths
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull
import kotlin.test.assertNotNull

class AgentIdentityResponderTest {

    @Test
    fun testRespondGreekMythology() {
        val root = Paths.get("/tmp")
        val agentService = AgentService(collector = atropos.core.agent.AgentContextCollector(root))
        val continuation = GoalContinuationService(root)
        val store = SupervisedSessionStore(root)
        
        val responder = AgentIdentityResponder(
            repoRoot = root,
            service = agentService,
            continuationService = continuation,
            sessionStore = store,
            activeProviderName = { "groq" }
        )
        
        // Mythology request should return null (pass-through to normal processing)
        assertNull(responder.respond("tell me about greek mythology atropos"))
    }
}
