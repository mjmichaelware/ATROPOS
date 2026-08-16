/* SPDX-License-Identifier: AGPL-3.0-only */
package atropos.cli

import atropos.cli.ui.AnsiTerminalEngine
import atropos.core.AtroposRepoRootLocator
import atropos.core.storage.BlobStoreGc
import atropos.core.storage.EvidenceBundleGc
import atropos.core.storage.GcOutcome
import atropos.core.storage.RetentionPolicy
import atropos.core.storage.StorageReclaimer
import atropos.core.storage.StorageSupervisor
import atropos.core.storage.WorktreeGc
import atropos.core.worktree.WorktreeRecordStore
import java.nio.file.Path

/**
 * `/storage` — the operator's view of a resource that is finite on a phone.
 *
 * `SUP.STOR.GLOBAL-BYTE-CEILING` asks for "`atropos storage status` showing
 * usage vs ceiling per category" and `SUP.STOR.RETENTION-TIERS` for
 * "`atropos storage policy`". `SUP.STOR.WORKTREE-GC` adds an explicit
 * `atropos gc worktrees`.
 *
 * `gc` reports without deleting unless `--apply` is given. Deletion is the one
 * storage operation with no undo, and a collector that ran for real when the
 * operator typed it to *see what would happen* would destroy exactly the run
 * they were about to go looking for.
 */
class StorageCommandHandler(
    private val uiEngine: AnsiTerminalEngine,
    private val repoRoot: Path = AtroposRepoRootLocator.resolve(),
    private val supervisor: StorageSupervisor = StorageSupervisor(),
    private val policy: RetentionPolicy = RetentionPolicy(),
    /**
     * The blob store's collector.
     *
     * Constructed lazily rather than eagerly: [BlobStoreGc.defaultDriver]
     * creates its directory on the way in, and `/storage status` should not
     * bring a blob tree into existence on a repository that has never stored
     * one.
     */
    private val blobGc: () -> BlobStoreGc = {
        BlobStoreGc(BlobStoreGc.defaultDriver(repoRoot), policy)
    }
) {
    fun execute(tokens: List<String>): RouterOutcome {
        when (tokens.getOrNull(1)?.lowercase()) {
            null, "status" -> renderStatus()
            "policy" -> renderPolicy()
            "gc" -> renderGc(tokens)
            "verify" -> renderVerify()
            else -> uiEngine.renderError(USAGE)
        }
        return RouterOutcome.CONTINUE
    }

    /**
     * `SUP.STOR.INTEGRITY`: a store that cannot say whether what it holds is
     * still what was written is a store that has already lost data without
     * anyone noticing.
     */
    private fun renderVerify() {
        val report = blobGc().verifyIntegrity()
        val body = buildString {
            appendLine(report.render())
            report.auditLines().forEach { appendLine("  $it") }
        }.trimEnd()
        if (report.sound) uiEngine.renderNotice(body) else uiEngine.renderError(body)
    }

    private fun renderStatus() {
        val constitution = supervisor.constitution()
        uiEngine.renderNotice(
            buildString {
                appendLine("Storage")
                appendLine("  used     ${mib(constitution.usedBytes)} of ${mib(constitution.ceilingBytes)} " +
                    "(${(constitution.fractionUsed * 100).toInt()}%)")
                appendLine("  free     ${mib(constitution.remainingBytes)} before the declared ceiling")
                appendLine("  reclaim  ${mib(constitution.reclaimableBytes())} available to collect")
                if (constitution.classes.isEmpty()) {
                    appendLine("  (nothing stored yet)")
                } else {
                    appendLine()
                    constitution.classes.forEach { storageClass ->
                        val retention = supervisor.retentionClass(storageClass)
                        appendLine(
                            "  ${storageClass.id.padEnd(16)} ${mib(storageClass.bytes).padStart(10)}  " +
                                retention.tier.canonical
                        )
                    }
                }
            }.trimEnd()
        )
    }

    private fun renderPolicy() {
        uiEngine.renderNotice(
            buildString {
                appendLine("Retention policy")
                policy.declared().forEach { (name, rule) ->
                    appendLine("  ${name.padEnd(16)} ${rule.describe()}")
                }
                appendLine()
                appendLine("  Anything referenced by an open run, gate or fingerprint is never collected,")
                appendLine("  whatever its age and however full the disk is.")
            }.trimEnd()
        )
    }

    private fun renderGc(tokens: List<String>) {
        val apply = tokens.any { it.equals("--apply", ignoreCase = true) }
        val target = tokens.getOrNull(2)?.lowercase()?.takeUnless { it.startsWith("--") }
        val pressure = supervisor.pressure()
        val reclaimer = StorageReclaimer(repoRoot.resolve(StorageSupervisor.STATE_DIR))

        val outcomes = buildList {
            if (target == null || target == "worktrees") {
                add(
                    WorktreeGc(
                        WorktreeRecordStore(repoRoot.resolve(WORKTREE_DIR)),
                        reclaimer,
                        policy
                    ).collect(pressure = pressure, dryRun = !apply)
                )
            }
            if (target == null || target == "evidence") {
                add(
                    EvidenceBundleGc(
                        repoRoot.resolve(EVIDENCE_DIR),
                        reclaimer,
                        policy
                    ).collect(pressure = pressure, dryRun = !apply)
                )
            }
            if (target == null || target == "blobs") {
                add(blobGc().collect(pressure = pressure, dryRun = !apply))
            }
        }

        if (outcomes.isEmpty()) {
            uiEngine.renderError(USAGE)
            return
        }
        uiEngine.renderNotice(render(outcomes, apply))
    }

    private fun render(outcomes: List<GcOutcome>, applied: Boolean): String = buildString {
        appendLine(if (applied) "Collected" else "Dry run — nothing was deleted")
        outcomes.forEach { outcome ->
            appendLine("  ${outcome.render()}")
            outcome.auditLines().forEach { appendLine("    $it") }
        }
        if (!applied) {
            appendLine()
            appendLine("  Re-run with --apply to delete.")
        }
    }.trimEnd()

    private fun mib(bytes: Long): String =
        if (bytes < 1024 * 1024) "${bytes}B" else "${bytes / (1024 * 1024)}MiB"

    private companion object {
        const val WORKTREE_DIR = ".atropos/worktrees"
        const val EVIDENCE_DIR = ".atropos/evidence"
        const val USAGE =
            "usage: /storage [status|policy|verify|gc [worktrees|evidence|blobs] [--apply]]"
    }
}
