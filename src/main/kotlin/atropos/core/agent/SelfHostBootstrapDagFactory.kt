package atropos.core.agent

import atropos.core.dag.DagDefinition
import atropos.core.dag.DagExecutionService
import atropos.core.dag.DagNode
import atropos.core.dag.DagNodeAction
import java.nio.charset.StandardCharsets
import java.nio.file.Path
import java.security.MessageDigest
import java.time.Instant

class SelfHostBootstrapDagFactory(
    private val repoRoot: Path,
    private val dagService: DagExecutionService,
    private val clock: () -> Instant
) {
    fun fingerprint(text: String): String {
        val digest = MessageDigest.getInstance("SHA-256")
            .digest(text.toByteArray(StandardCharsets.UTF_8))
            .joinToString("") { "%02x".format(it) }
        return digest.take(16)
    }

    fun create(record: GoalRunRecord, phase: String): DagDefinition {
        val now = clock()
        val probeId = "${record.id}-identity-probe"
        val markerId = "${record.id}-source-marker"
        val testId = "${record.id}-source-marker-test"
        val markerPath = "src/main/kotlin/atropos/core/agent/SelfHostCradleRuntimeState.kt"
        val testPath = "src/test/kotlin/atropos/core/agent/SelfHostCradleRuntimeStateTest.kt"
        val goalLiteral = kotlinString(record.id)
        val phaseLiteral = kotlinString(phase)
        val markerContent = """
            package atropos.core.agent

            object SelfHostCradleRuntimeState {
                const val LAST_SELF_HOST_GOAL: String = "$goalLiteral"
                const val LAST_SELF_HOST_PHASE: String = "$phaseLiteral"
            }
        """.trimIndent()
        val testContent = """
            package atropos.core.agent

            import kotlin.test.Test
            import kotlin.test.assertEquals

            class SelfHostCradleRuntimeStateTest {
                @Test
                fun records_latest_self_host_goal_and_phase() {
                    assertEquals("$goalLiteral", SelfHostCradleRuntimeState.LAST_SELF_HOST_GOAL)
                    assertEquals("$phaseLiteral", SelfHostCradleRuntimeState.LAST_SELF_HOST_PHASE)
                }
            }
        """.trimIndent()
        return dagService.createDag(
            label = "self-host bootstrap phase $phase: ${record.task.take(80)}",
            projectId = "atropos-self-host",
            nodes = listOf(
                DagNode(
                    id = probeId,
                    label = "ATROPOS cradle verification probe",
                    territory = listOf("src/main/kotlin/atropos", "src/test/kotlin/atropos"),
                    action = DagNodeAction.VERIFY,
                    actionPayload = "git status --short -- src/main/kotlin/atropos src/test/kotlin/atropos",
                    expectedOutputs = listOf("src/main/kotlin/atropos/Main.kt"),
                    optionalChecks = setOf("Focused Tests"),
                    createdAt = now,
                    updatedAt = now,
                    metaFile = repoRoot.resolve(".atropos/dag/$probeId.meta")
                ),
                DagNode(
                    id = markerId,
                    label = "ATROPOS deterministic self-host source marker",
                    dependencies = listOf(probeId),
                    territory = listOf("src/main/kotlin/atropos/core/agent"),
                    action = DagNodeAction.EDIT_FILE,
                    actionPayload = "$markerPath::$markerContent",
                    expectedOutputs = listOf(markerPath),
                    optionalChecks = setOf("Focused Tests"),
                    createdAt = now,
                    updatedAt = now,
                    metaFile = repoRoot.resolve(".atropos/dag/$markerId.meta")
                ),
                DagNode(
                    id = testId,
                    label = "SelfHostCradleRuntimeStateTest",
                    dependencies = listOf(markerId),
                    territory = listOf(
                        "src/main/kotlin/atropos/core/agent",
                        "src/test/kotlin/atropos/core/agent"
                    ),
                    action = DagNodeAction.CREATE_FILE,
                    actionPayload = "$testPath::$testContent",
                    expectedOutputs = listOf(testPath),
                    createdAt = now,
                    updatedAt = now,
                    metaFile = repoRoot.resolve(".atropos/dag/$testId.meta")
                )
            )
        )
    }

    private fun kotlinString(value: String): String =
        value
            .replace("\\", "\\\\")
            .replace("\"", "\\\"")
            .replace("\n", "\\n")
            .replace("\r", "\\r")
}
