/* SPDX-License-Identifier: AGPL-3.0-only */
package atropos.core.verification

import java.nio.file.Files
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNotNull
import kotlin.test.assertNull
import kotlin.test.assertTrue

/**
 * The compile gate is the boundary between "source was mutated" and "a jar may
 * be promoted". Everything asserted here is a way that boundary must refuse.
 */
class GovernedCompileGateTest {

    @Test
    fun zero_exit_passes_and_reports_the_observed_exit_code() {
        val root = Files.createTempDirectory("atropos-compile-gate-pass-")
        val gate = GovernedCompileGate(
            repoRoot = root,
            processRunner = { _, _ -> GovernedCompileGate.CompileRun(0, "> Task :compileKotlin\n") }
        )

        val result = gate.verify("goal-1")

        assertTrue(result.passed, result.message)
        assertEquals(0, result.exitCode)
        assertEquals("./gradlew compileKotlin", result.commandLine())
        assertNotNull(result.proposalId)
        assertTrue(result.evidenceLine().contains("compile_gate passed=true exit=0"), result.evidenceLine())
    }

    @Test
    fun nonzero_exit_fails_and_keeps_the_exit_code_in_evidence() {
        val root = Files.createTempDirectory("atropos-compile-gate-fail-")
        val gate = GovernedCompileGate(
            repoRoot = root,
            processRunner = { _, _ -> GovernedCompileGate.CompileRun(1, "e: Unresolved reference: nope") }
        )

        val result = gate.verify("goal-1")

        assertFalse(result.passed)
        assertEquals(1, result.exitCode)
        assertTrue(result.message.contains("compile failed"), result.message)
        assertTrue(result.evidenceLine().contains("exit=1"), result.evidenceLine())
    }

    @Test
    fun a_compile_that_never_started_is_not_a_pass() {
        val root = Files.createTempDirectory("atropos-compile-gate-start-")
        val gate = GovernedCompileGate(
            repoRoot = root,
            processRunner = { _, _ -> throw java.io.IOException("no such file: ./gradlew") }
        )

        val result = gate.verify("goal-1")

        assertFalse(result.passed)
        // Null, not 0: nothing was observed, so nothing is claimed.
        assertNull(result.exitCode)
        assertTrue(result.message.contains("failed to start"), result.message)
    }

    @Test
    fun a_command_the_policy_engine_refuses_never_runs() {
        val root = Files.createTempDirectory("atropos-compile-gate-refused-")
        var invoked = false
        val gate = GovernedCompileGate(
            repoRoot = root,
            // Not one of the launchers BUILD_TEST permits.
            command = listOf("make", "compile"),
            processRunner = { _, _ ->
                invoked = true
                GovernedCompileGate.CompileRun(0, "")
            }
        )

        val result = gate.verify("goal-1")

        assertFalse(invoked, "a policy-refused compile must not reach the process")
        assertFalse(result.passed)
        assertNull(result.exitCode)
        assertNotNull(result.refusalReason)
        assertTrue(result.message.contains("refused"), result.message)
    }

    @Test
    fun failure_detail_is_bounded_and_single_line_in_evidence() {
        val root = Files.createTempDirectory("atropos-compile-gate-bounded-")
        val noisy = (1..500).joinToString("\n") { "e: line $it unresolved reference" }
        val gate = GovernedCompileGate(
            repoRoot = root,
            processRunner = { _, _ -> GovernedCompileGate.CompileRun(2, noisy) }
        )

        val evidence = gate.verify("goal-1").evidenceLine()

        // Evidence goes into bundles and logs: it stays one bounded line.
        assertFalse(evidence.contains("\n"), "evidence line must not contain newlines")
        assertTrue(evidence.length < 400, "evidence line was ${evidence.length} chars")
        assertTrue(evidence.contains("exit=2"), evidence)
    }
}
