/* SPDX-License-Identifier: AGPL-3.0-only */
package atropos.cli.ui

import atropos.core.AtroposConfig

/**
 * Executes the opening terminal animation into [TerminalCanvas].
 */
class AnsiStartupPlayer(
    private val theme: TerminalTheme,
    private val canvas: TerminalCanvas
) {
    private val startupSequence = StartupSequence(theme)
    private var startupPlayed = false

    fun play(config: AtroposConfig, activeProvider: String, workspace: String) {
        if (startupPlayed) return
        startupPlayed = true
        if (!System.getenv("ATROPOS_NO_ANIMATION").isNullOrBlank()) return
        if (canvas.height < MINIMUM_ANIMATION_ROWS || canvas.width < MINIMUM_ANIMATION_COLUMNS) return

        val facts = StartupSequence.Facts(
            version = versionLabel(),
            provider = activeProvider,
            providerCount = configuredProviderCount(config),
            workspace = workspace
        )

        for (lines in startupSequence.frames(canvas.width, canvas.height, facts)) {
            val frame = ScreenFrame(canvas.width, canvas.height)
            lines.forEachIndexed(frame::setLine)
            frame.showCursor = false
            canvas.render(frame)
            if (!pause(FRAME_MILLIS)) return
        }
        pause(SETTLE_MILLIS)
    }

    private fun configuredProviderCount(config: AtroposConfig): Int {
        val legacy = with(config.keys) {
            listOf("groq" to groq, "openai" to openai, "anthropic" to anthropic, "xai" to xai)
                .filter { it.second.isNotBlank() }
                .map { it.first }
        }.toSet()

        return atropos.core.provider.StaticProviderDescriptorRegistry().getAll().count { descriptor ->
            when {
                descriptor.id in legacy -> true
                descriptor.requiredEnv.any { it == "OLLAMA_HOST" || it == "OLLAMA_MODEL" } -> true
                descriptor.requiredEnv.isEmpty() -> false
                else -> descriptor.requiredEnv.all { !System.getenv(it).isNullOrBlank() }
            }
        }
    }

    private fun pause(millis: Long): Boolean = try {
        Thread.sleep(millis)
        true
    } catch (_: InterruptedException) {
        Thread.currentThread().interrupt()
        false
    }

    private fun versionLabel(): String =
        javaClass.`package`?.implementationVersion?.takeIf(String::isNotBlank) ?: "dev"

    companion object {
        const val MINIMUM_ANIMATION_ROWS = 18
        const val MINIMUM_ANIMATION_COLUMNS = 24
        const val FRAME_MILLIS = 50L
        const val SETTLE_MILLIS = 400L
    }
}
