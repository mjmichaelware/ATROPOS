/* SPDX-License-Identifier: AGPL-3.0-only */
package atropos.core.factory

/** Immutable acceptance oracle for one factory plan. */
data class FactoryAcceptanceFreeze(
    val document: String,
    val sha256: String
) {
    companion object {
        fun create(
            promptSha256: String,
            researchSha256: String,
            atomIds: List<String>,
            promptSpans: String
        ): FactoryAcceptanceFreeze {
            val document = buildString {
                appendLine("schema=factory-acceptance-freeze-v1")
                appendLine("prompt_sha256=$promptSha256")
                appendLine("research_sha256=$researchSha256")
                appendLine("prompt_spans=$promptSpans")
                appendLine("atom_ids=${atomIds.sorted().joinToString(",").ifBlank { "none" }}")
                appendLine("predicate=all_declared_atoms_terminal_without_failure")
                appendLine("predicate=generated_language_source_and_tests_present")
                appendLine("predicate=verify_sh_exit_zero_and_marker_present")
                appendLine("predicate=deterministic_verifier_passed")
                appendLine("predicate=evidence_manifest_complete_and_hashed")
                appendLine("predicate=auditor_and_director_independent_decisions_present")
            }.trimEnd() + "\n"
            return FactoryAcceptanceFreeze(document, FactoryLineage.sha256(document))
        }
    }
}
