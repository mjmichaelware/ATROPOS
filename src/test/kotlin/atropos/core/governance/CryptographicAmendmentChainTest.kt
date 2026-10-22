/* SPDX-License-Identifier: AGPL-3.0-only */
package atropos.core.governance

import java.nio.file.Files
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class CryptographicAmendmentChainTest {

    @Test
    fun `records amendments and computes cryptographic chain integrity`() {
        val tempDir = Files.createTempDirectory("amendment-test")
        val chain = CryptographicAmendmentChain(tempDir)

        val amd1 = chain.recordAmendment(listOf("B-PROV-007"), "Initial contract")
        val amd2 = chain.recordAmendment(listOf("B-PROV-007", "B-PROV-008"), "Extended routing constraint")

        assertTrue(chain.verifyChainIntegrity())

        val invalidated = chain.getInvalidatedAtoms(amd1.timestampEpochMs)
        assertTrue(invalidated.contains("B-PROV-007"))
        assertTrue(invalidated.contains("B-PROV-008"))

        tempDir.toFile().deleteRecursively()
    }
}
