/* SPDX-License-Identifier: AGPL-3.0-only */
package com.atropos.android.app.ui

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class SixAnswersPanelTest {

    @Test
    fun each_known_health_has_its_own_shape() {
        // Section E: colour pairs with a redundant non-colour signal. Two
        // healths sharing a glyph would put the whole signal back on colour.
        val glyphs = listOf("verified", "pending", "error", "unknown").map(::healthGlyph)
        assertEquals(glyphs.size, glyphs.distinct().size)
    }

    @Test
    fun an_unrecognised_health_never_renders_as_healthy() {
        // The one thing this panel must not do is show a state it does not
        // understand as a passing one.
        assertEquals("?", healthGlyph("something-new"))
        assertTrue(healthGlyph("something-new") != healthGlyph("verified"))
    }

    @Test
    fun health_matching_ignores_case() {
        assertEquals(healthGlyph("verified"), healthGlyph("VERIFIED"))
    }
}

class ApprovalCardTest {

    @Test
    fun an_undeclared_territory_is_stated_rather_than_left_blank() {
        // Blank invites the reader to fill it in, and the two readings they
        // might choose -- nothing and everything -- are opposites.
        assertEquals("no territory declared", territoryLabel(emptyList()))
    }

    @Test
    fun a_declared_territory_is_listed_in_full() {
        assertEquals(
            "src/main/kotlin, app/src",
            territoryLabel(listOf("src/main/kotlin", "app/src"))
        )
    }
}
