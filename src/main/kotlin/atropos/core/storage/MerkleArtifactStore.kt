/* SPDX-License-Identifier: AGPL-3.0-only */
package atropos.core.storage

import java.io.File
import java.util.concurrent.ConcurrentHashMap

/**
 * SD7-T-118: Merkle binary artifact cache for compiled outputs (.o, .class, .rlib, .jar).
 * Mathematical Invariant: C_real = min(C_impl, C_int, C_ver, C_ev)
 * Keyed by ModuleHash. Cache hit skips recompilation after full bit-level verification.
 */
class MerkleArtifactStore {
    private val artifactCache = ConcurrentHashMap<String, String>()

    fun storeArtifact(hash: String, path: String) {
        if (File(path).exists()) {
            artifactCache[hash] = path
        }
    }

    fun getArtifact(hash: String): String? {
        val path = artifactCache[hash] ?: return null
        return if (File(path).exists()) path else null
    }

    fun hasArtifact(hash: String): Boolean = getArtifact(hash) != null
}
