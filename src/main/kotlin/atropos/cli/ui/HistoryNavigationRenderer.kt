/* SPDX-License-Identifier: AGPL-3.0-only */
package atropos.cli.ui

import atropos.cli.ui.design.Glyphs
import atropos.cli.ui.design.Role

/**
 * Data model and renderer for conversation/execution history navigation.
 *
 * Provides a timeline view with jump and collapse controls that can be
 * displayed in the transcript area. History entries are typed by kind
 * and carry enough metadata for truthful display without fabrication.
 *
 * Pure renderer: never reads state; receives it as parameters.
 */

// ---- data model -------------------------------------------------------------

/** One entry in the history timeline. */
data class HistoryEntry(
    /** Unique identifier for jump targeting. */
    val id: String,
    /** Display timestamp. */
    val timestamp: String,
    /** What kind of entry this is. */
    val kind: HistoryEntryKind,
    /** Primary display text. */
    val title: String,
    /** Optional detail text (shown when expanded). */
    val detail: String? = null,
    /** Optional provider name for provider-originated entries. */
    val provider: String? = null,
    /** Whether this entry's detail region is expanded. */
    val expanded: Boolean = false,
    /** Whether this entry is the current jump target (highlighted). */
    val active: Boolean = false,
    /** Duration in milliseconds, if applicable. */
    val durationMs: Long? = null,
    /** Associated file paths, if applicable. */
    val paths: List<String> = emptyList()
)

enum class HistoryEntryKind {
    /** User prompt. */
    PROMPT,
    /** Assistant response. */
    RESPONSE,
    /** Command execution (slash commands). */
    COMMAND,
    /** Tool execution (shell, file ops, etc). */
    TOOL,
    /** Verification or compile gate. */
    VERIFICATION,
    /** Patch generation or application. */
    PATCH,
    /** DAG node execution. */
    DAG_NODE,
    /** Error or failure. */
    ERROR,
    /** System event (startup, recovery, etc). */
    SYSTEM
}

/**
 * Navigation state for the history timeline. Tracks which entry is
 * focused and which entries are expanded.
 */
data class HistoryNavigationState(
    /** All history entries in chronological order. */
    val entries: List<HistoryEntry>,
    /** Index of the focused entry (for jump navigation). */
    val focusedIndex: Int = -1,
    /** Which entries have their details expanded, by ID. */
    val expandedIds: Set<String> = emptySet(),
    /** Whether the full timeline is collapsed to headers only. */
    val compactMode: Boolean = false,
    /** Current scroll offset into the timeline. */
    val scrollOffset: Int = 0
) {
    val isEmpty: Boolean get() = entries.isEmpty()
    val focusedEntry: HistoryEntry? get() = entries.getOrNull(focusedIndex)

    /** Move focus to the next entry. */
    fun focusNext(): HistoryNavigationState {
        val next = (focusedIndex + 1).coerceAtMost(entries.lastIndex)
        return copy(focusedIndex = next)
    }

    /** Move focus to the previous entry. */
    fun focusPrevious(): HistoryNavigationState {
        val prev = (focusedIndex - 1).coerceAtLeast(0)
        return copy(focusedIndex = prev)
    }

    /** Toggle expanded state for the focused entry. */
    fun toggleFocusedExpansion(): HistoryNavigationState {
        val entry = focusedEntry ?: return this
        val newExpanded = if (entry.id in expandedIds) expandedIds - entry.id
        else expandedIds + entry.id
        return copy(expandedIds = newExpanded)
    }

    /** Jump to an entry by its ID. */
    fun jumpTo(id: String): HistoryNavigationState {
        val index = entries.indexOfFirst { it.id == id }
        return if (index >= 0) copy(focusedIndex = index) else this
    }

    /** Collapse everything. */
    fun collapseAll(): HistoryNavigationState =
        copy(expandedIds = emptySet(), compactMode = true)

    /** Expand everything. */
    fun expandAll(): HistoryNavigationState =
        copy(expandedIds = entries.map { it.id }.toSet(), compactMode = false)
}

// ---- renderer ---------------------------------------------------------------

/**
 * Renders a [HistoryNavigationState] as beautiful themed terminal output.
 *
 * Design language:
 * - A vertical timeline rail in BRAND colour
 * - Each entry has a kind glyph, timestamp, and title
 * - The focused entry gets ACCENT_SELECTION highlighting
 * - Expanded entries show detail text, paths, and duration
 * - Compact mode shows one line per entry
 * - Width-safe throughout
 */
class HistoryNavigationRenderer(
    private val theme: TerminalTheme
) {
    private fun asciiOnly(): Boolean = !System.getenv("ATROPOS_ASCII").isNullOrBlank()

    private fun railGlyph(): String = if (asciiOnly()) Glyphs.Ascii.RAIL else Glyphs.RAIL
    private fun bulletGlyph(): String = if (asciiOnly()) Glyphs.Ascii.BULLET else Glyphs.BULLET

    /**
     * Renders the full timeline.
     *
     * @param state The history navigation state
     * @param width Terminal width
     * @param maxRows Maximum rows to render (viewport height)
     */
    fun render(state: HistoryNavigationState, width: Int, maxRows: Int): List<String> {
        if (state.isEmpty) return emptyList()

        val safeWidth = width.coerceAtLeast(20)
        val output = mutableListOf<String>()

        // Timeline header
        output += timelineHeader(state, safeWidth)

        // Window around focused entry
        val entries = state.entries
        val rowBudget = (maxRows - 2).coerceAtLeast(1)
        val windowStart = windowStart(state.focusedIndex, entries.size, rowBudget)

        var rowsUsed = 0
        entries.drop(windowStart).forEach { entry ->
            if (rowsUsed >= rowBudget) return@forEach

            val isFocused = entries.indexOf(entry) == state.focusedIndex
            val isExpanded = entry.id in state.expandedIds && !state.compactMode

            val lines = if (state.compactMode) {
                listOf(compactEntry(entry, isFocused, safeWidth))
            } else {
                fullEntry(entry, isFocused, isExpanded, safeWidth)
            }

            val available = rowBudget - rowsUsed
            output += lines.take(available)
            rowsUsed += lines.size.coerceAtMost(available)
        }

        // Key legend
        if (rowsUsed < rowBudget) {
            output += keyLegend(safeWidth)
        }

        return output
    }

    /**
     * Renders a single entry as a notification-style line (for toasts/status).
     */
    fun entryOneLiner(entry: HistoryEntry, width: Int): String {
        val glyph = kindGlyph(entry.kind)
        val ts = theme.subdued(entry.timestamp)
        val title = theme.strong(entry.title)
        return TerminalText.ellipsize("$glyph $ts $title", width)
    }

    // ---- header / legend ----------------------------------------------------

    private fun timelineHeader(state: HistoryNavigationState, width: Int): String {
        val rail = theme.paint(Role.BRAND, railGlyph())
        val pad = " ".repeat(Glyphs.RAIL_PADDING)
        val label = theme.paint(Role.BRAND, "HISTORY")
        val count = theme.subdued("  ${state.entries.size} entries")
        val mode = if (state.compactMode) theme.subdued("  [compact]") else ""
        return TerminalText.ellipsize("$rail$pad$label$count$mode", width)
    }

    private fun keyLegend(width: Int): String {
        val keys = listOf(
            "↑↓" to "navigate",
            "←→" to "collapse/expand",
            "enter" to "jump",
            "c" to "compact"
        )
        val text = keys.joinToString("  ") { (key, action) ->
            theme.subdued(key) + " " + theme.metadata(action)
        }
        return TerminalText.ellipsize("  $text", width)
    }

    // ---- entry rendering ----------------------------------------------------

    private fun compactEntry(entry: HistoryEntry, isFocused: Boolean, width: Int): String {
        val glyph = kindGlyph(entry.kind)
        val ts = theme.subdued(entry.timestamp.takeLast(8))
        val title = entry.title
        val duration = entry.durationMs?.let { theme.subdued(" ${formatDuration(it)}") } ?: ""
        val content = "$glyph $ts $title$duration"

        return if (isFocused) {
            theme.paint(
                Role.ACCENT_SELECTION,
                TerminalText.padEnd(TerminalText.ellipsize("▸ $content", width), width)
            )
        } else {
            TerminalText.ellipsize("  $content", width)
        }
    }

    private fun fullEntry(
        entry: HistoryEntry,
        isFocused: Boolean,
        isExpanded: Boolean,
        width: Int
    ): List<String> {
        val output = mutableListOf<String>()
        val rail = theme.paint(kindRole(entry.kind), railGlyph())
        val pad = " ".repeat(Glyphs.RAIL_PADDING)
        val glyph = kindGlyph(entry.kind)
        val ts = theme.subdued(entry.timestamp)

        // Primary line
        val titleText = theme.strong(entry.title)
        val providerText = entry.provider?.let { " " + theme.metadata(it) } ?: ""
        val durationText = entry.durationMs?.let { " " + theme.subdued(formatDuration(it)) } ?: ""
        val expandHint = if (entry.detail != null && !isExpanded) {
            theme.subdued(" ▸")
        } else if (isExpanded) {
            theme.subdued(" ▾")
        } else ""

        val primaryContent = "$glyph $ts $titleText$providerText$durationText$expandHint"
        val primaryLine = if (isFocused) {
            theme.paint(
                Role.ACCENT_SELECTION,
                TerminalText.padEnd(TerminalText.ellipsize("$rail$pad$primaryContent", width), width)
            )
        } else {
            TerminalText.ellipsize("$rail$pad$primaryContent", width)
        }
        output += primaryLine

        // Expanded detail
        if (isExpanded) {
            val innerWidth = (width - railGlyph().length - Glyphs.RAIL_PADDING - 4).coerceAtLeast(8)

            entry.detail?.let { detail ->
                AnsiLineWrapper.wrap(detail, innerWidth).forEach { line ->
                    output += TerminalText.ellipsize(
                        "$rail$pad  " + theme.subdued(line),
                        width
                    )
                }
            }

            if (entry.paths.isNotEmpty()) {
                val shown = entry.paths.take(6)
                shown.forEach { path ->
                    output += TerminalText.ellipsize(
                        "$rail$pad  " + theme.path(path),
                        width
                    )
                }
                val hidden = entry.paths.size - shown.size
                if (hidden > 0) {
                    output += TerminalText.ellipsize(
                        "$rail$pad  " + theme.subdued("+$hidden more"),
                        width
                    )
                }
            }

            // Separator after expanded content
            output += TerminalText.ellipsize(
                "$rail$pad" + theme.subdued("·".repeat((width - 6).coerceIn(1, 30))),
                width
            )
        }

        return output
    }

    // ---- kind mapping -------------------------------------------------------

    private fun kindGlyph(kind: HistoryEntryKind): String {
        val unicode = when (kind) {
            HistoryEntryKind.PROMPT -> "▸"
            HistoryEntryKind.RESPONSE -> "◆"
            HistoryEntryKind.COMMAND -> "⌘"
            HistoryEntryKind.TOOL -> "⚡"
            HistoryEntryKind.VERIFICATION -> "✓"
            HistoryEntryKind.PATCH -> "±"
            HistoryEntryKind.DAG_NODE -> "◇"
            HistoryEntryKind.ERROR -> "✗"
            HistoryEntryKind.SYSTEM -> "●"
        }
        val ascii = when (kind) {
            HistoryEntryKind.PROMPT -> ">"
            HistoryEntryKind.RESPONSE -> "*"
            HistoryEntryKind.COMMAND -> "#"
            HistoryEntryKind.TOOL -> "!"
            HistoryEntryKind.VERIFICATION -> "+"
            HistoryEntryKind.PATCH -> "~"
            HistoryEntryKind.DAG_NODE -> "o"
            HistoryEntryKind.ERROR -> "x"
            HistoryEntryKind.SYSTEM -> "-"
        }
        val glyph = if (asciiOnly()) ascii else unicode
        return theme.paint(kindRole(kind), glyph)
    }

    private fun kindRole(kind: HistoryEntryKind): Role = when (kind) {
        HistoryEntryKind.PROMPT -> Role.ACCENT_FOCUS
        HistoryEntryKind.RESPONSE -> Role.BRAND
        HistoryEntryKind.COMMAND -> Role.CODE
        HistoryEntryKind.TOOL -> Role.STATUS_RUNNING
        HistoryEntryKind.VERIFICATION -> Role.STATUS_VERIFIED
        HistoryEntryKind.PATCH -> Role.DIFF_ADD
        HistoryEntryKind.DAG_NODE -> Role.STATUS_PENDING
        HistoryEntryKind.ERROR -> Role.STATUS_ERROR
        HistoryEntryKind.SYSTEM -> Role.TEXT_SECONDARY
    }

    // ---- utilities ----------------------------------------------------------

    private fun formatDuration(ms: Long): String = when {
        ms < 1000 -> "${ms}ms"
        ms < 60_000 -> "${ms / 1000}s"
        ms < 3_600_000 -> "${ms / 60_000}m ${(ms % 60_000) / 1000}s"
        else -> "${ms / 3_600_000}h ${(ms % 3_600_000) / 60_000}m"
    }

    private fun windowStart(focused: Int, size: Int, budget: Int): Int {
        if (size <= budget) return 0
        return (focused - budget / 2).coerceIn(0, size - budget)
    }
}
