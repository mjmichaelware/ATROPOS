/* SPDX-License-Identifier: AGPL-3.0-only */
package atropos.cli.ui.vis

import atropos.cli.ui.HeaderRenderer
import atropos.cli.ui.StatusBarRenderer
import atropos.cli.ui.SessionPresentationState
import atropos.cli.ui.chrome.CheckpointAge
import atropos.cli.ui.design.Role

/**
 * F-VIS-001: CLI Open Frame
 *
 * The pinned reference's session-first frame layout:
 * - Fixed rows 1-2 header
 * - Body scroll
 * - Last 2 rows input
 *
 * Top-left: Brand
 * Header center: ContextSight + status vocab
 * Header right: Checkpoint age
 *
 * Depends on: F-CLI-001 (sticky header + anchored input)
 */
class CliOpenFrame(
    private val theme: TerminalTheme,
    private val headerRenderer: HeaderRenderer = HeaderRenderer(theme),
    private val statusBarRenderer: StatusBarRenderer = StatusBarRenderer(theme)
) {

    data class Frame(
        val header: String,
        val body: List<String>,
        val footer: String
    )

    fun render(
        state: SessionPresentationState,
        width: Int,
        height: Int,
        checkpointAge: CheckpointAge = CheckpointAge.Unknown
    ): Frame {
        val safeWidth = width.coerceAtLeast(20)
        val safeHeight = height.coerceAtLeast(6)

        // Header (rows 1-2)
        val headerLine = headerRenderer.render(state, safeWidth)

        // Footer (last 2 rows) - anchored input row + provider row
        val footerLine = statusBarRenderer.footer(state, safeWidth, checkpointAge)

        // Body = remaining rows between header and footer
        val headerHeight = 2
        val footerHeight = 2
        val bodyHeight = (safeHeight - headerHeight - footerHeight).coerceAtLeast(1)

        val body = buildList {
            // Body content would be filled by transcript renderer
            // This frame just provides the layout structure
            add("") // placeholder for transcript content
        }.take(bodyHeight)

        return Frame(
            header = headerLine,
            body = body,
            footer = footerLine
        )
    }
}

/**
 * F-VIS-002: CLI Hero / Body
 *
 * The transcript IS the hero. The six answers are pinned at the bottom
 * of the hero region, not in a separate panel.
 *
 * Depends on: F-CLI-002 (sticky header + anchored input)
 */
class CliHero(
    private val theme: TerminalTheme
) {
    data class Hero(
        val transcript: List<String>,
        val sixAnswers: List<String>,
        val height: Int
    )

    fun render(
        transcriptLines: List<String>,
        sixAnswers: List<String>,
        height: Int
    ): Hero {
        val heroHeight = height.coerceAtLeast(6)
        val answersHeight = sixAnswers.size.coerceAtMost(6)
        val transcriptHeight = (heroHeight - answersHeight).coerceAtLeast(1)

        val visibleTranscript = transcriptLines
            .takeLast(transcriptHeight)

        return Hero(
            transcript = visibleTranscript,
            sixAnswers = sixAnswers,
            height = heroHeight
        )
    }
}

/**
 * F-VIS-003: CLI Footer
 *
 * Anchored input + provider row.
 * The reference renders the input anchored at the bottom,
 * with provider/mode/tab/tokens/patch state pills on the right.
 *
 * Depends on: F-CLI-001 (sticky header), F-CLI-006 (provider summary)
 */
class CliFooter(
    private val theme: TerminalTheme,
    private val statusBarRenderer: StatusBarRenderer = StatusBarRenderer(theme)
) {
    data class Footer(
        val line: String
    )

    fun render(
        state: SessionPresentationState,
        width: Int,
        checkpointAge: CheckpointAge = CheckpointAge.Unknown
    ): Footer {
        val footerLine = StatusBarRenderer(theme).footer(state, width, checkpointAge)
        return Footer(footerLine)
    }
}