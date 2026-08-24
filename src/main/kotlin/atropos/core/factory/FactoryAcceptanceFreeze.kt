/* SPDX-License-Identifier: AGPL-3.0-only */
package atropos.core.factory

/** Immutable acceptance oracle for one factory plan. */
data class FactoryAcceptanceFreeze(
    val document: String,
    val sha256: String
) {
    data class RepairEvidence(
        val freezeSha256: String,
        val command: String,
        val exitCode: Int,
        val stderr: String,
        val predicateResults: Map<String, Boolean>
    )

    fun requireRepairEvidence(evidence: RepairEvidence): String {
        require(evidence.freezeSha256 == sha256) { "repair changed the acceptance freeze" }
        require(evidence.command.isNotBlank()) { "repair acceptance command is missing" }
        require(evidence.exitCode == 0) { "repair acceptance command failed: exit=${evidence.exitCode}" }
        require(evidence.stderr.isNotBlank()) { "repair must record stderr evidence" }
        require(evidence.predicateResults.isNotEmpty() &&
            evidence.predicateResults.keys.all { it.isNotBlank() } &&
            evidence.predicateResults.values.all { it }) {
            "repair acceptance predicates did not all pass"
        }
        return buildString {
            appendLine("acceptance_freeze_sha256=$sha256")
            appendLine("command=${evidence.command.replace(Regex("\\s+"), " ").trim()}")
            appendLine("exit_code=${evidence.exitCode}")
            appendLine("stderr_sha256=${FactoryLineage.sha256(evidence.stderr)}")
            evidence.predicateResults.toSortedMap().forEach { (name, passed) -> appendLine("predicate=$name passed=$passed") }
        }.trimEnd()
    }

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
