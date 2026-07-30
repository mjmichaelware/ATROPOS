package atropos.core.agent

import kotlin.test.Test
import kotlin.test.assertTrue

class SelfHostFailureCodeTest {
    @Test
    fun refusal_categories_cover_durable_self_host_boundaries() {
        val codes = SelfHostFailureCode.entries.toSet()

        assertTrue(SelfHostFailureCode.GOAL_NOT_FOUND in codes)
        assertTrue(SelfHostFailureCode.MISSING_DAG in codes)
        assertTrue(SelfHostFailureCode.EVIDENCE_EXPORT_FAILED in codes)
        assertTrue(SelfHostFailureCode.EVIDENCE_HASH_MISSING in codes)
        assertTrue(SelfHostFailureCode.PROMOTION_REFUSED in codes)
        assertTrue(SelfHostFailureCode.EVIDENCE_INCOMPLETE in codes)
    }
}
