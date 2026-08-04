/* SPDX-License-Identifier: AGPL-3.0-only */
package atropos.cli.ui.design

import atropos.core.project.ProjectStatus
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNotNull
import kotlin.test.assertNull
import kotlin.test.assertTrue

/**
 * The vocabulary's whole purpose is that Source Doc 4's nine status terms stay
 * nine *distinguishable* statuses, and that no status is ever carried by colour
 * alone. Both are asserted here on real values rather than on the object's own
 * boolean, and the cross-layer agreement with `atropos.core.project.ProjectStatus`
 * is asserted here because it is the only place that can see both packages.
 */
class HoeStatusVocabularyTest {

    @Test
    fun the_nine_doc_four_terms_resolve_to_nine_different_run_states() {
        val resolved = HoeStatusVocabulary.CANONICAL_TERMS.associateWith {
            HoeStatusVocabulary.resolve(it)
        }

        assertEquals(emptyList(), resolved.filterValues { it == null }.keys.toList())
        assertEquals(9, resolved.size)
        assertEquals(9, resolved.values.toSet().size, "collapsed states: $resolved")
    }

    @Test
    fun planning_and_review_required_no_longer_share_a_state_with_queued_or_waiting() {
        assertEquals(RunState.PLANNING, HoeStatusVocabulary.resolve("planning"))
        assertEquals(RunState.REVIEW_REQUIRED, HoeStatusVocabulary.resolve("review-required"))
        assertEquals(RunState.QUEUED, HoeStatusVocabulary.resolve("queued"))
        assertEquals(RunState.WAITING, HoeStatusVocabulary.resolve("waiting"))
    }

    @Test
    fun doc_four_spellings_map_onto_the_runtime_names_they_rename() {
        assertEquals(RunState.RUNNING, HoeStatusVocabulary.resolve("working"))
        assertEquals(RunState.RUNNING, HoeStatusVocabulary.resolve("running"))
        assertEquals(RunState.COMPLETE, HoeStatusVocabulary.resolve("completed"))
        assertEquals(RunState.COMPLETE, HoeStatusVocabulary.resolve("complete"))
    }

    @Test
    fun a_meaningless_term_resolves_to_null_rather_than_a_plausible_state() {
        assertNull(HoeStatusVocabulary.resolve("frobnicating"))
        assertNull(HoeStatusVocabulary.resolve(""))
        assertNull(HoeStatusVocabulary.signalFor("frobnicating"))
    }

    @Test
    fun a_surface_that_must_render_something_gets_unknown_and_never_idle_or_complete() {
        assertEquals(RunState.UNKNOWN, HoeStatusVocabulary.resolveOrUnknown("frobnicating"))
        assertEquals(RunState.UNKNOWN, HoeStatusVocabulary.resolveOrUnknown("   "))
        assertEquals(RunState.PLANNING, HoeStatusVocabulary.resolveOrUnknown("planning"))
    }

    @Test
    fun case_padding_and_underscore_or_hyphen_spellings_all_reach_the_same_state() {
        val spellings = listOf(
            "review-required",
            "REVIEW_REQUIRED",
            "Review Required",
            "  review_required  ",
            "Review-Required"
        )

        assertEquals(
            listOf(RunState.REVIEW_REQUIRED),
            spellings.map { HoeStatusVocabulary.resolve(it) }.distinct()
        )
    }

    @Test
    fun every_run_state_carries_both_an_icon_and_words() {
        RunState.entries.forEach { state ->
            val unicode = HoeStatusVocabulary.signal(state)
            val ascii = HoeStatusVocabulary.signal(state, asciiOnly = true)

            assertEquals(state, unicode.state)
            assertTrue(unicode.icon.isNotBlank(), "${state.name} has no unicode icon")
            assertTrue(unicode.text.isNotBlank(), "${state.name} has no text")
            assertTrue(ascii.icon.isNotBlank(), "${state.name} has no ascii icon")
            assertEquals(state.label, ascii.text)
        }
    }

    @Test
    fun the_ascii_legend_survives_a_terminal_that_cannot_print_the_glyphs() {
        val legend = HoeStatusVocabulary.legend(asciiOnly = true)

        assertEquals(9, legend.size)
        assertEquals(
            HoeStatusVocabulary.CANONICAL_TERMS.map { HoeStatusVocabulary.resolve(it) },
            legend.map { it.state }
        )
        legend.forEach { signal ->
            assertTrue(
                signal.icon.all { it.code < 128 },
                "${signal.state.name} ascii icon is not ascii: ${signal.icon}"
            )
            assertTrue(signal.text.isNotBlank())
        }
    }

    @Test
    fun the_unicode_legend_keeps_doc_four_order_and_starts_at_idle() {
        val legend = HoeStatusVocabulary.legend()

        assertEquals(RunState.IDLE, legend.first().state)
        assertEquals(RunState.CANCELLED, legend.last().state)
        assertEquals(RunState.IDLE.glyph, legend.first().icon)
    }

    @Test
    fun the_default_conformance_check_reports_no_violations_at_all() {
        val report = HoeStatusVocabulary.conformance()

        assertEquals(emptyList(), report.unresolvedTerms)
        assertEquals(emptyMap(), report.collidingTerms)
        assertEquals(emptyList(), report.statesMissingText)
        assertEquals(emptyList(), report.statesMissingIcon)
        assertEquals("", report.describeViolations())
        assertTrue(report.conformant)
    }

    @Test
    fun conformance_names_the_term_it_could_not_resolve() {
        val report = HoeStatusVocabulary.conformance(
            HoeStatusVocabulary.CANONICAL_TERMS + "half-planned"
        )

        assertFalse(report.conformant)
        assertEquals(listOf("half-planned"), report.unresolvedTerms)
        assertTrue(
            report.describeViolations().contains("half-planned"),
            report.describeViolations()
        )
    }

    @Test
    fun conformance_names_both_terms_when_two_of_them_collapse_onto_one_state() {
        val report = HoeStatusVocabulary.conformance(listOf("working", "running"))

        assertFalse(report.conformant)
        assertEquals(
            listOf("working", "running").sorted(),
            report.collidingTerms.getValue(RunState.RUNNING).sorted()
        )
        assertTrue(
            report.describeViolations().contains("RUNNING"),
            report.describeViolations()
        )
    }

    /**
     * The cross-layer check: `design/` imports nothing from `core/`, so only a
     * test can prove the presentation vocabulary and the domain's own wire forms
     * still describe the same nine situations.
     */
    @Test
    fun the_domain_status_wire_forms_all_resolve_to_distinct_presentation_states() {
        val domainTerms = ProjectStatus.entries.map { it.canonical }
        val report = HoeStatusVocabulary.conformance(domainTerms)

        assertTrue(report.conformant, report.describeViolations())
        assertEquals(9, domainTerms.size)
        assertEquals(
            domainTerms.size,
            domainTerms.mapNotNull { HoeStatusVocabulary.resolve(it) }.toSet().size
        )
    }

    @Test
    fun the_presentation_terms_are_exactly_the_domain_wire_forms() {
        assertEquals(
            ProjectStatus.entries.map { it.canonical }.sorted(),
            HoeStatusVocabulary.CANONICAL_TERMS.sorted()
        )
    }

    @Test
    fun every_domain_status_round_trips_through_the_presentation_vocabulary() {
        ProjectStatus.entries.forEach { status ->
            val signal = HoeStatusVocabulary.signalFor(status.canonical)
            assertNotNull(signal, "${status.name} has no signal")
            assertTrue(signal.icon.isNotBlank())
            assertTrue(signal.text.isNotBlank())
            assertEquals(status, ProjectStatus.fromCanonical(status.canonical))
        }
    }
}
