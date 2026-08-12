/* SPDX-License-Identifier: AGPL-3.0-only */
package atropos.core.auth

/**
 * Loads `Agents.md` as an attested, immutable authority layer.
 *
 * `SUP.AUTH.AGENTS-MD`: "P(authority-drift)=0 by cryptographic attestation at
 * load; competitors treat .md as mutable prose → ATROPOS treats as code.
 * Superiority: zero silent instruction injection."
 *
 * Source Doc 5 names the failure this closes: large-context providers "lose it
 * out of their context once they get focus — they lose everything in the
 * peripheral sight". The answer is not a longer prompt. It is that the
 * governing document is hashed, ranked, and placed above everything else, so
 * drift is a detectable state rather than something noticed later by reading
 * output that looks wrong.
 *
 * The loader produces a layer, never a decision. What the keys *mean* belongs
 * to [AuthCascadeResolver], which already owns precedence and the
 * non-overridable core set; duplicating any of that here would create a second
 * place where a core key could be weakened.
 */
class AgentsMdLoader(
    private val attestor: AuthorityAttestor,
    /** Candidate filenames in order. The first that exists is the authority. */
    private val candidates: List<String> = DEFAULT_CANDIDATES
) {
    /**
     * @return [AuthorityLoad.Absent] when no candidate exists. Absence is not a
     *   failure: a repository with no `Agents.md` has simply declared nothing,
     *   and refusing to boot over it would make the file mandatory rather than
     *   authoritative.
     */
    fun load(): AuthorityLoad {
        for (candidate in candidates) {
            when (val result = attestor.attest(candidate, RANK)) {
                is AttestationResult.Missing -> continue

                is AttestationResult.Mismatch -> return AuthorityLoad.Tampered(
                    path = candidate,
                    reason = result.reason(),
                    remedy = "Review the change, then accept it with 'atropos auth accept $candidate'."
                )

                is AttestationResult.Attested -> {
                    val text = attestor.readText(candidate)
                        ?: return AuthorityLoad.Tampered(
                            path = candidate,
                            reason = "$candidate attested but could not be read back.",
                            remedy = "Check file permissions on $candidate."
                        )
                    return AuthorityLoad.Loaded(
                        layer = AuthorityLayer(
                            name = candidate,
                            rank = RANK,
                            values = AuthorityMarkdownParser.parse(text)
                        ),
                        document = result.document
                    )
                }
            }
        }
        return AuthorityLoad.Absent(candidates.first())
    }

    companion object {
        /**
         * Strongest ordinary layer.
         *
         * Rank 0 is reserved for the architecture itself, which is not a file
         * and therefore cannot be edited into saying something else. `Agents.md`
         * is the strongest thing an operator can write down; it is still not
         * able to weaken a core key, which [AuthCascadeResolver] enforces.
         */
        const val RANK: Int = 1

        /**
         * Source Doc 5 asks for the "GitHub-like" files to point at each other
         * cascadingly, naming `Agents.md` and the tools that ship their own.
         * All of them are read as the same layer because they occupy the same
         * position — the first one present is the one the repository meant.
         */
        val DEFAULT_CANDIDATES: List<String> = listOf(
            "AGENTS.md",
            "Agents.md",
            "agents.md",
            "CLAUDE.md",
            ".cursorrules"
        )
    }
}

/** What a loader found. */
sealed class AuthorityLoad {
    data class Loaded(val layer: AuthorityLayer, val document: AuthorityDocument) : AuthorityLoad()

    /** No such document. The repository declared nothing at this layer. */
    data class Absent(val expectedPath: String) : AuthorityLoad()

    /** The bytes changed since they were recorded. Fail-closed. */
    data class Tampered(val path: String, val reason: String, val remedy: String) : AuthorityLoad()

    val trusted: Boolean get() = this !is Tampered
}
