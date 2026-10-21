/* SPDX-License-Identifier: AGPL-3.0-only */
package atropos.core.security

import java.nio.file.Files
import kotlin.test.Test
import kotlin.test.assertNull

class OwnerAuthorizationServiceHeadlessTest {

    @Test
    fun `missing console fails closed without crashing construction`() {
        val service = OwnerAuthorizationService(
            configRoot = Files.createTempDirectory("atropos-owner-auth-headless-"),
            console = null
        )

        assertNull(service.requireAuthorization(emptyList()))
    }
}
