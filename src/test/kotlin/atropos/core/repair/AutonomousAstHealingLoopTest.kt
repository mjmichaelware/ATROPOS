/* SPDX-License-Identifier: AGPL-3.0-only */
package atropos.core.repair

import java.nio.file.Path
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotNull
import kotlin.test.assertTrue

class AutonomousAstHealingLoopTest {

    @Test
    fun `slices compiler logs and generates surgical healing iterations`() {
        val loop = AutonomousAstHealingLoop(Path.of("."))

        val rawLog = """
            > Task :compileKotlin FAILED
            e: file:///root/ATROPOS/src/Main.kt:15:20 Unresolved reference: foo
            e: file:///root/ATROPOS/src/Utils.kt:32:5 Type mismatch: inferred type is String but Int was expected
        """.trimIndent()

        val slices = loop.sliceCompilerLog(rawLog)
        assertEquals(2, slices.size)
        assertEquals(15, slices[0].line)
        assertEquals(20, slices[0].column)
        assertTrue(slices[0].message.contains("Unresolved reference: foo"))

        val healResult = loop.executeRepairIteration(1, slices[0], "+ original diff")
        assertTrue(healResult.diagnosticResolved)
        assertNotNull(healResult.repairedDiff)
        assertTrue(loop.verifyHealedDiff("+ original diff", healResult.repairedDiff!!))
    }
}
