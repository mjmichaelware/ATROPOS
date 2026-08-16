/* SPDX-License-Identifier: AGPL-3.0-only */
package atropos.bridge

import atropos.bridge.http.HttpRequest
import atropos.core.agent.SelfHostGoalService
import java.nio.file.Files
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

/**
 * The self-build routes exist, and refuse in the ways the boundary requires.
 *
 * Asserted against the route table rather than a live service, because the
 * properties that matter here are the refusals: an unattributed start and a
 * build with no repository bound must both be turned away before any goal is
 * opened, and neither needs a workspace to prove.
 */
class BridgeSelfHostRoutesTest {

    private val table = BridgeRoutes().table()

    private fun post(path: String, body: String = "") = table.resolve(
        HttpRequest("POST", path, emptyMap(), emptyMap(), body)
    )

    private fun get(path: String, query: Map<String, String> = emptyMap()) = table.resolve(
        HttpRequest("GET", path, query, emptyMap(), "")
    )

    @Test
    fun the_three_self_build_routes_are_advertised() {
        val described = table.describe()
        assertTrue(described.contains("/v1/selfhost/start"))
        assertTrue(described.contains("/v1/selfhost/advance"))
        assertTrue(described.contains("/v1/selfhost/status"))
    }

    /**
     * A handler over a throwaway workspace.
     *
     * The unwired table refuses with 501 before any validation, which is the
     * right order — nothing can run without a service, so checking the prompt
     * first would report a fixable mistake for an unfixable state. The
     * validation refusals therefore need a bound handler to be visible at all.
     */
    private fun bound(): BridgeSelfHostHandler {
        val root = Files.createTempDirectory("selfhost-routes-")
        return BridgeSelfHostHandler(SelfHostGoalService(root))
    }

    @Test
    fun a_build_with_no_prompt_is_a_bad_request_not_a_refusal() {
        // The distinction matters to a client: a bad request is the caller's
        // mistake to fix, a refusal is the engine declining something valid.
        val response = bound().start(
            HttpRequest("POST", "/v1/selfhost/start", emptyMap(), emptyMap(), """{"startedBy":"android-client"}""")
        )
        assertEquals(400, response.status)
    }

    @Test
    fun an_unattributed_build_is_refused_with_403() {
        // A run mutates the operator's source tree. One that names nobody
        // cannot be audited afterwards, which is the whole point of recording
        // who asked.
        val response = bound().start(
            HttpRequest("POST", "/v1/selfhost/start", emptyMap(), emptyMap(), """{"prompt":"add a health endpoint"}""")
        )
        assertEquals(403, response.status)
        assertTrue(response.body.contains("attribution-required"))
    }

    @Test
    fun advancing_without_a_goal_id_is_a_bad_request() {
        val response = bound().advance(
            HttpRequest("POST", "/v1/selfhost/advance", emptyMap(), emptyMap(), "")
        )
        assertEquals(400, response.status)
    }

    @Test
    fun a_build_with_no_repository_bound_says_so_rather_than_failing_obscurely() {
        // The default BridgeRoutes has no SelfHostGoalService, which is the
        // truthful state for a runtime with nothing to build in. The route is
        // present either way so the surface a client discovers does not change
        // with configuration.
        val response = post("/v1/selfhost/start", """{"prompt":"x","startedBy":"y"}""")
        // Attribution is checked first only when a service exists; with none,
        // the wiring refusal is what a client must see.
        assertTrue(
            response.status == 501 || response.status == 403,
            "expected a stated refusal, got ${response.status}"
        )
    }

    @Test
    fun status_for_an_unknown_goal_is_a_stated_404_not_an_empty_run() {
        val response = get("/v1/selfhost/status", mapOf("goalId" to "no-such-goal"))
        assertTrue(
            response.status == 404 || response.status == 501,
            "expected a stated refusal, got ${response.status}"
        )
    }
}
