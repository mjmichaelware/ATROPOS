/* SPDX-License-Identifier: AGPL-3.0-only */
package atropos.core.certificate

import org.junit.Test
import java.nio.file.Files
import kotlin.test.assertEquals
import kotlin.test.assertNotNull
import kotlin.test.assertTrue

class ZeroEntropyAttestationVerifierTest {

    private val verifier = ZeroEntropyAttestationVerifier()

    @Test
    fun `verifyAndCertify computes Merkle hashes and emits proof bundle`() {
        val worktree = Files.createTempDirectory("test_worktree")
        val outputDir = Files.createTempDirectory("test_proof_out")

        val fileA = worktree.resolve("Contract.kt")
        Files.writeString(fileA, "package test\ninterface Contract")

        val cert = verifier.verifyAndCertify(worktree, outputDir)
        assertNotNull(cert)
        assertEquals(40, cert.rootCommitHash.length)
        assertTrue(cert.specHash.isNotBlank())
        assertTrue(cert.mutationScoreHash.isNotBlank())

        // Proof bundle should exist in outputDir
        val proofBundle = outputDir.resolve("proof_bundle.json")
        assertTrue(Files.isRegularFile(proofBundle))

        val bundleJson = Files.readString(proofBundle)
        assertTrue(bundleJson.contains("\"validatable\": true"))
        assertTrue(bundleJson.contains("\"merkleRoot\":"))
    }
}
