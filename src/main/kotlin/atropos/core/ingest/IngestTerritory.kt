/* SPDX-License-Identifier: AGPL-3.0-only */
package atropos.core.ingest

import java.nio.file.Files
import java.nio.file.Path

/**
 * Which directories an `@mention` is allowed to read from.
 *
 * The launch directory alone was the whole boundary, which is right for a
 * tool run inside a repository and wrong for the way this one is actually
 * used: on a phone, the document an operator wants to feed the engine is in
 * Downloads, and the engine is running in a source tree. `@spec.docx` was
 * refused for being outside territory when the operator could see the file on
 * the same screen.
 *
 * ## Precedence, and why widening is never silent
 *
 * 1. **The launch directory**, always. Territory can be added to; the place
 *    the operator started the tool cannot be taken away.
 * 2. **`ATROPOS_INGEST_ROOTS`** — a `File.pathSeparator` list, for this run.
 * 3. **`.atropos/ingest-roots`** — one path per line, durably, in the
 *    workspace. Blank lines and `#` comments ignored.
 * 4. **Android shared storage**, when it is already there.
 *
 * The fourth needs its reasoning stated, because it reads like the boundary
 * granting itself a wider boundary. `~/storage/shared` and its siblings are
 * symlinks Termux creates only after the operator runs `termux-setup-storage`
 * and approves the OS permission dialog. Their existence *is* an operator
 * grant, made deliberately, one level down. ATROPOS honouring a grant the
 * operator already gave is different from ATROPOS awarding itself one — and
 * nothing here reaches a directory the shell the operator launched from could
 * not already read.
 *
 * Every root carries the [Source] that produced it, and refusals name the
 * roots, so an operator can always see the boundary they are standing in
 * rather than inferring it from what failed.
 */
class IngestTerritory(
    private val launchDirectory: Path,
    private val env: (String) -> String? = System::getenv,
    private val homeDirectory: () -> Path? = {
        System.getProperty("user.home")?.takeIf(String::isNotBlank)?.let(Path::of)
    },
    private val isDirectory: (Path) -> Boolean = { path ->
        Files.isDirectory(path) && Files.isReadable(path)
    },
    private val realPath: (Path) -> Path? = { path -> runCatching { path.toRealPath() }.getOrNull() }
) {

    enum class Source {
        LAUNCH_DIRECTORY,
        ENVIRONMENT,
        WORKSPACE_FILE,
        SHARED_STORAGE;

        fun label(): String = name.lowercase().replace('_', ' ')
    }

    data class Root(val path: Path, val source: Source)

    /**
     * The granted roots, launch directory first, without duplicates.
     *
     * Each configured root is recorded both as written and as its real path,
     * because the two differ across a symlink and an operator may mention
     * either: `@storage/shared/Download/spec.docx` goes through the link,
     * `@/storage/emulated/0/Download/spec.docx` goes around it, and refusing
     * the second while allowing the first would be arbitrary.
     */
    fun roots(): List<Root> {
        val found = LinkedHashMap<Path, Root>()

        fun offer(path: Path, source: Source) {
            val normalized = path.toAbsolutePath().normalize()
            if (!isDirectory(normalized)) return
            found.putIfAbsent(normalized, Root(normalized, source))
            realPath(normalized)?.takeIf { it != normalized }?.let { real ->
                found.putIfAbsent(real, Root(real, source))
            }
        }

        offer(launchDirectory, Source.LAUNCH_DIRECTORY)

        env(ENVIRONMENT_KEY)
            ?.split(java.io.File.pathSeparatorChar)
            ?.forEach { entry -> pathOrNull(entry)?.let { offer(it, Source.ENVIRONMENT) } }

        configuredRoots().forEach { offer(it, Source.WORKSPACE_FILE) }

        homeDirectory()?.let { home ->
            SHARED_STORAGE.forEach { name -> offer(home.resolve(name), Source.SHARED_STORAGE) }
        }

        return found.values.toList()
    }

    fun paths(): List<Path> = roots().map(Root::path)

    /** One line per root, for a refusal message or an evidence bundle. */
    fun describe(): String = roots().joinToString("\n") { root ->
        "  ${root.path} (${root.source.label()})"
    }

    private fun configuredRoots(): List<Path> {
        val file = launchDirectory.resolve(WORKSPACE_FILE)
        if (!Files.isRegularFile(file)) return emptyList()
        return runCatching { Files.readAllLines(file) }.getOrDefault(emptyList())
            .map(String::trim)
            .filter { it.isNotEmpty() && !it.startsWith("#") }
            .mapNotNull(::pathOrNull)
    }

    private fun pathOrNull(value: String): Path? {
        val trimmed = value.trim()
        if (trimmed.isEmpty()) return null
        // `~` is shell syntax the JVM never expands, and an operator writing it
        // into a config file means their home directory, not a directory named
        // `~` in the workspace.
        val expanded =
            if (trimmed == "~" || trimmed.startsWith("~/")) {
                homeDirectory()?.resolve(trimmed.removePrefix("~").removePrefix("/")) ?: return null
            } else {
                Path.of(trimmed)
            }
        return runCatching { launchDirectory.resolve(expanded) }.getOrNull()
    }

    private companion object {
        const val ENVIRONMENT_KEY = "ATROPOS_INGEST_ROOTS"
        const val WORKSPACE_FILE = ".atropos/ingest-roots"

        /**
         * The links `termux-setup-storage` creates.
         *
         * `shared` is the whole of internal storage and covers the rest; the
         * others are listed because a device may have granted some and not
         * others, and because naming them makes the boundary readable in
         * [describe] rather than opaque.
         */
        val SHARED_STORAGE = listOf(
            "storage/shared",
            "storage/downloads",
            "storage/dcim",
            "storage/pictures",
            "storage/documents",
            "storage/music",
            "storage/movies",
            "storage/external-1"
        )
    }
}
