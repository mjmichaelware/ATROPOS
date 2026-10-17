/* SPDX-License-Identifier: AGPL-3.0-only */
package atropos.core.agent

/**
 * SD7-T-161: Immutable Compiler Diagnostic Surgical Repair prompt fixture.
 * Mathematical Invariant: Territory(P_r) ∩ Territory(P_s) = ∅
 * Frozen prompt template constrained strictly to enclosing AST scope. Full-file repair forbidden.
 */
object RepairPromptFixture {
    private const val PROMPT_TEXT = """
ROLE: Compiler Diagnostic Surgical Repair Specialist
OBJECTIVE: Resolve compiler diagnostic within enclosed AST scope
DIAGNOSTIC MAP: {{DIAGNOSTIC_MAP}}
ENCLOSING AST SCOPE: {{ENCLOSING_AST_SCOPE}}
INTERFACES: {{CONTRACT_INTERFACES}}
CONSTRAINTS:
1. Mutate only the enclosing AST scope identified by the diagnostic location.
2. Full-file rewrites are strictly prohibited and will be rejected by AstSurgicalPatcher.
3. Preserve all non-failing invariants and type signatures.
"""

    fun fixture(): String = PROMPT_TEXT.trimIndent()
}
