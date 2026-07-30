package atropos.core.verification

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class ArchitectureSourceMaskerTest {
    @Test
    fun masks_non_code_regions_without_shifting_lines() {
        val source = """
            fun real() = 1 // fun fake() = 2
            val text = "class Fake"
            /* fun blockFake() = 3 */
            val triple = ""${'"'}fun tripleFake() = 4""${'"'}
            val character = 'x'
            fun after() = 5
        """.trimIndent()

        val masked = ArchitectureSourceMasker().mask(source)

        assertEquals(source.length, masked.length)
        assertEquals(source.count { it == '\n' }, masked.count { it == '\n' })
        assertTrue(masked.contains("fun real()"))
        assertTrue(masked.contains("fun after()"))
        assertTrue(!masked.contains("fun fake()"))
        assertTrue(!masked.contains("class Fake"))
        assertTrue(!masked.contains("fun blockFake()"))
        assertTrue(!masked.contains("fun tripleFake()"))
    }
}
