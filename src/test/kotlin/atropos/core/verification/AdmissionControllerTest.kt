/* SPDX-License-Identifier: AGPL-3.0-only */
package atropos.core.verification

import kotlin.test.Test
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class AdmissionControllerTest {

    @Test
    fun `rejects config update that attempts to disable core invariants`() {
        val badConfig = mapOf("sourceAuthorityIsImmutable" to false)
        assertFalse(AdmissionController.validateConfigUpdate(badConfig))

        val goodConfig = mapOf("sourceAuthorityIsImmutable" to true, "threads" to 4)
        assertTrue(AdmissionController.validateConfigUpdate(goodConfig))
    }
}
