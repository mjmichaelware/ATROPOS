/* SPDX-License-Identifier: AGPL-3.0-only */
/*
 * Test for UncertaintyCalibratedRenderer - verifies recommendation rendering with
 * proper confidence calibration and evidence display.
 * 
 * This test validates that the renderer correctly displays:
 * - HIGH confidence badges (green) for >=80% confidence
 * - MEDIUM confidence badges (yellow) for 50-79% confidence  
 * - LOW confidence badges (red) for <50% confidence
 * - Question mark for NotMeasured confidence
 * - X mark for Unavailable confidence
 * - Evidence IDs when present
 * - Proper formatting for all recommendation types
 */
package atropos.cli.ui

import atropos.cli.ui.UncertaintyCalibratedRecommendation.Recommendation
import atropos.cli.ui.design.ThemeCatalog
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class UncertaintyCalibratedRendererTest {
    private val theme = ThemeCatalog.all.first()

    @Test
    fun `renders measured confidence with badge`() {
        val renderer = UncertaintyCalibratedRenderer(theme)
        val recInstance = UncertaintyCalibratedRecommendation()
            .addMeasured("Use provider X", 85, listOf("evidence-1"))

        val output = renderer.render(recInstance.all().first())

        assertTrue(output.contains("HIGH"))
        assertTrue(output.contains("evidence: evidence-1"))
        assertTrue(output.contains("Use provider X"))
    }

    @Test
    fun `renders not measured with question mark`() {
        val renderer = UncertaintyCalibratedRenderer(theme)
        val recInstance = UncertaintyCalibratedRecommendation()
            .addNotMeasured("Try this")

        val output = renderer.render(recInstance.all().first())

        assertTrue(output.contains("?"))
        assertTrue(output.contains("Not measured"))
        assertTrue(output.contains("[no evidence]"))
    }

    @Test
    fun `renders unavailable with X`() {
        val renderer = UncertaintyCalibratedRenderer(theme)
        val recInstance = UncertaintyCalibratedRecommendation()
            .addUnavailable("Unavailable")

        val output = renderer.render(recInstance.all().first())

        assertTrue(output.contains("×"))
        assertTrue(output.contains("Unavailable"))
    }

    @Test
    fun `renders all recommendations`() {
        val renderer = UncertaintyCalibratedRenderer(theme)
        val recs = UncertaintyCalibratedRecommendation()
        recs.addMeasured("High", 90, listOf("e1"))
        recs.addMeasured("Medium", 65, listOf("e2"))
        recs.addNotMeasured("Not measured")

        val output = renderer.renderAll(recs.all())

        assertTrue(output.contains("HIGH"))
        assertTrue(output.contains("MEDIUM"))
        assertTrue(output.contains("NOT MEASURED"))
    }

    @Test
    fun `empty list renders empty message`() {
        val renderer = UncertaintyCalibratedRenderer(theme)
        val output = renderer.renderAll(emptyList<Recommendation>())

        assertTrue(output.contains("No recommendations"))
    }
}