/* SPDX-License-Identifier: AGPL-3.0-only */
package atropos.core.evaluation

/**
 * The evaluation report as text, for a terminal and for a machine.
 *
 * Source Doc 3 item 65: "Evaluation CLI and dashboard — results available in
 * terminal, web, machine-readable, and human-readable forms." Both forms are
 * produced from one classification pass so the CLI and any surface consuming
 * the JSON cannot show different verdicts for the same run — which they would
 * if each rendered from its own computation.
 *
 * Ordered worst-first. An evaluation is read when something is wrong, and a
 * report that opens with twelve passing metrics buries the one that is not.
 * Unmeasured metrics come last and are never hidden: item 62 forbids
 * unsupported percentages, and a metric quietly dropped from a dashboard is the
 * same failure with better manners.
 */
class EvaluationDashboard(
    private val classifier: ClassificationCalculator = ClassificationCalculator()
) {

    /** Human-readable, for the terminal. */
    fun render(
        metrics: List<AtroposMetric>,
        benchmarks: List<BenchmarkResult> = emptyList(),
        coverage: BenchmarkCoverage? = null
    ): String {
        val run = classifier.classifyAll(metrics)
        return buildString {
            appendLine("ATROPOS EVALUATION")
            appendLine()
            appendLine("verdict            ${run.overall.label}")
            run.tierVerdicts().forEach { (tier, met) ->
                appendLine("${tier.padEnd(18)} ${if (met) "met" else "NOT MET"}")
            }
            appendLine("measured           ${metrics.count { !it.unmeasured }} of ${metrics.size}")
            appendLine("supported          ${metrics.count { it.supported }} of ${metrics.size}")
            appendLine()

            appendSection(this, "BLOCKING", run.blocking())
            appendSection(this, "OFF TARGET", run.metrics.filter {
                it.classification == ReleaseClassification.SCORE_REDUCTION
            })
            appendSection(this, "ON TARGET", run.metrics.filter {
                it.classification == ReleaseClassification.PASS
            })

            val unmeasured = metrics.filter { it.unmeasured }
            if (unmeasured.isNotEmpty()) {
                appendLine("UNMEASURED (${unmeasured.size})")
                unmeasured.forEach { appendLine("  ${it.id.canonical}: ${it.detail}") }
                appendLine()
            }

            if (benchmarks.isNotEmpty()) {
                appendLine("BENCHMARKS")
                coverage?.let { appendLine("  ${it.render()}") }
                benchmarks.forEach { result ->
                    appendLine("  " + if (result.notRun) "${result.id.displayName}: not run" else result.render())
                }
            }
        }.trimEnd()
    }

    /**
     * Machine-readable, for a web surface or an external consumer.
     *
     * Hand-written for the same reason [atropos.core.observability.JsonExporter]
     * is: the engine carries no serialization dependency, and the wire shape
     * should be a decision in a file rather than a consequence of field order.
     */
    fun renderJson(
        metrics: List<AtroposMetric>,
        benchmarks: List<BenchmarkResult> = emptyList()
    ): String {
        val run = classifier.classifyAll(metrics)
        return buildString {
            appendLine("{")
            appendLine("  \"verdict\": \"${run.overall.name}\",")
            appendLine("  \"blocksRelease\": ${run.blocksRelease},")
            run.tierVerdicts().forEach { (tier, met) -> appendLine("  \"$tier\": $met,") }
            appendLine("  \"metrics\": [")
            run.metrics.forEachIndexed { index, classification ->
                val metric = classification.metric
                appendLine("    {")
                appendLine("      \"id\": \"${metric.id.canonical}\",")
                appendLine("      \"value\": ${number(metric.value)},")
                appendLine("      \"score\": ${number(classification.score)},")
                appendLine("      \"target\": ${metric.id.target},")
                appendLine("      \"direction\": \"${metric.id.direction.name}\",")
                appendLine("      \"sampleSize\": ${metric.sampleSize},")
                appendLine("      \"supported\": ${metric.supported},")
                appendLine("      \"unmeasured\": ${metric.unmeasured},")
                appendLine("      \"classification\": \"${classification.classification.name}\",")
                appendLine("      \"evidenceHashes\": [${metric.evidenceHashes.joinToString(", ") { "\"$it\"" }}],")
                appendLine("      \"reason\": \"${escape(classification.reason)}\"")
                appendLine(if (index == run.metrics.lastIndex) "    }" else "    },")
            }
            appendLine("  ],")
            appendLine("  \"benchmarks\": [")
            benchmarks.forEachIndexed { index, result ->
                appendLine("    {")
                appendLine("      \"id\": \"${result.id.canonical}\",")
                appendLine("      \"score\": ${number(result.score)},")
                appendLine("      \"notRun\": ${result.notRun},")
                appendLine("      \"competitive\": ${!result.notRun && result.competitive},")
                appendLine("      \"reproducible\": ${result.reproducible},")
                appendLine("      \"benchmarkVersion\": \"${escape(result.benchmarkVersion)}\",")
                appendLine("      \"sourceFingerprint\": \"${escape(result.sourceFingerprint)}\"")
                appendLine(if (index == benchmarks.lastIndex) "    }" else "    },")
            }
            appendLine("  ]")
            appendLine("}")
        }
    }

    private fun appendSection(builder: StringBuilder, heading: String, rows: List<MetricClassification>) {
        if (rows.isEmpty()) return
        builder.appendLine("$heading (${rows.size})")
        rows.sortedByDescending { it.classification.severity }.forEach { row ->
            builder.appendLine("  " + row.render())
        }
        builder.appendLine()
    }

    /** `NaN` is not valid JSON, so an unmeasured value is `null`. */
    private fun number(value: Double): String =
        if (value.isNaN() || value.isInfinite()) "null" else "%.6f".format(value).trimEnd('0').trimEnd('.')

    private fun escape(value: String): String =
        value.replace("\\", "\\\\").replace("\"", "\\\"").replace("\n", " ")
}
