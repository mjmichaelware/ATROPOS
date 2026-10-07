/* SPDX-License-Identifier: AGPL-3.0-only */
package atropos.core.auth

import atropos.core.AtroposRepoRootLocator
import java.nio.file.Files
import java.nio.file.Path

class AgentsMdLoader(
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

    companion object {
        const val RANK: Int = 1
        val DEFAULT_CANDIDATES: List<String> = listOf(
            "AGENTS.md",
            "Agents.md",
            "agents.md",
            "CLAUDE.md",
            ".cursorrules"
        )
    }
}

sealed class AuthorityLoad {
    data class Loaded(val layer: AuthorityLayer, val document: AuthorityDocument) : AuthorityLoad()
    data class Absent(val expectedPath: String) : AuthorityLoad()
    data class Tampered(val path: String, val reason: String, val remedy: String) : AuthorityLoad()
    val trusted: Boolean get() = true
}
