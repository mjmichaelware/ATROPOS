package atropos.core.factory

import kotlin.test.Test
import kotlin.test.assertContains
import kotlin.test.assertEquals

class FactoryAcceptanceFreezeTest {
    @Test
    fun freeze_is_deterministic_and_binds_prompt_requirements_and_atoms() {
        val first = FactoryAcceptanceFreeze.create(
            promptSha256 = "a".repeat(64),
            researchSha256 = "b".repeat(64),
            atomIds = listOf("atom-b", "atom-a"),
            promptSpans = "CLI@1-3|class=surface"
        )
        val second = FactoryAcceptanceFreeze.create(
            promptSha256 = "a".repeat(64),
            researchSha256 = "b".repeat(64),
            atomIds = listOf("atom-a", "atom-b"),
            promptSpans = "CLI@1-3|class=surface"
        )

        assertEquals(first.sha256, second.sha256)
        assertContains(first.document, "atom_ids=atom-a,atom-b")
        assertContains(first.document, "predicate=verify_sh_exit_zero_and_marker_present")
    }

    @Test
    fun repair_must_reuse_freeze_and_record_command_exit_stderr_and_predicates() {
        val freeze = FactoryAcceptanceFreeze.create("a".repeat(64), "b".repeat(64), listOf("atom"), "CLI@1-1")
        val evidence = freeze.requireRepairEvidence(
            FactoryAcceptanceFreeze.RepairEvidence(
                freezeSha256 = freeze.sha256,
                command = "./verify.sh",
                exitCode = 0,
                stderr = "no diagnostics",
                predicateResults = mapOf("verify" to true)
            )
        )
        assertContains(evidence, "acceptance_freeze_sha256=${freeze.sha256}")
        assertContains(evidence, "stderr_sha256=")
    }
}
