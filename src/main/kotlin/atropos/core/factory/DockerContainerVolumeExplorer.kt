/* SPDX-License-Identifier: AGPL-3.0-only */
package atropos.core.factory

import org.json.JSONArray
import org.json.JSONObject

/**
 * ATOM EX6: DOCKER EXPLORER CONTAINER AND VOLUME MANAGEMENT
 * Path: src/main/kotlin/atropos/core/factory/DockerContainerVolumeExplorer.kt
 *
 * Provides management capabilities to inspect, list, start, stop, and prune
 * local Docker/Podman containers, images, volumes, and networks.
 */
data class DockerContainerRecord(
    val id: String,
    val name: String,
    val image: String,
    val status: String,
    val state: String,
    val ports: List<String>
)

data class DockerVolumeRecord(
    val name: String,
    val driver: String,
    val mountpoint: String,
    val sizeBytes: Long? = null
)

class DockerContainerVolumeExplorer {
    fun parseContainerListJson(rawJson: String): List<DockerContainerRecord> {
        val list = mutableListOf<DockerContainerRecord>()
        val lines = rawJson.lines().filter { it.isNotBlank() }

        for (line in lines) {
            val obj = try { JSONObject(line.trim()) } catch (_: Exception) { continue }
            val id = obj.optString("ID")
            val names = obj.optString("Names")
            val image = obj.optString("Image")
            val status = obj.optString("Status")
            val state = obj.optString("State")
            val ports = obj.optString("Ports").split(',').map { it.trim() }.filter { it.isNotEmpty() }

            list.add(
                DockerContainerRecord(
                    id = id,
                    name = names,
                    image = image,
                    status = status,
                    state = state,
                    ports = ports
                )
            )
        }
        return list
    }

    fun parseVolumeListJson(rawJson: String): List<DockerVolumeRecord> {
        val list = mutableListOf<DockerVolumeRecord>()
        val root = try { JSONObject(rawJson) } catch (_: Exception) { return emptyList() }
        val volumes = root.optJSONArray("Volumes") ?: return emptyList()

        for (i in 0 until volumes.length()) {
            val v = volumes.optJSONObject(i) ?: continue
            list.add(
                DockerVolumeRecord(
                    name = v.optString("Name"),
                    driver = v.optString("Driver", "local"),
                    mountpoint = v.optString("Mountpoint")
                )
            )
        }
        return list
    }

    companion object {
        val defaultExplorer = DockerContainerVolumeExplorer()
    }
}
