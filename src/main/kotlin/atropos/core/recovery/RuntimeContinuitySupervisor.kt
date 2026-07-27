/* SPDX-License-Identifier: AGPL-3.0-only */
package atropos.core.recovery

import java.util.concurrent.atomic.AtomicBoolean

/** What automatic continuity did on this start. */
sealed interface ContinuityOutcome {
    /** Recovery ran. [report] carries what it found. */
    data class Recovered(val report: RecoveryReport) : ContinuityOutcome

    /** Recovery already ran in this process; nothing to do. */
    data object AlreadyRecovered : ContinuityOutcome

    /**
     * Recovery could not run at all.
     *
     * Surfaced rather than swallowed: an operator who does not know recovery
     * failed will assume stale claims were cleared when they were not.
     */
    data class Failed(val reason: String) : ContinuityOutcome

    /** True when there was durable state to repair. */
    val repairedSomething: Boolean
        get() = this is Recovered && with(report) {
            staleQueueEntries + staleSessions + staleDagClaims + interruptedRuns > 0
        }
}

/**
 * Runs crash recovery once per process, at startup.
 *
 * [CrashRecoveryService] was already real — stale queue leases, dead sessions,
 * stale DAG claims, interrupted runs — but it only ever ran when an operator
 * typed `/agent recover`. Long-horizon continuity that depends on someone
 * remembering to ask for it is not continuity.
 *
 * This is a composition owner, deliberately above both services rather than
 * beside them: it depends on [CrashRecoveryService], which depends on
 * `GoalContinuationService`. Nothing points back. Putting the trigger inside
 * either service would recreate the mutual default construction that the
 * completion gate had to be untangled from.
 *
 * Recovery is a global sweep, so it runs exactly once. Re-sweeping on every
 * command would re-examine state the process itself is actively changing.
 */
class RuntimeContinuitySupervisor(
    /**
     * The sweep. Injected as a function so a caller can supervise a differently
     * rooted service, and so this can be exercised without standing up the whole
     * recovery dependency graph.
     */
    private val recover: () -> RecoveryReport = { CrashRecoveryService().recover() }
) {
    private val alreadyRan = AtomicBoolean(false)

    /**
     * Ensures durable state is consistent before the runtime serves anything.
     *
     * A failure here does not stop startup — refusing to run because a stale
     * lease could not be cleared would leave the operator with no way to fix it
     * — but it is reported, never hidden.
     */
    fun ensureRecovered(): ContinuityOutcome {
        if (!alreadyRan.compareAndSet(false, true)) return ContinuityOutcome.AlreadyRecovered

        return try {
            ContinuityOutcome.Recovered(recover())
        } catch (failure: Exception) {
            ContinuityOutcome.Failed(
                "${failure.javaClass.simpleName}: ${failure.message ?: "crash recovery could not run"}"
            )
        }
    }

    /**
     * One line for startup, or `null` when there is nothing worth saying.
     *
     * A clean start stays quiet; repairs and failures do not.
     */
    fun startupNotice(outcome: ContinuityOutcome): String? = when (outcome) {
        is ContinuityOutcome.Failed -> "continuity: crash recovery did not run — ${outcome.reason}"
        is ContinuityOutcome.AlreadyRecovered -> null
        is ContinuityOutcome.Recovered -> outcome.report.takeIf { outcome.repairedSomething }?.let {
            "continuity: recovered ${it.staleQueueEntries} queue, ${it.staleSessions} session, " +
                "${it.staleDagClaims} dag claim, ${it.interruptedRuns} interrupted run(s)" +
                if (it.errors.isEmpty()) "" else " (${it.errors.size} error(s): ${it.errors.first()})"
        }
    }
}
