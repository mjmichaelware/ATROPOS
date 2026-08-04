/* SPDX-License-Identifier: AGPL-3.0-only */
package atropos.core.verification

/**
 * Outcome of one governed compile run.
 *
 * [exitCode] is null when nothing ran: the policy engine refused the proposal,
 * no executor was bound, or the process failed to start. A null exit code is
 * never a pass — [passed] requires an observed zero exit, so "we could not
 * compile" can never be reported as "it compiles".
 */
data class GovernedCompileGateResult(
    val passed: Boolean,
    val command: List<String>,
    val exitCode: Int?,
    val message: String,
    val proposalId: String? = null,
    val refusalReason: String? = null
) {
    fun commandLine(): String = command.joinToString(" ")

    fun evidenceLine(): String =
        "compile_gate passed=$passed exit=${exitCode ?: "none"} command=${commandLine()} " +
            "proposal=${proposalId ?: "none"} message=${message.replace('\n', ' ').take(240)}"
}
