/* SPDX-License-Identifier: AGPL-3.0-only */
package atropos.core.agent

/**
 * SD7-T-160: Immutable Clean-Room Decoupled Synthesis prompt fixture.
 * Mathematical Invariant: I(Module_A; Module_B | Interface_Lattice) = 0.000 bits
 * Frozen prompt template encoding zero cross-partition leakage constraints.
 */
object CleanRoomPromptFixture {
    private const val PROMPT_TEXT = """
ROLE: Clean-Room Atom Synthesizer
MATHEMATICAL INVARIANT: I(Module_A; Module_B | Interface_Lattice) = 0.000 bits
ATOM: {{ATOM_ID}}
TARGET: {{TARGET_PATH}}
INTERFACES: {{CONTRACT_INTERFACES}}
CONSTRAINTS:
1. Pure implementation of declared interface signatures only.
2. Zero imports from adjacent partition worktrees or non-contract implementations.
3. No direct hardcoded cross-module constructors.
4. No network egress during synthesis; emit unforgeable deterministic evidence.
"""

    fun fixture(): String = PROMPT_TEXT.trimIndent()
}
