/* SPDX-License-Identifier: AGPL-3.0-only */
package atropos.cli.ui

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue
import kotlin.test.assertFalse

class UncertaintyCalibratedRecommendationTest {

    @Test
    fun `confidence bands from percent`() {
        assertEquals(UncertaintyCalibratedRecommendation.Confidence.Band.HIGH, 
            UncertaintyCalibratedRecommendation.Confidence.fromPercent(80).band)
        assertEquals(UncertaintyCalibratedRecommendation.Confidence.Band.HIGH,
            UncertaintyCalibratedRecommendation.Confidence.fromPercent(95).band)
        assertEquals(UncertaintyCalibratedRecommendation.Confidence.Band.MEDIUM,
            UncertaintyCalibratedRecommendation.Confidence.fromPercent(50).band)
        assertEquals(UncertaintyCalibratedRecommendation.Confidence.Band.MEDIUM,
            UncertaintyCalibratedRecommendation.Confidence.fromPercent(79).band)
        assertEquals(UncertaintyCalibratedRecommendation.Confidence.Band.LOW,
            UncertaintyCalibratedRecommendation.Confidence.fromPercent(49).band)
        assertEquals(UncertaintyCalibratedRecommendation.Confidence.Band.LOW,
            UncertaintyCalibratedRecommendation.Confidence.fromPercent(0).band)
    }

    @Test
    fun `recommendation with measured confidence`() {
        val rec = UncertaintyCalibratedRecommendation()
            .addMeasured("Use provider X", 85, listOf("evidence-1", "evidence-2"))

        assertEquals("85% (High (≥80%))", rec.confidenceDisplay)
        assertTrue(rec.actionable)
        assertEquals(listOf("evidence-1", "evidence-2"), rec.evidenceIds)
    }

    @Test
    fun `recommendation with low confidence not actionable`() {
        val rec = UncertaintyCalibratedRecommendation()
            .addMeasured("Try provider Y", 45, listOf("evidence-1"))

        assertFalse(rec.actionable)
    }

    @Test
    fun `recommendation without evidence not actionable even with high confidence`() {
        val rec = UncertaintyCalibratedRecommendation()
            .addMeasured("Try provider Z", 90, emptyList())

        assertFalse(rec.actionable)
    }

    @Test
    fun `not measured recommendation not actionable`() {
        val rec = UncertaintyCalibratedRecommendation()
            .addNotMeasured("Try this approach")

        assertEquals("Not measured — treat as guess", rec.confidenceDisplay)
        assertFalse(rec.actionable)
    }

    @Test
    fun `unavailable recommendation not actionable`() {
        val rec = UncertaintyCalibratedRecommendation()
            .addUnavailable("Provider status unknown")

        assertEquals("Unavailable — no evidence", rec.confidenceDisplay)
        assertFalse(rec.actionable)
    }

    @Test
    fun `render all formats recommendations`() {
        val recs = UncertaintyCalibratedRecommendation()
        recs.addMeasured("High confidence", 90, listOf("e1"))
        recs.addMeasured("Medium confidence", 65, listOf("e2"))
        recs.addMeasured("Low confidence", 30, listOf("e3"))
        recs.addNotMeasured("Not measured")
        recs.addUnavailable("Unavailable")

        val output = recs.renderAll()

        assertTrue(output.contains("HIGH"))
        assertTrue(output.contains("MEDIUM"))
        assertTrue(output.contains("LOW"))
        assertTrue(output.contains("NOT MEASURED"))
        assertTrue(output.contains("UNAVAILABLE"))
    }

    @Test
    fun `filter by category`() {
        val recs = UncertaintyCalibratedRecommendation()
        recs.addMeasured("Provider advice", 80, emptyList(), UncertaintyCalibratedRecommendation.Category.PROVIDER)
        recs.addMeasured("Verification advice", 75, emptyList(), UncertaintyCalibratedRecommendation.Category.VERIFICATION)

        val providerRecs = recs.byCategory(UncertaintyCalibratedRecommendation.Category.PROVIDER)
        val verificationRecs = recs.byCategory(UncertaintyCalibratedRecommendation.Category.VERIFICATION)

        assertEquals(1, providerRecs.size)
        assertEquals(1, verificationRecs.size)
        assertEquals("Provider advice", providerRecs[0].text)
    }

    @Test
    fun `actionable filters only actionable`() {
        val recs = UncertaintyCalibratedRecommendation()
        recs.addMeasured("Actionable", 90, listOf("e1"))
        recs.addMeasured("Not actionable - low confidence", 30, listOf("e2"))
        recs.addNotMeasured("Not measured")

        val actionable = recs.actionable()

        assertEquals(1, actionable.size)
        assertEquals("Actionable", actionable[0].text)
    }

    @Test
    fun `clear removes all recommendations`() {
        val recs = UncertaintyCalibratedRecommendation()
        recs.addMeasured("Test", 80, emptyList())
        recs.clear()

        assertTrue(recs.all().isEmpty())
    }
}