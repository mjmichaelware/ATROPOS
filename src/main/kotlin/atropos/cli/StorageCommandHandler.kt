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
    private val renderer = atropos.cli.ui.StatusStorageRenderer()

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

    private fun renderVerify() {
        val report = blobGc().verifyIntegrity()
        val lines = renderer.renderVerify(report, uiEngine.viewportWidth)
        if (report.sound) uiEngine.renderBlock(lines) else uiEngine.renderError(lines.joinToString("\n"))
    }

    private fun renderStatus() {
        val constitution = supervisor.constitution()
        uiEngine.renderBlock(renderer.renderStatus(constitution, supervisor, uiEngine.viewportWidth))
    }

    private fun renderPolicy() {
        uiEngine.renderBlock(renderer.renderPolicy(policy, uiEngine.viewportWidth))
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
        uiEngine.renderBlock(renderer.renderGc(outcomes, apply, uiEngine.viewportWidth))
    }

    private companion object {
        const val WORKTREE_DIR = ".atropos/worktrees"
        const val EVIDENCE_DIR = ".atropos/evidence"
        const val USAGE =
            "usage: /storage [status|policy|verify|gc [worktrees|evidence|blobs] [--apply]]"
    }
}
