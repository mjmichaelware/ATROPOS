/* SPDX-License-Identifier: AGPL-3.0-only */
package atropos.core.intent

data class SwarmAgentConfig(
    val agentName: String,
    val role: String,
    val permissions: List<String>
)

object SwarmAgentParser {
    private val agentRegex = Regex("""agent:\s*(?<name>\w+)\s*\|\s*role:\s*(?<role>\w+)\s*\|\s*permissions:\s*\[(?<perms>[^\]]+)\]""")

    fun parse(content: String): List<SwarmAgentConfig> {
        return agentRegex.findAll(content).map { match ->
            val perms = match.groups["perms"]?.value?.split(",")?.map { it.trim() } ?: emptyList()
            SwarmAgentConfig(
                agentName = match.groups["name"]?.value ?: "",
                role = match.groups["role"]?.value ?: "",
                permissions = perms
            )
        }.toList()
    }
}
