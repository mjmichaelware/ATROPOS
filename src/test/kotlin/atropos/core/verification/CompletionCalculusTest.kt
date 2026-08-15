/* SPDX-License-Identifier: AGPL-3.0-only */
package atropos.core.verification

import kotlin.test.Test
import kotlin.test.assertEquals

class CompletionCalculusTest {

    @Test
    fun `calculates real completion as the minimum of all metrics`() {
        val comp = ComponentCompletion(
            implementationPercent = 95.0,
            integrationPercent = 90.0,
            verificationPercent = 50.0, // verification debt present
            evidencePercent = 80.0
        )
        assertEquals(50.0, CompletionCalculus.calculateRealCompletion(comp))
    }
}
