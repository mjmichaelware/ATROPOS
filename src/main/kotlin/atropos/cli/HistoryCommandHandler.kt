/* SPDX-License-Identifier: AGPL-3.0-only */
package atropos.cli

import atropos.cli.ui.AnsiTerminalEngine
import atropos.cli.ui.HistoryEntry
import atropos.cli.ui.HistoryEntryKind
import atropos.cli.ui.HistoryNavigationRenderer
import atropos.cli.ui.HistoryNavigationState
import atropos.cli.ui.TerminalTheme
import atropos.cli.config.ConfigurationManager
import atropos.core.policy.BoundedProcessRunner
import atropos.core.journal.EventCategory
import atropos.core.observability.ExecutionEvent
import atropos.core.observability.ExecutionHistoryStore
import atropos.core.observability.HistoryQuery
import java.nio.file.Files
import java.nio.file.Path

/**
 * Handles the `/history` command: renders the session execution timeline
 * from the execution history store.
 *
 * Reads `.atropos/runs/` event journal entries and renders them through
 * [HistoryNavigationRenderer] as a beautiful timeline with kind-specific
 * glyphs and colour coding.
 *
 * Supports:
 * - `/history` — show full session timeline
 * - `/history compact` — compact one-line-per-entry mode
 * - `/history <n>` — show the last N entries
 */
class HistoryCommandHandler(
    private val uiEngine: AnsiTerminalEngine
) {
    private val theme = TerminalTheme(ConfigurationManager())
    private val renderer = HistoryNavigationRenderer(theme)
    private val processRunner = BoundedProcessRunner()
    private val historyStore = ExecutionHistoryStore()

    fun execute(tokens: List<String>): RouterOutcome {
        val args = tokens.drop(1).map(String::lowercase)
        val compact = "compact" in args || "-c" in args
        val limit = args.firstOrNull { it.all(Char::isDigit) }?.toIntOrNull() ?: 100

        val entries = readHistoryEntries(limit)

        if (entries.isEmpty()) {
            uiEngine.renderNotice("no history entries found · use ATROPOS to build a timeline")
            return RouterOutcome.CONTINUE
        }

        val state = HistoryNavigationState(
            entries = entries,
            focusedIndex = entries.lastIndex,
            compactMode = compact
        )

        val lines = renderer.render(
            state = state,
            width = uiEngine.viewportWidth,
            maxRows = 40
        )

        uiEngine.renderBlock(lines)
        return RouterOutcome.CONTINUE
    }

    /**
     * Reads history entries from the execution history store.
     *
     * Falls back to reading `.atropos/runs/events.jsonl` directly if
     * the store is not wired, and produces entries from git log as a
     * last resort.
     */
    private fun readHistoryEntries(limit: Int): List<HistoryEntry> {
        val durableEntries = runCatching {
            historyStore.searchAll(HistoryQuery(limit = limit)).events.map(::toHistoryEntry)
        }.getOrDefault(emptyList())
        if (durableEntries.isNotEmpty()) return durableEntries.takeLast(limit)

        val entries = mutableListOf<HistoryEntry>()

        // Try reading from execution history journal
        val journalPath = Path.of(".atropos/runs/events.jsonl")
        if (Files.isRegularFile(journalPath)) {
            try {
                val lines = Files.readAllLines(journalPath)
                    .takeLast(limit * 2) // Read more than limit since we'll filter
                    .filter { it.isNotBlank() }

                lines.forEach { line ->
                    parseJournalEntry(line)?.let(entries::add)
                }
            } catch (_: Exception) {
                // Fall through to git log fallback
            }
        }

        // Supplement with recent git history for repository context
        if (entries.size < limit) {
            val gitEntries = readGitLogEntries((limit - entries.size).coerceAtMost(20))
            entries += gitEntries
        }

        return entries.takeLast(limit).sortedBy { it.timestamp }
    }

    private fun toHistoryEntry(event: ExecutionEvent): HistoryEntry = HistoryEntry(
        id = event.runId?.let { "$it-${event.sequence}" } ?: "event-${event.sequence}",
        timestamp = formatTimestamp(event.timestamp.toString()),
        kind = event.category.toHistoryEntryKind(),
        title = event.task?.take(120) ?: event.category.name.lowercase().replace('_', ' ').take(120),
        detail = event.payload.take(500),
        provider = event.provider,
        durationMs = null
    )

    private fun EventCategory.toHistoryEntryKind(): HistoryEntryKind = when (this) {
        EventCategory.TEST -> HistoryEntryKind.VERIFICATION
        EventCategory.VERIFICATION -> HistoryEntryKind.VERIFICATION
        EventCategory.FILE_MUTATION, EventCategory.DIFF -> HistoryEntryKind.PATCH
        EventCategory.TOOL_CALL -> HistoryEntryKind.TOOL
        EventCategory.COMMAND -> HistoryEntryKind.COMMAND
        EventCategory.ERROR, EventCategory.FAILURE -> HistoryEntryKind.ERROR
        EventCategory.DAG, EventCategory.CONTINUATION, EventCategory.CHILD_RUN -> HistoryEntryKind.DAG_NODE
        EventCategory.TEXT, EventCategory.REASONING -> HistoryEntryKind.RESPONSE
        else -> HistoryEntryKind.SYSTEM
    }

    private fun parseJournalEntry(json: String): HistoryEntry? {
        // Minimal JSON extraction without a full parser dependency.
        // The event journal is newline-delimited JSON with known fields.
        val id = extractField(json, "id") ?: return null
        val ts = extractField(json, "timestamp") ?: extractField(json, "ts") ?: ""
        val type = extractField(json, "type") ?: extractField(json, "kind") ?: ""
        val title = extractField(json, "title")
            ?: extractField(json, "summary")
            ?: extractField(json, "command")
            ?: type
        val detail = extractField(json, "detail")
            ?: extractField(json, "output")
        val provider = extractField(json, "provider")
        val duration = extractField(json, "durationMs")?.toLongOrNull()
            ?: extractField(json, "duration_ms")?.toLongOrNull()

        val kind = when {
            type.contains("prompt", ignoreCase = true) -> HistoryEntryKind.PROMPT
            type.contains("response", ignoreCase = true) -> HistoryEntryKind.RESPONSE
            type.contains("command", ignoreCase = true) -> HistoryEntryKind.COMMAND
            type.contains("tool", ignoreCase = true) -> HistoryEntryKind.TOOL
            type.contains("verif", ignoreCase = true) -> HistoryEntryKind.VERIFICATION
            type.contains("patch", ignoreCase = true) -> HistoryEntryKind.PATCH
            type.contains("dag", ignoreCase = true) || type.contains("node", ignoreCase = true) ->
                HistoryEntryKind.DAG_NODE
            type.contains("error", ignoreCase = true) || type.contains("fail", ignoreCase = true) ->
                HistoryEntryKind.ERROR
            else -> HistoryEntryKind.SYSTEM
        }

        return HistoryEntry(
            id = id,
            timestamp = formatTimestamp(ts),
            kind = kind,
            title = title.take(120),
            detail = detail?.take(500),
            provider = provider,
            durationMs = duration
        )
    }

    private fun extractField(json: String, field: String): String? {
        val pattern = """"$field"\s*:\s*"([^"]*?)""""
        return Regex(pattern).find(json)?.groupValues?.get(1)
    }

    private fun formatTimestamp(ts: String): String {
        // Extract time portion from ISO-8601 or similar
        val timeMatch = Regex("""(\d{2}:\d{2}:\d{2})""").find(ts)
        return timeMatch?.groupValues?.get(1) ?: ts.takeLast(8)
    }

    private fun readGitLogEntries(limit: Int): List<HistoryEntry> {
        return try {
            val result = processRunner.run(
                command = listOf(
                    "git", "log", "--oneline", "--no-decorate", "-n", limit.toString(),
                    "--format=%H|%ai|%s"
                ),
                directory = Path.of("").toAbsolutePath().normalize(),
                timeoutMillis = 10_000,
                maxOutputBytes = 200_000,
                maxOutputLines = 10_000
            )
            if (result.exitCode != 0 || result.timedOut || result.launchError != null) return emptyList()

            result.stdout.lines()
                .filter { it.contains('|') }
                .mapNotNull { line ->
                    val parts = line.split('|', limit = 3)
                    if (parts.size < 3) return@mapNotNull null
                    HistoryEntry(
                        id = "git-${parts[0].take(8)}",
                        timestamp = formatTimestamp(parts[1].trim()),
                        kind = HistoryEntryKind.SYSTEM,
                        title = "commit: ${parts[2].trim().take(80)}",
                        detail = parts[0].trim()
                    )
                }
        } catch (_: Exception) {
            emptyList()
        }
    }
}
