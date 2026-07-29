/* SPDX-License-Identifier: AGPL-3.0-only */
package atropos.core.agent

import atropos.core.dag.DagDefinition
import atropos.core.dag.DagNode
import atropos.core.dag.DagNodeAction
import atropos.core.verification.GovernedCompileGateResult
import java.nio.file.Files
import java.nio.file.Path
import java.time.Instant
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

/**
 * The Phase 11 acceptance chain, asserted predicate by predicate: a run only
 * reports VERIFIED when the source really moved, `git status` really shows it,
 * and the compile gate really exited zero.
 */
class SelfHostRunProofBuilderTest {

    private val markerPath = "src/main/kotlin/atropos/core/agent/SelfHostCradleRuntimeState.kt"

    @Test
    fun mutated_compiled_and_git_visible_sources_produce_a_verified_proof() {
        val root = newRepo()
        writeMarker(root)

        val proof = SelfHostRunProofBuilder(root).build(
            goalId = "shg-1",
            dag = dag(listOf(markerPath)),
            compileGate = compilePassed()
        )

        assertEquals(SelfHostRunVerdict.VERIFIED, proof.verdict, proof.evidenceLine())
        assertTrue(proof.unmetPredicates.isEmpty(), proof.unmetPredicates.toString())
        val mutation = proof.mutations.single()
        assertTrue(mutation.present)
        assertEquals(64, mutation.sha256?.length)
        // Untracked in a fresh repo: the operator sees the same code git prints.
        assertEquals("??", mutation.gitStatusCode)
        assertTrue(proof.gitStatusLines.any { it.contains(markerPath) }, proof.gitStatusLines.toString())
    }

    @Test
    fun a_missing_expected_output_leaves_the_mutation_predicate_unmet() {
        val root = newRepo()

        val proof = SelfHostRunProofBuilder(root).build(
            goalId = "shg-2",
            dag = dag(listOf(markerPath)),
            compileGate = compilePassed()
        )

        assertEquals(SelfHostRunVerdict.PARTIAL, proof.verdict)
        assertTrue(proof.unmetPredicates.contains(SelfHostRunPredicate.SOURCE_MUTATED))
        assertTrue(proof.unmetPredicates.contains(SelfHostRunPredicate.GIT_STATUS_VISIBLE))
        val mutation = proof.mutations.single()
        assertFalse(mutation.present)
        assertEquals(null, mutation.sha256)
    }

    @Test
    fun a_run_that_declared_no_outputs_does_not_pass_by_being_empty() {
        val root = newRepo()

        val proof = SelfHostRunProofBuilder(root).build(
            goalId = "shg-3",
            dag = dag(emptyList()),
            compileGate = compilePassed()
        )

        assertEquals(SelfHostRunVerdict.PARTIAL, proof.verdict)
        assertTrue(proof.unmetPredicates.contains(SelfHostRunPredicate.SOURCE_MUTATED))
        assertTrue(proof.mutations.isEmpty())
    }

    @Test
    fun a_missing_compile_gate_can_never_reach_verified() {
        val root = newRepo()
        writeMarker(root)

        val proof = SelfHostRunProofBuilder(root).build(
            goalId = "shg-4",
            dag = dag(listOf(markerPath)),
            compileGate = null
        )

        assertEquals(SelfHostRunVerdict.PARTIAL, proof.verdict)
        assertTrue(proof.unmetPredicates.contains(SelfHostRunPredicate.COMPILE_GATE_PASSED))
        assertTrue(proof.satisfiedPredicates.contains(SelfHostRunPredicate.SOURCE_MUTATED))
    }

    @Test
    fun a_failed_compile_gate_leaves_the_compile_predicate_unmet() {
        val root = newRepo()
        writeMarker(root)

        val proof = SelfHostRunProofBuilder(root).build(
            goalId = "shg-5",
            dag = dag(listOf(markerPath)),
            compileGate = GovernedCompileGateResult(
                passed = false,
                command = listOf("./gradlew", "compileKotlin"),
                exitCode = 1,
                message = "compile failed: e: Unresolved reference"
            )
        )

        assertEquals(SelfHostRunVerdict.PARTIAL, proof.verdict)
        assertTrue(proof.unmetPredicates.contains(SelfHostRunPredicate.COMPILE_GATE_PASSED))
        assertTrue(proof.evidenceLine().contains("compile=1"), proof.evidenceLine())
    }

    @Test
    fun a_verify_nodes_expected_precondition_is_not_counted_as_a_mutation() {
        val root = newRepo()
        // A committed file the cradle's identity probe inspects. Nothing mutated
        // it, so git is silent about it — and that must not read as a failed run.
        val probePath = "src/main/kotlin/atropos/Main.kt"
        val probeFile = root.resolve(probePath)
        Files.createDirectories(probeFile.parent)
        Files.writeString(probeFile, "package atropos\nfun main() {}\n")
        git(root, "add", ".")
        git(root, "commit", "-m", "baseline")
        writeMarker(root)

        val dag = DagDefinition(
            id = "dag-2",
            label = "self-host bootstrap",
            nodes = listOf(
                node("node-probe", DagNodeAction.VERIFY, listOf(probePath)),
                node("node-marker", DagNodeAction.EDIT_FILE, listOf(markerPath))
            ),
            createdAt = Instant.parse("2026-07-29T00:00:00Z"),
            updatedAt = Instant.parse("2026-07-29T00:00:00Z"),
            metaFile = Path.of("unused")
        )

        val proof = SelfHostRunProofBuilder(root).build("shg-7", dag, compilePassed())

        assertEquals(listOf(markerPath), proof.mutations.map { it.path })
        assertEquals(SelfHostRunVerdict.VERIFIED, proof.verdict, proof.evidenceLine())
    }

    @Test
    fun evidence_bundle_paths_travel_with_the_proof() {
        val root = newRepo()
        writeMarker(root)

        val proof = SelfHostRunProofBuilder(root).build(
            goalId = "shg-6",
            dag = dag(listOf(markerPath)),
            compileGate = compilePassed(),
            evidenceMarkdownPath = "/tmp/bundle.md",
            evidenceJsonPath = "/tmp/bundle.json"
        )

        assertEquals("/tmp/bundle.md", proof.evidenceMarkdownPath)
        assertEquals("/tmp/bundle.json", proof.evidenceJsonPath)
    }

    private fun compilePassed() = GovernedCompileGateResult(
        passed = true,
        command = listOf("./gradlew", "compileKotlin"),
        exitCode = 0,
        message = "compilation succeeded"
    )

    private fun dag(expectedOutputs: List<String>) = DagDefinition(
        id = "dag-1",
        label = "self-host bootstrap",
        nodes = listOf(node("node-marker", DagNodeAction.EDIT_FILE, expectedOutputs)),
        createdAt = Instant.parse("2026-07-29T00:00:00Z"),
        updatedAt = Instant.parse("2026-07-29T00:00:00Z"),
        metaFile = Path.of("unused")
    )

    private fun node(id: String, action: DagNodeAction, expectedOutputs: List<String>) = DagNode(
        id = id,
        label = id,
        action = action,
        territory = listOf("src/main/kotlin/atropos/core/agent"),
        expectedOutputs = expectedOutputs,
        createdAt = Instant.parse("2026-07-29T00:00:00Z"),
        updatedAt = Instant.parse("2026-07-29T00:00:00Z"),
        metaFile = Path.of("unused")
    )

    private fun writeMarker(root: Path) {
        val file = root.resolve(markerPath)
        Files.createDirectories(file.parent)
        Files.writeString(
            file,
            "package atropos.core.agent\n\nobject SelfHostCradleRuntimeState {\n" +
                "    const val LAST_SELF_HOST_GOAL: String = \"shg-1\"\n}\n"
        )
    }

    private fun newRepo(): Path {
        val root = Files.createTempDirectory("atropos-run-proof-")
        git(root, "init")
        git(root, "config", "user.email", "atropos@example.invalid")
        git(root, "config", "user.name", "ATROPOS Test")
        return root
    }

    private fun git(root: Path, vararg args: String) {
        ProcessBuilder(listOf("git", *args))
            .directory(root.toFile())
            .redirectErrorStream(true)
            .start()
            .waitFor()
    }
}
