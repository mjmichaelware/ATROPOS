/* SPDX-License-Identifier: AGPL-3.0-only */
package atropos.cli.ui

import org.junit.jupiter.api.Test
import org.junit.jupiter.api.Assertions.*

class DiffContentRendererTest {

    private val theme = TerminalTheme(
        atropos.cli.config.ConfigurationManager(),
        tierOverride = atropos.cli.ui.design.ColorTier.NONE
    )
    private val renderer = DiffContentRenderer(theme)
    private val parser = DiffContentParser()

    @Test
    fun `empty diff renders placeholder`() {
        val lines = renderer.render(DiffContent.EMPTY, 80)
        assertEquals(1, lines.size)
        assertTrue(lines[0].contains("empty diff"))
    }

    @Test
    fun `single file diff renders file header and hunks`() {
        val diff = parser.parse("""
            diff --git a/file.kt b/file.kt
            --- a/file.kt
            +++ b/file.kt
            @@ -1,3 +1,4 @@
             line 1
            +added line
             line 2
             line 3
        """.trimIndent())

        val lines = renderer.render(diff, 80)
        assertTrue(lines.isNotEmpty())
        // Should contain a summary header
        assertTrue(lines.any { it.contains("DIFF") })
        // Should contain file path
        assertTrue(lines.any { it.contains("file.kt") })
        // Should contain hunk header
        assertTrue(lines.any { it.contains("@@") })
        // Should contain the added line text
        assertTrue(lines.any { it.contains("added line") })
    }

    @Test
    fun `compact summary for inline display`() {
        val diff = parser.parse("""
            diff --git a/a.kt b/a.kt
            --- a/a.kt
            +++ b/a.kt
            @@ -1,1 +1,2 @@
             existing
            +new
            diff --git a/b.kt b/b.kt
            --- a/b.kt
            +++ b/b.kt
            @@ -1,2 +1,1 @@
             keep
            -remove
        """.trimIndent())

        val summary = renderer.compactSummary(diff)
        assertTrue(summary.contains("2 file"))
        // In NO_COLOR mode, the raw text is present without ANSI codes
        assertTrue(summary.contains("+1"))
        assertTrue(summary.contains("-1"))
    }

    @Test
    fun `compact summary for empty diff`() {
        assertEquals("no changes", renderer.compactSummary(DiffContent.EMPTY))
    }

    @Test
    fun `collapsed paths hide hunks`() {
        val diff = parser.parse("""
            diff --git a/file.kt b/file.kt
            --- a/file.kt
            +++ b/file.kt
            @@ -1,1 +1,2 @@
             existing
            +new
        """.trimIndent())

        val expanded = renderer.render(diff, 80)
        val collapsed = renderer.render(diff, 80, collapsedPaths = setOf("file.kt"))

        // Collapsed should have fewer lines (no hunk content)
        assertTrue(collapsed.size < expanded.size)
        assertTrue(collapsed.any { it.contains("collapsed") })
    }

    @Test
    fun `width safety - no line exceeds requested width`() {
        val diff = parser.parse("""
            diff --git a/very/long/path/to/some/deeply/nested/file.kt b/very/long/path/to/some/deeply/nested/file.kt
            --- a/very/long/path/to/some/deeply/nested/file.kt
            +++ b/very/long/path/to/some/deeply/nested/file.kt
            @@ -1,1 +1,2 @@
             This is a very long line of context that should be properly clipped when the terminal width is narrow
            +This is a very long added line that also needs to be properly truncated to fit within bounds
        """.trimIndent())

        val lines = renderer.render(diff, 40)
        for (line in lines) {
            // Cell width of each line should not exceed 40
            val cellWidth = TerminalText.cellWidth(line)
            assertTrue(cellWidth <= 40, "Line exceeds width 40: cellWidth=$cellWidth, line='$line'")
        }
    }

    @Test
    fun `new file shows new status`() {
        val diff = parser.parse("""
            diff --git a/new.kt b/new.kt
            --- /dev/null
            +++ b/new.kt
            @@ -0,0 +1,1 @@
            +content
        """.trimIndent())

        val lines = renderer.render(diff, 80)
        assertTrue(lines.any { it.contains("new") })
    }

    @Test
    fun `deleted file shows deleted status`() {
        val diff = parser.parse("""
            diff --git a/old.kt b/old.kt
            --- a/old.kt
            +++ /dev/null
            @@ -1,1 +0,0 @@
            -content
        """.trimIndent())

        val lines = renderer.render(diff, 80)
        assertTrue(lines.any { it.contains("deleted") })
    }

    @Test
    fun `renderFile works for single file`() {
        val diff = parser.parse("""
            diff --git a/file.kt b/file.kt
            --- a/file.kt
            +++ b/file.kt
            @@ -1,1 +1,2 @@
             existing
            +new
        """.trimIndent())

        val lines = renderer.renderFile(diff.files.first(), 80)
        assertTrue(lines.isNotEmpty())
        assertTrue(lines.any { it.contains("file.kt") })
    }
}
