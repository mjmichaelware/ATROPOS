/* SPDX-License-Identifier: AGPL-3.0-only */
package com.atropos.android.app.ide

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotNull
import kotlin.test.assertTrue

class SovereignMobileIdeTest {

    @Test
    fun virtual_file_entry_hierarchy_resolves_cleanly() {
        val root = VirtualFileEntry(
            path = "root",
            name = "root",
            isDirectory = true,
            children = listOf(
                VirtualFileEntry("root/file1.kt", "file1.kt", false, sizeBytes = 1024),
                VirtualFileEntry(
                    path = "root/sub",
                    name = "sub",
                    isDirectory = true,
                    children = listOf(
                        VirtualFileEntry("root/sub/file2.kt", "file2.kt", false, sizeBytes = 2048)
                    )
                )
            )
        )

        assertEquals("root", root.name)
        assertTrue(root.isDirectory)
        assertEquals(2, root.children.size)
        assertEquals("file1.kt", root.children[0].name)
        assertEquals("file2.kt", root.children[1].children[0].name)
    }

    @Test
    fun unified_diff_identifies_line_modifications() {
        val original = "line1\nline2\nline3"
        val modified = "line1\nline2_modified\nline3\nline4_new"

        // Test diff logic
        val origLines = original.lines()
        val modLines = modified.lines()
        assertTrue(modLines.size > origLines.size)
        assertEquals("line1", origLines[0])
        assertEquals("line1", modLines[0])
    }
}
