/* SPDX-License-Identifier: AGPL-3.0-only */
package atropos.core.auth

/**
 * The boot-sequence face of [AuthBootstrap].
 *
 * `SUP.AUTH.AGENTS-MD`: "Wire loader into CommandRouter boot sequence before
 * any agent dispatch."
 *
 * A tampered authority document does not stop the process. It stops *dispatch*.
 * The difference is the whole usefulness of the feature: an operator whose
 * `AGENTS.md` was modified needs a running CLI to look at what changed and
 * accept or revert it, and a system that refuses to start leaves them with no
 * way to do either except editing the fingerprint table by hand — which is
 * indistinguishable from disabling the gate.
 *
 * So the gate reports loudly and holds [dispatchPermitted] false. What consults
 * that flag is the dispatch path, not `main`.
 */
class AuthorityBootGate(private val bootstrap: AuthBootstrap = AuthBootstrap()) {

    fun evaluate(): AuthorityBootAnnouncement = when (val boot = bootstrap.boot()) {
        is AuthBootResult.Booted -> AuthorityBootAnnouncement(
            dispatchPermitted = true,
            notice = boot.documents
                .takeIf { it.isNotEmpty() }
                ?.let { docs ->
                    "authority attested: " + docs.joinToString(", ") {
                        "${it.path}@${it.sha256.take(8)}"
                    }
                },
            error = null,
            layers = boot.layers,
            swarm = boot.swarm
        )

        is AuthBootResult.Refused -> AuthorityBootAnnouncement(
            dispatchPermitted = false,
            notice = null,
            error = "${boot.cause.reason}\n${boot.cause.remedy}\n" +
                "Agent dispatch is held until this is resolved. Reads are unaffected.",
            layers = emptyList(),
            swarm = null
        )
    }
}

/**
 * @param notice shown on a clean boot, or null when the repository declares no
 *   authority documents. Announcing "no documents found" on every start would
 *   train the operator to ignore the line that matters.
 */
data class AuthorityBootAnnouncement(
    val dispatchPermitted: Boolean,
    val notice: String?,
    val error: String?,
    val layers: List<AuthorityLayer>,
    val swarm: SwarmSpec?
)
