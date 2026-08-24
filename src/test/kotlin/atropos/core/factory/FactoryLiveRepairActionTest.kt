package atropos.core.factory

import java.nio.file.Files
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertTrue

class FactoryLiveRepairActionTest {
    @Test
    fun missing_operator_repair_command_fails_closed() {
        val root = Files.createTempDirectory("factory-live-repair")
        val plan = FactoryPlan(
            id = "run-1",
            prompt = "repair",
            intent = "repair",
            projectSpec = AppProjectSpec("repair", AppIntent("repair", "cli", emptyList())),
            steps = emptyList(),
            paidAllowed = false,
            queuedWork = emptyList(),
            assetFiles = emptyList(),
            memoryRecordId = null
        )
        val freeze = FactoryAcceptanceFreeze.create(
            promptSha256 = "a".repeat(64),
            researchSha256 = "b".repeat(64),
            atomIds = emptyList(),
            promptSpans = "none"
        )

        assertFailsWith<IllegalStateException> {
            FactoryLiveRepairAction(root, command = null).invoke(
                plan,
                root.resolve("target"),
                IllegalStateException("verification failed"),
                freeze
            )
        }
    }

    @Test
    fun configured_repair_command_returns_real_stderr_and_preserves_freeze() {
        val root = Files.createTempDirectory("factory-live-repair-success")
        val script = root.resolve("repair.sh")
        Files.writeString(script, "#!/bin/sh\nprintf '%s\\n' 'repair verification evidence' >&2\n")
        script.toFile().setExecutable(true)
        val plan = FactoryPlan(
            id = "run-success",
            prompt = "repair",
            intent = "repair",
            projectSpec = AppProjectSpec("repair", AppIntent("repair", "cli", emptyList())),
            steps = emptyList(),
            paidAllowed = false,
            queuedWork = emptyList(),
            assetFiles = emptyList(),
            memoryRecordId = null
        )
        val freeze = FactoryAcceptanceFreeze.create(
            promptSha256 = "a".repeat(64),
            researchSha256 = "b".repeat(64),
            atomIds = emptyList(),
            promptSpans = "none"
        )

        val evidence = FactoryLiveRepairAction(root, command = listOf("./repair.sh")).invoke(
            plan,
            root.resolve("target"),
            IllegalStateException("verification failed"),
            freeze
        )

        assertEquals(freeze.sha256, evidence.freezeSha256)
        assertEquals(0, evidence.exitCode)
        assertTrue(evidence.stderr.contains("repair verification evidence"))
        assertTrue(evidence.predicateResults.values.all { it })
        assertTrue(freeze.requireRepairEvidence(evidence).contains("stderr_sha256="))
    }

    @Test
    fun repair_command_evidence_redacts_api_key_arguments() {
        val root = Files.createTempDirectory("factory-live-repair-redaction")
        val script = root.resolve("repair.sh")
        Files.writeString(script, "#!/bin/sh\nprintf '%s\\n' 'repair complete' >&2\n")
        script.toFile().setExecutable(true)
        val plan = FactoryPlan(
            id = "run-redaction",
            prompt = "repair",
            intent = "repair",
            projectSpec = AppProjectSpec("repair", AppIntent("repair", "cli", emptyList())),
            steps = emptyList(),
            paidAllowed = false,
            queuedWork = emptyList(),
            assetFiles = emptyList(),
            memoryRecordId = null
        )
        val freeze = FactoryAcceptanceFreeze.create(
            promptSha256 = "a".repeat(64),
            researchSha256 = "b".repeat(64),
            atomIds = emptyList(),
            promptSpans = "none"
        )
        val secret = "sk-live-12345678"

        val evidence = FactoryLiveRepairAction(
            root,
            command = listOf("./repair.sh", "--api-key=$secret")
        ).invoke(plan, root.resolve("target"), IllegalStateException("verification failed"), freeze)

        assertTrue(!evidence.command.contains(secret))
        assertTrue(evidence.command.contains("<redacted:api_key>"))
    }
}
