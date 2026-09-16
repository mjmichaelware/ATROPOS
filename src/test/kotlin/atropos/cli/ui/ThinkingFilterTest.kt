/* SPDX-License-Identifier: AGPL-3.0-only */
package atropos.cli.ui

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue
import kotlin.test.assertFalse

class ThinkingFilterTest {

    @Test
    fun `thinking level cycles L1 -> L2 -> L3 -> L1`() {
        val filter = ThinkingFilter()

        assertEquals(ThinkingLevel.L1, filter.current)

        filter.cycle()
        assertEquals(ThinkingLevel.L2, filter.current)

        filter.cycle()
        assertEquals(ThinkingLevel.L3, filter.current)

        filter.cycle()
        assertEquals(ThinkingLevel.L1, filter.current)
    }

    @Test
    fun `set level directly`() {
        val filter = ThinkingFilter()

        filter.setLevel(ThinkingLevel.L3)
        assertEquals(ThinkingLevel.L3, filter.current)

        filter.setLevel(ThinkingLevel.L1)
        assertEquals(ThinkingLevel.L1, filter.current)
    }

    @Test
    fun `filter removes lines deeper than current level`() {
        val filter = ThinkingFilter()
        filter.setLevel(ThinkingLevel.L2)

        val lines = listOf(
            "L1: outline",
            "L2: step 1",
            "L2: step 2",
            "L3: detail",
            "L3: more detail",
            "L1: back to outline"
        )
        val depths = listOf(1, 2, 2, 3, 3, 1)

        val filtered = filter.filter(lines, depths)

        assertEquals(listOf("L1: outline", "L2: step 1", "L2: step 2", "L1: back to outline"), filtered)
    }

    @Test
    fun `L1 shows only outline`() {
        val filter = ThinkingFilter()
        filter.setLevel(ThinkingLevel.L1)

        val lines = listOf("L1: outline", "L2: step", "L3: detail")
        val depths = listOf(1, 2, 3)

        val filtered = filter.filter(lines, depths)

        assertEquals(listOf("L1: outline"), filtered)
    }

    @Test
    fun `L3 shows everything`() {
        val filter = ThinkingFilter()
        filter.setLevel(ThinkingLevel.L3)

        val lines = listOf("L1: outline", "L2: step", "L3: detail")
        val depths = listOf(1, 2, 3)

        val filtered = filter.filter(lines, depths)

        assertEquals(lines, filtered)
    }
}