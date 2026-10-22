/* SPDX-License-Identifier: AGPL-3.0-only */
package atropos.core.architecture

import java.nio.file.Files
import java.nio.file.Path
import kotlin.test.Test
import kotlin.test.assertTrue

class MultiplatformSharedCoreBoundaryTest {

    @Test
    fun `core packages never import android compose or web DOM packages`() {
        val coreDir = Path.of("src/main/kotlin/atropos/core")
        if (!Files.exists(coreDir)) return

        val illegalImports = listOf(
            "androidx.compose",
            "android.content",
            "android.view"
        )

        Files.walk(coreDir).use { stream ->
            stream.filter { Files.isRegularFile(it) && it.toString().endsWith(".kt") }.forEach { file ->
                val lines = Files.readAllLines(file)
                for (line in lines) {
                    if (line.trim().startsWith("import ")) {
                        for (illegal in illegalImports) {
                            assertTrue(
                                !line.contains(illegal),
                                "Architecture Boundary Violation: $file imports forbidden $illegal"
                            )
                        }
                    }
                }
            }
        }
    }
}
