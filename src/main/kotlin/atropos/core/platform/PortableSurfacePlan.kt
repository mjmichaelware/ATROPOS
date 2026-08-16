package atropos.core.platform

import java.nio.file.Files
import java.nio.file.Path

data class PortableSurfacePlanReport(
    val planPath: Path,
    val missingMarkers: List<String>,
) {
    val valid: Boolean get() = missingMarkers.isEmpty()
}

/** The single executable owner for the Docker/native/desktop/Android/Web plan. */
object PortableSurfacePlan {
    private val requiredMarkers = listOf(
        "src/main/kotlin/atropos/core",
        "AtroposRepoRootLocator",
        "Packaging and installation proof",
        "must not create a second DAG",
    )

    fun inspect(repoRoot: Path): PortableSurfacePlanReport {
        val path = repoRoot.resolve("docs/architecture/DOCKER_NATIVE_DESKTOP_ANDROID_WEB_PLAN.md").normalize()
        val text = runCatching { Files.readString(path) }.getOrDefault("")
        return PortableSurfacePlanReport(path, requiredMarkers.filterNot(text::contains))
    }
}
