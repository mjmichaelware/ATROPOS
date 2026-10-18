/* SPDX-License-Identifier: AGPL-3.0-only */
package atropos.cli.input

import java.io.File
import java.nio.file.Path

/**
 * Keybinding and Mobile Shortcut Management Engine.
 * Manages the canonical 34 keyboard shortcuts and binds mobile `#<ABC>` macros to any action.
 */
class KeybindingManager(
    private val macroEngine: NumberSignMacroEngine = NumberSignMacroEngine()
) {
    data class ShortcutDefinition(
        val key: String,
        val action: String,
        val description: String
    )

    companion object {
        val CANONICAL_SHORTCUTS: List<ShortcutDefinition> = listOf(
            ShortcutDefinition("alt+m", "TOGGLE_QUEUE", "Send with opposite Queued Messages setting"),
            ShortcutDefinition("alt+v", "PASTE_MEDIA", "Paste image/video"),
            ShortcutDefinition("ctrl+v", "PASTE_CLIPBOARD", "Paste text from system clipboard"),
            ShortcutDefinition("ctrl+_", "UNDO", "Undo last buffer modification"),
            ShortcutDefinition("ctrl+shift+-", "UNDO", "Alternative undo sequence"),
            ShortcutDefinition("ctrl+c", "CANCEL", "Go back / dismiss interactive modal"),
            ShortcutDefinition("esc", "DISMISS", "Close modal or cancel active prompt"),
            ShortcutDefinition("ctrl+d", "EXIT", "Cleanly terminate CLI process"),
            ShortcutDefinition("ctrl+end", "SCROLL_BOTTOM", "Jump viewport to bottom"),
            ShortcutDefinition("ctrl+g", "EXTERNAL_EDITOR", "Open active prompt in \$EDITOR"),
            ShortcutDefinition("ctrl+home", "SCROLL_TOP", "Jump viewport to top"),
            ShortcutDefinition("ctrl+k", "APPROVE_FAST", "Approve pending subagent proposal immediately"),
            ShortcutDefinition("ctrl+l", "CLEAR_SCREEN", "Emit ANSI clear viewport sequence"),
            ShortcutDefinition("ctrl+o", "TOGGLE_TRAJECTORY", "Toggle execution trajectory tree view"),
            ShortcutDefinition("ctrl+r", "REVIEW_ARTIFACT", "Open artifact review split"),
            ShortcutDefinition("ctrl+shift+z", "REDO", "Redo previously reverted mutation"),
            ShortcutDefinition("ctrl+y", "YANK", "Paste from internal kill ring"),
            ShortcutDefinition("ctrl+z", "SUSPEND", "Suspend CLI execution loop"),
            ShortcutDefinition("down", "NAVIGATE_DOWN", "Move selector down"),
            ShortcutDefinition("enter", "CONFIRM", "Send message or confirm selection"),
            ShortcutDefinition("f2", "RENAME", "Rename active tab or project"),
            ShortcutDefinition("f4", "DELETE", "Delete active temporary workspace"),
            ShortcutDefinition("f5", "VOICE", "Start native speech-to-text dictation"),
            ShortcutDefinition("left", "NAVIGATE_LEFT", "Move left / switch to previous tab"),
            ShortcutDefinition("pgdown", "PAGE_DOWN", "Scroll one page down"),
            ShortcutDefinition("pgup", "PAGE_UP", "Scroll one page up"),
            ShortcutDefinition("right", "NAVIGATE_RIGHT", "Move right / drill into leaf description"),
            ShortcutDefinition("shift+down", "HALF_PAGE_DOWN", "Scroll half page down"),
            ShortcutDefinition("shift+tab", "CYCLE_MODE", "Cycle active agent mode"),
            ShortcutDefinition("shift+up", "HALF_PAGE_UP", "Scroll half page up"),
            ShortcutDefinition("shift+click", "COPY_SELECTION", "Select and copy text to clipboard"),
            ShortcutDefinition("tab", "AUTOCOMPLETE", "Trigger command autocomplete or cycle view"),
            ShortcutDefinition("up", "NAVIGATE_UP", "Move selector up"),
            ShortcutDefinition("f12", "TELEPORT", "Bind terminal to Web Cockpit socket mirror")
        )

        val RESERVED_PREFIXES: Set<String> = setOf("#", "@", "/", "?", "!", "+", "$")
        val RESERVED_MACROS: Set<String> = setOf(
            "F1", "F2", "F3", "F4", "F5", "F6", "F7", "F8", "F9", "F10", "F11", "F12",
            "ESC", "TAB", "ENTER", "UP", "DOWN", "LEFT", "RIGHT"
        )

        fun keybindingsFile(): File {
            val home = System.getProperty("user.home") ?: "."
            val dir = File(home, ".atropos")
            if (!dir.exists()) dir.mkdirs()
            return File(dir, "keybindings.json")
        }
    }

    private val memoryLattice = atropos.core.memory.MemoryLayerLattice()

    init {
        loadCustomBindings()
    }

    sealed class BindResult {
        data class Success(val macro: String, val action: String) : BindResult()
        data class Collision(val macro: String, val reason: String) : BindResult()
        data class Invalid(val reason: String) : BindResult()
    }

    fun bind(shortcutString: String, targetAction: String, force: Boolean = false): Boolean {
        return when (bindWithResult(shortcutString, targetAction, force)) {
            is BindResult.Success -> true
            else -> false
        }
    }

    fun bindWithResult(shortcutString: String, targetAction: String, force: Boolean = false): BindResult {
        val clean = shortcutString.trim().uppercase().removePrefix("#")
        if (!clean.matches(Regex("[A-Z0-9_-]{1,16}"))) {
            return BindResult.Invalid("invalid macro key: must be 1-16 alphanumeric chars")
        }
        if (!force) {
            if (clean in RESERVED_MACROS) {
                return BindResult.Collision(clean, "collides with reserved hardware macro #$clean (confirmation required)")
            }
            if (RESERVED_PREFIXES.any { clean.startsWith(it) }) {
                return BindResult.Collision(clean, "collides with reserved prefix character")
            }
        }
        val before = macroEngine.resolveMacro("#$clean") ?: "none"
        macroEngine.registerMacro(clean, targetAction)
        saveCustomBindings()
        emitEvidenceReceipt("KEYBIND_MACRO_BOUND", clean, before, targetAction)
        return BindResult.Success(clean, targetAction)
    }

    fun unbind(shortcutString: String): Boolean {
        val clean = shortcutString.trim().uppercase().removePrefix("#")
        val before = macroEngine.resolveMacro("#$clean") ?: "none"
        val removed = macroEngine.removeMacro(clean)
        if (removed) {
            saveCustomBindings()
            emitEvidenceReceipt("KEYBIND_MACRO_UNBOUND", clean, before, "none")
        }
        return removed
    }

    fun listBindings(): Map<String, String> = macroEngine.listMacros()

    fun listAlphabetical(): List<Pair<String, String>> =
        macroEngine.listMacros().entries.sortedBy { it.key }.map { it.key to it.value }

    fun expand(macro: String): String? = macroEngine.resolveMacro(macro)

    private fun emitEvidenceReceipt(action: String, macro: String, before: String, after: String) {
        val timestamp = System.currentTimeMillis()
        val payload = "$timestamp:$action:$macro:$before:$after"
        val sha256 = atropos.core.memory.MemoryLayerLattice.sha256(payload)
        val json = "{\"timestamp\":$timestamp,\"sha256\":\"$sha256\",\"action\":\"$action\"," +
            "\"actor\":\"KeybindingManager\",\"surface\":\"CLI\",\"before\":\"$before\",\"after\":\"$after\"}"
        memoryLattice.put(
            layer = atropos.core.memory.MemoryLayerLattice.LayerType.EVIDENCE_LAYER,
            key = "keybind_mutation_$timestamp",
            content = json
        )
    }

    fun renderMenu(width: Int = 80): String {
        val sb = StringBuilder()
        sb.append("┌─ ATROPOS KEYBINDINGS & MOBILE NUMBER-SIGN MACROS ────────┐\n")
        sb.append("│ Keyboard: ↑/↓ Navigate   ←/→ Switch View   esc Close    │\n")
        sb.append("├──────────────────────────────────────────────────────────┤\n")
        sb.append("│ CANONICAL SHORTCUTS (34 items):                          │\n")
        for (sc in CANONICAL_SHORTCUTS) {
            val keyCol = sc.key.padEnd(16)
            val descCol = sc.description.take(width - 24)
            sb.append("│  $keyCol $descCol\n")
        }
        sb.append("├──────────────────────────────────────────────────────────┤\n")
        sb.append("│ ACTIVE MOBILE MACROS (#<ABC> -> ACTION):                 │\n")
        val active = listBindings()
        if (active.isEmpty()) {
            sb.append("│  (no custom macros registered; use /keybinding bind #...)│\n")
        } else {
            for ((k, v) in active) {
                sb.append("│  #${k.padEnd(14)} -> $v\n")
            }
        }
        sb.append("└──────────────────────────────────────────────────────────┘\n")
        return sb.toString()
    }

    private fun loadCustomBindings() {
        val file = keybindingsFile()
        if (!file.exists()) return
        val content = runCatching { file.readText() }.getOrNull() ?: return
        val regex = Regex("\"([A-Z0-9_-]+)\"\\s*:\\s*\"([^\"]+)\"")
        for (match in regex.findAll(content)) {
            val key = match.groupValues[1]
            val target = match.groupValues[2]
            macroEngine.registerMacro(key, target)
        }
    }

    private fun saveCustomBindings() {
        val file = keybindingsFile()
        val entries = listBindings().entries.toList()
        val sb = StringBuilder("{\n")
        for (i in entries.indices) {
            val (k, v) = entries[i]
            val comma = if (i < entries.size - 1) "," else ""
            sb.append("  \"$k\": \"$v\"$comma\n")
        }
        sb.append("}\n")
        runCatching { file.writeText(sb.toString()) }
    }
}
