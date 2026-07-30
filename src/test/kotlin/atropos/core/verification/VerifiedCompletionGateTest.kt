/* SPDX-License-Identifier: AGPL-3.0-only */
package atropos.core.verification

import atropos.core.dag.DagNode
import atropos.core.dag.DagNodeAction
import atropos.core.dag.DagStore
import atropos.core.policy.BoundedProcessRunner
import java.nio.charset.StandardCharsets
import java.nio.file.Files
import java.nio.file.Path
import java.time.Instant
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

/**
 * Batch 10 — the completion boundary.
 *
 * Verification is fail-closed: a check that inspected nothing does not get to
 * report safety. Every "nothing to inspect" path asserted here used to pass.
 */
class VerifiedCompletionGateTest {

    private fun repo(): Path {
        val root = Files.createTempDirectory("atropos-completion-")
        ProcessBuilder("git", "init")
            .directory(root.toFile())
            .redirectErrorStream(true)
            .start()
            .waitFor()
        ProcessBuilder("git", "config", "user.email", "atropos@example.invalid")
            .directory(root.toFile())
            .redirectErrorStream(true)
            .start()
            .waitFor()
        ProcessBuilder("git", "config", "user.name", "ATROPOS Test")
            .directory(root.toFile())
            .redirectErrorStream(true)
            .start()
            .waitFor()
        Files.createDirectories(root.resolve("src"))
        Files.writeString(root.resolve("src/A.kt"), "class A\n")
        ProcessBuilder("git", "add", ".")
            .directory(root.toFile())
            .redirectErrorStream(true)
            .start()
            .waitFor()
        ProcessBuilder("git", "commit", "-m", "initial")
            .directory(root.toFile())
            .redirectErrorStream(true)
            .start()
            .waitFor()
        return root
    }

    private fun gate(root: Path) = VerifiedCompletionGate(repoRoot = root, dagStore = DagStore(root))

    private fun node(
        payload: String? = "./gradlew test",
        territory: List<String> = listOf("src"),
        expectedOutputs: List<String> = listOf("src/A.kt"),
        optionalChecks: Set<String> = emptySet(),
        result: String? = "done",
        claimOwner: String? = null
    ) = DagNode(
        id = "node-1",
        label = "completion",
        action = DagNodeAction.RUN_COMMAND,
        actionPayload = payload,
        territory = territory,
        expectedOutputs = expectedOutputs,
        optionalChecks = optionalChecks,
        result = result,
        claimOwner = claimOwner,
        createdAt = Instant.now(),
        updatedAt = Instant.now(),
        metaFile = Path.of("unused")
    )

    private fun gateNamed(root: Path, node: DagNode, name: String): GateResult =
        gate(root).evaluateNode(node).gateResults.single { it.gateName == name }

    // --- D: the construction cycle is gone -------------------------------

    @Test
    fun the_completion_gate_and_the_dag_service_can_both_be_built_with_defaults() {
        val root = repo()
        // Before this batch these two default-constructed each other and this
        // line recursed until the stack ran out.
        val completionGate = VerifiedCompletionGate(repoRoot = root, dagStore = DagStore(root))
        val dagService = atropos.core.dag.DagExecutionService(repoRoot = root)

        assertTrue(completionGate.detectFalseCompletions("no-such-dag").isEmpty())
        assertEquals(
            "DAG not found",
            completionGate.reVerifyNode("no-such-dag", "no-such-node").message
        )
        assertTrue(dagService.readDag("no-such-dag") == null)
    }

    // --- F: fail-closed --------------------------------------------------

    @Test
    fun a_node_with_no_payload_fails_the_focused_test_gate() {
        val root = repo()
        val result = gateNamed(root, node(payload = null), "Focused Tests")

        assertFalse(result.passed, "used to pass as 'no tests required (skipped)'")
        assertTrue(result.detail.contains("nothing was verified"), result.detail)
    }

    @Test
    fun failed_or_bounded_gradle_commands_never_pass_verification_gates() {
        val root = repo()
        val runner = BoundedProcessRunner { _, _, _, _ -> ProcessBuilder("false").start() }
        val result = VerifiedCompletionGate(
            repoRoot = root,
            dagStore = DagStore(root),
            processRunner = runner
        ).evaluateNode(node())

        assertFalse(result.gateResults.single { it.gateName == "Focused Tests" }.passed)
        assertFalse(result.gateResults.single { it.gateName == "Compile Gate" }.passed)
        assertTrue(result.gateResults.single { it.gateName == "Focused Tests" }.detail.contains("exit=1"))
        assertTrue(result.gateResults.single { it.gateName == "Compile Gate" }.detail.contains("exit=1"))
    }

    @Test
    fun a_node_with_no_territory_fails_the_territory_and_secrets_gate() {
        val root = repo()
        val result = gateNamed(root, node(territory = emptyList()), "Territory & Secrets")

        assertFalse(result.passed, "used to pass because territoryOk was unconditionally true")
        assertTrue(result.detail.contains("no territory"), result.detail)
    }

    @Test
    fun a_node_with_no_expected_outputs_fails_that_gate() {
        val root = repo()
        val result = gateNamed(root, node(expectedOutputs = emptyList()), "Expected Outputs")

        assertFalse(result.passed, "used to pass as 'no expected outputs defined'")
        assertTrue(result.detail.contains("nothing was verified"), result.detail)
    }

    @Test
    fun a_node_naming_no_files_fails_the_auditor_gate() {
        val root = repo()
        val result = gateNamed(
            root,
            node(territory = emptyList(), expectedOutputs = emptyList()),
            "Auditor Findings"
        )

        assertFalse(result.passed)
        assertTrue(result.detail.contains("no files"), result.detail)
    }

    @Test
    fun self_host_evidence_bundle_satisfies_acceptance_evidence_gate() {
        val root = repo()
        val goalId = "shg-abc123"
        val evidenceDir = root.resolve(".atropos/self-hosting/evidence/$goalId")
        Files.createDirectories(evidenceDir)
        Files.writeString(evidenceDir.resolve("bundle.md"), "# evidence\n", StandardCharsets.UTF_8)
        Files.writeString(evidenceDir.resolve("bundle.json"), "{\"goalId\":\"$goalId\"}\n", StandardCharsets.UTF_8)

        val result = gateNamed(
            root,
            node().copy(id = "$goalId-source-marker"),
            "Acceptance Evidence"
        )

        assertTrue(result.passed, result.detail)
        assertTrue(result.detail.contains("self-host evidence bundle exists"), result.detail)
    }

    @Test
    fun incomplete_self_host_evidence_bundle_does_not_satisfy_acceptance_gate() {
        val root = repo()
        val goalId = "shg-def456"
        val evidenceDir = root.resolve(".atropos/self-hosting/evidence/$goalId")
        Files.createDirectories(evidenceDir)
        Files.writeString(evidenceDir.resolve("bundle.md"), "# evidence\n", StandardCharsets.UTF_8)

        val result = gateNamed(
            root,
            node().copy(id = "$goalId-source-marker"),
            "Acceptance Evidence"
        )

        assertFalse(result.passed)
        assertTrue(result.detail.contains("no evidence directory or self-host evidence bundle"), result.detail)
    }

    @Test
    fun an_explicit_opt_out_in_the_node_contract_is_honoured() {
        val root = repo()
        val optedOut = gateNamed(
            root,
            node(expectedOutputs = emptyList(), optionalChecks = setOf("Expected Outputs")),
            "Expected Outputs"
        )
        assertTrue(optedOut.passed, "the node contract may declare a check inapplicable")
        assertTrue(optedOut.detail.contains("declared optional"), optedOut.detail)

        // Opting one check out does not opt any other check out.
        val neighbour = gateNamed(
            root,
            node(payload = null, optionalChecks = setOf("Expected Outputs")),
            "Focused Tests"
        )
        assertFalse(neighbour.passed)
    }

    // --- E: Auditor authority --------------------------------------------

    @Test
    fun secret_material_in_an_audited_file_refuses_completion() {
        val root = repo()
        Files.createDirectories(root.resolve("src"))
        Files.writeString(
            root.resolve("src/Leak.kt"),
            "val key = \"sk-live-abcdefghijklmnopqrstuvwxyz0123456789\"\n",
            StandardCharsets.UTF_8
        )

        val result = gateNamed(
            root,
            node(territory = listOf("src"), expectedOutputs = listOf("src/Leak.kt")),
            "Auditor Findings"
        )

        assertFalse(result.passed, "an auditor failure must refuse completion")
        assertTrue(result.detail.contains("auditor blocked"), result.detail)
    }

    @Test
    fun the_auditor_can_only_subtract_never_approve() {
        val root = repo()
        Files.createDirectories(root.resolve("src"))
        Files.writeString(root.resolve("src/A.kt"), "val a = 1\n", StandardCharsets.UTF_8)

        // Clean files, so the auditor gate itself passes...
        val clean = node(payload = null, territory = listOf("src"), expectedOutputs = listOf("src/A.kt"))
        val report = gate(root).evaluateNode(clean)

        assertTrue(
            report.gateResults.single { it.gateName == "Auditor Findings" }.passed,
            "the audit itself is clean"
        )
        // ...yet the node is still not completable, because another gate failed.
        assertFalse(report.canComplete, "a clean audit must not rescue a failing node")
    }

    @Test
    fun auditor_gate_blocks_self_audited_claims() {
        val root = repo()
        Files.createDirectories(root.resolve("src"))
        Files.writeString(root.resolve("src/A.kt"), "val a = 1\n", StandardCharsets.UTF_8)

        val result = gateNamed(
            root,
            node(
                payload = null,
                territory = listOf("src"),
                expectedOutputs = listOf("src/A.kt"),
                claimOwner = "auditor"
            ),
            "Auditor Findings"
        )

        assertFalse(result.passed)
        assertTrue(result.detail.contains("auditor-independence"), result.detail)
    }

    @Test
    fun each_evaluation_gets_a_fresh_auditor() {
        val root = repo()
        Files.createDirectories(root.resolve("src"))
        Files.writeString(
            root.resolve("src/Leak.kt"),
            "val key = \"sk-live-abcdefghijklmnopqrstuvwxyz0123456789\"\n",
            StandardCharsets.UTF_8
        )
        Files.writeString(root.resolve("src/Clean.kt"), "val a = 1\n", StandardCharsets.UTF_8)

        val g = gate(root)
        g.evaluateNode(node(territory = listOf("src"), expectedOutputs = listOf("src/Leak.kt")))

        // The dirty node's findings must not follow the clean one.
        val second = g.evaluateNode(
            node(territory = listOf("src"), expectedOutputs = listOf("src/Clean.kt"))
        ).gateResults.single { it.gateName == "Auditor Findings" }

        assertTrue(second.passed, "findings leaked between evaluations: ${second.detail}")
    }

    // --- store round trip -------------------------------------------------

    @Test
    fun optional_checks_survive_a_store_round_trip_and_default_to_empty() {
        val root = repo()
        val store = DagStore(root)
        val dag = store.createDag(
            "batch10",
            listOf(
                node(optionalChecks = setOf("Expected Outputs", "Focused Tests")).copy(id = "with-opts"),
                node().copy(id = "without-opts")
            )
        )

        val reloaded = DagStore(root).readDag(dag.id)!!
        assertEquals(
            setOf("Expected Outputs", "Focused Tests"),
            reloaded.nodes.single { it.id.endsWith("with-opts") || it.label == "completion" && it.optionalChecks.isNotEmpty() }.optionalChecks
        )
        assertTrue(
            reloaded.nodes.any { it.optionalChecks.isEmpty() },
            "a node that opted out of nothing must load with an empty set"
        )
    }
}
