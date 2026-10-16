/* SPDX-License-Identifier: AGPL-3.0-only */
package atropos.core.factory

import java.lang.UnsupportedOperationException

/**
 * LanguageScaffold has been eradicated.
 *
 * Invariant §1 Inv_AuthMono: All code synthesis originates from atomic LLM
 * proposals under Wave-0 lattice, not hard-coded rank-0 strings.
 *
 * The 478 lines of static string maps for 28 languages have been removed.
 * Any legacy access throws UnsupportedOperationException with a directive
 * to use Wave-0 ContractLatticeSynthesizer.
 */
object LanguageScaffoldEradicated {
    fun reject(): Nothing = throw UnsupportedOperationException(
        "Static scaffolding extirpated. " +
        "Use Wave-0 ContractLatticeSynthesizer for all code synthesis. " +
        "LanguageScaffold contained 478 lines of template strings; zero remain."
    )

    @Suppress("UNUSED_PARAMETER")
    operator fun invoke(vararg args: Any): Nothing = reject()

    /** Sentinel for static analysis — ArchitectureComplianceChecker flags any reference. */
    const val ERADICATED: Boolean = true
}