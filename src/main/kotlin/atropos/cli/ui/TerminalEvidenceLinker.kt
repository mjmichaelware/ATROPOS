/* SPDX-License-Identifier: AGPL-3.0-only */
package atropos.cli.ui

/**
 * HOE-B03: Terminal first-class + evidence linkage.
 * Terminals are rendered as objects with attached evidence hashes.
 * Every result/output links back to the evidence that produced it.
 */
data class TerminalEvidenceLink(
    val outputHash: String,       // SHA-256 of output text
    val evidenceId: String,       // Reference to evidence store
    val lineRange: IntRange,      // Lines in output that correspond to evidence
    val timestamp: Long = System.currentTimeMillis(),
    val source: String = "cli"    // cli/web/android
)

class TerminalEvidenceLinker {
    private val links = mutableMapOf<String, TerminalEvidenceLink>()
    private val evidenceMorph = EvidenceMorph()

    fun linkOutput(output: String, evidenceId: String, lineStart: Int = 0): TerminalEvidenceLink {
        val hash = hashOutput(output)
        val link = TerminalEvidenceLink(
            outputHash = hash,
            evidenceId = evidenceId,
            lineRange = lineStart..(lineStart + output.lines().size - 1)
        )
        links[hash] = link
        return link
    }

    fun getLink(outputHash: String): TerminalEvidenceLink? = links[outputHash]

    fun renderWithEvidence(output: String, link: TerminalEvidenceLink): String {
        val view = evidenceMorph.morph(
            summary = output,
            evidence = "evidence: ${link.evidenceId}  ${link.outputHash.take(8)}",
            expanded = true,
            width = Int.MAX_VALUE
        )
        return view.text
    }

    private fun hashOutput(output: String): String {
        val digest = java.security.MessageDigest.getInstance("SHA-256")
        val hash = digest.digest(output.toByteArray())
        return hash.joinToString("") { "%02x".format(it) }
    }
}
