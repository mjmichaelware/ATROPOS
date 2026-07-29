/* SPDX-License-Identifier: AGPL-3.0-only */
package atropos.cli.commands

import atropos.core.agent.SelfHostMutationProof
import atropos.core.agent.SelfHostRunPredicate
import atropos.core.agent.SelfHostRunProof
import atropos.core.verification.GovernedCompileGateResult
import kotlin.test.Test
import kotlin.test.assertFalse
import kotlin.test.assertTrue

/**
 * What the operator reads after typing a self-host prompt into the JAR. An
 * unmet predicate must be legible as unmet; nothing here may round up.
 */
class SelfHostRunProofRendererTest {

    @Test
    fun a_verified_proof_shows_the_whole_chain() {
        val text = SelfHostRunProofRenderer().render(
            proof(
                mutations = listOf(
                    SelfHostMutationProof(
                        path = "src/main/kotlin/atropos/core/agent/SelfHostCradleRuntimeState.kt",
                        present = true,
                        sha256 = "a".repeat(64),
                        gitStatusCode = "??"
                    )
                ),
                compileGate = GovernedCompileGateResult(true, listOf("./gradlew", "compileKotlin"), 0, "compilation succeeded"),
                gitStatusLines = listOf("?? src/main/kotlin/atropos/core/agent/SelfHostCradleRuntimeState.kt"),
                satisfied = SelfHostRunPredicate.entries.toList(),
                unmet = emptyList()
            )
        )

        assertTrue(text.contains("verdict: VERIFIED"), text)
        assertTrue(text.contains("compile gate: passed=true exit=0 command=./gradlew compileKotlin"), text)
        assertTrue(text.contains("SelfHostCradleRuntimeState.kt present=true"), text)
        assertTrue(text.contains("git status:"), text)
        assertFalse(text.contains("[UNMET]"), text)
    }

    @Test
    fun an_unmet_predicate_is_printed_as_unmet() {
        val text = SelfHostRunProofRenderer().render(
            proof(
                mutations = emptyList(),
                compileGate = GovernedCompileGateResult(false, listOf("./gradlew", "compileKotlin"), 1, "compile failed: e: Unresolved reference"),
                gitStatusLines = emptyList(),
                satisfied = listOf(SelfHostRunPredicate.NL_ROUTED),
                unmet = listOf(
                    SelfHostRunPredicate.SOURCE_MUTATED,
                    SelfHostRunPredicate.GIT_STATUS_VISIBLE,
                    SelfHostRunPredicate.COMPILE_GATE_PASSED
                )
            )
        )

        assertTrue(text.contains("verdict: PARTIAL (predicates still unmet)"), text)
        assertTrue(text.contains("[UNMET]   source_mutated"), text)
        assertTrue(text.contains("[UNMET]   compile_gate_passed"), text)
        assertTrue(text.contains("compile gate detail: compile failed"), text)
        assertTrue(text.contains("(none declared)"), text)
        assertTrue(text.contains("(clean)"), text)
    }

    @Test
    fun a_compile_gate_that_never_ran_is_reported_as_not_run() {
        val text = SelfHostRunProofRenderer().render(
            proof(
                mutations = emptyList(),
                compileGate = null,
                gitStatusLines = emptyList(),
                satisfied = listOf(SelfHostRunPredicate.NL_ROUTED),
                unmet = listOf(SelfHostRunPredicate.COMPILE_GATE_PASSED)
            )
        )

        assertTrue(text.contains("compile gate: not run"), text)
    }

    @Test
    fun long_git_status_output_is_bounded_with_a_remainder_count() {
        val lines = (1..20).map { "?? src/main/kotlin/atropos/generated/File$it.kt" }
        val text = SelfHostRunProofRenderer(maxStatusLines = 5).render(
            proof(
                mutations = emptyList(),
                compileGate = null,
                gitStatusLines = lines,
                satisfied = listOf(SelfHostRunPredicate.NL_ROUTED),
                unmet = listOf(SelfHostRunPredicate.SOURCE_MUTATED)
            )
        )

        assertTrue(text.contains("File5.kt"), text)
        assertFalse(text.contains("File6.kt"), text)
        assertTrue(text.contains("... 15 more"), text)
    }

    private fun proof(
        mutations: List<SelfHostMutationProof>,
        compileGate: GovernedCompileGateResult?,
        gitStatusLines: List<String>,
        satisfied: List<SelfHostRunPredicate>,
        unmet: List<SelfHostRunPredicate>
    ) = SelfHostRunProof(
        goalId = "shg-1",
        mutations = mutations,
        compileGate = compileGate,
        gitStatusLines = gitStatusLines,
        satisfiedPredicates = satisfied,
        unmetPredicates = unmet
    )
}
