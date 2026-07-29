package atropos.core

import java.nio.file.Files
import kotlin.test.Test
import kotlin.test.assertEquals

class AtroposRepoRootLocatorTest {
    @Test
    fun resolves_atropos_root_from_nested_app_directory() {
        val root = Files.createTempDirectory("atropos-root-locator-")
        Files.writeString(root.resolve("settings.gradle.kts"), "pluginManagement {}\n")
        Files.writeString(root.resolve("build.gradle.kts"), "plugins {}\n")
        Files.createDirectories(root.resolve("src/main/kotlin/atropos"))
        val nested = root.resolve("apps/specgraph-foundry")
        Files.createDirectories(nested)

        assertEquals(root, AtroposRepoRootLocator.resolve(nested))
    }
}
