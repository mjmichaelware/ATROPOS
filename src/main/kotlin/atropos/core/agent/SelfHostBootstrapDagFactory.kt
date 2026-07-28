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
        return dagService.createDag(
            label = "self-host bootstrap phase $phase: ${record.task.take(80)}",
            projectId = "atropos-self-host",
            nodes = listOf(
                DagNode(
                    id = "${record.id}-identity-probe",
                    label = "ATROPOS cradle verification probe",
                    territory = listOf("src/main/kotlin/atropos", "src/test/kotlin/atropos"),
                    action = DagNodeAction.VERIFY,
                    actionPayload = "git status --short -- src/main/kotlin/atropos src/test/kotlin/atropos",
                    expectedOutputs = listOf("src/main/kotlin/atropos/Main.kt"),
                    optionalChecks = setOf("Focused Tests"),
                    createdAt = now,
                    updatedAt = now,
                    metaFile = repoRoot.resolve(".atropos/dag/${record.id}-identity-probe.meta")
                )
            )
        )
    }
}
