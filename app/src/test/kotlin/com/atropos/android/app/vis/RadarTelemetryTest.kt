/* SPDX-License-Identifier: AGPL-3.0-only */
package com.atropos.android.app.vis

import com.atropos.android.app.bridge.AnswerDimension
import com.atropos.android.app.bridge.MobileSixAnswers
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class RadarTelemetryTest {

    @Test
    fun testRadarAxisNormalizationClampedBetweenZeroAndOne() {
        val axis = RadarAxis(
            label = "Evidence",
            valueNormalized = 1.0f,
            rawValue = "Attested SHA-256",
            health = "OPTIMAL"
        )

        assertEquals("Evidence", axis.label)
        assertTrue(axis.valueNormalized in 0.0f..1.0f)
        assertEquals("OPTIMAL", axis.health)
    }

    @Test
    fun testSixAnswersDefaultMappingPopulatesAllSixDimensions() {
        val mockSixAnswers = MobileSixAnswers(
            objective = AnswerDimension("Solve issue", "OPTIMAL"),
            doing = AnswerDimension("Parsing AST", "OPTIMAL"),
            why = AnswerDimension("Requirement invariant", "OPTIMAL"),
            progress = AnswerDimension("75%", "OPTIMAL"),
            next = AnswerDimension("Run test suite", "OPTIMAL"),
            evidence = AnswerDimension("All tests green", "OPTIMAL")
        )

        val axes = listOf(
            RadarAxis("Objective", 0.95f, mockSixAnswers.objective.value, mockSixAnswers.objective.health),
            RadarAxis("Doing", 0.70f, mockSixAnswers.doing.value, mockSixAnswers.doing.health),
            RadarAxis("Why", 0.85f, mockSixAnswers.why.value, mockSixAnswers.why.health),
            RadarAxis("Progress", 0.68f, mockSixAnswers.progress.value, mockSixAnswers.progress.health),
            RadarAxis("Next", 0.80f, mockSixAnswers.next.value, mockSixAnswers.next.health),
            RadarAxis("Evidence", 1.0f, mockSixAnswers.evidence.value, mockSixAnswers.evidence.health)
        )

        assertEquals(6, axes.size)
        assertEquals("Solve issue", axes[0].rawValue)
        assertEquals("All tests green", axes[5].rawValue)
        assertTrue(axes.all { it.health == "OPTIMAL" })
    }
}
