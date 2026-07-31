/* SPDX-License-Identifier: AGPL-3.0-only */
package atropos.cli

/** Converts the shorthand command into the canonical agent command. */
internal object SelfHostAliasTranslator {
    private val routedSubcommands = setOf(
        "run", "start", "status", "watch", "resume", "recover", "next",
        "stop", "verify", "promote", "export-evidence", "history", "learned",
        "benchmark"
    )

    fun translate(tokens: List<String>): List<String>? {
        if (tokens.isEmpty()) return null
        val first = tokens.first().lowercase()
        if (first != "/self-host" && first != "self-host") return null

        val remainder = tokens.drop(1)
        val subcommand = remainder.firstOrNull()?.lowercase()
        return when {
            subcommand == null -> listOf("/agent", "self-host")
            subcommand in routedSubcommands -> listOf("/agent", "self-host") + remainder
            else -> listOf("/agent", "self-host", "run") + remainder
        }
    }
}
