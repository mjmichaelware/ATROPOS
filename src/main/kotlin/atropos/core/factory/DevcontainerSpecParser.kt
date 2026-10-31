/* SPDX-License-Identifier: AGPL-3.0-only */
package atropos.core.factory

import org.json.JSONObject
import java.nio.file.Files
import java.nio.file.Path

/**
 * ATOM CC8: REPOSITORY DEVCONTAINER SPECIFICATION PARSER
 * Path: src/main/kotlin/atropos/core/factory/DevcontainerSpecParser.kt
 *
 * Parses repository .devcontainer/devcontainer.json manifests to configure
 * container base images, pre-installed toolchains, IDE extensions, and runtime forwardPorts.
 */
data class DevcontainerSpec(
    val name: String,
    val image: String?,
    val dockerfile: String?,
    val forwardPorts: List<Int>,
    val extensions: List<String>,
    val features: Map<String, String>,
    val postCreateCommand: String?,
    val postStartCommand: String?,
    val remoteUser: String?
)

class DevcontainerSpecParser {
    fun parseManifest(rawJson: String): DevcontainerSpec {
        val root = try {
            JSONObject(rawJson)
        } catch (_: Exception) {
            JSONObject()
        }

        val name = root.optString("name", "Atropos DevContainer")
        val image = if (root.has("image")) root.optString("image") else null
        val dockerfile = if (root.has("dockerfile")) root.optString("dockerfile") else null

        val ports = mutableListOf<Int>()
        val portsArray = root.optJSONArray("forwardPorts")
        if (portsArray != null) {
            for (i in 0 until portsArray.length()) {
                val p = portsArray.optInt(i)
                if (p > 0) ports.add(p)
            }
        }

        val extensions = mutableListOf<String>()
        val customObj = root.optJSONObject("customizations")
        val vscodeObj = customObj?.optJSONObject("vscode")
        val extArray = vscodeObj?.optJSONArray("extensions")
        if (extArray != null) {
            for (i in 0 until extArray.length()) {
                val ext = extArray.optString(i)
                if (ext.isNotBlank()) extensions.add(ext)
            }
        }

        val features = mutableMapOf<String, String>()
        val featuresObj = root.optJSONObject("features")
        if (featuresObj != null) {
            for (key in featuresObj.keySet()) {
                features[key] = featuresObj.get(key).toString()
            }
        }

        val postCreate = if (root.has("postCreateCommand")) root.optString("postCreateCommand") else null
        val postStart = if (root.has("postStartCommand")) root.optString("postStartCommand") else null
        val remoteUser = if (root.has("remoteUser")) root.optString("remoteUser") else null

        return DevcontainerSpec(
            name = name,
            image = image,
            dockerfile = dockerfile,
            forwardPorts = ports,
            extensions = extensions,
            features = features,
            postCreateCommand = postCreate,
            postStartCommand = postStart,
            remoteUser = remoteUser
        )
    }

    fun findAndParse(repoRoot: Path): DevcontainerSpec? {
        val candidatePaths = listOf(
            repoRoot.resolve(".devcontainer/devcontainer.json"),
            repoRoot.resolve(".devcontainer.json")
        )
        for (path in candidatePaths) {
            if (Files.isRegularFile(path)) {
                val content = Files.readString(path)
                return parseManifest(content)
            }
        }
        return null
    }

    companion object {
        val defaultParser = DevcontainerSpecParser()
    }
}
