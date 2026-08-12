/* SPDX-License-Identifier: AGPL-3.0-only */
package atropos.cli.commands

import atropos.core.multimodal.SnapshotKind
import atropos.core.multimodal.SnapshotService

/**
 * `/snapshot` — Phase 17 capture, compare, and list.
 *
 * Content hashes are shown truncated because they are used here for recognition
 * — telling two snapshots apart at a glance — not for verification. Anything
 * verifying a snapshot reads the full hash from the record rather than from a
 * terminal line.
 */
class SnapshotCommandHandler(
    private val snapshotService: SnapshotService = SnapshotService()
) {
    fun handle(args: List<String>): String = when (args.firstOrNull()) {
        "capture" -> capture(args)
        "compare" -> compare(args)
        "list" -> list(args)
        else -> "Snapshot service: ${snapshotService.listSnapshots().size} snapshots recorded"
    }

    private fun capture(args: List<String>): String = when (args.getOrNull(1)?.lowercase()) {
        "terminal" -> {
            val source = args.getOrNull(2) ?: "default"
            val reference = snapshotService.captureTerminal("", source)
            "terminal snapshot: ${reference.id} hash=${reference.contentHash.take(HASH_PREVIEW)}"
        }

        "file" -> {
            if (args.size < 3) {
                "usage: /snapshot capture file <path>"
            } else {
                // A missing or unreadable path is an ordinary operator mistake,
                // not a reason to unwind the command loop.
                runCatching { snapshotService.captureFile(args[2]) }.fold(
                    onSuccess = { reference ->
                        "file snapshot: ${reference.id} hash=${reference.contentHash.take(HASH_PREVIEW)} " +
                            "bytes=${reference.byteSize}"
                    },
                    onFailure = { failure -> "snapshot error: ${failure.message}" }
                )
            }
        }

        else -> "usage: /snapshot capture terminal|file [source]"
    }

    private fun compare(args: List<String>): String {
        if (args.size < 3) return "usage: /snapshot compare <left-id> <right-id>"
        val result = snapshotService.compareSnapshots(args[1], args[2])
        val verdict = if (result.passed) "MATCH" else "DIFFER"
        return "compare ${args[1]} vs ${args[2]}: $verdict (score=${result.matchScore})"
    }

    private fun list(args: List<String>): String {
        val kind = args.getOrNull(1)?.let { requested ->
            runCatching { SnapshotKind.valueOf(requested.uppercase()) }.getOrNull()
        }
        val snapshots = snapshotService.recentSnapshots(kind, RECENT_LIMIT)
        if (snapshots.isEmpty()) return "no snapshots"
        return snapshots.joinToString("\n") {
            "  ${it.id}: ${it.kind.name} src=${it.source.take(SOURCE_PREVIEW)} " +
                "hash=${it.contentHash.take(HASH_PREVIEW)}"
        }
    }

    private companion object {
        const val HASH_PREVIEW = 8
        const val SOURCE_PREVIEW = 40
        const val RECENT_LIMIT = 20
    }
}
