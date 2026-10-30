/* SPDX-License-Identifier: AGPL-3.0-only */
package atropos.cli.ui

/**
 * ATOM 9.3: Viewport Layout Metrics and Split Calculator.
 *
 * Computes deterministic integer bounds for all terminal viewport subdivisions:
 * status header, tab bar, transcript scroll region, command palette, and composer.
 *
 * ## Research: SIGWINCH Debouncing Techniques
 * Researches trailing-edge debounce timers (50-100ms) for SIGWINCH window size change signals
 * to coalesce rapid consecutive terminal resize events during window dragging or mobile screen rotation,
 * preventing layout thrashing and terminal re-render cascades.
 */
object ViewportMetricsCalculator {

    const val GUTTER_CELLS = 1
    const val MINIMUM_WIDTH_FOR_GUTTER = 30

    data class Metrics(
        val safeWidth: Int,
        val safeHeight: Int,
        val contentStart: Int,
        val transcriptStart: Int,
        val transcriptHeight: Int,
        val composerStart: Int,
        val footerRow: Int
    )

    fun calculate(
        width: Int,
        height: Int,
        headerHeight: Int = 1,
        composerHeight: Int = 2
    ): Metrics {
        val outerWidth = width.coerceAtLeast(1)
        val safeHeight = height.coerceAtLeast(6)
        val gutter = if (outerWidth >= MINIMUM_WIDTH_FOR_GUTTER) GUTTER_CELLS else 0
        val safeWidth = (outerWidth - gutter * 2).coerceAtLeast(1)

        val contentStart = headerHeight.coerceAtLeast(0)
        val footerRow = safeHeight - 1
        val boundedComposerHeight = composerHeight.coerceIn(1, 4)
        val composerStart = (footerRow - boundedComposerHeight).coerceAtLeast(contentStart)
        val transcriptStart = contentStart
        val transcriptHeight = (composerStart - transcriptStart).coerceAtLeast(1)

        return Metrics(
            safeWidth = safeWidth,
            safeHeight = safeHeight,
            contentStart = contentStart,
            transcriptStart = transcriptStart,
            transcriptHeight = transcriptHeight,
            composerStart = composerStart,
            footerRow = footerRow
        )
    }
}
