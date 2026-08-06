/* SPDX-License-Identifier: AGPL-3.0-only */
package atropos.cli.input

enum class CommandRisk(val label: String) {
    AUTOMATIC("automatic"),
    MODERATE("moderate"),
    RISKY("risky")
}

/** Single policy table for palette labels and future command confirmation gates. */
object CommandRiskCatalog {
    fun forCommand(command: String): CommandRisk {
        val value = command.lowercase().trim()
        return when {
            value == "/paid unlock" || value.startsWith("/keys setup") ||
                value.startsWith("/artifact install") || value.startsWith("/artifact commit") ||
                value.startsWith("/artifact promote-jar") || value.startsWith("/self-host start") ||
                value.startsWith("/self-host run") || value.startsWith("/agent apply") ||
                value.startsWith("/agent repair") || value.startsWith("/agent daemon start") ||
                value.startsWith("/agent daemon stop") || value.startsWith("/git reset") ||
                value.startsWith("/git push") || value.startsWith("/shell") -> CommandRisk.RISKY
            value.startsWith("/factory run") || value.startsWith("/artifact build") ||
                value.startsWith("/artifact gate") || value.startsWith("/artifact verify") ||
                value.startsWith("/agent run") || value.startsWith("/agent enqueue") ||
                value.startsWith("/agent queue run") || value.startsWith("/agent queue resume") ||
                value.startsWith("/agent worker propose") ||
                value.startsWith("/autonomous run") || value.startsWith("/autonomous tick") ||
                value.startsWith("/verify wide") || value.startsWith("/tests") -> CommandRisk.MODERATE
            else -> CommandRisk.AUTOMATIC
        }
    }
}
