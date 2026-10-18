/* SPDX-License-Identifier: AGPL-3.0-only */
package atropos.cli.input

import java.io.File
import java.nio.file.Path

/**
 * ATOM-MACRO-015: Hashtag Mention & Number-Sign Keybinding Engine.
 *
 * Implements:
 * 1. File mentions (#filename): scans directory root alphabetically and provides marquee expansion.
 * 2. Number sign macros (#<ABC>): translates short mobile strings to function keys / commands.
 */
class NumberSignMacroEngine(
    private val rootDirectory: Path = Path.of(".")
) {
    private val macros = mutableMapOf<String, String>()

    init {
        bootstrapDefaultMacros()
    }

    fun registerMacro(shortcut: String, target: String) {
        val cleanShortcut = shortcut.trim().uppercase().removePrefix("#")
        require(cleanShortcut.matches(Regex("[A-Z0-9_-]{1,16}"))) {
            "invalid macro shortcut: must be 1-16 alphanumeric chars"
        }
        macros[cleanShortcut] = target.trim()
    }

    fun removeMacro(shortcut: String): Boolean {
        val clean = shortcut.trim().uppercase().removePrefix("#")
        return macros.remove(clean) != null
    }

    fun resolveMacro(token: String): String? {
        if (!token.startsWith("#")) return null
        val key = token.removePrefix("#").trim().uppercase()
        return macros[key]
    }

    fun listMacros(): Map<String, String> = macros.toMap()

    fun findMentionCandidates(query: String, limit: Int = 24): List<String> {
        val needle = query.removePrefix("#").trim().lowercase()
        val dir = rootDirectory.toFile()
        if (!dir.exists() || !dir.isDirectory) return emptyList()

        return runCatching {
            dir.walkTopDown()
                .maxDepth(3)
                .filter { file ->
                    !file.name.startsWith(".") &&
                    file.isFile &&
                    (needle.isEmpty() || file.name.lowercase().contains(needle))
                }
                .map { file ->
                    file.relativeTo(dir).path.replace(File.separatorChar, '/')
                }
                .sorted()
                .take(limit)
                .toList()
        }.getOrDefault(emptyList())
    }

    private fun bootstrapDefaultMacros() {
        // Mobile hardware key compensations
        macros["F1"] = "/help"
        macros["F2"] = "RENAME"
        macros["F4"] = "DELETE"
        macros["F5"] = "VOICE"
        macros["F12"] = "VOICE"
        macros["ESC"] = "CANCEL"
        macros["TAB"] = "CYCLE"
        macros["UP"] = "PREV"
        macros["DOWN"] = "NEXT"
        macros["LEFT"] = "BACK"
        macros["RIGHT"] = "DRILL"
    }
}
