// SPDX-License-Identifier: AGPL-3.0-only
package atropos.core.verification

import kotlin.test.*

class BatchReporterTest {
    @Test
    fun `report counts lines and calculates basic diff`() {
        val before = listOf("fun a() {", "    print(1)", "}")
        val after = listOf("fun a() {", "    print(2)", "    print(3)", "}")
        
        val report = BatchReporter.report(before, after)
        
        assertEquals(4, report.physicalLines)
        assertEquals(4, report.codeBearingLines)
        assertEquals(2, report.additions) // "    print(2)" and "    print(3)" added
        assertEquals(1, report.deletions) // "    print(1)" deleted
    }

    @Test
    fun `report handles blank lines correctly`() {
        val before = listOf("a", "")
        val after = listOf("a", "", " ", "b")
        
        val report = BatchReporter.report(before, after)
        
        assertEquals(4, report.physicalLines)
        assertEquals(2, report.codeBearingLines) // "a" and "b"
        assertEquals(2, report.additions) // " " and "b"
        assertEquals(0, report.deletions)
    }
}
