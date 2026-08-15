/* SPDX-License-Identifier: AGPL-3.0-only */
package atropos.core.verification

import kotlin.test.Test
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class AuthorityAttestationTest {

    @Test
    fun `verifies attestation matches successfully`() {
        val content = "# Section Title\nSome content description."
        val hash = AuthorityAttestation.sha256(content.trim())

        val pass = AuthorityAttestation.verify("AGENTS.md", content, hash)
        assertTrue(pass.matching)

        val fail = AuthorityAttestation.verify("AGENTS.md", content + " modified", hash)
        assertFalse(fail.matching)
    }
}
