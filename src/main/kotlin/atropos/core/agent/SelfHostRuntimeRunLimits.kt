/* SPDX-License-Identifier: AGPL-3.0-only */
package atropos.core.agent

/**
 * How many times the self-host loop may continue before it stops on its own.
 *
 * This used to be the constant 25, and a constant is the wrong shape for it.
 * The budget is spent one advance at a time against a DAG, so the only number
 * that means anything is the size of that DAG. Twenty-five is generous for a
 * three-node bootstrap graph and stops a four-hundred-atom document at six
 * percent of its work -- with no error, because stopping at the budget is a
 * normal exit. The operator sees a run that "finished" having built almost
 * nothing.
 *
 * It also read as a claim about the document. `advance 3 of 25` after attaching
 * a large specification looks exactly like "the atomizer found 25 things", and
 * that is how it was read.
 *
 * So: derive it. The ceiling and the operator override stay, because a derived
 * number still has to be bounded and a human still has to be able to say
 * otherwise.
 */
object SelfHostRuntimeRunLimits {

    /**
     * Advances allowed per node.
     *
     * More than one because a node is not always finished in a single advance:
     * it can need a retry, or a recovery, or an evaluation pass after the edit.
     * Three is the observed worst case for a node that succeeds -- propose,
     * apply, evaluate -- and a node needing more than that is not making
     * progress, which is what the budget exists to catch.
     */
    const val ADVANCES_PER_NODE = 3

    /**
     * Advances that belong to the run rather than to any node: starting the
     * goal, the final evaluation, and the two automatic recoveries the runner
     * allows itself.
     */
    const val RUN_OVERHEAD_ADVANCES = 6

    /** What a run gets when nothing has told it how much work there is. */
    const val FALLBACK_MAX_ADVANCES = 25

    /**
     * The hard ceiling.
     *
     * A budget derived from node count would otherwise be unbounded, and an
     * unbounded autonomous loop on someone's phone is not a budget at all.
     */
    const val MAX_ALLOWED_ADVANCES = 4_000

    /**
     * The operator's override, when they set one.
     *
     * Checked before the derivation, because a person who names a number has
     * more context than this arithmetic does.
     */
    fun override(
        environment: Map<String, String> = System.getenv(),
        properties: (String) -> String? = System::getProperty
    ): Int? = (environment["ATROPOS_SELF_HOST_MAX_ADVANCES"]
        ?: properties("atropos.selfHost.maxAdvances"))
        ?.toIntOrNull()
        ?.coerceIn(1, MAX_ALLOWED_ADVANCES)

    /**
     * The budget for a run against a DAG of [nodeCount] nodes.
     *
     * [nodeCount] of zero means the DAG is not known yet -- not that there is
     * no work -- so it falls back rather than returning a budget of six that
     * would end the run before it started.
     */
    fun forNodeCount(
        nodeCount: Int,
        environment: Map<String, String> = System.getenv(),
        properties: (String) -> String? = System::getProperty
    ): Int {
        override(environment, properties)?.let { return it }
        if (nodeCount <= 0) return FALLBACK_MAX_ADVANCES
        return (nodeCount * ADVANCES_PER_NODE + RUN_OVERHEAD_ADVANCES)
            .coerceIn(1, MAX_ALLOWED_ADVANCES)
    }

    @Deprecated(
        "A budget with no DAG to measure cannot be right. Use forNodeCount.",
        ReplaceWith("forNodeCount(nodeCount, environment, properties)")
    )
    fun maxAdvances(
        environment: Map<String, String> = System.getenv(),
        properties: (String) -> String? = System::getProperty
    ): Int = forNodeCount(0, environment, properties)
}
