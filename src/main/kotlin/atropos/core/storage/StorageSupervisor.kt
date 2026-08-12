/* SPDX-License-Identifier: AGPL-3.0-only */
package atropos.core.storage

import atropos.core.AtroposRepoRootLocator
import java.nio.file.Path

/**
 * The one thing callers ask "may I write this?".
 *
 * `SUP.STOR.FREE-SPACE-GATE`: "Wire as first check inside BoundedAgencyGate for
 * any FILE_MUTATION or WORKTREE action" — and the predicate,
 * `P(disk-full-crash)=0` under continuous operation.
 *
 * Three separate limits bind, and a caller that had to know about all three
 * would eventually check two. Composed here so a call site asks once:
 *
 * - the **device** will refuse a write when it is genuinely out of blocks
 *   ([FreeSpaceProbe]);
 * - the **declared ceiling** is the operator's promise about how much of the
 *   phone ATROPOS may occupy ([StorageConstitution]);
 * - the **bands** decide when a permitted write should still warn
 *   ([FreeSpaceGate]).
 *
 * Every refusal carries what could be freed, because §4.1 requires a failure to
 * state what to do about it — and on a phone "out of space" without a next step
 * is where an autonomous run stops for good.
 */
class StorageSupervisor(
    private val stateRoot: Path = AtroposRepoRootLocator.resolve().resolve(STATE_DIR),
    private val ceilingBytes: Long = DEFAULT_CEILING_BYTES,
    private val gate: FreeSpaceGate = FreeSpaceGate(),
    private val probe: FreeSpaceProbe = FreeSpaceProbe(stateRoot),
    private val accountant: StorageAccountant = StorageAccountant(stateRoot)
) {
    /** The current picture, for `/storage status` and for the gate. */
    fun constitution(): StorageConstitution = accountant.measure(ceilingBytes)

    fun pressure(): Double = constitution().fractionUsed

    /**
     * Whether a write of [bytes] may proceed.
     *
     * The device is checked first. A ceiling with headroom left is irrelevant
     * when the filesystem itself will reject the write, and reporting the
     * ceiling's opinion in that case would send the operator to free ATROPOS
     * data when the problem is somewhere else on the device entirely.
     */
    fun admit(bytes: Long): FreeSpaceDecision {
        val constitution = constitution()

        if (probe.wouldExhaustDevice(bytes)) {
            val usable = probe.usableBytes()
            return FreeSpaceDecision.Refused(
                reason = if (usable == null) {
                    "the filesystem holding $stateRoot could not be read, so free space is unknown"
                } else {
                    "the device has ${usable}B usable and this write needs ${bytes}B " +
                        "plus ${FreeSpaceProbe.DEFAULT_RESERVE_BYTES}B of reserve"
                },
                reclaimableBytes = constitution.reclaimableBytes(),
                emergency = true
            )
        }

        return gate.evaluate(constitution, bytes)
    }

    /** What a refusal suggests freeing, largest first. */
    fun reclaimTargets(): List<StorageClass> = constitution().reclaimable()

    companion object {
        const val STATE_DIR: String = ".atropos"

        /**
         * `SUP.STOR.GLOBAL-BYTE-CEILING` sets "1–4 GB usable after system" as
         * the phone profile. 2 GiB is the middle of that band: enough for days
         * of autonomous work with evidence retained, and small enough that a
         * 64 GB phone with photos on it stays usable.
         *
         * The operator may raise it. The atom asks for that to be an explicit
         * confirmation, which is the caller's job — this is only the default.
         */
        const val DEFAULT_CEILING_BYTES: Long = 2L * 1024 * 1024 * 1024
    }
}
