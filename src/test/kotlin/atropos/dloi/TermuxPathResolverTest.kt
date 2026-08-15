/* SPDX-License-Identifier: AGPL-3.0-only */
package atropos.dloi

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class TermuxPathResolverTest {

    @Test
    fun `resolves termux path prefix to standard linux path`() {
        val termux = "/data/data/com.termux/files/home/ATROPOS/file.txt"
        assertTrue(TermuxPathResolver.isTermuxPath(termux))
        assertEquals("/home/ATROPOS/file.txt", TermuxPathResolver.toStandardPath(termux))
    }

    @Test
    fun `resolves standard path to termux path`() {
        val standard = "/home/ATROPOS/file.txt"
        assertFalse(TermuxPathResolver.isTermuxPath(standard))
        assertEquals("/data/data/com.termux/files/home/ATROPOS/file.txt", TermuxPathResolver.toTermuxPath(standard))
    }

    @Test
    fun `resolve handles bidirectional path translation`() {
        val termux = "/data/data/com.termux/files/home/ATROPOS/file.txt"
        val standard = "/home/ATROPOS/file.txt"
        val root = "/root/ATROPOS/file.txt"

        assertEquals(standard, TermuxPathResolver.resolve(termux))
        assertEquals(termux, TermuxPathResolver.resolve(standard))
        assertEquals(termux, TermuxPathResolver.resolve(root))
        assertEquals("/tmp/file.txt", TermuxPathResolver.resolve("/tmp/file.txt"))
    }
}
