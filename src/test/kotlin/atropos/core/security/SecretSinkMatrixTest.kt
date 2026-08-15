/* SPDX-License-Identifier: AGPL-3.0-only */
package atropos.core.security

import kotlin.test.AfterTest
import kotlin.test.Test
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class SecretSinkMatrixTest {

    @AfterTest
    fun tearDown() {
        SecretSinkMatrix.resetDefaults()
    }

    @Test
    fun `respects sink permission boundaries correctly`() {
        assertTrue(SecretSinkMatrix.isEgressPermitted(SecretSinkKind.USER_UI))
        assertFalse(SecretSinkMatrix.isEgressPermitted(SecretSinkKind.RESEARCH_QUERY))

        SecretSinkMatrix.setPermitted(SecretSinkKind.RESEARCH_QUERY, true)
        assertTrue(SecretSinkMatrix.isEgressPermitted(SecretSinkKind.RESEARCH_QUERY))
    }
}
