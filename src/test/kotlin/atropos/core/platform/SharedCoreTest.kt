/* SPDX-License-Identifier: AGPL-3.0-only */
package atropos.core.platform

import java.nio.file.Files
import java.nio.file.Path
import kotlin.io.path.createDirectories
import kotlin.io.path.writeText
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class SharedCoreTest {

    private fun tempSource(vararg files: Pair<String, String>): Path {
        val root = Files.createTempDirectory("shared-core")
        files.forEach { (name, body) ->
            val path = root.resolve(name)
            path.parent.createDirectories()
            path.writeText(body)
        }
        return root
    }

    @Test
    fun `the real core carries no platform-only import`() {
        // The assertion this atom exists for. It runs against the shipped
        // source tree, not a fixture.
        val root = Path.of("src/main/kotlin/atropos/core")
        val result = SharedCore.scan(root)

        assertTrue(result.scanned > 0, "no core sources were scanned: ${result.render()}")
        assertTrue(result.isShareable, result.render())
    }

    @Test
    fun `an android import is caught`() {
        val root = tempSource(
            "a/Leak.kt" to "package a\nimport android.content.Context\nclass Leak"
        )

        val result = SharedCore.scan(root)
        assertEquals(1, result.violations.size)
        assertEquals("android.content.Context", result.violations.first().import)
        assertEquals(2, result.violations.first().line)
        assertFalse(result.isShareable)
    }

    @Test
    fun `a desktop toolkit import is caught`() {
        val root = tempSource("a/Ui.kt" to "package a\nimport javax.swing.JFrame\nclass Ui")

        assertFalse(SharedCore.scan(root).isShareable)
    }

    @Test
    fun `an aliased import is still caught`() {
        val root = tempSource("a/Ui.kt" to "package a\nimport java.awt.Color as Colour\nclass Ui")

        assertEquals("java.awt.Color", SharedCore.scan(root).violations.single().import)
    }

    @Test
    fun `ordinary jvm imports are left alone`() {
        val root = tempSource(
            "a/Fine.kt" to "package a\nimport java.nio.file.Path\nimport java.time.Instant\nclass Fine"
        )

        val result = SharedCore.scan(root)
        assertTrue(result.violations.isEmpty())
        assertTrue(result.isShareable)
    }

    @Test
    fun `a scan that examined nothing is not a pass`() {
        // Absence of a finding is not a finding of absence: a checker pointed
        // at a moved directory must never report the core as clean.
        val result = SharedCore.scan(Path.of("src/main/kotlin/atropos/does-not-exist"))

        assertEquals(0, result.scanned)
        assertFalse(result.isShareable, "an empty scan must not pass")
    }

    @Test
    fun `the render names each offending file and line`() {
        val root = tempSource("a/Leak.kt" to "package a\nimport android.os.Build\nclass Leak")

        val rendered = SharedCore.scan(root).render()
        assertTrue(rendered.contains("Leak.kt:2"))
        assertTrue(rendered.contains("android.os.Build"))
        assertTrue(rendered.contains("shareable=false"))
    }
}
