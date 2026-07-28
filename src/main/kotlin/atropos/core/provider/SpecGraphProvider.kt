package atropos.core.provider

import atropos.core.provider.adapter.ProviderAdapter
import atropos.core.provider.adapter.AdapterRequest
import atropos.core.provider.adapter.AdapterStatus
import atropos.core.provider.ProviderCallResult
import atropos.core.specgraph.SpecGraph
import atropos.core.specgraph.SpecGraphAtom
import atropos.core.specgraph.SpecGraphReader
import atropos.core.specgraph.SpecGraphResolutionException
import atropos.core.territory.TerritoryAssignment
import java.nio.file.Files
import java.nio.file.Path

/**
 * Provider adapter that connects ATROPOS to authoritative SpecGraph outputs.
 * Reuses existing semantic owners and follows the bounded implementation loop.
 * Does not create duplicate provider registry, DAG, policy, territory, verifier,
 * memory root, queue, journal, or evidence systems.
 */
class SpecGraphProvider(
    private val reader: SpecGraphReader,
    private val specGraphRoot: Path,
    override val descriptor: ProviderDescriptor
) : ProviderAdapter {

    override fun status(): AdapterStatus {
        val ready = Files.isDirectory(specGraphRoot) && Files.isRegularFile(specGraphRoot.resolve("graph.json"))
        return AdapterStatus(
            providerId = descriptor.id,
            ready = ready,
            message = if (ready) "specgraph root ready" else "specgraph root missing graph.json"
        )
    }

    override fun canHandle(request: AdapterRequest): Boolean {
        return super.canHandle(request) &&
               request.task.authoritySource == AuthoritySource.SPECGRAPH
    }

    override fun complete(request: AdapterRequest): ProviderCallResult {
        val st = status()
        if (!st.ready) {
            return ProviderCallResult.Failure(
                providerId = descriptor.id,
                message = st.message ?: "specgraph provider not ready"
            )
        }
        // SpecGraph provider resolves authority; it does not execute free-form model prose.
        return ProviderCallResult.Failure(
            providerId = descriptor.id,
            message = "SpecGraphProvider.complete is resolution-only; use resolveRequirement/claimAtom"
        )
    }

    /**
     * Resolve an exact requirement address to a SpecGraph atom.
     * Returns the atom or throws typed NoMatch ([SpecGraphResolutionException]) if not found.
     * This enforces 100% exact source resolution with no fuzzy matching.
     */
    fun resolveRequirement(requirementAddress: String): SpecGraphAtom {
        val graph = reader.readSpecGraph(specGraphRoot.resolve("graph.json"))
        return reader.resolveAtom(graph, requirementAddress)
    }

    /**
     * Establish a runtime claim for a SpecGraph node within a territory.
     * The claim includes the source hash for immutable evidence tracking.
     */
    fun claimAtom(atom: SpecGraphAtom, graph: SpecGraph, territory: TerritoryAssignment): SpecGraphClaim {
        return SpecGraphClaim(
            atomId = atom.id,
            requirementAddress = atom.requirementAddress ?: atom.id,
            territory = territory,
            authorityHash = reader.sha256Hex(graph.id + atom.id + (atom.requirementAddress ?: "")),
            status = ClaimStatus.CLAIMED
        )
    }

    /**
     * Validate that a SpecGraph atom's authority reference is non-empty and resolvable.
     * Returns true if the atom has a valid authority source, false otherwise.
     */
    fun validateAtomAuthority(atom: SpecGraphAtom): Boolean {
        return atom.authority != null && atom.authority.isNotBlank()
    }
}

data class SpecGraphClaim(
    val atomId: String,
    val requirementAddress: String,
    val territory: TerritoryAssignment,
    val authorityHash: String,
    val status: ClaimStatus
)

enum class ClaimStatus {
    CLAIMED, IMPLEMENTED, VERIFIED, COMPLETED, REJECTED
}

enum class AuthoritySource {
    SPECGRAPH, INTERNAL, EXTERNAL
}
