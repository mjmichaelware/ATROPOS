// SPDX-License-Identifier: AGPL-3.0-only
package atropos.core.verification

import kotlin.test.*

import java.io.File
import kotlin.io.path.createTempFile
import kotlin.io.path.writeText

class RiskyStdlibScannerTest {
    @Test
    fun `scans and finds risky usages`() {
        val tempFile = createTempFile("TestRisky", ".kt").toFile()
        tempFile.writeText("""
            import java.util.Date
            import kotlinx.coroutines.*
            import kotlin.io.path.Path
            
            fun main() {
                val s = sequenceOf(1).takeLast(1)
            }
        """.trimIndent())
        
        try {
            val results = RiskyStdlibScanner.scan(listOf(tempFile))
            assertEquals(4, results.size)
            
            assertEquals("Java APIs above target", results[0].pattern)
            assertEquals("kotlinx imports above target", results[1].pattern)
            assertEquals("kotlin.io.path", results[2].pattern)
            assertEquals("Sequence.takeLast", results[3].pattern)
            
            assertEquals(1, results[0].line)
            assertEquals(2, results[1].line)
            assertEquals(3, results[2].line)
            assertEquals(6, results[3].line)
        } finally {
            tempFile.delete()
        }
    }
}
