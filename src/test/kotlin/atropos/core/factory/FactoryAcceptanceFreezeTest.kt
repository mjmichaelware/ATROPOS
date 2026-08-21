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
}
