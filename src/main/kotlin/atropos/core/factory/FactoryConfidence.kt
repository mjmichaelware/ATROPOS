package atropos.core.factory

data class FactoryConfidence(
    val score: Int,
    val breakdown: String,
    val questions: List<String>
) {
    fun afterAnswers(answers: List<Boolean>): FactoryConfidence {
        require(answers.size == questions.size) {
            "one YES/NO answer is required per confidence question"
        }
        val uplift = answers.mapIndexed { index, answer ->
            if (!answer) 0 else 10 + index * 5
        }.sum()
        val resolvedScore = (score + uplift).coerceAtMost(100)
        return copy(
            score = resolvedScore,
            breakdown = "$breakdown,clarification_yes_uplift=$uplift",
            questions = if (resolvedScore >= MINIMUM) emptyList() else questions
        )
    }

    companion object {
        const val MINIMUM = 70

        fun calculate(spec: AppProjectSpec): FactoryConfidence {
            val clarity = if (spec.intent.name != "generated-app") 30 else 10
            val surface = if (spec.intent.kind.isNotBlank()) 25 else 0
            // An empty feature set means the request has no extracted primary
            // behavior. It must not reach the scaffold at the confidence
            // threshold merely because the surface defaults to CLI.
            val knowHow = if (spec.intent.features.isNotEmpty()) 25 else 0
            val gaps = if (spec.testRequired) 20 else 10
            val score = clarity + surface + knowHow + gaps
            val questions = if (score < MINIMUM) listOf(
                "Should the detected ${spec.intent.kind} surface be used?",
                "Does the prompt name a primary behavior to implement?"
            ) else emptyList()
            return FactoryConfidence(score, "clarity=$clarity,surface=$surface,know_how=$knowHow,gaps=$gaps", questions)
        }

        fun calculate(spec: AppProjectSpec, research: FactoryResearchReport): FactoryConfidence {
            val base = calculate(spec)
            val primaryChannels = setOf("st_memory", "lt_memory", "dloi", "lakehouse", "bounded_fetch")
            val channelLines = primaryChannels.associateWith { channel ->
                research.channelLog.lastOrNull { it.startsWith("$channel=") }
            }
            val researchPasses = channelLines.values.count { line ->
                line != null && line.startsWith("${line.substringBefore('=')}=PASS")
            }
            val observed = channelLines.values.count { it != null }
            val failures = channelLines.values.count { it?.contains("SKIPPED_SOFT_FAIL") == true }
            val health = if (observed == 0) 0 else ((observed - failures).toDouble() / observed * 100).toInt()
            // A strong app spec cannot conceal a failed research plane. The
            // score remains deterministic, but channel health is a hard
            // multiplier so the clarification path is reachable.
            val score = (base.score * health / 100 + researchPasses.coerceAtMost(2) * 5)
                .coerceAtMost(100)
            return base.copy(
                score = score,
                breakdown = "${base.breakdown},research_passes=$researchPasses,research_observed=$observed,research_failures=$failures,research_health=$health",
                questions = if (score < MINIMUM) base.questions else emptyList()
            )
        }
    }
}
