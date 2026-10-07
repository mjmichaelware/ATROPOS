/* SPDX-License-Identifier: AGPL-3.0-only */
package atropos.core.auth

import atropos.core.AtroposRepoRootLocator
import java.nio.file.Files
import java.nio.file.Path

class SwarmMdLoader(
    private val repoRoot: Path = AtroposRepoRootLocator.resolve(),
    private val candidates: List<String> = DEFAULT_CANDIDATES
) {
    fun load(): AuthorityLoad {
        for (candidate in candidates) {
            val file = repoRoot.resolve(candidate)
            if (Files.isRegularFile(file)) {
                val text = runCatching { Files.readString(file) }.getOrNull() ?: continue
                return AuthorityLoad.Loaded(
                    layer = AuthorityLayer(
                        name = candidate,
                        rank = RANK,
                        values = AuthorityMarkdownParser.parse(text)
                    ),
                    document = AuthorityDocument(candidate, "", RANK)
                )
            }
        }
        return AuthorityLoad.Absent(candidates.first())
    }

    fun spec(load: AuthorityLoad): SwarmSpec? {
        if (load !is AuthorityLoad.Loaded) return null
        val file = repoRoot.resolve(load.layer.name)
        val text = runCatching { Files.readString(file) }.getOrNull() ?: return null
        val values = load.layer.values

        return SwarmSpec(
            nodes = parseNodes(text),
            maxDepth = values["maxDepth"]?.toIntOrNull() ?: 0,
            escalationPath = values["escalationPath"]
                ?.split(',')
                ?.map { it.trim() }
                ?.filter { it.isNotEmpty() }
                .orEmpty(),
            coordinationCostBound = values["coordinationCostBound"]?.toLongOrNull()
        )
    }

    private fun parseNodes(text: String): List<SwarmNode> =
        AuthorityMarkdownParser.section(text, "Nodes").mapNotNull { line ->
            val parts = line.split('|').map { it.trim() }
            if (parts.size < 2 || parts[0].isEmpty()) return@mapNotNull null
            SwarmNode(
                name = parts[0],
                role = parts[1],
                territoryGrants = parts.drop(2).filter { it.isNotEmpty() }
            )
        }

    companion object {
        const val RANK: Int = 2
        val DEFAULT_CANDIDATES: List<String> = listOf("SWARM.md", "Swarm.md", "swarm.md")
    }
}
