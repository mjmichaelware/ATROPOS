/* SPDX-License-Identifier: AGPL-3.0-only */
package atropos.core

import java.nio.file.Files
import java.nio.file.Path

/**
 * Finds the checkout the running ATROPOS was built from.
 *
 * Separate from [AtroposRepoRootLocator] because they answer different
 * questions, and conflating them breaks whichever one loses.
 *
 * - [AtroposRepoRootLocator] answers *where is the work*: where generated
 *   projects are written, where `.atropos` state lives, what territory bounds
 *   apply. That must follow the operator's working directory.
 * - This answers *where is ATROPOS itself*: where bundled resources such as
 *   `apps/specgraph-foundry` live. That must follow the installed code.
 *
 * Merging the two into one cwd-walk left the second question unanswerable, and
 * [atropos.core.factory.SpecGraphAtomizer] reported
 * `SKIPPED_SOFT_FAIL:root_missing` for every operator who ran ATROPOS from
 * anywhere but the source tree — which is nearly all of them. Merging them the
 * other way is equally wrong: it relocates a user's generated projects into the
 * ATROPOS checkout.
 */
object AtroposInstallationLocator {

    /**
     * The installation checkout, or null when the code is not running from one.
     *
     * Null rather than a fallback. A packaged distribution with no source tree
     * beside it genuinely has no SpecGraph, and inventing a path would turn that
     * into a confusing failure deeper in — the caller's "not configured" branch
     * is the correct answer.
     */
    fun resolve(): Path? = runCatching {
        val location = AtroposInstallationLocator::class.java
            .protectionDomain
            ?.codeSource
            ?.location
            ?: return null

        val start = Path.of(location.toURI()).toAbsolutePath().normalize()

        // A jar gives the jar's own path (build/libs/ATROPOS.jar); a classes
        // directory gives the directory. Both reduce to walking up from a
        // filesystem location until a checkout root appears.
        walkUp(if (Files.isDirectory(start)) start else start.parent ?: return null)
    }.getOrNull()

    /**
     * The installation's copy of [relativePath], when it exists.
     *
     * The intended entry point for bundled resources: it answers "is this
     * available" and "where" in one call, so a caller cannot accidentally build
     * a path against an installation that was never found.
     */
    fun resource(relativePath: String): Path? =
        resolve()?.resolve(relativePath)?.takeIf { Files.exists(it) }

    private fun walkUp(from: Path): Path? {
        var current: Path? = from
        while (current != null) {
            if (isAtroposRoot(current)) return current
            current = current.parent
        }
        return null
    }

    private fun isAtroposRoot(path: Path): Boolean =
        Files.isRegularFile(path.resolve("settings.gradle.kts")) &&
            Files.isRegularFile(path.resolve("build.gradle.kts")) &&
            Files.isDirectory(path.resolve("src/main/kotlin/atropos"))
}
