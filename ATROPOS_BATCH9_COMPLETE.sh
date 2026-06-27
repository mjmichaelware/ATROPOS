#!/data/data/com.termux/files/usr/bin/bash
(
set -euo pipefail
cd "$HOME/ATROPOS"

TMP="$(mktemp -d)"
BACKUP="$TMP/source-backup"
mkdir -p "$BACKUP"
SUCCESS=0

FILES=(
  src/main/kotlin/atropos/cli/ui/TerminalTheme.kt
  src/main/kotlin/atropos/cli/ui/WelcomePanel.kt
  src/main/kotlin/atropos/cli/ui/StatusBarRenderer.kt
  src/main/kotlin/atropos/cli/ui/TranscriptBuffer.kt
  src/main/kotlin/atropos/cli/ui/ComposerViewport.kt
  src/main/kotlin/atropos/cli/ui/ViewportLayout.kt
  src/main/kotlin/atropos/cli/ui/SessionPresentationState.kt
  src/main/kotlin/atropos/cli/ui/HeaderRenderer.kt
  src/main/kotlin/atropos/cli/ui/SessionOverviewRenderer.kt
  src/main/kotlin/atropos/cli/ui/CommandPaletteRenderer.kt
)

for file in "${FILES[@]}"; do
  name="$(basename "$file")"
  if [ -f "$file" ]; then
    cp "$file" "$BACKUP/$name"
  else
    : > "$BACKUP/$name.missing"
  fi
done

finish() {
  rc=$?
  trap - EXIT
  if [ "$SUCCESS" -ne 1 ]; then
    for file in "${FILES[@]}"; do
      name="$(basename "$file")"
      if [ -f "$BACKUP/$name.missing" ]; then
        rm -f "$file"
      else
        cp "$BACKUP/$name" "$file"
      fi
    done
    echo "BATCH 9 FAILED: SOURCE FILES RESTORED"
  fi
  rm -rf "$TMP"
  exit "$rc"
}
trap finish EXIT

cat > src/main/kotlin/atropos/cli/ui/TerminalTheme.kt <<'EOF'
/* SPDX-License-Identifier: AGPL-3.0-only */
package atropos.cli.ui

import atropos.cli.config.ConfigurationManager

class TerminalTheme(
    private val capabilities: ConfigurationManager
) {
    val colorEnabled: Boolean
        get() = capabilities.isColorEnabled

    fun brand(text: String): String = style(text, "1;36")
    fun success(text: String): String = style(text, "1;32")
    fun error(text: String): String = style(text, "1;31")
    fun warning(text: String): String = style(text, "33")
    fun metadata(text: String): String = style(text, "90")
    fun strong(text: String): String = style(text, "1")
    fun path(text: String): String = style(text, "4;36")
    fun code(text: String): String = style(text, "36")
    fun header(text: String): String = style(text, "1;36;48;5;235")
    fun footer(text: String): String = style(text, "38;5;245;48;5;235")
    fun selection(text: String): String = style(text, "30;46")

    fun reset(): String = if (colorEnabled) "\u001B[0m" else ""

    private fun style(text: String, code: String): String =
        if (colorEnabled && text.isNotEmpty()) {
            "\u001B[${code}m$text\u001B[0m"
        } else {
            text
        }
}
EOF

cat > src/main/kotlin/atropos/cli/ui/SessionPresentationState.kt <<'EOF'
/* SPDX-License-Identifier: AGPL-3.0-only */
package atropos.cli.ui

sealed interface MetricValue {
    data class Known(val display: String) : MetricValue
    object Unknown : MetricValue

    fun text(): String = when (this) {
        is Known -> display
        Unknown -> "--"
    }
}

data class SessionPresentationState(
    val provider: String,
    val mode: String,
    val workspace: String,
    val commands: List<String>,
    val tokens: MetricValue,
    val cost: MetricValue,
    val activeOperation: String?
)
EOF

cat > src/main/kotlin/atropos/cli/ui/HeaderRenderer.kt <<'EOF'
/* SPDX-License-Identifier: AGPL-3.0-only */
package atropos.cli.ui

class HeaderRenderer(
    private val theme: TerminalTheme
) {
    fun render(
        state: SessionPresentationState,
        width: Int
    ): String {
        val safeWidth = width.coerceAtLeast(1)
        val left = " ATROPOS"
        val candidates = listOf(
            "${state.provider.lowercase()} · ${state.mode.lowercase()} · /help ",
            "${state.mode.lowercase()} · /help ",
            "/help "
        )
        val right = candidates.firstOrNull {
            TerminalText.cellWidth(left) +
                TerminalText.cellWidth(it) + 1 <= safeWidth
        }.orEmpty()
        val gap = (
            safeWidth - TerminalText.cellWidth(left) -
                TerminalText.cellWidth(right)
            ).coerceAtLeast(0)
        val plain = TerminalText.padEnd(left + " ".repeat(gap) + right, safeWidth)
        return theme.header(plain)
    }
}
EOF

cat > src/main/kotlin/atropos/cli/ui/SessionOverviewRenderer.kt <<'EOF'
/* SPDX-License-Identifier: AGPL-3.0-only */
package atropos.cli.ui

class SessionOverviewRenderer(
    private val theme: TerminalTheme
) {
    fun render(
        state: SessionPresentationState,
        width: Int
    ): List<String> {
        val safeWidth = width.coerceIn(36, 160)
        val provider = TerminalText.ellipsize(state.provider.lowercase(), 18)
        val mode = TerminalText.ellipsize(state.mode.lowercase(), 12)
        val workspace = TerminalText.ellipsize(
            TerminalText.compactPath(state.workspace),
            (safeWidth - 12).coerceAtLeast(16)
        )
        val commands = state.commands.joinToString("  ")
        val output = mutableListOf<String>()

        output += theme.brand("SESSION")
        if (safeWidth >= 56) {
            output += theme.metadata("provider  ") + theme.strong(provider) +
                theme.metadata("    mode  ") + theme.strong(mode)
        } else {
            output += theme.metadata("provider  ") + theme.strong(provider)
            output += theme.metadata("mode      ") + theme.strong(mode)
        }
        output += theme.metadata("workspace ") + theme.path(workspace)
        output += theme.metadata("commands  ") + theme.code(commands)

        return output.flatMap { AnsiLineWrapper.wrap(it, safeWidth) }
    }
}
EOF

cat > src/main/kotlin/atropos/cli/ui/WelcomePanel.kt <<'EOF'
/* SPDX-License-Identifier: AGPL-3.0-only */
package atropos.cli.ui

class WelcomePanel(
    theme: TerminalTheme
) {
    private val overview = SessionOverviewRenderer(theme)

    fun render(
        provider: String,
        workspace: String,
        mode: String,
        terminalWidth: Int
    ): List<String> = overview.render(
        SessionPresentationState(
            provider = provider,
            mode = mode,
            workspace = workspace,
            commands = listOf("/help", "/status", "/use", "/verify", "/exit"),
            tokens = MetricValue.Unknown,
            cost = MetricValue.Unknown,
            activeOperation = null
        ),
        terminalWidth
    )
}
EOF

cat > src/main/kotlin/atropos/cli/ui/StatusBarRenderer.kt <<'EOF'
/* SPDX-License-Identifier: AGPL-3.0-only */
package atropos.cli.ui

import atropos.cli.session.QuotaSessionTracker

class StatusBarRenderer(
    private val theme: TerminalTheme
) {
    private val headerRenderer = HeaderRenderer(theme)

    fun header(width: Int): String = headerRenderer.render(
        SessionPresentationState(
            "--", "ASK", "--", emptyList(),
            MetricValue.Unknown, MetricValue.Unknown, null
        ),
        width
    )

    fun header(
        provider: String,
        mode: String,
        workspace: String,
        width: Int
    ): String = headerRenderer.render(
        SessionPresentationState(
            provider, mode, workspace, emptyList(),
            MetricValue.Unknown, MetricValue.Unknown, null
        ),
        width
    )

    fun footer(
        provider: String,
        mode: String,
        workspace: String,
        tracker: QuotaSessionTracker,
        verificationState: String?,
        width: Int
    ): String {
        val tokens = tracker.estimatedTokens.takeIf { it > 0 }
            ?.toString() ?: "--"
        val cost = tracker.estimatedCostUsd().takeIf { it > 0.0 }
            ?.let { "$" + String.format("%.4f", it) } ?: "--"
        val path = TerminalText.compactPath(workspace)
        val activity = verificationState
            ?.let(TerminalText::sanitize)
            ?.takeIf(String::isNotBlank)
        val candidates = listOfNotNull(
            listOfNotNull(
                provider.lowercase(), mode.lowercase(),
                "$tokens tok", cost, path, activity
            ).joinToString(" · "),
            listOfNotNull(
                provider.lowercase(), mode.lowercase(), "$tokens tok", path, activity
            ).joinToString(" · "),
            listOfNotNull(provider.lowercase(), mode.lowercase(), "$tokens tok", activity)
                .joinToString(" · "),
            listOfNotNull(provider.lowercase(), mode.lowercase(), activity)
                .joinToString(" · ")
        )
        val content = candidates.firstOrNull {
            TerminalText.cellWidth(" $it") <= width
        } ?: candidates.last()
        val plain = TerminalText.padEnd(
            TerminalText.ellipsize(" $content", width),
            width
        )
        return theme.footer(plain)
    }
}
EOF

cat > src/main/kotlin/atropos/cli/ui/TranscriptBuffer.kt <<'EOF'
/* SPDX-License-Identifier: AGPL-3.0-only */
package atropos.cli.ui

class TranscriptBuffer(
    private val maximumBlocks: Int = 600
) {
    private val blocks = ArrayDeque<String>()
    private var scrollOffset = 0

    init {
        require(maximumBlocks > 0)
    }

    val isEmpty: Boolean
        get() = blocks.isEmpty()

    val isFollowingTail: Boolean
        get() = scrollOffset == 0

    fun append(value: String) {
        blocks.addLast(value)
        while (blocks.size > maximumBlocks) blocks.removeFirst()
        scrollOffset = 0
    }

    fun clear() {
        blocks.clear()
        scrollOffset = 0
    }

    fun followTail() {
        scrollOffset = 0
    }

    fun scrollUp(lines: Int = 4) {
        scrollOffset = (
            scrollOffset.toLong() + lines.coerceAtLeast(1).toLong()
        ).coerceAtMost(Int.MAX_VALUE.toLong()).toInt()
    }

    fun scrollDown(lines: Int = 4) {
        scrollOffset = (scrollOffset - lines.coerceAtLeast(1)).coerceAtLeast(0)
    }

    fun visibleLines(width: Int, height: Int): List<String> {
        if (width <= 0 || height <= 0 || blocks.isEmpty()) return emptyList()
        val lines = blocks.flatMap { AnsiLineWrapper.wrap(it, width) }
        val maximumOffset = (lines.size - height).coerceAtLeast(0)
        scrollOffset = scrollOffset.coerceIn(0, maximumOffset)
        val end = (lines.size - scrollOffset).coerceAtLeast(0)
        val start = (end - height).coerceAtLeast(0)
        return lines.subList(start, end)
    }
}
EOF

cat > src/main/kotlin/atropos/cli/ui/CommandPaletteRenderer.kt <<'EOF'
/* SPDX-License-Identifier: AGPL-3.0-only */
package atropos.cli.ui

data class CommandPaletteItem(
    val command: String,
    val description: String
)

class CommandPaletteRenderer(
    private val theme: TerminalTheme
) {
    private val commands = listOf(
        CommandPaletteItem("/help", "commands"),
        CommandPaletteItem("/status", "session status"),
        CommandPaletteItem("/use", "switch provider"),
        CommandPaletteItem("/verify", "verify build"),
        CommandPaletteItem("/exit", "close session"),
        CommandPaletteItem("/quit", "close session")
    )

    fun render(query: String?, width: Int, maximumRows: Int): List<String> {
        if (query == null || maximumRows <= 0) return emptyList()
        val matches = commands.filter { it.command.startsWith(query) }
            .take(maximumRows)
        if (matches.isEmpty()) return emptyList()
        return matches.mapIndexed { index, item ->
            val marker = if (index == 0) "› " else "  "
            val available = (width - TerminalText.cellWidth(marker) -
                TerminalText.cellWidth(item.command) - 3).coerceAtLeast(0)
            val description = TerminalText.ellipsize(item.description, available)
            val plain = marker + item.command +
                if (description.isEmpty()) "" else " · $description"
            if (index == 0) theme.selection(
                TerminalText.padEnd(plain, width)
            ) else theme.metadata(plain)
        }
    }
}
EOF

cat > src/main/kotlin/atropos/cli/ui/ComposerViewport.kt <<'EOF'
/* SPDX-License-Identifier: AGPL-3.0-only */
package atropos.cli.ui

data class ComposerSnapshot(
    val line: String,
    val cursorColumn: Int,
    val lines: List<String> = listOf(line),
    val cursorRow: Int = 0
)

class ComposerViewport(
    private val theme: TerminalTheme
) {
    private var buffer = ""
    private var suggestion = ""
    private var cursor = 0
    private var mode = "ASK"

    fun update(buffer: String, suggestion: String, cursor: Int, mode: String) {
        this.buffer = TerminalText.sanitize(buffer)
        this.suggestion = TerminalText.sanitize(suggestion).replace('\n', ' ')
        this.cursor = safeCursorBoundary(
            this.buffer,
            cursor.coerceIn(0, this.buffer.length)
        )
        this.mode = mode.uppercase()
    }

    fun render(width: Int): ComposerSnapshot = renderMultiline(width, 4)

    fun renderMultiline(width: Int, maximumLines: Int): ComposerSnapshot {
        val safeWidth = width.coerceAtLeast(1)
        val limit = maximumLines.coerceAtLeast(1)
        val badge = "[${mode.lowercase()}] "
        val prompt = "› "
        val prefixPlain = badge + prompt
        val before = buffer.substring(0, cursor)
        val after = buffer.substring(cursor)
        val rendered = theme.metadata(badge) + theme.brand(prompt) +
            before + theme.metadata(suggestion) + after
        val wrapped = AnsiLineWrapper.wrap(rendered, safeWidth).toMutableList()
        val cursorPosition = cursorPosition(
            prefixPlain + before,
            safeWidth
        )
        var absoluteRow = cursorPosition.first
        var cursorColumn = cursorPosition.second

        while (wrapped.size <= absoluteRow) {
            wrapped += ""
        }

        val maximumStart = (wrapped.size - limit).coerceAtLeast(0)
        val start = (absoluteRow - limit + 1).coerceIn(0, maximumStart)
        val visible = wrapped.drop(start).take(limit).ifEmpty { listOf("") }
        absoluteRow -= start

        return ComposerSnapshot(
            line = visible.first(),
            cursorColumn = cursorColumn.coerceIn(1, safeWidth),
            lines = visible,
            cursorRow = absoluteRow.coerceIn(0, visible.lastIndex)
        )
    }

    fun mode(): String = mode

    fun commandQuery(): String? {
        val value = buffer.trimStart()
        return value.takeIf {
            it.startsWith("/") && !it.contains(' ') && !it.contains('\n')
        }
    }

    private fun cursorPosition(value: String, width: Int): Pair<Int, Int> {
        var row = 0
        var cells = 0
        val points = value.codePoints().toArray()

        points.forEach { point ->
            if (point == '\n'.code) {
                row++
                cells = 0
            } else {
                val character = String(Character.toChars(point))
                val size = TerminalText.cellWidth(character)
                if (cells + size > width) {
                    row++
                    cells = 0
                }
                cells += size
                if (cells == width) {
                    row++
                    cells = 0
                }
            }
        }

        return row to (cells + 1).coerceIn(1, width)
    }

    private fun safeCursorBoundary(value: String, requested: Int): Int {
        var position = requested.coerceIn(0, value.length)
        if (position in 1 until value.length &&
            Character.isLowSurrogate(value[position]) &&
            Character.isHighSurrogate(value[position - 1])) {
            position--
        }
        return position
    }
}
EOF

cat > src/main/kotlin/atropos/cli/ui/ViewportLayout.kt <<'EOF'
/* SPDX-License-Identifier: AGPL-3.0-only */
package atropos.cli.ui

import atropos.cli.session.QuotaSessionTracker

class ViewportLayout(
    private val theme: TerminalTheme,
    private val welcomePanel: WelcomePanel,
    private val statusBar: StatusBarRenderer
) {
    private val palette = CommandPaletteRenderer(theme)

    fun build(
        width: Int,
        height: Int,
        transcript: TranscriptBuffer,
        composer: ComposerViewport,
        provider: String,
        workspace: String,
        tracker: QuotaSessionTracker,
        activity: String?,
        verificationState: String?
    ): ScreenFrame {
        val safeWidth = width.coerceIn(36, 160)
        val safeHeight = height.coerceAtLeast(12)
        val frame = ScreenFrame(safeWidth, safeHeight)
        val state = SessionPresentationState(
            provider = provider,
            mode = composer.mode(),
            workspace = workspace,
            commands = listOf("/help", "/status", "/use", "/verify", "/exit"),
            tokens = tracker.estimatedTokens.takeIf { it > 0 }
                ?.let { MetricValue.Known(it.toString()) } ?: MetricValue.Unknown,
            cost = tracker.estimatedCostUsd().takeIf { it > 0.0 }
                ?.let { MetricValue.Known("$" + String.format("%.4f", it)) }
                ?: MetricValue.Unknown,
            activeOperation = activity?.let(TerminalText::stripAnsi)
                ?: verificationState
        )

        frame.setLine(
            0,
            statusBar.header(provider, composer.mode(), workspace, safeWidth)
        )

        val footerRow = safeHeight - 1
        val composerSnapshot = composer.renderMultiline(
            safeWidth,
            (safeHeight / 3).coerceIn(1, 4)
        )
        val paletteLines = palette.render(
            composer.commandQuery(),
            safeWidth,
            4
        )
        val composerHeight = composerSnapshot.lines.size
        val paletteHeight = paletteLines.size
        val composerStart = footerRow - composerHeight
        val paletteStart = composerStart - paletteHeight
        val separatorRow = (paletteStart - 1).coerceAtLeast(2)
        val transcriptStart = 1
        val transcriptHeight = (separatorRow - transcriptStart).coerceAtLeast(1)

        if (transcript.isEmpty) {
            val overview = welcomePanel.render(
                state.provider,
                state.workspace,
                state.mode,
                safeWidth
            )
            overview.take(transcriptHeight).forEachIndexed { index, line ->
                frame.setLine(transcriptStart + index, line)
            }
            activity?.let {
                val row = transcriptStart + overview.size
                if (row < separatorRow) frame.setLine(row, it)
            }
        } else {
            val reserve = if (activity == null) 0 else 1
            val visible = transcript.visibleLines(
                safeWidth,
                (transcriptHeight - reserve).coerceAtLeast(1)
            ).toMutableList()
            activity?.let(visible::add)
            visible.takeLast(transcriptHeight).forEachIndexed { index, line ->
                frame.setLine(transcriptStart + index, line)
            }
        }

        frame.setLine(separatorRow, theme.metadata("─".repeat(safeWidth)))
        paletteLines.forEachIndexed { index, line ->
            val row = paletteStart + index
            if (row in 1 until composerStart) frame.setLine(row, line)
        }
        composerSnapshot.lines.forEachIndexed { index, line ->
            frame.setLine(composerStart + index, line)
        }
        frame.setLine(
            footerRow,
            statusBar.footer(
                provider,
                composer.mode(),
                workspace,
                tracker,
                verificationState ?: state.activeOperation,
                safeWidth
            )
        )
        frame.cursorX = composerSnapshot.cursorColumn
        frame.cursorY = composerStart + composerSnapshot.cursorRow + 1
        frame.showCursor = true
        return frame
    }
}
EOF

echo "=== BATCH 9 FULL COMPILE ==="
mapfile -d '' SOURCES < <(
  find src/main/kotlin -type f -name '*.kt' -print0
)
kotlinc -include-runtime -d "$TMP/atropos.jar" "${SOURCES[@]}"

cat > "$TMP/Batch9SmokeTest.kt" <<'EOF'
import atropos.cli.config.ConfigurationManager
import atropos.cli.session.QuotaSessionTracker
import atropos.cli.ui.*
import java.io.ByteArrayOutputStream
import java.io.PrintStream

fun plain(value: String) = TerminalText.stripAnsi(value)

fun main() {
    val color = ConfigurationManager(
        envProvider = { key -> if (key == "TERM") "xterm-256color" else null },
        hasConsole = true
    )
    val theme = TerminalTheme(color)
    val tracker = QuotaSessionTracker()
    val transcript = TranscriptBuffer(20)
    val composer = ComposerViewport(theme)
    val layout = ViewportLayout(
        theme,
        WelcomePanel(theme),
        StatusBarRenderer(theme)
    )

    listOf(36 to 12, 40 to 18, 56 to 20, 80 to 24, 120 to 40)
        .forEach { (width, height) ->
            composer.update("", "", 0, "ASK")
            val frame = layout.build(
                width, height, transcript, composer,
                "groq", "~/ATROPOS", tracker, null, null
            )
            check(frame.lines.size == height)
            check(frame.lines.all { TerminalText.cellWidth(it) == width })
            check(plain(frame.lines[0]).contains("ATROPOS"))
            check(plain(frame.lines[1]).contains("SESSION"))
            check(frame.lines.none { plain(it).contains('╭') || plain(it).contains('╯') })
            check(plain(frame.lines.last()).contains("--"))
        }

    composer.update("/v", "erify", 2, "ASK")
    val paletteFrame = layout.build(
        56, 20, transcript, composer,
        "groq", "~/ATROPOS", tracker, null, null
    )
    check(paletteFrame.lines.any { plain(it).contains("/verify") })

    composer.update("0123456789".repeat(12), "", 120, "PLAN")
    val wrapped = composer.renderMultiline(40, 4)
    check(wrapped.lines.size > 1)
    check(wrapped.cursorRow in wrapped.lines.indices)

    repeat(12) { transcript.append("line-$it") }
    val tail = transcript.visibleLines(40, 4)
    transcript.scrollUp(3)
    val older = transcript.visibleLines(40, 4)
    check(tail != older)
    transcript.followTail()
    check(transcript.visibleLines(40, 4) == tail)

    val bytes = ByteArrayOutputStream()
    val stream = PrintStream(bytes)
    val headless = ConfigurationManager(
        envProvider = { key -> if (key == "TERM") "dumb" else null },
        hasConsole = false
    )
    val headlessUi = AnsiTerminalEngine(headless, stream, stream)
    headlessUi.renderNotice("plain")
    headlessUi.cleanup()
    check(!bytes.toString().contains("\u001B["))

    bytes.reset()
    val interactiveUi = AnsiTerminalEngine(
        color,
        stream,
        stream,
        TerminalGeometryProvider { TerminalGeometry(18, 40) }
    )
    interactiveUi.initializeReactive()
    interactiveUi.renderNotice("frame")
    interactiveUi.cleanup()
    val terminalOutput = bytes.toString()
    check(terminalOutput.contains("\u001B[?1049h"))
    check(terminalOutput.contains("\u001B[?1049l"))

    println("BATCH9_SMOKE_OK")
}
EOF

echo "=== BATCH 9 BEHAVIOR TEST ==="
kotlinc -cp "$TMP/atropos.jar" -include-runtime \
  -d "$TMP/smoke.jar" "$TMP/Batch9SmokeTest.kt"
java -cp "$TMP/atropos.jar:$TMP/smoke.jar" Batch9SmokeTestKt |
  grep -Fx "BATCH9_SMOKE_OK"

jar tf "$TMP/atropos.jar" |
  grep -Fx 'atropos/MainKt.class' >/dev/null

echo "=== BATCH 9 HEADLESS REGRESSION ==="
printf '/exit\n' | java -jar "$TMP/atropos.jar" >/dev/null

git diff --check -- "${FILES[@]}"

mkdir -p .atropos/backups
if [ -f atropos.jar ]; then
  cp atropos.jar ".atropos/backups/atropos.$(date +%s).jar"
fi
mv "$TMP/atropos.jar" "$HOME/ATROPOS/atropos.jar"

SUCCESS=1
echo "BATCH 9 COMPLETE UI SYSTEM SUCCESSFUL | E(DELTA)=0"
)
