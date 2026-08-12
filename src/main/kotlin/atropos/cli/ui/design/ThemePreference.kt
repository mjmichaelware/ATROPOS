/* SPDX-License-Identifier: AGPL-3.0-only */
package atropos.cli.ui.design

import java.io.File

/**
 * The theme the operator chose, remembered across restarts.
 *
 * [ThemeCatalog] gained four accents and the only way to select one was
 * `ATROPOS_THEME` in the environment — which means a phone operator had no way
 * at all, since there is nowhere to export a variable before a tap launches the
 * jar. A palette nobody can select is decoration.
 *
 * Stored beside the config rather than inside it. `~/.atropos/config.json` is
 * read by a regex extractor and rewritten by other tooling; a one-line file
 * that holds exactly one value cannot be corrupted by an unrelated write, and a
 * theme is not worth risking a config file for.
 *
 * The environment variable still wins. A session started with an explicit theme
 * asked for that theme, and a stored preference silently overriding it would
 * make the variable useless for exactly the scripted and CI cases it exists for.
 */
object ThemePreference {

    /** The active theme id: environment first, then stored, then the default. */
    fun resolve(
        env: (String) -> String? = System::getenv,
        home: String? = System.getProperty("user.home")
    ): String {
        env("ATROPOS_THEME")?.trim()?.takeIf { it.isNotBlank() }?.let { return it }
        return read(home) ?: ThemeCatalog.DEFAULT_ID
    }

    /** @return null when nothing is stored or the file cannot be read. */
    fun read(home: String? = System.getProperty("user.home")): String? = runCatching {
        val file = fileFor(home) ?: return@runCatching null
        if (!file.isFile) return@runCatching null
        file.readText().trim().takeIf { it.isNotBlank() }
    }.getOrNull()

    /**
     * @return false when the choice could not be persisted. The caller must say
     *   so rather than reporting success: a theme that reverts on restart with
     *   no explanation reads as the command not working.
     */
    fun write(themeId: String, home: String? = System.getProperty("user.home")): Boolean = runCatching {
        val known = ThemeCatalog.all.firstOrNull { it.id.equals(themeId.trim(), ignoreCase = true) }
            ?: return@runCatching false
        val file = fileFor(home) ?: return@runCatching false
        file.parentFile?.mkdirs()
        file.writeText(known.id)
        true
    }.getOrDefault(false)

    fun clear(home: String? = System.getProperty("user.home")): Boolean = runCatching {
        fileFor(home)?.delete() ?: false
    }.getOrDefault(false)

    private fun fileFor(home: String?): File? =
        home?.takeIf { it.isNotBlank() }?.let { File(File(it, ".atropos"), "theme") }
}
