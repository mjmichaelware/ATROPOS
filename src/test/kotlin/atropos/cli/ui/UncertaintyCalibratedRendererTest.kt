/* SPDX-License-Identifier: AGPL-3.0-only */
package atropos.cli.ui

import atropos.cli.ui.design.ThemePalette
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class UncertaintyCalibratedRendererTest {
    private val theme = ThemePalette()

    @Test
    fun `renders measured confidence with badge`() {
        val renderer = UncertaintyCalibratedRenderer(theme)
        val rec = UncertaintyCalibratedRecommendation()
            .addMeasured("Use provider X", 85, listOf("evidence-1"))

        val output = renderer.render(rec.all()[0])

        assertTrue(output.contains("HIGH"))
        assertTrue(output.contains("evidence: evidence-1"))
        assertTrue(output.contains("Use provider X"))
    }

    @Test
    fun `renders not measured with question mark`() {
        val renderer = UncertaintyCalibratedRenderer(theme)
        val rec = UncertaintyCalibratedRecommendation()
            .addNotMeasured("Try this")

        val output = renderer.render(rec.all()[0])

        assertTrue(output.contains("?"))
        assertTrue(output.contains("Not measured"))
        assertTrue(output.contains("[no evidence]"))
    }

    @Test
    fun `renders unavailable with X`() {
        val renderer = UncertaintyCalibratedRenderer(theme)
        val rec = UncertaintyCalibratedRecommendation()
            .addUnavailable("Unavailable")

        val output = renderer.render(rec.all()[0])

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
        val output = renderer.renderAll(emptyList())

        assertTrue(output.contains("No recommendations"))
    }
}