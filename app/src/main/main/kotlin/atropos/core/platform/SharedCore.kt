/* SPDX-License-Identifier: AGPL-3.0-only */
package atropos.core.platform

import java.nio.file.Files
import java.nio.file.Path

/**
 * What the shared core is allowed to depend on.
 *
 * ATROPOS runs as a CLI, a TUI and an Android build over the same engine. That
 * only holds if the engine's core never reaches for an API that exists on one
 * of them: a single `javax.swing` import in a verifier compiles fine on a
 * desktop JVM and fails at class-load time on a phone, which is the worst place
 * to discover it — the operator sees a crash, not a build error.
 *
 * The usual mitigation is a rule in a design document. This is the rule as a
 * check, run against the actual source tree, because a convention nobody can
 * fail is a convention that decays.
 *
 * It reads imports rather than bytecode deliberately. Bytecode analysis would
 * catch more, but it needs a build to have succeeded first; this can run before
 * anything is compiled, which is when the answer is cheapest to act on.
 */
object SharedCore {

    /**
     * Package prefixes the shared core may not import.
     *
     * Desktop toolkits and Android platform classes both fail the same way —
     * present on exactly one target. `java.awt.Color` is included even though
     * it is tempting for a terminal palette: colour belongs to the surface, and
     * a core that knows about it has already leaked presentation downward.
     */
    val FORBIDDEN_PREFIXES: List<String> = listOf(
        "java.awt",
        "javax.swing",
        "javafx",
        "android.",
        "androidx.",
        "java.applet",
        "javax.sound",
        "java.beans"
    )

    /**
     * Source roots that must remain platform-free.
     *
     * The bridge and the CLI are not listed: the bridge owns a socket and the
     * CLI owns a terminal, and both are legitimately platform-facing. Naming
     * only the core keeps the check honest rather than aspirational.
     */
    val SHARED_ROOTS: List<String> = listOf("atropos/core")

    data class Violation(
        val file: Path,
        val line: Int,
        val import: String
    ) {
        fun render(): String = "$file:$line imports $import"
    }

    /**
     * Scans a source tree for imports the shared core may not carry.
     *
     * Returns an empty list for a missing directory rather than throwing: a
     * scan that cannot find its input has found nothing, and reporting that as
     * "clean" is why [scanned] is returned alongside. A caller asserting zero
     * violations without asserting a nonzero file count is asserting nothing.
     */
    fun scan(root: Path): ScanResult {
        if (!Files.isDirectory(root)) {
            return ScanResult(scanned = 0, violations = emptyList())
        }

        var scanned = 0
        val violations = mutableListOf<Violation>()

        Files.walk(root).use { paths ->
            paths.filter { Files.isRegularFile(it) && it.fileName.toString().endsWith(".kt") }.forEach { file ->
                scanned += 1
                Files.readString(file).lineSequence().forEachIndexed { index, raw ->
                    val line = raw.trim()
                    if (!line.startsWith("import ")) return@forEachIndexed
                    val imported = line.removePrefix("import ").substringBefore(" as ").trim()
                    FORBIDDEN_PREFIXES.firstOrNull { imported.startsWith(it) }?.let {
                        violations += Violation(file, index + 1, imported)
                    }
                }
            }
        }

        return ScanResult(scanned = scanned, violations = violations)
    }

    data class ScanResult(
        val scanned: Int,
        val violations: List<Violation>
    ) {
        /**
         * True only when files were actually examined and none violated.
         *
         * A scan of zero files is not a passing scan. This is the same shape as
         * every other conclusiveness rule in the system: absence of a finding
         * is not a finding of absence.
         */
        val isShareable: Boolean
            get() = scanned > 0 && violations.isEmpty()

        fun render(): String =
            "shared_core scanned=$scanned violations=${violations.size} shareable=$isShareable" +
                violations.joinToString("") { "\n  ${it.render()}" }
    }
}
