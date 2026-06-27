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
