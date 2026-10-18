/* SPDX-License-Identifier: AGPL-3.0-only */
package atropos.core.config

import atropos.core.AtroposRepoRootLocator
import java.io.File
import java.nio.file.Path

/**
 * Authoritative Unified Configuration Matrix for ATROPOS (atropos.config.json).
 * Maps 24 settings across competitive CLI capabilities (Claude Code, Aider, Goose, Cursor)
 * to deterministic runtime options with safe defaults and validation.
 */
class AtroposUnifiedConfig(
    val values: MutableMap<String, String> = defaultValues().toMutableMap()
) {
    companion object {
        val ALLOWED_OPTIONS: Map<String, List<String>> = linkedMapOf(
            "Agent_Mode" to listOf("autonomous", "supervised", "plan-only"),
            "Animation_Speed" to listOf("instant", "fast", "medium", "off"),
            "Artifact_Review" to listOf("auto-accept", "ask-review", "strict-gate"),
            "Color_Scheme" to listOf("atropos-dark", "electric-blue", "amber-crt", "solarized", "terminal"),
            "Conversation_Grouping" to listOf("by-project", "by-date", "flat"),
            "Copy_on_Select" to listOf("on", "off"),
            "Editor" to listOf("auto (\$EDITOR)", "auto", "vim", "nano", "custom"),
            "Editor_Mode" to listOf("inline", "modal", "split-view"),
            "Enable_Telemetry" to listOf("on", "off", "local-only"),
            "Non-Workspace_Access" to listOf("blocked", "prompt", "allowed"),
            "Notifications" to listOf("terminal-bell", "os-toast", "off"),
            "Queued_Messages" to listOf("queue", "interrupt", "discard"),
            "Rendering_Mode" to listOf("native-terminal", "alternate-screen", "headless"),
            "Sandbox_Mode" to listOf("ephemeral-vfs", "chroot", "off"),
            "Show_Active_Tasks" to listOf("on", "off"),
            "Show_Feedback_Survey" to listOf("on", "off"),
            "Show_Tips" to listOf("on", "off", "unread-only"),
            "Tool_Permission" to listOf("confirm-all", "bounded-auto", "always-proceed", "strict"),
            "Use_AI_Credits" to listOf("off", "emergency-only", "unlimited"),
            "Verbosity" to listOf("L1-compact", "L2-standard", "L3-detailed", "L4-internal"),
            "Auto-Lint" to listOf("on", "off"),
            "Auto-Test_Gating" to listOf("on", "off"),
            "Max_Context_Tokens" to listOf("8k", "16k", "32k", "64k", "128k", "max"),
            "Model_Fallback_Cascade" to listOf("on", "off")
        )

        fun defaultValues(): Map<String, String> = linkedMapOf(
            "Agent_Mode" to "supervised",
            "Animation_Speed" to "fast",
            "Artifact_Review" to "ask-review",
            "Color_Scheme" to "atropos-dark",
            "Conversation_Grouping" to "by-project",
            "Copy_on_Select" to "on",
            "Editor" to "auto (\$EDITOR)",
            "Editor_Mode" to "inline",
            "Enable_Telemetry" to "local-only",
            "Non-Workspace_Access" to "prompt",
            "Notifications" to "terminal-bell",
            "Queued_Messages" to "queue",
            "Rendering_Mode" to "native-terminal",
            "Sandbox_Mode" to "ephemeral-vfs",
            "Show_Active_Tasks" to "on",
            "Show_Feedback_Survey" to "off",
            "Show_Tips" to "unread-only",
            "Tool_Permission" to "bounded-auto",
            "Use_AI_Credits" to "off",
            "Verbosity" to "L2-standard",
            "Auto-Lint" to "on",
            "Auto-Test_Gating" to "on",
            "Max_Context_Tokens" to "64k",
            "Model_Fallback_Cascade" to "on"
        )

        fun configFile(): File {
            val root = runCatching { AtroposRepoRootLocator.resolve() }.getOrNull()
                ?: Path.of(System.getProperty("user.home")).resolve(".atropos")
            val targetDir = root.resolve(".atropos").toFile()
            if (!targetDir.exists()) targetDir.mkdirs()
            return targetDir.resolve("atropos.config.json")
        }

        fun load(file: File = configFile()): AtroposUnifiedConfig {
            val config = AtroposUnifiedConfig()
            if (!file.exists()) return config
            val text = runCatching { file.readText() }.getOrNull() ?: return config
            for ((key, _) in ALLOWED_OPTIONS) {
                val pattern = Regex("\"$key\"\\s*:\\s*\"([^\"]+)\"")
                pattern.find(text)?.groupValues?.get(1)?.let { rawVal ->
                    config.set(key, rawVal)
                }
            }
            return config
        }
    }

    fun get(key: String): String = values[key] ?: defaultValues()[key].orEmpty()

    fun set(key: String, value: String): Boolean {
        val allowed = ALLOWED_OPTIONS[key] ?: return false
        val matched = allowed.firstOrNull { it.equals(value, ignoreCase = true) } ?: return false
        values[key] = matched
        return true
    }

    fun save(file: File = configFile()) {
        val sb = StringBuilder("{\n")
        val entries = values.entries.toList()
        for (i in entries.indices) {
            val (k, v) = entries[i]
            val comma = if (i < entries.size - 1) "," else ""
            sb.append("  \"$k\": \"$v\"$comma\n")
        }
        sb.append("}\n")
        runCatching { file.writeText(sb.toString()) }
    }

    fun renderMatrix(width: Int = 80): String {
        val sb = StringBuilder()
        sb.append("┌─ ATROPOS UNIFIED CONFIGURATION MATRIX (atropos.config.json) ─┐\n")
        for ((key, value) in values) {
            val keyCol = key.padEnd(26)
            val valCol = value.padEnd(20)
            val options = ALLOWED_OPTIONS[key]?.joinToString("|") ?: ""
            sb.append("│ $keyCol $valCol [$options]\n")
        }
        sb.append("└" + "─".repeat((width - 2).coerceAtLeast(40)) + "┘\n")
        return sb.toString()
    }
}
