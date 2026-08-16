/* SPDX-License-Identifier: AGPL-3.0-only */
package atropos.core.platform

import java.nio.file.Files
import java.nio.file.Path

data class PlatformModule(
    val id: String,
    val path: String,
    val role: String,
)

data class PlatformModuleTopologyReport(
    val root: Path,
    val modules: List<PlatformModule>,
    val missingPaths: List<String>,
) {
    val valid: Boolean get() = missingPaths.isEmpty()
}

/** Single topology owner for engine, surfaces, server, and shared contracts. */
object PlatformModuleTopology {
    val canonicalModules: List<PlatformModule> = listOf(
        PlatformModule("core", "core/build.gradle.kts", "shared engine contracts"),
        PlatformModule("cli", "src/main/kotlin/atropos/cli", "sovereign local operator surface"),
        PlatformModule("desktop", "desktop/build.gradle.kts", "Compose Desktop surface"),
        PlatformModule("androidApp", "app/build.gradle.kts", "Android client surface"),
        PlatformModule("server", "server/build.gradle.kts", "Ktor transport adapter"),
        PlatformModule("shared-ui", "core/src/commonMain/kotlin", "portable shared contracts"),
    )

    fun inspect(root: Path): PlatformModuleTopologyReport {
        val normalized = root.toAbsolutePath().normalize()
        val missing = canonicalModules
            .map { it.path }
            .filterNot { Files.exists(normalized.resolve(it)) }
        return PlatformModuleTopologyReport(normalized, canonicalModules, missing)
    }
}
