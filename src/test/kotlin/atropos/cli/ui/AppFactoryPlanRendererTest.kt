package atropos.cli.ui

import kotlin.test.Test
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class AppFactoryPlanRendererTest {
    @Test
    fun status_does_not_claim_runtime_acceptance_before_verification() {
        val status = AppFactoryPlanRenderer().renderStatus()

        assertTrue("runtime verification pending" in status)
        assertFalse("final acceptance: ready" in status)
    }
}
