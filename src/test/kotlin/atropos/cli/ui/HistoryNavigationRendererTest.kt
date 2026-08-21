/* SPDX-License-Identifier: AGPL-3.0-only */
package atropos.cli.ui

import org.junit.jupiter.api.Test
import org.junit.jupiter.api.Assertions.*

class HistoryNavigationRendererTest {

    private val theme = TerminalTheme(
        atropos.cli.config.ConfigurationManager(),
        tierOverride = atropos.cli.ui.design.ColorTier.NONE
    )
    private val renderer = HistoryNavigationRenderer(theme)

    private fun sampleEntries() = listOf(
        HistoryEntry("1", "10:00:01", HistoryEntryKind.PROMPT, "hello world"),
        HistoryEntry("2", "10:00:02", HistoryEntryKind.RESPONSE, "response text", provider = "groq"),
        HistoryEntry("3", "10:00:03", HistoryEntryKind.COMMAND, "/status"),
        HistoryEntry("4", "10:00:04", HistoryEntryKind.VERIFICATION, "compile check", durationMs = 1500),
        HistoryEntry("5", "10:00:05", HistoryEntryKind.ERROR, "provider timeout", detail = "Connection refused")
    )

    @Test
    fun `empty state produces empty output`() {
        val state = HistoryNavigationState(entries = emptyList())
        val lines = renderer.render(state, 80, 20)
        assertTrue(lines.isEmpty())
    }

    @Test
    fun `basic rendering produces timeline header`() {
        val state = HistoryNavigationState(entries = sampleEntries(), focusedIndex = 0)
        val lines = renderer.render(state, 80, 20)
        assertTrue(lines.isNotEmpty())
        assertTrue(lines.any { it.contains("HISTORY") })
    }

    @Test
    fun `compact mode renders one line per entry`() {
        val entries = sampleEntries()
        val compact = HistoryNavigationState(entries = entries, focusedIndex = 0, compactMode = true)
        val full = HistoryNavigationState(entries = entries, focusedIndex = 0, compactMode = false)

        val compactLines = renderer.render(compact, 80, 40)
        val fullLines = renderer.render(full, 80, 40)

        // Compact should be shorter or equal
        assertTrue(compactLines.size <= fullLines.size)
    }

    @Test
    fun `entry one-liner produces single line`() {
        val entry = HistoryEntry("1", "10:00:01", HistoryEntryKind.PROMPT, "test prompt")
        val line = renderer.entryOneLiner(entry, 80)
        assertFalse(line.contains("\n"))
        assertTrue(line.contains("test prompt"))
    }

    @Test
    fun `focus navigation moves correctly`() {
        val state = HistoryNavigationState(entries = sampleEntries(), focusedIndex = 0)

        val next = state.focusNext()
        assertEquals(1, next.focusedIndex)

        val nextNext = next.focusNext()
        assertEquals(2, nextNext.focusedIndex)

        val prev = nextNext.focusPrevious()
        assertEquals(1, prev.focusedIndex)
    }

    @Test
    fun `focus does not go out of bounds`() {
        val entries = sampleEntries()
        val state = HistoryNavigationState(entries = entries, focusedIndex = entries.lastIndex)

        val next = state.focusNext()
        assertEquals(entries.lastIndex, next.focusedIndex)

        val atStart = HistoryNavigationState(entries = entries, focusedIndex = 0)
        val prev = atStart.focusPrevious()
        assertEquals(0, prev.focusedIndex)
    }

    @Test
    fun `toggle expansion adds and removes IDs`() {
        val state = HistoryNavigationState(entries = sampleEntries(), focusedIndex = 0)
        assertTrue(state.expandedIds.isEmpty())

        val expanded = state.toggleFocusedExpansion()
        assertTrue("1" in expanded.expandedIds)

        val collapsed = expanded.toggleFocusedExpansion()
        assertTrue("1" !in collapsed.expandedIds)
    }

    @Test
    fun `jump to entry by ID`() {
        val state = HistoryNavigationState(entries = sampleEntries(), focusedIndex = 0)
        val jumped = state.jumpTo("4")
        assertEquals(3, jumped.focusedIndex)
    }

    @Test
    fun `jump to nonexistent ID is no-op`() {
        val state = HistoryNavigationState(entries = sampleEntries(), focusedIndex = 2)
        val jumped = state.jumpTo("nonexistent")
        assertEquals(2, jumped.focusedIndex)
    }

    @Test
    fun `collapse all and expand all`() {
        val state = HistoryNavigationState(entries = sampleEntries(), focusedIndex = 0)

        val expanded = state.expandAll()
        assertEquals(5, expanded.expandedIds.size)
        assertFalse(expanded.compactMode)

        val collapsed = expanded.collapseAll()
        assertTrue(collapsed.expandedIds.isEmpty())
        assertTrue(collapsed.compactMode)
    }

    @Test
    fun `width safety - no line exceeds width`() {
        val state = HistoryNavigationState(
            entries = sampleEntries(),
            focusedIndex = 2,
            expandedIds = setOf("5")
        )
        val lines = renderer.render(state, 40, 30)
        for (line in lines) {
            val cellWidth = TerminalText.cellWidth(line)
            assertTrue(cellWidth <= 40, "Line exceeds width: $cellWidth")
        }
    }

    @Test
    fun `entries with all kinds render without error`() {
        val entries = HistoryEntryKind.entries.mapIndexed { i, kind ->
            HistoryEntry("entry-$i", "12:${i.toString().padStart(2, '0')}:00", kind, "test $kind")
        }
        val state = HistoryNavigationState(entries = entries, focusedIndex = 0)
        val lines = renderer.render(state, 80, 40)
        assertTrue(lines.isNotEmpty())
    }

    @Test
    fun `duration formatting`() {
        val entry = HistoryEntry(
            "1", "10:00:01", HistoryEntryKind.TOOL, "shell",
            durationMs = 65_000 // 1m 5s
        )
        val line = renderer.entryOneLiner(entry, 120)
        assertTrue(line.contains("1m"))
    }
}
