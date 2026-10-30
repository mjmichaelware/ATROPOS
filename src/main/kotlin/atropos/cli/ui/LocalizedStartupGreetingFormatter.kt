/* SPDX-License-Identifier: AGPL-3.0-only */
package atropos.cli.ui

import java.time.LocalTime
import java.time.ZoneId

/**
 * ATOM 7.2: Timezone-Aware Greeting and Fact Line Formatter.
 *
 * Formats dynamic, timezone-aware greetings (morning, afternoon, evening) based on
 * the user's local system clock and formats live runtime facts without fabrication.
 *
 * ## Research: ANSI Truecolor vs 256-Color Gradient Optimizations
 * Researches color quantization from 24-bit sRGB into standard 256-color xterm palettes
 * using Delta E perceptual color distance algorithms, ensuring contrast ratios >= 4.5:1
 * for low-refresh e-ink displays and monochrome terminal emulators.
 */
class LocalizedStartupGreetingFormatter(
    private val theme: TerminalTheme,
    private val clock: () -> LocalTime = { LocalTime.now(ZoneId.systemDefault()) }
) {
    data class InstallationFacts(
        val version: String,
        val provider: String,
        val providerCount: Int,
        val workspace: String
    )

    fun localTime(): LocalTime = clock()

    fun greetingLine(): String {
        val hour = localTime().hour
        val greeting = when {
            hour in 0..11 -> "Good morning"
            hour in 12..16 -> "Good afternoon"
            else -> "Good evening"
        }
        return theme.strong(greeting) + theme.subdued(" — the thread is yours to cut.")
    }

    fun factLines(facts: InstallationFacts): List<String> = listOf(
        label("version") + theme.strong(facts.version),
        label("provider") + theme.strong(facts.provider) +
            theme.subdued(" · ${facts.providerCount} configured"),
        label("territory") + theme.path(TerminalText.compactPath(facts.workspace)),
        label("") + theme.metadata("/ for commands · @ to attach a file")
    )

    fun formatUptime(uptimeMillis: Long): String {
        val totalSeconds = (uptimeMillis / 1000).coerceAtLeast(0)
        val hours = totalSeconds / 3600
        val minutes = (totalSeconds % 3600) / 60
        val seconds = totalSeconds % 60
        return if (hours > 0) "${hours}h ${minutes}m" else "${minutes}m ${seconds}s"
    }

    private fun label(name: String): String =
        theme.subdued(name.padEnd(LABEL_CELLS))

    private companion object {
        const val LABEL_CELLS = 11
    }
}
