/* SPDX-License-Identifier: AGPL-3.0-only */
package atropos.data.lakehouse

import atropos.core.AtroposConfig
import atropos.data.storage.CloudLakehouseSyncEngine
import atropos.data.storage.R2CasFetcher
import java.io.File

data class LakehousePathResult(
    val path: String,
    val nodeId: String?,
    val status: String, // HIT | MISS | REJECT | ERROR
    val bytes: ByteArray?,
    val reason: String
)

class LakehousePathRetrieve(
    private val cas: CloudLakehouseSyncEngine = CloudLakehouseSyncEngine(),
    private val config: AtroposConfig = AtroposConfig.load()
) {
    fun get(pathTag: String): LakehousePathResult {
        val mount = File(config.lakehouse.mountPath)
        val pathsFile = File(mount, "index/paths.txt")
        val objectsFile = File(mount, "index/objects.tsv")
        if (!pathsFile.isFile || !objectsFile.isFile) {
            return LakehousePathResult(pathTag, null, "MISS", null, "index_missing")
        }
        val allowed = pathsFile.readLines().map { it.trim() }.filter { it.isNotEmpty() }.toSet()
        if (pathTag !in allowed) {
            return LakehousePathResult(pathTag, null, "REJECT", null, "path_not_in_registry")
        }
        val line = objectsFile.readLines().firstOrNull { ln ->
            val p = ln.split("\t")
            p.size >= 2 && p[1].trim() == pathTag
        } ?: return LakehousePathResult(pathTag, null, "MISS", null, "no_object_for_path")

        val hash = line.split("\t")[0].trim().lowercase()
        cas.retrieveContent(hash)?.let {
            return LakehousePathResult(pathTag, hash, "HIT", it, "local_cas")
        }

        // remote fill then local read — soft-fail if still missing
        runCatching {
            cas.syncDelta(listOf(hash)) { h -> R2CasFetcher.fetch(h) }
        }
        cas.retrieveContent(hash)?.let {
            return LakehousePathResult(pathTag, hash, "HIT", it, "synced_from_r2")
        }
        return LakehousePathResult(pathTag, hash, "MISS", null, "remote_unavailable_or_hash_mismatch")
    }
}
