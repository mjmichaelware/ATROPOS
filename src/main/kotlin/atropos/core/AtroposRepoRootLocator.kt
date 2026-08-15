package atropos.core

import java.nio.file.Files
import java.nio.file.Path

/**
 * Finds the root of the work — where `.atropos` state and generated projects
 * belong.
 *
 * Deliberately cwd-derived. This answers "where is the operator working",
 * which must follow the working directory: resolving it from the installation
 * instead would write a user's generated projects into the ATROPOS checkout.
 *
 * For "where is ATROPOS itself installed" — bundled resources such as
 * `apps/specgraph-foundry` — use [AtroposInstallationLocator]. The two were the
 * same function once, and the single cwd-walk silently answered the second
 * question wrong for every operator outside the source tree.
 */
object AtroposRepoRootLocator {
    fun resolve(start: Path = Path.of(System.getProperty("user.dir")).toAbsolutePath().normalize()): Path {
        var current: Path? = start.toAbsolutePath().normalize()
        while (current != null) {
            if (isAtroposRoot(current)) return current
            current = current.parent
        }
        return start.toAbsolutePath().normalize()
    }

    private fun isAtroposRoot(path: Path): Boolean =
        Files.isRegularFile(path.resolve("settings.gradle.kts")) &&
            Files.isRegularFile(path.resolve("build.gradle.kts")) &&
            Files.isDirectory(path.resolve("src/main/kotlin/atropos"))
}
