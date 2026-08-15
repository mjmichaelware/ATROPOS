/* SPDX-License-Identifier: AGPL-3.0-only */
package com.atropos.android.app.bridge

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class ApprovalDecisionTest {

    private fun engine(
        get: (String) -> BridgeResult = { BridgeResult.Unreachable("unexpected") },
        post: (String, String) -> BridgeResult = { _, _ -> BridgeResult.Unreachable("unexpected") }
    ) = AndroidEngineBridge(
        discovery = BridgeDiscovery(candidates = listOf(8787)) { url ->
            if (url.endsWith("/v1/health")) BridgeResult.Ok("{}") else BridgeResult.Unreachable("unexpected")
        },
        http = object : BridgeHttpApi {
            override fun get(url: String): BridgeResult = get(url)
            override fun post(url: String, body: String): BridgeResult = post(url, body)
        }
    )

    @Test
    fun a_decision_carries_attribution_to_the_engine() {
        var sentBody = ""
        val outcome = engine(
            post = { url, body ->
                sentBody = body
                if (url.endsWith("/v1/approvals/decide")) BridgeResult.Ok("""{"ok":true,"id":"apr-1"}""")
                else BridgeResult.Unreachable("unexpected")
            }
        ).decideApproval("apr-1", approved = true, decidedBy = "android-client")

        assertTrue(outcome is ApprovalOutcome.Recorded)
        assertTrue(sentBody.contains("\"decidedBy\":\"android-client\""))
        assertTrue(sentBody.contains("\"approved\":true"))
    }

    @Test
    fun an_unattributed_decision_is_refused_before_it_reaches_the_engine() {
        // The engine answers 403 for this, and it is right to: an approval
        // nobody is named for cannot be audited. Refusing locally means the
        // client never presents an unattributable decision as sent.
        var posted = false
        val outcome = engine(post = { _, _ -> posted = true; BridgeResult.Ok("{}") })
            .decideApproval("apr-1", approved = true, decidedBy = "  ")

        assertTrue(outcome is ApprovalOutcome.Refused)
        assertTrue(!posted, "no request should have been sent")
    }

    @Test
    fun a_refusal_reports_the_engines_reason_rather_than_a_status_code() {
        val outcome = engine(
            post = { _, _ -> BridgeResult.HttpError(409, """{"detail":"already decided"}""") }
        ).decideApproval("apr-1", approved = false, decidedBy = "android-client")

        assertEquals("already decided", (outcome as ApprovalOutcome.Refused).detail)
    }

    @Test
    fun reachability_loss_is_distinct_from_refusal() {
        // The operator must be able to tell "the engine said no" from "the
        // engine never heard it" — only one of the two leaves the decision
        // still owed.
        val outcome = engine(post = { _, _ -> BridgeResult.Unreachable("closed") })
            .decideApproval("apr-1", approved = true, decidedBy = "android-client")

        assertEquals(ApprovalOutcome.EngineUnreachable, outcome)
    }

    @Test
    fun only_pending_approvals_reach_the_screen() {
        val approvals = engine(
            get = { url ->
                if (url.endsWith("/v1/approvals")) {
                    BridgeResult.Ok(
                        """{"ok":true,"pending":[{"id":"a","pending":true},{"id":"b","pending":false}]}"""
                    )
                } else BridgeResult.Unreachable("unexpected")
            }
        ).approvals()

        assertEquals(listOf("a"), approvals.map { it.id })
    }

    @Test
    fun the_six_answers_and_the_active_provider_come_from_the_engine() {
        val body = """{"ok":true,"provider":"groq","answers":{""" +
            """"objective":{"value":"ship","health":"verified","signal":"check"},""" +
            """"doing":{"value":"compiling","health":"pending","signal":"half"},""" +
            """"why":{"value":"asked","health":"verified","signal":"check"},""" +
            """"progress":{"value":"3/8","health":"pending","signal":"half"},""" +
            """"next":{"value":"/verify","health":"unknown","signal":"circle"},""" +
            """"evidence":{"value":"none recorded","health":"unknown","signal":"circle"}}}"""
        val bridge = engine(
            get = { url ->
                if (url.endsWith("/v1/answers")) BridgeResult.Ok(body)
                else BridgeResult.Unreachable("unexpected")
            }
        )

        assertEquals("ship", bridge.sixAnswers()?.objective?.value)
        assertEquals("pending", bridge.sixAnswers()?.progress?.health)
        assertEquals("groq", bridge.activeProvider())
    }

    @Test
    fun an_engine_that_names_no_provider_yields_null_rather_than_a_guess() {
        val bridge = engine(
            get = { url ->
                if (url.endsWith("/v1/answers")) BridgeResult.Ok("""{"ok":true,"answers":{}}""")
                else BridgeResult.Unreachable("unexpected")
            }
        )
        assertEquals(null, bridge.activeProvider())
    }
}
