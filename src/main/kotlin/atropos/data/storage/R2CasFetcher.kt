/* SPDX-License-Identifier: AGPL-3.0-only */
package atropos.data.storage

import atropos.core.policy.BoundedProcessRunner
import java.nio.file.Files
import java.nio.file.Path

/**
 * Transport adapter for CloudLakehouseSyncEngine.syncDelta.
 * Bytes must match hash or the engine will skip (hash mismatch).
 *
 * Objects are copied to a file and read back, rather than streamed through the
 * process pipe. Two things were wrong with the pipe:
 *
 * 1. **It could not carry the bytes intact.** Captured stdout is decoded as
 *    UTF-8, and this read it back with `toByteArray(UTF_8)`. That round trip is
 *    lossless only for valid UTF-8 — any other byte becomes U+FFFD, so the
 *    content hash could never match and every such object failed as a mismatch
 *    rather than as the decoding fault it was.
 * 2. **It capped the object size at the pipe's ceiling.** Captured output is
 *    bounded at 256 KiB and 4,000 lines, which is correct for a *command's*
 *    output and wrong for a document store: any larger object came back
 *    truncated and was discarded. Raising that ceiling would have weakened a
 *    bound that exists to stop a runaway subprocess, for the sake of a case
 *    that should not have been using the pipe at all.
 *
 * Execution still goes through [BoundedProcessRunner] — it remains the only
 * thing in the system that starts a process — but what it bounds now is
 * rclone's few lines of progress chatter, not the payload.
 *
 * The caller verifies the hash before the bytes can enter the store, so a
 * corrupt or substituted object is rejected there regardless of what this
 * returns.
 */
object R2CasFetcher {
    private val processRunner = BoundedProcessRunner()

    fun fetch(hash: String): ByteArray? {
        val normalized = hash.trim().lowercase()
        if (!normalized.matches(HASH)) return null
        val bucket = System.getenv("R2_BUCKET") ?: DEFAULT_BUCKET
        val remote = System.getenv("R2_REMOTE") ?: DEFAULT_REMOTE

        val staging = runCatching { Files.createTempDirectory("atropos-cas-") }.getOrNull() ?: return null
        val target = staging.resolve("$normalized.bin")
        try {
            val result = runCatching {
                processRunner.run(
                    command = listOf("rclone", "copyto", "$remote:$bucket/$normalized", target.toString()),
                    directory = staging,
                    timeoutMillis = TIMEOUT_MILLIS,
                    maxOutputBytes = CHATTER_BYTES,
                    maxOutputLines = CHATTER_LINES
                )
            }.getOrNull() ?: return null

            if (result.timedOut || result.launchError != null || result.exitCode != 0) return null
            if (!Files.isRegularFile(target)) return null
            return runCatching { Files.readAllBytes(target) }.getOrNull()?.takeIf { it.isNotEmpty() }
        } finally {
            runCatching { Files.deleteIfExists(target) }
            runCatching { Files.deleteIfExists(staging) }
        }
    }

    private val HASH = Regex("[a-f0-9]{64}")
    private const val DEFAULT_BUCKET = "atropos-lakehouse"

    /** rclone remote name, overridable because it is chosen at `rclone config` time. */
    private const val DEFAULT_REMOTE = "r2"

    private const val TIMEOUT_MILLIS = 120_000L

    /** Bounds rclone's own output, which the payload no longer travels through. */
    private const val CHATTER_BYTES = 64 * 1024
    private const val CHATTER_LINES = 500
}
