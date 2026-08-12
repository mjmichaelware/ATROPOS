/* SPDX-License-Identifier: AGPL-3.0-only */
package atropos.core.interrupt

import atropos.core.AtroposRepoRootLocator
import java.nio.file.Path
import java.time.Instant

/**
 * The one interrupt state a process has, and the freeze that outlives it.
 *
 * `SUP.UX.INTERRUPT-PRIMITIVE` requires the CLI, the status matrix and the
 * running loop to agree about whether an interrupt is pending. Each holding its
 * own [InterruptController] would mean `/pause` set a flag that the loop never
 * read — the command would appear to work and nothing would stop.
 *
 * A process-wide singleton is the right shape here precisely because the thing
 * being modelled *is* process-wide: there is one operator pressing one key, and
 * "which run did they mean" is not a question a second controller would answer
 * any better.
 */
object InterruptRegistry {

    private const val FROZEN_FILE = ".atropos/recovery/frozen-run.tsv"

    val controller: InterruptController = InterruptController()

    private var store: FrozenRunStore = FrozenRunStore(
        AtroposRepoRootLocator.resolve().resolve(FROZEN_FILE)
    )

    /** Points the durable half at another location. For tests and for Termux. */
    fun useStore(path: Path) {
        store = FrozenRunStore(path)
    }

    fun frozen(): FrozenRun? = store.read()

    /**
     * Asks the running loop to stop.
     *
     * Returns immediately. The loop stops at its own next boundary, and
     * [InterruptController.state] reports the difference between asked and
     * stopped until it does.
     */
    fun request(level: InterruptLevel, requestedBy: String): InterruptState =
        controller.request(level, requestedBy)

    /**
     * Takes a pending interrupt and, for a freeze, makes the position durable.
     *
     * The durable write happens *before* the interrupt is marked taken. If the
     * order were reversed, a crash between the two would leave a run marked
     * stopped with no record of where — the exact state a freeze exists to make
     * impossible.
     */
    fun take(runId: String, resumePoint: String?, evidencePaths: List<String> = emptyList()): InterruptOutcome {
        val pending = controller.state().requested
            ?: return InterruptOutcome.Refused("no interrupt was requested")

        if (pending.level == InterruptLevel.FREEZE) {
            if (resumePoint.isNullOrBlank()) {
                return InterruptOutcome.Refused(
                    "a freeze needs a resume point; the run reported none"
                )
            }
            val written = store.freeze(
                FrozenRun(runId, resumePoint, pending.level, Instant.now(), evidencePaths)
            )
            if (!written) {
                return InterruptOutcome.Refused(
                    "the freeze could not be written durably, so the run was left running"
                )
            }
        }
        return controller.take(resumePoint)
    }

    /**
     * Consumes the frozen record so a run can continue from it.
     *
     * The record is cleared only after the caller has it in hand, so a resume
     * that fails part-way leaves the position on disk to be tried again.
     */
    fun resume(): FrozenRun? {
        val record = store.read() ?: return null
        controller.clear()
        store.clear()
        return record
    }

    /** The line the status matrix shows. */
    fun render(): String {
        val frozen = store.read()
        val live = controller.state().render()
        return if (frozen == null) live else "$live; frozen: ${frozen.render()}"
    }
}
