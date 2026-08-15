/* SPDX-License-Identifier: AGPL-3.0-only */
package atropos.core.verification

import java.io.File
import java.nio.file.Files
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class SourceDoc2RulesTest {

    @Test
    fun `Rule127 removes ANSI and limits columns`() {
        val raw = "hello \u001B[31mworld\u001B[0m very long line here that exceeds forty characters limit"
        val formatted = Rule127Snapshot.formatSnapshot(raw.toByteArray())
        assertEquals(3, formatted.size)
        assertTrue(formatted[40]!!.length <= 40)
        assertFalse(formatted[40]!!.contains("\u001B"))
    }

    @Test
    fun `Rule129 compiles to temp and promotes only when gates pass`() {
        val tempSource = Files.createTempFile("src-", ".kt").toFile()
        tempSource.writeText("content")
        val target = Files.createTempFile("target-", ".kt").toFile()
        target.delete() // start empty

        val success = Rule129Compile.compileAndPromote(
            tempSource,
            target,
            compileFn = { true },
            gateFn = { true }
        )
        assertTrue(success)
        assertEquals("content", target.readText())

        // fails when gate fails
        val target2 = Files.createTempFile("target2-", ".kt").toFile()
        target2.delete()
        val fail = Rule129Compile.compileAndPromote(
            tempSource,
            target2,
            compileFn = { true },
            gateFn = { false }
        )
        assertFalse(fail)
        assertFalse(target2.exists())
    }

    @Test
    fun `Rule137 requires all checks to pass`() {
        assertTrue(Rule137Success.verifySuccess(0, 0, true, 0, true))
        assertFalse(Rule137Success.verifySuccess(1, 0, true, 0, true))
        assertFalse(Rule137Success.verifySuccess(0, 0, false, 0, true))
    }

    @Test
    fun `Rule142 generates termux script commands`() {
        val cmd = Rule142Export.generateExportCommand(listOf("a.kt", "b.kt"))
        assertTrue(cmd.contains("a.kt b.kt"))
        assertTrue(Rule142Export.triggerMediaScan().contains("termux-media-scan"))
    }
}
