/* SPDX-License-Identifier: AGPL-3.0-only */
package atropos.data.storage

import java.util.concurrent.TimeUnit

/**
 * Transport adapter for CloudLakehouseSyncEngine.syncDelta.
 * Bytes must match hash or the engine will skip (hash mismatch).
 */
object R2CasFetcher {
    fun fetch(hash: String): ByteArray? {
        val h = hash.trim().lowercase()
        if (!h.matches(Regex("[a-f0-9]{64}"))) return null
        val bucket = System.getenv("R2_BUCKET") ?: "atropos-lakehouse"
        return try {
            val pb = ProcessBuilder("rclone", "cat", "r2:$bucket/$h")
                .redirectErrorStream(true)
            val proc = pb.start()
            val bytes = proc.inputStream.readBytes()
            val finished = proc.waitFor(120, TimeUnit.SECONDS)
            if (!finished) {
                proc.destroyForcibly()
                return null
            }
            if (proc.exitValue() != 0) return null
            bytes.takeIf { it.isNotEmpty() }
        } catch (_: Exception) {
            null
        }
    }
}
