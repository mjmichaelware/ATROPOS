/* SPDX-License-Identifier: AGPL-3.0-only */
package atropos.core.benchmark

import java.nio.file.Files
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class ReproducingTestSynthesizerTest {

    @Test
    fun `parses requirements and synthesizes reproducer script`() {
        val tempDir = Files.createTempDirectory("reproducer-test")
        val synth = ReproducingTestSynthesizer(
            repoRoot = tempDir,
            sandboxRunner = { cmd -> Pair(1, "AssertionError: Reproduced bug condition") }
        )

        val issueText = "Bug: ValueError: cannot convert float NaN to integer\nExpected: None"
        val reqs = synth.parseIssueRequirements(issueText)
        assertTrue(reqs.isNotEmpty())

        val script = synth.synthesizeMinimalReproducer("test-issue-1", issueText, listOf("file.py"))
        assertTrue(script.contains("test-issue-1"))

        val result = synth.executeBaselineValidation(script)
        assertEquals(ReproducerStatus.CONFIRMED_FAILURE, result.status)
        assertEquals(1, result.baselineExitCode)

        val patchOk = synth.verifyPatchEliminatesBug(script, "+ fix applied")
        assertTrue(patchOk)

        tempDir.toFile().deleteRecursively()
    }
}
