/* SPDX-License-Identifier: AGPL-3.0-only */
package com.atropos.android.app.bridge

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNull
import kotlin.test.assertTrue

class SelfHostParserTest {

    private fun run(extra: String = "", dag: String? = null) = SelfHostParser.parse(
        """{"ok":true,"goalId":"g-1","status":"running","message":"advancing"$extra""" +
            (dag?.let { ""","dag":$it""" } ?: "") + "}"
    )

    @Test
    fun a_running_goal_is_not_finished() {
        val parsed = run()!!
        assertEquals("g-1", parsed.goalId)
        assertFalse(parsed.finished)
        assertFalse(parsed.succeeded)
    }

    @Test
    fun finished_and_succeeded_are_different_questions() {
        // A client polling only for completion would show a terminal failure as
        // a finished build.
        val failed = run(""","terminalCondition":"terminal_failure"""")!!
        assertTrue(failed.finished)
        assertFalse(failed.succeeded)

        val ok = run(""","terminalCondition":"verified_complete"""")!!
        assertTrue(ok.finished)
        assertTrue(ok.succeeded)
    }

    @Test
    fun a_run_with_no_graph_reports_no_progress_rather_than_zero() {
        // "No plan yet" and "a plan that achieved nothing" look identical as
        // counts and mean opposite things.
        assertNull(run().let { it!!.dag })
    }

    @Test
    fun progress_is_a_fraction_of_the_graph() {
        val parsed = run(dag = """{"dagId":"d1","total":8,"complete":2,"failed":0,"blocked":0,"pending":6,"running":0,"message":"ok"}""")!!
        assertEquals(0.25, parsed.dag!!.fraction())
        assertFalse(parsed.dag!!.stalled)
    }

    @Test
    fun an_empty_graph_has_no_fraction_rather_than_zero_or_a_divide_by_zero() {
        val parsed = run(dag = """{"dagId":"d1","total":0,"complete":0,"failed":0,"blocked":0,"pending":0,"running":0,"message":"none"}""")!!
        assertNull(parsed.dag!!.fraction())
    }

    @Test
    fun failed_or_blocked_nodes_mark_the_run_stalled() {
        val failed = run(dag = """{"dagId":"d1","total":8,"complete":6,"failed":2,"blocked":0,"pending":0,"running":0,"message":"x"}""")!!
        assertTrue(failed.dag!!.stalled)

        val blocked = run(dag = """{"dagId":"d1","total":8,"complete":6,"failed":0,"blocked":2,"pending":0,"running":0,"message":"x"}""")!!
        assertTrue(blocked.dag!!.stalled)
    }

    @Test
    fun the_string_null_is_not_mistaken_for_a_value() {
        // JsonWriter emits a bare null for an unmeasured field; optString reads
        // that back as the text "null", which must not become a phase name.
        val parsed = SelfHostParser.parse(
            """{"ok":true,"goalId":"g-1","status":"running","phase":"null","currentNodeId":"null","message":""}"""
        )!!
        assertNull(parsed.phase)
        assertNull(parsed.currentNodeId)
    }

    @Test
    fun an_unreadable_or_refused_body_yields_nothing() {
        assertNull(SelfHostParser.parse("not json"))
        assertNull(SelfHostParser.parse("""{"ok":false}"""))
        assertNull(SelfHostParser.parse("""{"ok":true}"""), "a run with no goal id is not a run")
    }

    @Test
    fun a_refusal_surfaces_the_remedy_not_just_the_complaint() {
        val text = SelfHostParser.refusal(
            """{"detail":"A self-build run must name who asked for it.","remedy":"Send startedBy=<operator>."}"""
        )
        assertTrue(text.contains("must name who asked"))
        assertTrue(text.contains("Send startedBy"), "the remedy is the actionable half")
    }
}
