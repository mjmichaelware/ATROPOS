/* SPDX-License-Identifier: AGPL-3.0-only */
package atropos.data.storage

import atropos.core.policy.BoundedProcessRunner
import java.nio.file.Path

/**
 * Transport adapter for CloudLakehouseSyncEngine.syncDelta.
 * Bytes must match hash or the engine will skip (hash mismatch).
 */
object R2CasFetcher {
    private val processRunner = BoundedProcessRunner()

    fun fetch(hash: String): ByteArray? {
        val h = hash.trim().lowercase()
        if (!h.matches(Regex("[a-f0-9]{64}"))) return null
        val bucket = System.getenv("R2_BUCKET") ?: "atropos-lakehouse"
        val result = runCatching {
            processRunner.run(
                command = listOf("rclone", "cat", "r2:$bucket/$h"),
                directory = Path.of("/"),
                timeoutMillis = 120_000L,
                maxOutputBytes = 256 * 1024,
                maxOutputLines = 4_000
            )
        }.getOrNull() ?: return null
        if (result.timedOut || result.launchError != null || result.exitCode != 0 || result.outputTruncated) return null
        return result.stdout.toByteArray(Charsets.UTF_8).takeIf { it.isNotEmpty() }
    }
}
