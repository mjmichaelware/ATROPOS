/* SPDX-License-Identifier: AGPL-3.0-only */
package atropos.core.contract

import atropos.bridge.LocalHttpServer
import atropos.core.intent.CanonicalVerb
import atropos.core.verification.UiParityVerifier
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class SurfaceContractsTest {

    data class TestState(val count: Int) : MviState
    data class Increment(val diff: Int) : MviIntent

    @Test
    fun `MVI pattern updates state flow`() {
        val reducer = object : MviReducer<TestState, Increment> {
            override fun reduce(currentState: TestState, intent: Increment): TestState {
                return TestState(currentState.count + intent.diff)
            }
        }
        val manager = ViewStateManager(TestState(0), reducer)
        assertEquals(0, manager.state.value.count)
        manager.dispatch(Increment(5))
        assertEquals(5, manager.state.value.count)
    }

    @Test
    fun `UiDesignTokens concentricity and targets values`() {
        val outerRadius = 16.0
        val inset = 4.0
        assertEquals(12.0, UiDesignTokens.parentRadiusMinusInset(outerRadius, inset))
        assertEquals(44.0, UiDesignTokens.MINIMUM_TAP_TARGET_PT)
    }

    @Test
    fun `verifyMaxVerbLimit enforces ceiling`() {
        val verbs = CanonicalVerb.values().toList()

        // The canonical set is exactly at the ceiling, so no view built from it
        // can exceed the limit — the enum is the enforcement, and this asserts
        // that rather than asserting a refusal the type system makes
        // unreachable. The previous form passed the full 13-verb list expecting
        // a rejection; with 13 entries `take(13)` and the whole list are the
        // same value, so it asserted true and false about one input.
        assertEquals(13, verbs.size)
        assertTrue(UiParityVerifier.verifyMaxVerbLimit(AtroposView.GOVERNANCE, verbs))

        // Duplicates do not consume budget: a view showing the same verb twice
        // is showing one verb.
        assertTrue(UiParityVerifier.verifyMaxVerbLimit(AtroposView.GOVERNANCE, verbs + verbs))
    }

    @Test
    fun `verifyExpansionState enforces non-persistence`() {
        assertTrue(UiParityVerifier.verifyExpansionState(isExpandedByDefault = false, isStatePersistent = false))
        assertFalse(UiParityVerifier.verifyExpansionState(isExpandedByDefault = true, isStatePersistent = false))
    }

    @Test
    fun `LocalHttpServer handles session creation and SSE streams`() {
        val server = LocalHttpServer("127.0.0.1", 8080, "password123")
        val responseNoAuth = server.handleRequest("/v1/session", "POST", emptyMap())
        assertEquals(401, responseNoAuth.statusCode)

        val responseWithAuth = server.handleRequest(
            "/v1/session", "POST", mapOf("Authorization" to "Bearer password123")
        )
        assertEquals(200, responseWithAuth.statusCode)
        assertTrue(responseWithAuth.body.contains("session_id"))
    }
}
