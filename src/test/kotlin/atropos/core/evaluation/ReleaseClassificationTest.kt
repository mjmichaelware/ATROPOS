package atropos.core.evaluation

import kotlin.test.Test
import kotlin.test.assertEquals

class ReleaseClassificationTest {
    @Test
    fun `classify returns PASS when empty`() {
        val classifier = ReleaseClassifier()
        val result = classifier.classify(emptyList())
        assertEquals(ReleaseClassification.PASS, result)
    }

    @Test
    fun `classify detects safety hard failure`() {
        val classifier = ReleaseClassifier()
        val safetyMetric = AtroposMetric(MetricId.SECRET_SAFETY, 1.0, 1)
        val result = classifier.classify(listOf(safetyMetric))
        assertEquals(ReleaseClassification.SAFETY_HARD_FAILURE, result)
    }

    @Test
    fun `classify maps scores to correct thresholds`() {
        val thresholds = mapOf(
            MetricId.BATCH_COMPLETION_RATE to ReleaseThresholds(0.5, 0.75, 0.95)
        )
        val classifier = ReleaseClassifier(thresholds)

        // score = 0.40 -> below minimum (0.50)
        val metricMin = AtroposMetric(MetricId.BATCH_COMPLETION_RATE, 0.40, 10)
        assertEquals(ReleaseClassification.MINIMUM_FAILURE, classifier.classify(listOf(metricMin)))

        // score = 0.80 -> between competitive (0.75) and frontier (0.95)
        val metricFrontier = AtroposMetric(MetricId.BATCH_COMPLETION_RATE, 0.80, 10)
        assertEquals(ReleaseClassification.FRONTIER_FAILURE, classifier.classify(listOf(metricFrontier)))
    }
}
