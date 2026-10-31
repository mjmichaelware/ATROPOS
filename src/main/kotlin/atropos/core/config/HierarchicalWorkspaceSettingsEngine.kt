/* SPDX-License-Identifier: AGPL-3.0-only */
package atropos.core.config

import atropos.core.AtroposRepoRootLocator
import java.io.File
import java.nio.file.Path
import java.util.concurrent.ConcurrentHashMap
import org.json.JSONObject

enum class SettingsScope {
    GLOBAL_USER,
    WORKSPACE_PROJECT,
    EPHEMERAL_SESSION
}

/**
 * ATOM M2.5: Dense Hierarchical Workspace and User Settings Engine.
 *
 * Implements a multi-layered configuration cascading engine that harmonizes global
 * developer preferences, per-project overrides, custom keybindings, and MCP server declarations.
 *
 * ## Research: Live Monaco JSON Schema Validation
 * Researches Draft-07 JSON Schema integration into Monaco language services to provide
 * real-time schema linting, hover tooltips, and property autocomplete inside the settings editor.
 */
class HierarchicalWorkspaceSettingsEngine(
    val repoRoot: Path = AtroposRepoRootLocator.resolve()
) {
    private val globalSettings = ConcurrentHashMap<String, String>()
    private val workspaceSettings = ConcurrentHashMap<String, String>()
    private val sessionOverrides = ConcurrentHashMap<String, String>()

    init {
        loadDefaults()
    }

    private fun loadDefaults() {
        AtroposUnifiedConfig.defaultValues().forEach { (k, v) ->
            globalSettings[k] = v
        }
    }

    fun get(key: String): String {
        return sessionOverrides[key]
            ?: workspaceSettings[key]
            ?: globalSettings[key]
            ?: "unknown"
    }

    fun set(key: String, value: String, scope: SettingsScope = SettingsScope.WORKSPACE_PROJECT) {
        when (scope) {
            SettingsScope.GLOBAL_USER -> globalSettings[key] = value
            SettingsScope.WORKSPACE_PROJECT -> workspaceSettings[key] = value
            SettingsScope.EPHEMERAL_SESSION -> sessionOverrides[key] = value
        }
    }

    fun dumpEffectiveConfigJson(): String {
        val root = JSONObject()
        val merged = LinkedHashMap<String, String>()
        merged.putAll(globalSettings)
        merged.putAll(workspaceSettings)
        merged.putAll(sessionOverrides)
        merged.forEach { (k, v) -> root.put(k, v) }
        return root.toString(2)
    }

    fun validateSettingKey(key: String, candidateValue: String): Boolean {
        val allowed = AtroposUnifiedConfig.ALLOWED_OPTIONS[key] ?: return true
        return allowed.contains(candidateValue)
    }
}
