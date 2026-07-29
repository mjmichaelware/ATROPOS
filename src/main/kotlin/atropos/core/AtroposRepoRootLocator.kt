package atropos.core

import java.nio.file.Files
import java.nio.file.Path

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
