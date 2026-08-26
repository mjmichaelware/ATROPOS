/* SPDX-License-Identifier: AGPL-3.0-only */
package atropos.cli.ui

import kotlin.test.Test
import kotlin.test.*

class DiffContentParserTest {

    private val parser = DiffContentParser()

    @Test
    fun `empty input produces empty content`() {
        val result = parser.parse("")
        assertTrue(result.files.isEmpty())
        assertEquals(0, result.totalFiles)
    }

    @Test
    fun `blank input produces empty content`() {
        val result = parser.parse("   \n  \n  ")
        assertTrue(result.files.isEmpty())
    }

    @Test
    fun `single file add-only patch`() {
        val diff = """
            diff --git a/src/Example.kt b/src/Example.kt
            --- a/src/Example.kt
            +++ b/src/Example.kt
            @@ -10,3 +10,5 @@ fun example() {
                 val a = 1
                 val b = 2
                 val c = 3
            +    val d = 4
            +    val e = 5
        """.trimIndent()

        val result = parser.parse(diff)
        assertEquals(1, result.totalFiles)
        assertEquals(2, result.totalAdditions)
        assertEquals(0, result.totalDeletions)

        val file = result.files.first()
        assertEquals("a/src/Example.kt", file.oldPath)
        assertEquals("b/src/Example.kt", file.newPath)
        assertEquals("src/Example.kt", file.displayPath)
        assertFalse(file.isNewFile)
        assertFalse(file.isDeletedFile)

        assertEquals(1, file.hunks.size)
        val hunk = file.hunks.first()
        assertEquals(10, hunk.oldStart)
        assertEquals(3, hunk.oldCount)
        assertEquals(10, hunk.newStart)
        assertEquals(5, hunk.newCount)
        assertEquals("fun example() {", hunk.sectionLabel)
    }

    @Test
    fun `new file patch`() {
        val diff = """
            diff --git a/new.txt b/new.txt
            --- /dev/null
            +++ b/new.txt
            @@ -0,0 +1,3 @@
            +line 1
            +line 2
            +line 3
        """.trimIndent()

        val result = parser.parse(diff)
        assertEquals(1, result.totalFiles)

        val file = result.files.first()
        assertTrue(file.isNewFile)
        assertEquals("new.txt", file.displayPath)
        assertEquals(3, file.totalAdditions)
    }

    @Test
    fun `deleted file patch`() {
        val diff = """
            diff --git a/old.txt b/old.txt
            --- a/old.txt
            +++ /dev/null
            @@ -1,2 +0,0 @@
            -line 1
            -line 2
        """.trimIndent()

        val result = parser.parse(diff)
        val file = result.files.first()
        assertTrue(file.isDeletedFile)
        assertEquals("old.txt", file.displayPath)
        assertEquals(2, file.totalDeletions)
    }

    @Test
    fun `multi-file diff`() {
        val diff = """
            diff --git a/a.kt b/a.kt
            --- a/a.kt
            +++ b/a.kt
            @@ -1,3 +1,4 @@
             line 1
            +added
             line 2
             line 3
            diff --git a/b.kt b/b.kt
            --- a/b.kt
            +++ b/b.kt
            @@ -5,3 +5,2 @@
             keep
            -remove
             keep
        """.trimIndent()

        val result = parser.parse(diff)
        assertEquals(2, result.totalFiles)
        assertEquals(1, result.totalAdditions)
        assertEquals(1, result.totalDeletions)
        assertEquals("a.kt", result.files[0].displayPath)
        assertEquals("b.kt", result.files[1].displayPath)
    }

    @Test
    fun `hunk with multiple additions and removals tracks line numbers`() {
        val diff = """
            --- a/file.kt
            +++ b/file.kt
            @@ -1,4 +1,4 @@
             context
            -old line
            +new line
             context
            -another old
            +another new
        """.trimIndent()

        val result = parser.parse(diff)
        val hunk = result.files.first().hunks.first()
        val addLines = hunk.lines.filter { it.kind == DiffLine.Kind.ADD }
        val removeLines = hunk.lines.filter { it.kind == DiffLine.Kind.REMOVE }

        assertEquals(2, addLines.size)
        assertEquals(2, removeLines.size)

        // Line numbers should be tracked
        assertNotNull(addLines[0].newLineNumber)
        assertNull(addLines[0].oldLineNumber)
        assertNotNull(removeLines[0].oldLineNumber)
        assertNull(removeLines[0].newLineNumber)
    }

    @Test
    fun `no newline at end of file marker is preserved`() {
        val diff = """
            --- a/file.txt
            +++ b/file.txt
            @@ -1,1 +1,1 @@
            -old
            +new
            \ No newline at end of file
        """.trimIndent()

        val result = parser.parse(diff)
        val hunk = result.files.first().hunks.first()
        val noNewline = hunk.lines.filter { it.kind == DiffLine.Kind.NO_NEWLINE }
        assertEquals(1, noNewline.size)
    }

    @Test
    fun `DiffContent EMPTY constant`() {
        assertTrue(DiffContent.EMPTY.files.isEmpty())
        assertEquals(0, DiffContent.EMPTY.totalFiles)
        assertEquals(0, DiffContent.EMPTY.totalAdditions)
        assertEquals(0, DiffContent.EMPTY.totalDeletions)
    }

    @Test
    fun `rename detection`() {
        val diff = """
            diff --git a/old.kt b/new.kt
            --- a/old.kt
            +++ b/new.kt
            @@ -1,1 +1,1 @@
             same content
        """.trimIndent()

        val result = parser.parse(diff)
        val file = result.files.first()
        assertTrue(file.isRename)
        assertEquals("new.kt", file.displayPath)
    }
}
