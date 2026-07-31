package atropos.cli

import atropos.cli.config.ConfigurationManager
import atropos.cli.session.QuotaSessionTracker
import atropos.cli.ui.AnsiTerminalEngine
import atropos.core.AIProvider
import atropos.core.ApiKeys
import atropos.core.AtroposConfig
import atropos.core.AtroposRepoRootLocator
import atropos.core.LakehouseConfig
import atropos.core.RuntimeConfig
import atropos.core.agent.GoalRunStore
import atropos.core.agent.SelfHostFileHasher
import atropos.core.agent.SelfHostPromotionRequest
import atropos.core.agent.SelfHostPromotionService
import atropos.core.dag.DagExecutionService
import atropos.core.verification.VerifiedCompletionGate
import java.io.ByteArrayOutputStream
import java.io.PrintStream
import java.nio.file.Files
import java.nio.file.Path
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class SelfHostInsideOutSandboxProofTest {
    @Test
    fun natural_language_prompt_builds_atropos_itself_in_sandbox_and_records_evidence() {
        val operatorRoot = AtroposRepoRootLocator.resolve()
        val sandboxRoot = Files.createTempDirectory("atropos-inside-out-proof-")
        val prompt = "ATROPOS, build yourself from the inside out and run self-host Phase 11"
        initializeSandboxAtroposRepo(sandboxRoot)
        val installedJar = sandboxRoot.resolve("installed/atropos.jar")
        Files.createDirectories(installedJar.parent)
        Files.writeString(installedJar, "prior installed jar\n")

        val oldUserDir = System.getProperty("user.dir")
        val oldInstalledJar = System.getProperty("atropos.installed.jar")
        val out = ByteArrayOutputStream()
        var providerCalls = 0
        try {
            System.setProperty("user.dir", sandboxRoot.toString())
            System.setProperty("atropos.installed.jar", installedJar.toString())
            val router = CommandRouter(
                config = AtroposConfig(
                    ApiKeys("", "", "", ""),
                    LakehouseConfig(sandboxRoot.resolve("lakehouse").toString(), sandboxRoot.resolve("lakehouse/vector_storage.db").toString()),
                    RuntimeConfig("fake", 0.2)
                ),
                uiEngine = AnsiTerminalEngine(
                    capabilities = ConfigurationManager(),
                    out = PrintStream(out),
                    errors = PrintStream(ByteArrayOutputStream())
                ),
                sessionTracker = QuotaSessionTracker(),
                providerResolver = {
                    object : AIProvider {
                        override val name: String = "fake"
                        override fun complete(prompt: String, context: String): String {
                            providerCalls += 1
                            return "provider chat must not handle self-host proof"
                        }
                    }
                }
            )

            val outcome = router.handleInput(prompt)

            assertEquals(RouterOutcome.CONTINUE, outcome)
            assertEquals(0, providerCalls)
            val rendered = out.toString()
            assertTrue(rendered.contains("SELF-HOST RUN"), rendered)
            assertTrue(rendered.contains("self-host run promoted verified jar"), rendered)
            assertTrue(rendered.contains("git_status_short") && rendered.contains("exit=0"), rendered)
        } finally {
            restoreProperty("user.dir", oldUserDir)
            restoreProperty("atropos.installed.jar", oldInstalledJar)
        }

        val store = GoalRunStore(sandboxRoot)
        val record = store.listRuns(20).firstOrNull { it.provider == "self-host" }
            ?: error("self-host goal was not persisted")
        val marker = sandboxRoot.resolve("src/main/kotlin/atropos/core/agent/SelfHostCradleRuntimeState.kt")
        val markerTest = sandboxRoot.resolve("src/test/kotlin/atropos/core/agent/SelfHostCradleRuntimeStateTest.kt")
        assertTrue(Files.isRegularFile(marker), "marker file missing")
        assertTrue(Files.size(marker) > 0L, "marker file is empty")
        assertTrue(Files.readString(marker).contains("LAST_SELF_HOST_GOAL: String = \"${record.id}\""))
        assertTrue(Files.isRegularFile(markerTest), "focused test node file missing")
        assertTrue(Files.size(markerTest) > 0L, "focused test file is empty")

        val mutationStatus = git(sandboxRoot, "status", "--short", "--", "src/main/kotlin/atropos/core/agent/SelfHostCradleRuntimeState.kt", "src/test/kotlin/atropos/core/agent/SelfHostCradleRuntimeStateTest.kt")
        assertTrue(mutationStatus.contains("SelfHostCradleRuntimeState.kt"), mutationStatus)
        assertTrue(mutationStatus.contains("SelfHostCradleRuntimeStateTest.kt"), mutationStatus)

        val safetyIndex = record.evidence.indexOfFirst { it.startsWith("self_host_safety passed=true") }
        val directorIndex = record.evidence.indexOfFirst { it.startsWith("director_pre_promote allowed=true") }
        val gateIndex = record.evidence.indexOfFirst { it.startsWith("promotion_gate") && it.contains("canComplete=true") }
        val swapIndex = record.evidence.indexOfFirst { it.startsWith("jar_swap promoted=true") }
        assertTrue(safetyIndex >= 0, record.evidence.joinToString("\n"))
        assertTrue(directorIndex > safetyIndex, record.evidence.joinToString("\n"))
        assertTrue(gateIndex > directorIndex, record.evidence.joinToString("\n"))
        assertTrue(swapIndex > gateIndex, record.evidence.joinToString("\n"))
        assertTrue(record.evidence.any { it.startsWith("context_preflight_verified") })
        assertTrue(record.evidence.any { it.startsWith("context_attestation system=ATROPOS") })
        assertEquals("sandbox candidate jar\n", Files.readString(installedJar))

        val backup = record.evidence.first { it.startsWith("jar_swap promoted=true") }
            .substringAfter(" backup=")
            .substringBefore(" ")
            .takeIf { it != "none" }
            ?: error("jar swap did not record a backup")
        val backupPath = installedJar.parent.resolve(backup)
        assertEquals("prior installed jar\n", Files.readString(backupPath))

        val evidenceDir = sandboxRoot.resolve(".atropos/self-hosting/evidence/${record.id}")
        val markdown = evidenceDir.resolve("bundle.md")
        val json = evidenceDir.resolve("bundle.json")
        assertTrue(Files.isRegularFile(markdown) && Files.size(markdown) > 0L)
        assertTrue(Files.isRegularFile(json) && Files.size(json) > 0L)
        val hasher = SelfHostFileHasher()
        val markerHash = hasher.sha256(marker) ?: error("missing marker hash")
        val markdownHash = hasher.sha256(markdown) ?: error("missing markdown hash")
        val jsonHash = hasher.sha256(json) ?: error("missing json hash")
        val jsonText = Files.readString(json)
        assertTrue(jsonText.contains("\"redacted\": true"), jsonText)
        assertTrue(jsonText.contains("\"hashAlgorithm\": \"SHA-256\""), jsonText)
        assertTrue(jsonText.contains("\"sha256\""), jsonText)

        val hardFailTarget = sandboxRoot.resolve("installed/hard-fail-target.jar")
        val hardFailCandidate = sandboxRoot.resolve("build/libs/hard-fail-candidate.jar")
        Files.writeString(hardFailTarget, "hard fail prior jar\n")
        Files.writeString(hardFailCandidate, "hard fail candidate jar\n")
        val unsafeRecord = store.update(record.copy(evidence = record.evidence + "fake_success placeholder green"))
        val hardFail = SelfHostPromotionService(
            repoRoot = sandboxRoot,
            store = store,
            dagService = DagExecutionService(repoRoot = sandboxRoot),
            completionGate = VerifiedCompletionGate(repoRoot = sandboxRoot)
        ).promote(
            SelfHostPromotionRequest(
                goalId = unsafeRecord.id,
                nodeId = unsafeRecord.currentNodeId,
                candidateJar = hardFailCandidate,
                targetJar = hardFailTarget
            )
        )
        assertTrue(!hardFail.promoted)
        assertTrue(hardFail.message.contains("self-host safety hard-fail gate"), hardFail.message)
        assertEquals("hard fail prior jar\n", Files.readString(hardFailTarget))

        writeProofSummary(
            operatorRoot = operatorRoot,
            prompt = prompt,
            sandboxRoot = sandboxRoot,
            goalId = record.id,
            worktreeId = record.evidence.firstOrNull { it.contains("worktree=") }?.substringAfter("worktree=")?.substringBefore(" "),
            markerHash = markerHash,
            mutationStatus = mutationStatus,
            evidenceMarkdown = markdown,
            evidenceJson = json,
            markdownHash = markdownHash,
            jsonHash = jsonHash,
            gateEvidence = record.evidence.filter {
                it.startsWith("self_host_safety") ||
                    it.startsWith("director_pre_promote") ||
                    it.startsWith("promotion_gate") ||
                    it.startsWith("jar_swap")
            },
            backupPath = backupPath,
            hardFailMessage = hardFail.message,
            hardFailTargetHash = hasher.sha256(hardFailTarget) ?: "missing"
        )
    }

    private fun initializeSandboxAtroposRepo(repoRoot: Path) {
        Files.writeString(repoRoot.resolve("settings.gradle.kts"), "pluginManagement {}\nrootProject.name = \"ATROPOS\"\n")
        Files.writeString(
            repoRoot.resolve("build.gradle.kts"),
            "plugins { id(\"org.jetbrains.kotlin.jvm\") version \"1.9.24\" }\n"
        )
        Files.createDirectories(repoRoot.resolve("gradle/wrapper"))
        Files.writeString(
            repoRoot.resolve("gradle/wrapper/gradle-wrapper.properties"),
            "distributionUrl=gradle-9.6.0-bin.zip\n"
        )
        Files.writeString(
            repoRoot.resolve("gradlew"),
            """
                #!/bin/sh
                mkdir -p build/libs
                case " ${'$'}* " in
                  *" jar "*) printf 'sandbox candidate jar\n' > build/libs/ATROPOS.jar ;;
                esac
                exit 0
            """.trimIndent() + "\n"
        )
        repoRoot.resolve("gradlew").toFile().setExecutable(true)
        Files.createDirectories(repoRoot.resolve("src/main/kotlin/atropos/core/agent"))
        Files.createDirectories(repoRoot.resolve("src/test/kotlin/atropos/core/agent"))
        Files.writeString(repoRoot.resolve("src/main/kotlin/atropos/Main.kt"), "package atropos\nfun main() {}\n")
        git(repoRoot, "init")
        git(repoRoot, "config", "user.email", "atropos@example.invalid")
        git(repoRoot, "config", "user.name", "ATROPOS Sandbox Proof")
        git(repoRoot, "add", ".")
        git(repoRoot, "commit", "-m", "sandbox baseline")
    }

    private fun writeProofSummary(
        operatorRoot: Path,
        prompt: String,
        sandboxRoot: Path,
        goalId: String,
        worktreeId: String?,
        markerHash: String,
        mutationStatus: String,
        evidenceMarkdown: Path,
        evidenceJson: Path,
        markdownHash: String,
        jsonHash: String,
        gateEvidence: List<String>,
        backupPath: Path,
        hardFailMessage: String,
        hardFailTargetHash: String
    ) {
        val proofDir = operatorRoot.resolve(".atropos/self-hosting/proofs")
        Files.createDirectories(proofDir)
        Files.writeString(
            proofDir.resolve("phase11-inside-out-sandbox-proof.properties"),
            buildString {
                appendLine("prompt=$prompt")
                appendLine("sandboxRoot=$sandboxRoot")
                appendLine("goalId=$goalId")
                appendLine("worktreeId=${worktreeId ?: "none"}")
                appendLine("worktreePath=${worktreeId?.let { sandboxRoot.resolve(".atropos/worktrees/$it") } ?: "none"}")
                appendLine("markerPath=${sandboxRoot.resolve("src/main/kotlin/atropos/core/agent/SelfHostCradleRuntimeState.kt")}")
                appendLine("markerBeforeSha256=missing")
                appendLine("markerAfterSha256=$markerHash")
                appendLine("mutationStatus=${mutationStatus.lineSequence().joinToString(" | ")}")
                appendLine("evidenceMarkdown=$evidenceMarkdown")
                appendLine("evidenceMarkdownSha256=$markdownHash")
                appendLine("evidenceJson=$evidenceJson")
                appendLine("evidenceJsonSha256=$jsonHash")
                gateEvidence.forEachIndexed { index, line -> appendLine("gateEvidence$index=$line") }
                appendLine("sandboxBackupJar=$backupPath")
                appendLine("hardFailPromoted=false")
                appendLine("hardFailMessage=$hardFailMessage")
                appendLine("hardFailTargetSha256=$hardFailTargetHash")
            }
        )
    }

    private fun restoreProperty(key: String, oldValue: String?) {
        if (oldValue == null) {
            System.clearProperty(key)
        } else {
            System.setProperty(key, oldValue)
        }
    }

    private fun git(repoRoot: Path, vararg args: String): String {
        val process = ProcessBuilder(listOf("git") + args)
            .directory(repoRoot.toFile())
            .redirectErrorStream(true)
            .start()
        val output = process.inputStream.bufferedReader().readText()
        val exit = process.waitFor()
        if (exit != 0) error("git ${args.joinToString(" ")} failed: $output")
        return output
    }
}
