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

        /**
         * The ceiling on what one clarification round may ask.
         *
         * An operator answering a prompt will answer three questions. Past
         * that the round stops being a clarification and becomes a form, and
         * the answers get worse rather than better.
         */
        const val MAX_QUESTIONS = 3

        /**
         * The most confidence a dark research plane may cost.
         *
         * Held below the gap between a fully specified prompt's score and
         * [MINIMUM], so an offline device slows the factory down without
         * stopping it.
         */
        const val MAX_RESEARCH_PENALTY = 15

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

            // A bounded penalty, not a multiplier over the whole score.
            //
            // A soft-failed channel is soft by construction: the research plane
            // records SKIPPED_SOFT_FAIL precisely because a channel being
            // unreachable is an expected state, not a fault in the request. On
            // a device with no network — the aarch64 Termux target — most of
            // these channels never answer, and multiplying the spec's own
            // clarity by channel availability sent every fully specified prompt
            // to a clarification round that could not tell the operator
            // anything they did not already know: their device is offline.
            //
            // The penalty is still real, so a research plane that is entirely
            // dark cannot be ignored, and it is capped below the threshold gap
            // so it can lower confidence in a weak spec without ever being the
            // sole reason a strong one stops.
            val researchPenalty = when {
                observed == 0 -> MAX_RESEARCH_PENALTY
                else -> (failures * 5).coerceAtMost(MAX_RESEARCH_PENALTY)
            }
            val score = (base.score - researchPenalty + researchPasses.coerceAtMost(2) * 5)
                .coerceIn(0, 100)

            // Every below-threshold score must arrive with something to ask.
            // `base.questions` alone could not carry that: a strong spec scores
            // above MINIMUM and returns no questions, and the research penalty
            // can still drop the total below it — leaving a clarification with
            // nothing in it, which the persister rightly refuses. When research
            // is the only thing that pulled the score down, research is what to
            // ask about.
            val questions = when {
                score >= MINIMUM -> emptyList()
                base.questions.isNotEmpty() -> base.questions.take(MAX_QUESTIONS)
                observed == 0 -> listOf("No research channel answered. Proceed on the prompt alone?")
                else -> listOf(
                    "$failures of $observed research channels did not answer. " +
                        "Proceed with what is available?"
                )
            }
            return base.copy(
                score = score,
                breakdown = "${base.breakdown},research_passes=$researchPasses,research_observed=$observed," +
                    "research_failures=$failures,research_health=$health,research_penalty=$researchPenalty",
                questions = questions
            )
        }
    }
}
