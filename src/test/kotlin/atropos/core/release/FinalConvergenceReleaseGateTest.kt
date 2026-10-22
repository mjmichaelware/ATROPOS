/* SPDX-License-Identifier: AGPL-3.0-only */
package atropos.core.release

import java.nio.file.Paths
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class FinalConvergenceReleaseGateTest {

    @Test
    fun `sealMasterRelease creates valid Merkle certificate across platforms`() {
        val gate = FinalConvergenceReleaseGate(Paths.get("."))
        val cert = gate.sealMasterRelease("v1.0.0", "abcdef123456")

        assertEquals("v1.0.0", cert.version)
        assertEquals("abcdef123456", cert.gitCommitSha)
        assertEquals(3, cert.artifacts.size)
        assertTrue(cert.rootMerkleHash.isNotBlank())
        assertTrue(cert.artifacts.any { it.platform == "CLI_JAR" })
        assertTrue(cert.artifacts.any { it.platform == "WEB_VERCEL" })
        assertTrue(cert.artifacts.any { it.platform == "ANDROID_APK" })
    }
}
