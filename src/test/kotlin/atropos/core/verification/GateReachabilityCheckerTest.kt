/* SPDX-License-Identifier: AGPL-3.0-only */
package atropos.core.verification

import atropos.core.AtroposRepoRootLocator
import java.io.File
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

/**
 * `SUP.VERIF.BOUNDED-AGENCY-GATE`: no path reaches execution without the gate.
 *
 * The tree currently does not satisfy that, and the test says so rather than
 * being written around it. What it enforces today is the part that can be
 * enforced today: no *new* execution site appears undeclared.
 */
class GateReachabilityCheckerTest {

    private fun sandbox(vararg files: Pair<String, String>): File {
        val root = createTempDir("atropos-gate-reach")
        files.forEach { (path, body) ->
            val file = File(root, path)
            file.parentFile.mkdirs()
            file.writeText(body)
        }
        return root
    }

    @Test
    fun `a new execution site outside the bounded runners is a violation`() {
        val root = sandbox(
            "core/rogue/QuickRunner.kt" to """
                package atropos.core.rogue
                class QuickRunner {
                    fun run() = ProcessBuilder("sh", "-c", "echo hi").start()
                }
            """.trimIndent()
        )

        val report = GateReachabilityChecker(knownDebt = emptySet()).check(root)

        assertFalse(report.passed)
        assertEquals("core/rogue/QuickRunner.kt", report.violations.single().path.substringAfter("${root.name}/"))
        assertTrue(report.violations.single().render().contains("execution.routes_through_bounded_gate"))
    }

    @Test
    fun `the declared bounded runners are permitted`() {
        val root = sandbox(
            "core/policy/BoundedProcessRunner.kt" to "ProcessBuilder(\"git\")",
            "core/worktree/BoundedGitWorktreeCommandRunner.kt" to "ProcessBuilder(\"git\")"
        )

        assertTrue(GateReachabilityChecker(knownDebt = emptySet()).check(root).passed)
    }

    @Test
    fun `a declared debt site is reported but does not fail`() {
        val root = sandbox(
            "core/memory/MemoryBackendProbe.kt" to "ProcessBuilder(\"sqlite3\")"
        )

        val report = GateReachabilityChecker().check(root)

        assertTrue(report.passed, "a recorded site is not a new violation")
        assertFalse(report.predicateHolds, "but the predicate does not hold while it exists")
        assertEquals(1, report.declaredDebt.size)
        assertTrue(report.render().contains("P(raw-prose-execution)=0 does not hold"))
    }

    @Test
    fun `Runtime exec is caught as well as ProcessBuilder`() {
        val root = sandbox(
            "core/rogue/Legacy.kt" to "Runtime.getRuntime().exec(\"ls\")"
        )

        assertFalse(GateReachabilityChecker(knownDebt = emptySet()).check(root).passed)
    }

    @Test
    fun `test sources are not execution sites the engine can reach`() {
        val root = sandbox(
            "src/test/kotlin/atropos/SomeTest.kt" to "ProcessBuilder(\"git\")"
        )

        assertTrue(GateReachabilityChecker(knownDebt = emptySet()).check(root).passed)
    }

    @Test
    fun `the real tree introduces no undeclared execution site`() {
        val main = AtroposRepoRootLocator.resolve().resolve("src/main/kotlin").toFile()

        val report = GateReachabilityChecker().check(main)

        assertTrue(
            report.passed,
            "new unbounded execution site(s) — route them through BoundedProcessRunner, " +
                "or record them deliberately:\n" + report.render()
        )
    }
}
