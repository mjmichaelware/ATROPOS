/* SPDX-License-Identifier: AGPL-3.0-only */
/**
 * B-SUP-006: MDL Patch Preference
 *
 * Implements Minimum Description Length (MDL) principle for patch selection,
 * preferring patches that minimize the total description length.
 */
package atropos.core.supremacy

import java.nio.file.Files
import java.nio.file.Path

object MDLPatchPreference {
    data class Patch(
        val id: String,
        val description: String,
        val diff: String,
        val complexity: Double,
        val fitsEvidence: Boolean
    )

    data class MDLScore(
        val patchId: String,
        val descriptionLength: Double,
        val evidenceLength: Double,
        val totalLength: Double
    ) {
        fun betterThan(other: MDLScore): Boolean = this.totalLength < other.totalLength
    }

    /**
     * Computes the MDL score for a patch.
     * L = L(description) + L(evidence|description)
     */
    fun computeScore(patch: Patch): MDLScore {
        val descLength = patch.description.length.toDouble() + patch.diff.length.toDouble() * 0.1
        val evidenceLength = if (patch.fitsEvidence) 10.0 else 1000.0
        val complexityPenalty = patch.complexity * 10.0
        val total = descLength + evidenceLength + complexityPenalty

        return MDLScore(patch.id, descLength, evidenceLength, total)
    }

    /**
     * Selects the best patch using MDL principle.
     */
    fun selectBestPatch(patches: List<Patch>): Patch? {
        return patches
            .map { it to computeScore(it) }
            .minByOrNull { it.second }
            ?.first
    }

    /**
     * Persists the MDL scores to CAS.
     */
    fun persistScores(casDir: Path, scores: List<MDLScore>): Path {
        Files.createDirectories(casDir)
        val scoresFile = casDir.resolve("mdl-scores.json")
        val json = scores.map { s ->
            """
                {
                    "patchId": "${s.patchId}",
                    "descriptionLength": ${s.descriptionLength},
                    "evidenceLength": ${s.evidenceLength},
                    "totalLength": ${s.totalLength}
                }
            """.trimIndent()
        }.joinToString(",\n", "[\n", "\n]")
        Files.writeString(scoresFile, json)
        return scoresFile
    }

    /**
     * CLI command to show MDL scores.
     */
    fun showScores(scores: List<MDLScore>): String {
        return scores.joinToString("\n") { "Patch ${it.patchId}: total=${"%.2f".format(it.totalLength)}" }
    }
}