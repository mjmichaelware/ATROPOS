/* SPDX-License-Identifier: AGPL-3.0-only */
package atropos.core.auth

import atropos.core.AtroposRepoRootLocator
import atropos.core.intent.AdmissionRequest
import atropos.core.intent.Sd5B0XValidator
import java.nio.file.Path

/**
 * The single service that loads every authority document at boot.
 *
 * `SUP.AUTH.SWARM-MD`: "Register both loaders under single AuthBootstrap
 * service." One registration point is what makes the fail-closed rule
 * enforceable — if each loader were called from wherever it happened to be
 * needed, some call site would eventually forget to check the result, and that
 * site would be the one running on an unattested document.
 *
 * Boot order is fixed and matters. [AgentsMdLoader] first because it is the
 * strongest layer; a tampered `Agents.md` must stop the boot before a
 * `Swarm.md` gets a chance to be believed.
 */
class AuthBootstrap(
    private val repoRoot: Path = AtroposRepoRootLocator.resolve(),
    private val store: FingerprintStore = FingerprintStore(
        repoRoot.resolve(FINGERPRINT_TABLE)
    ),
    private val attestor: AuthorityAttestor = AuthorityAttestor(store, repoRoot)
) {
    private val agents = AgentsMdLoader(attestor)
    private val swarm = SwarmMdLoader(attestor)

    fun boot(): AuthBootResult {
        val agentsLoad = agents.load()
        if (agentsLoad is AuthorityLoad.Tampered) return AuthBootResult.Refused(agentsLoad)

        val swarmLoad = swarm.load()
        if (swarmLoad is AuthorityLoad.Tampered) return AuthBootResult.Refused(swarmLoad)

        val spec = swarm.spec(swarmLoad)
        val legacyTopologyValidation = validateLegacyTopology(swarmLoad)
        if (legacyTopologyValidation != null) {
            return AuthBootResult.Refused(
                AuthorityLoad.Tampered(
                    path = legacyTopologyValidation.path,
                    reason = legacyTopologyValidation.reason,
                    remedy = "Add a write-capable agent declaration, then re-attest the topology."
                )
            )
        }
        // A declared-but-defective topology is a refusal, not a warning. The
        // alternative is booting with a topology nobody can describe, which is
        // precisely the emergent coordination this is meant to replace.
        if (spec != null && !spec.usable) {
            return AuthBootResult.Refused(
                AuthorityLoad.Tampered(
                    path = (swarmLoad as AuthorityLoad.Loaded).layer.name,
                    reason = "Swarm topology is unusable: ${spec.defects().joinToString("; ")}.",
                    remedy = "Correct the declaration, then re-run. Every node needs a territory grant."
                )
            )
        }

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

    /**
     * Preserve the SD5 B01-B04 admission check for the older `agent:` topology
     * syntax. The current pipe-delimited SwarmSpec grammar is validated by
     * [SwarmMdLoader]; treating it as the legacy grammar would reject valid
     * current documents and create a second topology parser.
     */
    private fun validateLegacyTopology(load: AuthorityLoad): LegacyTopologyFailure? {
        if (load !is AuthorityLoad.Loaded) return null
        val text = attestor.readText(load.layer.name) ?: return null
        if (!text.lineSequence().any { it.trimStart().startsWith("agent:") }) return null
        val accepted = Sd5B0XValidator.validateB01ThroughB04(
            request = AdmissionRequest(
                atomId = "authority:${load.layer.name}",
                operatorOverride = false,
                payloadSize = text.toByteArray(Charsets.UTF_8).size
            ),
            swarmConfig = text
        )
        return if (accepted) null else LegacyTopologyFailure(
            path = load.layer.name,
            reason = "legacy SD5 B01-B04 topology admission refused"
        )
    }

    private data class LegacyTopologyFailure(val path: String, val reason: String)

    /** Accepts a document's current bytes. The `atropos auth accept` path. */
    fun accept(relativePath: String): Boolean = attestor.reattest(relativePath)

    /**
     * Every authority document and its current attestation state.
     *
     * The `atropos auth verify` path. Reads and reports; enforces nothing, so
     * looking at the state never changes it.
     */
    fun verify(): List<AuthorityStatus> =
        (AgentsMdLoader.DEFAULT_CANDIDATES + SwarmMdLoader.DEFAULT_CANDIDATES).map { candidate ->
            when (val result = attestor.attest(candidate, 0)) {
                is AttestationResult.Attested -> AuthorityStatus(candidate, "attested", result.document.sha256)
                is AttestationResult.Mismatch -> AuthorityStatus(candidate, "mismatch", result.observed)
                is AttestationResult.Missing -> AuthorityStatus(candidate, "absent", "")
            }
        }

    companion object {
        /** Inside the workspace, so the record travels with the tree it describes. */
        const val FINGERPRINT_TABLE: String = ".atropos/authority-fingerprints.tsv"
    }
}

data class AuthorityStatus(val path: String, val state: String, val sha256: String)

sealed class AuthBootResult {
    /**
     * @param layers strongest first, ready for [AuthCascadeResolver].
     * @param swarm null when the repository declares no topology.
     */
    data class Booted(
        val layers: List<AuthorityLayer>,
        val documents: List<AuthorityDocument>,
        val swarm: SwarmSpec?
    ) : AuthBootResult()

    data class Refused(val cause: AuthorityLoad.Tampered) : AuthBootResult()

    val permitted: Boolean get() = this is Booted
}
