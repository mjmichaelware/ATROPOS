/* SPDX-License-Identifier: AGPL-3.0-only */
package atropos.core.telemetry

import atropos.core.parser.TreeSitterGrammarBridge
import java.nio.file.Files
import java.nio.file.Path

/**
 * SD7-T1-TEL07: Worktree AST Delta Snapshotter.
 *
 * Tracks structural AST transformations and file modifications between successive agent steps.
 * Computes TreeSitter node delta metrics and structural edit distances on touched files.
 */
class WorktreeAstDeltaSnapshotter(
    private val astBridge: TreeSitterGrammarBridge = TreeSitterGrammarBridge(),
    private val ringBuffer: TelemetryRingBuffer? = null
) {
    data class FileAstSnapshot(
        val path: String,
        val nodeCount: Int,
        val contentSha256: String
    )

    /**
     * Captures pre-mutation AST fingerprint of a target file.
     */
    fun captureSnapshot(worktree: Path, relativePath: String): FileAstSnapshot {
        val target = worktree.resolve(relativePath)
        if (!Files.exists(target) || !Files.isRegularFile(target)) {
            return FileAstSnapshot(relativePath, 0, "0".repeat(64))
        }
        val content = Files.readString(target)
        val hash = FrontierSweTelemetryRecord.sha256Hex(content)
        val nodeCount = content.lines().size // heuristic AST node count proxy
        return FileAstSnapshot(relativePath, nodeCount, hash)
    }

    /**
     * Compares pre and post mutation states, emitting an AstMutationRecord to telemetry.
     */
    fun recordDelta(
        instanceId: String,
        worktree: Path,
        relativePath: String,
        before: FileAstSnapshot
    ) {
        val after = captureSnapshot(worktree, relativePath)
        val nodesAdded = (after.nodeCount - before.nodeCount).coerceAtLeast(0)
        val nodesDeleted = (before.nodeCount - after.nodeCount).coerceAtLeast(0)
        val nodesModified = if (before.contentSha256 != after.contentSha256) 1 else 0

        val structuralDistance = (nodesAdded + nodesDeleted + nodesModified).toDouble()

        val payload = AstMutationRecord(
            filePath = relativePath,
            nodesAdded = nodesAdded,
            nodesDeleted = nodesDeleted,
            nodesModified = nodesModified,
            structuralDistance = structuralDistance
        )

        val record = FrontierSweTelemetryRecord.create(
            instanceId = instanceId,
            h0PromptLineage = before.contentSha256,
            payload = payload,
            rewardSignal = 0.0
        )
        ringBuffer?.offer(record)
    }
}
