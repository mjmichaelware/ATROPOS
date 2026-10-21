/* SPDX-License-Identifier: AGPL-3.0-only */
package atropos.core.phase20

import atropos.core.output.OutputMode
import atropos.core.security.TestSecretVaultKeyProvider
import atropos.core.security.TokenIsolationVault
import java.nio.file.Files
import kotlin.test.Test
import kotlin.test.assertNull

class Phase20AuthorizationHeadlessTest {

    @Test
    fun `missing console does not crash authorization construction or prompt`() {
        val vault = TokenIsolationVault(
            Files.createTempDirectory("atropos-phase20-headless-"),
            TestSecretVaultKeyProvider()
        )
        val authorization = Phase20Authorization(
            vault = vault,
            console = null,
            outputMode = OutputMode.HEADLESS
        )

        assertNull(authorization.requireAuthorization("headless smoke"))
    }
}
