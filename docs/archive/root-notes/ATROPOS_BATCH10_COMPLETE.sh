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
  src/main/kotlin/atropos/cli/ui/SessionPresentationState.kt
  src/main/kotlin/atropos/cli/ui/HeaderRenderer.kt
  src/main/kotlin/atropos/cli/ui/WelcomePanel.kt
  src/main/kotlin/atropos/cli/ui/StatusBarRenderer.kt
  src/main/kotlin/atropos/cli/ui/ViewportLayout.kt
  src/main/kotlin/atropos/cli/ui/WorkspaceIntelligence.kt
  src/main/kotlin/atropos/cli/ui/LandingRenderer.kt
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
    echo "BATCH 10 FAILED: SOURCE FILES RESTORED"
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
    fun metadata(text: String): String = style(text, "38;5;245")
    fun subdued(text: String): String = style(text, "38;5;239")
    fun strong(text: String): String = style(text, "1;37")
    fun path(text: String): String = style(text, "36")
    fun code(text: String): String = style(text, "38;5;252")
    fun headerBrand(text: String): String = style(text, "1;36;48;5;235")
    fun headerText(text: String): String = style(text, "38;5;250;48;5;235")
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

cat > src/main/kotlin/atropos/cli/ui/WorkspaceIntelligence.kt <<'EOF'
/* SPDX-License-Identifier: AGPL-3.0-only */
package atropos.cli.ui

import java.io.File
import java.util.concurrent.Executors
import java.util.concurrent.TimeUnit

data class RepositoryState(
    val isRepository: Boolean,
    val branch: String?,
    val changedFiles: Int?,
    val available: Boolean
) {
    val clean: Boolean?
        get() = changedFiles?.let { it == 0 }

    companion object {
        fun unknown(): RepositoryState =
            RepositoryState(false, null, null, false)
    }
}

fun interface WorkspaceInspector {
    fun inspect(workspace: String): RepositoryState
}

class CachingGitWorkspaceInspector(
    private val cacheMillis: Long = 2_000,
    private val timeoutMillis: Long = 750,
    private val outputLimit: Int = 256 * 1024
) : WorkspaceInspector {
    private var cachedPath: String? = null
    private var cachedAt = 0L
    private var cachedState = RepositoryState.unknown()

    @Synchronized
    override fun inspect(workspace: String): RepositoryState {
        val now = System.currentTimeMillis()
        if (workspace == cachedPath && now - cachedAt < cacheMillis) {
            return cachedState
        }
        cachedPath = workspace
        cachedAt = now
        cachedState = inspectNow(workspace)
        return cachedState
    }

    private fun inspectNow(workspace: String): RepositoryState {
        val directory = File(workspace)
        if (!directory.isDirectory) return RepositoryState.unknown()

        val process = try {
            ProcessBuilder(
                listOf(
                    "git", "-C", directory.absolutePath,
                    "status", "--porcelain=v1", "--branch"
                )
            ).redirectErrorStream(true).start()
        } catch (_: Exception) {
            return RepositoryState.unknown()
        }

        val pump = Executors.newSingleThreadExecutor { task ->
            Thread(task, "atropos-git-output").apply { isDaemon = true }
        }
        val output = pump.submit<String> {
            process.inputStream.bufferedReader().use { reader ->
                val buffer = CharArray(4096)
                val result = StringBuilder()
                while (result.length < outputLimit) {
                    val count = reader.read(
                        buffer,
                        0,
                        minOf(buffer.size, outputLimit - result.length)
                    )
                    if (count < 0) break
                    result.append(buffer, 0, count)
                }
                result.toString()
            }
        }

        return try {
            if (!process.waitFor(timeoutMillis, TimeUnit.MILLISECONDS)) {
                process.descendants().forEach { it.destroyForcibly() }
                process.destroyForcibly()
                output.cancel(true)
                RepositoryState.unknown()
            } else if (process.exitValue() != 0) {
                RepositoryState(false, null, null, true)
            } else {
                parse(output.get(250, TimeUnit.MILLISECONDS))
            }
        } catch (_: Exception) {
            process.destroyForcibly()
            RepositoryState.unknown()
        } finally {
            pump.shutdownNow()
        }
    }

    private fun parse(output: String): RepositoryState {
        val lines = output.lineSequence().filter(String::isNotBlank).toList()
        val header = lines.firstOrNull()?.takeIf { it.startsWith("## ") }
            ?: return RepositoryState(false, null, null, true)
        val branchText = header.removePrefix("## ").substringBefore("...").trim()
        val branch = branchText.takeUnless {
            it.isBlank() || it == "HEAD (no branch)" || it == "No commits yet on"
        }
        val changes = lines.drop(1).count()
        return RepositoryState(true, branch, changes, true)
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
    val activeOperation: String?,
    val repository: RepositoryState = RepositoryState.unknown()
)
EOF

cat > src/main/kotlin/atropos/cli/ui/HeaderRenderer.kt <<'EOF'
/* SPDX-License-Identifier: AGPL-3.0-only */
package atropos.cli.ui

class HeaderRenderer(
    private val theme: TerminalTheme
) {
    fun render(state: SessionPresentationState, width: Int): String {
        val safeWidth = width.coerceAtLeast(1)
        val brand = " ATROPOS "
        val rightCandidates = listOf(
            "${state.provider.lowercase()} · ${state.mode.lowercase()} · /help ",
            "${state.mode.lowercase()} · /help ",
            "/help "
        )
        val right = rightCandidates.firstOrNull {
            TerminalText.cellWidth(brand) + TerminalText.cellWidth(it) <= safeWidth
        }.orEmpty()
        val gap = (
            safeWidth - TerminalText.cellWidth(brand) - TerminalText.cellWidth(right)
            ).coerceAtLeast(0)
        return theme.headerBrand(brand) +
            theme.headerText(" ".repeat(gap) + right)
    }
}
EOF

cat > src/main/kotlin/atropos/cli/ui/LandingRenderer.kt <<'EOF'
/* SPDX-License-Identifier: AGPL-3.0-only */
package atropos.cli.ui

class LandingRenderer(
    private val theme: TerminalTheme
) {
    fun render(state: SessionPresentationState, width: Int): List<String> {
        val safeWidth = width.coerceIn(36, 160)
        val path = TerminalText.ellipsize(
            TerminalText.compactPath(state.workspace),
            (safeWidth - 3).coerceAtLeast(16)
        )
        val repository = state.repository
        val output = mutableListOf<String>()

        output += theme.strong("Workspace")
        output += theme.path(path)

        if (repository.available && repository.isRepository) {
            val branch = repository.branch ?: "detached"
            val condition = when (repository.clean) {
                true -> theme.success("clean")
                false -> theme.warning("${repository.changedFiles ?: "--"} changed")
                null -> theme.metadata("status --")
            }
            output += theme.metadata("git  ") + theme.code(branch) +
                theme.metadata(" · ") + condition
        }

        output += ""
        output += theme.metadata("Capabilities")
        output += if (safeWidth >= 56) {
            "ask · plan · autopilot · verify · provider switching"
        } else {
            "ask · plan · verify · switch provider"
        }
        output += theme.subdued("Type / to browse commands")

        return output.flatMap { AnsiLineWrapper.wrap(it, safeWidth) }
    }
}
EOF

cat > src/main/kotlin/atropos/cli/ui/WelcomePanel.kt <<'EOF'
/* SPDX-License-Identifier: AGPL-3.0-only */
package atropos.cli.ui

class WelcomePanel(
    private val landing: LandingRenderer
) {
    constructor(theme: TerminalTheme) : this(LandingRenderer(theme))

    fun render(
        state: SessionPresentationState,
        terminalWidth: Int
    ): List<String> = landing.render(state, terminalWidth)

    fun render(
        provider: String,
        workspace: String,
        mode: String,
        terminalWidth: Int
    ): List<String> = render(
        SessionPresentationState(
            provider,
            mode,
            workspace,
            listOf("/help", "/status", "/use", "/verify", "/exit"),
            MetricValue.Unknown,
            MetricValue.Unknown,
            null
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

    fun header(state: SessionPresentationState, width: Int): String =
        headerRenderer.render(state, width)

    fun footer(
        provider: String,
        mode: String,
        workspace: String,
        tracker: QuotaSessionTracker,
        verificationState: String?,
        width: Int
    ): String = footer(
        SessionPresentationState(
            provider = provider,
            mode = mode,
            workspace = workspace,
            commands = emptyList(),
            tokens = tracker.estimatedTokens.takeIf { it > 0 }
                ?.let { MetricValue.Known(it.toString()) } ?: MetricValue.Unknown,
            cost = tracker.estimatedCostUsd().takeIf { it > 0.0 }
                ?.let { MetricValue.Known("$" + String.format("%.4f", it)) }
                ?: MetricValue.Unknown,
            activeOperation = verificationState
        ),
        width
    )

    fun footer(state: SessionPresentationState, width: Int): String {
        val operation = state.activeOperation
            ?.let(TerminalText::sanitize)
            ?.takeIf(String::isNotBlank)
        val branch = state.repository.branch
        val path = TerminalText.compactPath(state.workspace)
        val candidates = listOfNotNull(
            listOfNotNull(
                operation,
                branch,
                "${state.tokens.text()} tok",
                state.cost.text(),
                path
            ).joinToString(" · "),
            listOfNotNull(
                operation,
                branch,
                "${state.tokens.text()} tok",
                path
            ).joinToString(" · "),
            listOfNotNull(operation, branch, "${state.tokens.text()} tok")
                .joinToString(" · "),
            listOfNotNull(operation, branch, path).joinToString(" · ")
        )
        val content = candidates.firstOrNull {
            TerminalText.cellWidth(" $it") <= width
        } ?: candidates.last()
        return theme.footer(
            TerminalText.padEnd(
                TerminalText.ellipsize(" $content", width),
                width
            )
        )
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
    private val statusBar: StatusBarRenderer,
    private val workspaceInspector: WorkspaceInspector =
        CachingGitWorkspaceInspector()
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
        val repository = workspaceInspector.inspect(workspace)
        val operation = activity?.let(TerminalText::stripAnsi)
            ?: verificationState
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
            activeOperation = operation,
            repository = repository
        )

        frame.setLine(0, statusBar.header(state, safeWidth))

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
            val landing = welcomePanel.render(state, safeWidth)
            landing.take(transcriptHeight).forEachIndexed { index, line ->
                frame.setLine(transcriptStart + index, line)
            }
            activity?.let {
                val row = transcriptStart + landing.size
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

        frame.setLine(separatorRow, theme.subdued("─".repeat(safeWidth)))
        paletteLines.forEachIndexed { index, line ->
            val row = paletteStart + index
            if (row in 1 until composerStart) frame.setLine(row, line)
        }
        composerSnapshot.lines.forEachIndexed { index, line ->
            frame.setLine(composerStart + index, line)
        }
        frame.setLine(footerRow, statusBar.footer(state, safeWidth))
        frame.cursorX = composerSnapshot.cursorColumn
        frame.cursorY = composerStart + composerSnapshot.cursorRow + 1
        frame.showCursor = true
        return frame
    }
}
EOF

echo "=== BATCH 10 FULL COMPILE ==="
mapfile -d '' SOURCES < <(
  find src/main/kotlin -type f -name '*.kt' -print0
)
kotlinc -include-runtime -d "$TMP/atropos.jar" "${SOURCES[@]}"

cat > "$TMP/Batch10SmokeTest.kt" <<'EOF'
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

    fun layout(repository: RepositoryState) = ViewportLayout(
        theme,
        WelcomePanel(theme),
        StatusBarRenderer(theme),
        WorkspaceInspector { repository }
    )

    listOf(36 to 12, 40 to 18, 56 to 20, 80 to 24, 120 to 40)
        .forEach { (width, height) ->
            composer.update("", "", 0, "ASK")
            val frame = layout(
                RepositoryState(true, "main", 2, true)
            ).build(
                width, height, transcript, composer,
                "groq", "~/ATROPOS", tracker, null, null
            )
            check(frame.lines.size == height)
            check(frame.lines.all { TerminalText.cellWidth(it) == width })
            check(plain(frame.lines[0]).contains("ATROPOS"))
            check(plain(frame.lines[1]).contains("Workspace"))
            check(frame.lines.any { plain(it).contains("main") })
            check(frame.lines.any { plain(it).contains("2 changed") })
            val landing = frame.lines.take(8).joinToString("\n", transform = ::plain)
            check(!landing.contains("provider:", ignoreCase = true))
            check(!landing.contains("mode:", ignoreCase = true))
            check(!landing.contains("SESSION"))
        }

    val cleanFrame = layout(
        RepositoryState(true, "main", 0, true)
    ).build(56, 20, transcript, composer, "groq", "~/ATROPOS", tracker, null, null)
    check(cleanFrame.lines.any { plain(it).contains("clean") })

    val unknownFrame = layout(RepositoryState.unknown()).build(
        40, 18, transcript, composer, "groq", "~/ATROPOS", tracker, null, null
    )
    check(unknownFrame.lines.none { plain(it).contains("unknown", ignoreCase = true) })

    transcript.append("first interaction")
    val activeFrame = layout(RepositoryState(true, "main", 0, true)).build(
        56, 20, transcript, composer, "groq", "~/ATROPOS", tracker, null, null
    )
    check(activeFrame.lines.none { plain(it).contains("Capabilities") })
    check(activeFrame.lines.any { plain(it).contains("first interaction") })

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
    check(bytes.toString().contains("\u001B[?1049h"))
    check(bytes.toString().contains("\u001B[?1049l"))

    println("BATCH10_SMOKE_OK")
}
EOF

echo "=== BATCH 10 BEHAVIOR TEST ==="
kotlinc -cp "$TMP/atropos.jar" -include-runtime \
  -d "$TMP/smoke.jar" "$TMP/Batch10SmokeTest.kt"
java -cp "$TMP/atropos.jar:$TMP/smoke.jar" Batch10SmokeTestKt |
  grep -Fx "BATCH10_SMOKE_OK"

jar tf "$TMP/atropos.jar" |
  grep -Fx 'atropos/MainKt.class' >/dev/null

echo "=== BATCH 10 HEADLESS REGRESSION ==="
printf '/exit\n' | java -jar "$TMP/atropos.jar" >/dev/null

git diff --check -- "${FILES[@]}"

mkdir -p .atropos/backups
if [ -f atropos.jar ]; then
  cp atropos.jar ".atropos/backups/atropos.$(date +%s).jar"
fi
mv "$TMP/atropos.jar" "$HOME/ATROPOS/atropos.jar"

SUCCESS=1
echo "BATCH 10 AGENT WORKBENCH SUCCESSFUL | E(DELTA)=0"
)
