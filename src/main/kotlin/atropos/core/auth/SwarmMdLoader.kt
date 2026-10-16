/* SPDX-License-Identifier: AGPL-3.0-only */
package atropos.core.auth

/**
 * Loads `Swarm.md` as an attested topology declaration.
 *
 * Parallel to [AgentsMdLoader] by design — same attestation contract, same
 * fail-closed behaviour, one rank lower. Source Doc 5 asks for `Swarm.md` "tied
 * to the agents.md in a way that causes any CLI or agent to treat them as the
 * same"; treating them the same means they are loaded the same way, not that
 * they are merged into one file.
 *
 * `Swarm.md` sits *below* `Agents.md` in the cascade because a topology
 * declaration must not be able to restate a general instruction. A file whose
 * job is "how many agents and where they may write" should not also be able to
 * decide, say, the secret policy — and under [AuthCascadeResolver] it cannot,
 * because the stronger layer wins and core keys cannot be overridden at all.
 */
class SwarmMdLoader(
    private val attestor: AuthorityAttestor,
    private val candidates: List<String> = DEFAULT_CANDIDATES
) {
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

    /**
     * The topology the loaded document declares.
     *
     * Separate from [load] because the layer and the topology answer different
     * questions: the layer feeds the cascade, the topology bounds spawning.
     * Returning both from one call would force every caller that only wanted
     * one of them to know about the other.
     */
    fun spec(load: AuthorityLoad): SwarmSpec? {
        if (load !is AuthorityLoad.Loaded) return null
        val text = attestor.readText(load.layer.name) ?: return null
        val values = load.layer.values

        return SwarmSpec(
            nodes = parseNodes(text),
            // Absent means "no delegation", not "unlimited". A missing depth
            // that meant unlimited would make forgetting the line the most
            // permissive thing an operator could do.
            maxDepth = values["maxDepth"]?.toIntOrNull() ?: 0,
            escalationPath = values["escalationPath"]
                ?.split(',')
                ?.map { it.trim() }
                ?.filter { it.isNotEmpty() }
                .orEmpty(),
            coordinationCostBound = values["coordinationCostBound"]?.toLongOrNull()
        )
    }

    /**
     * Reads the `Nodes` section, one node per line.
     *
     * ```
     * ## Nodes
     * - reviewer | auditor | src/main/kotlin/atropos/core/verifier
     * ```
     *
     * Pipe-separated rather than nested markdown because the grammar has to be
     * unambiguous to a byte-level parser. A nested structure would need a real
     * markdown reader, and a governing document that can only be read by a
     * complicated parser is a document whose meaning depends on that parser's
     * bugs.
     */
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
        /** One below [AgentsMdLoader.RANK]: topology never outranks instruction. */
        const val RANK: Int = 2

        val DEFAULT_CANDIDATES: List<String> = listOf("SWARM.md", "Swarm.md", "swarm.md")
    }
}
