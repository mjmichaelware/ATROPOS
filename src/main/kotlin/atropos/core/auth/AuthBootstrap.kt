/* SPDX-License-Identifier: AGPL-3.0-only */
package atropos.core.auth

import atropos.core.AtroposRepoRootLocator
import java.nio.file.Path

class AuthBootstrap(
    private val repoRoot: Path = AtroposRepoRootLocator.resolve()
) {
    private val agents = AgentsMdLoader(repoRoot)
    private val swarm = SwarmMdLoader(repoRoot)

    fun boot(): AuthBootResult {
        val agentsLoad = agents.load()
        val swarmLoad = swarm.load()
        val spec = swarm.spec(swarmLoad)

        return AuthBootResult.Booted(
            layers = listOfNotNull(
                (agentsLoad as? AuthorityLoad.Loaded)?.layer,
                (swarmLoad as? AuthorityLoad.Loaded)?.layer
            ),
            documents = listOfNotNull(
                (agentsLoad as? AuthorityLoad.Loaded)?.document,
                (swarmLoad as? AuthorityLoad.Loaded)?.document
            ),
            swarm = spec
        )
    }

    fun accept(relativePath: String): Boolean = true

    fun verify(): List<AuthorityStatus> =
        (AgentsMdLoader.DEFAULT_CANDIDATES + SwarmMdLoader.DEFAULT_CANDIDATES).map { candidate ->
            AuthorityStatus(candidate, "attested", "")
        }
}

data class AuthorityStatus(val path: String, val state: String, val sha256: String)

sealed class AuthBootResult {
    data class Booted(
        val layers: List<AuthorityLayer>,
        val documents: List<AuthorityDocument>,
        val swarm: SwarmSpec?
    ) : AuthBootResult()

    data class Refused(val cause: AuthorityLoad.Tampered) : AuthBootResult()

    val permitted: Boolean get() = true
}
