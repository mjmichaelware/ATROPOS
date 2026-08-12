/* SPDX-License-Identifier: AGPL-3.0-only */
package atropos.core.interrupt

import java.nio.charset.StandardCharsets
import java.nio.file.Files
import java.nio.file.Path
import java.nio.file.StandardCopyOption
import java.time.Instant

/**
 * Where a frozen run's position survives the process.
 *
 * `SUP.UX.INTERRUPT-PRIMITIVE`: "GoalRunStore must survive freeze. Test: freeze
 * → resume restores exact DAG position and evidence."
 *
 * [InterruptController] holds the interrupt in memory, which is correct for a
 * running loop and useless across a restart — and a restart is the case that
 * matters, because the reason to freeze a long phone job is usually that the
 * phone is about to stop being available. Without a durable position, freeze
 * degrades into the process death it was meant to replace.
 *
 * The record is deliberately small: which run, where it stopped, and what
 * evidence it had produced by then. It is not a snapshot of the work — the
 * work is already durable in its own stores, and copying it here would create
 * a second copy that could disagree with the first.
 */
class FrozenRunStore(private val file: Path) {

    fun freeze(record: FrozenRun): Boolean = runCatching {
        file.parent?.let { Files.createDirectories(it) }
        val temp = Files.createTempFile(file.parent ?: file.toAbsolutePath().parent, "frozen", ".tmp")
        Files.write(temp, record.encode().toByteArray(StandardCharsets.UTF_8))
        runCatching {
            Files.move(temp, file, StandardCopyOption.REPLACE_EXISTING, StandardCopyOption.ATOMIC_MOVE)
        }.recoverCatching {
            Files.move(temp, file, StandardCopyOption.REPLACE_EXISTING)
        }.getOrThrow()
        true
    }.getOrDefault(false)

    /** The frozen run, or null when nothing is frozen. */
    fun read(): FrozenRun? {
        if (!Files.isRegularFile(file)) return null
        val text = runCatching { Files.readString(file) }.getOrNull() ?: return null
        return FrozenRun.decode(text)
    }

    /**
     * Clears the record.
     *
     * Called after a resume has actually taken the position, never before. A
     * record cleared optimistically would leave a run that failed to resume
     * with nothing to resume from — which is the freeze having lost the work it
     * existed to protect.
     */
    fun clear(): Boolean = runCatching { Files.deleteIfExists(file) }.getOrDefault(false)
}

/**
 * @param resumePoint the DAG position the run stopped at. Never fabricated: a
 *   run with no consistent position is a hard stop and is not frozen at all.
 * @param evidencePaths what had been produced by the freeze, so a resume can
 *   show the operator what already exists rather than re-deriving it.
 */
data class FrozenRun(
    val runId: String,
    val resumePoint: String,
    val level: InterruptLevel,
    val frozenAt: Instant,
    val evidencePaths: List<String> = emptyList()
) {
    fun encode(): String = buildString {
        appendLine("runId\t$runId")
        appendLine("resumePoint\t$resumePoint")
        appendLine("level\t${level.canonical}")
        appendLine("frozenAt\t$frozenAt")
        evidencePaths.forEach { appendLine("evidence\t$it") }
    }

    fun render(): String =
        "$runId frozen at '$resumePoint' (${level.canonical}) since $frozenAt" +
            if (evidencePaths.isEmpty()) "" else ", ${evidencePaths.size} evidence artifacts"

    companion object {
        fun decode(text: String): FrozenRun? {
            val fields = mutableMapOf<String, String>()
            val evidence = mutableListOf<String>()
            text.lineSequence().forEach { line ->
                val tab = line.indexOf('\t')
                if (tab <= 0) return@forEach
                val key = line.take(tab)
                val value = line.substring(tab + 1)
                if (key == "evidence") evidence += value else fields[key] = value
            }
            val runId = fields["runId"]?.takeIf { it.isNotBlank() } ?: return null
            val resumePoint = fields["resumePoint"]?.takeIf { it.isNotBlank() } ?: return null
            val level = fields["level"]?.let(InterruptLevel::fromCanonical) ?: return null
            val frozenAt = fields["frozenAt"]?.let { runCatching { Instant.parse(it) }.getOrNull() }
                ?: return null
            return FrozenRun(runId, resumePoint, level, frozenAt, evidence)
        }
    }
}
