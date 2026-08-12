/* SPDX-License-Identifier: AGPL-3.0-only */
package atropos.core.recovery

/**
 * What startup is allowed to do about work left behind by a previous process.
 *
 * Two different things used to happen under one word. Repair — releasing a
 * stale lease, marking a crashed run as crashed — has to be automatic, because
 * leaving durable state inconsistent is worse than any surprise it could
 * cause. Continuation — actually resuming a goal and letting it act — is a
 * decision, and doing it because the process happened to start means the
 * operator opens the runtime and finds it already working on something they
 * did not ask for in this session.
 *
 * So repair stays automatic and continuation becomes explicit. Startup reports
 * what is resumable; `/agent self-host recover` resumes it.
 */
enum class StartupContinuationPolicy {
    /** Repair durable state, report what could be resumed, resume nothing. */
    ANNOUNCE_ONLY,

    /** Repair and immediately continue. Only for an unattended runner. */
    CONTINUE_AUTOMATICALLY;

    companion object {
        /**
         * The default is [ANNOUNCE_ONLY].
         *
         * `ATROPOS_AUTO_CONTINUE=1` opts back in, for a daemon or CI runner
         * where no one is present to type the command. An environment variable
         * rather than config because it is a property of how the process was
         * launched, not of the project.
         */
        const val AUTO_CONTINUE_VARIABLE = "ATROPOS_AUTO_CONTINUE"

        fun fromEnvironment(environment: (String) -> String? = System::getenv): StartupContinuationPolicy =
            when (environment(AUTO_CONTINUE_VARIABLE)?.trim()) {
                "1", "true", "TRUE", "yes" -> CONTINUE_AUTOMATICALLY
                else -> ANNOUNCE_ONLY
            }
    }
}

/** What startup found, and what it did about it. */
data class StartupContinuationReport(
    val resumable: Boolean,
    val continued: Boolean,
    val message: String?
)

/**
 * Applies the policy. Separated from [RuntimeContinuitySupervisor] because that
 * owns *repairing* durable state and this owns *deciding whether to act on it*;
 * fusing them is what made the decision invisible.
 */
class StartupContinuationDecider(
    private val policy: StartupContinuationPolicy = StartupContinuationPolicy.fromEnvironment()
) {
    fun decide(resumable: Boolean): StartupContinuationReport = when {
        !resumable -> StartupContinuationReport(
            resumable = false,
            continued = false,
            message = null
        )

        policy == StartupContinuationPolicy.CONTINUE_AUTOMATICALLY -> StartupContinuationReport(
            resumable = true,
            continued = true,
            message = "resuming interrupted work automatically ($AUTO_CONTINUE_NOTE)"
        )

        else -> StartupContinuationReport(
            resumable = true,
            continued = false,
            message = "interrupted work is resumable. Run `/agent self-host recover` to continue it."
        )
    }

    private companion object {
        const val AUTO_CONTINUE_NOTE = "ATROPOS_AUTO_CONTINUE is set"
    }
}
