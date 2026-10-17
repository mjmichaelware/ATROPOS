/* SPDX-License-Identifier: AGPL-3.0-only */
package atropos.cli.tips

import org.junit.Test
import java.io.File
import java.nio.file.Files
import kotlin.test.assertEquals
import kotlin.test.assertNotNull
import kotlin.test.assertTrue

class RotatingTipBufferTest {

    @Test
    fun `sequential nextTip calls rotate modulo 20 and persist index`() {
        val tempFile = File.createTempFile("test_tips", ".idx")
        tempFile.deleteOnExit()

        val buffer = RotatingTipBuffer(tempFile)
        val tipsSeen = mutableListOf<String>()

        for (i in 0 until 25) {
            val tip = buffer.nextTip()
            tipsSeen.add(tip)
        }

        assertEquals(25, tipsSeen.size)
        // Check that index wrapped modulo 20
        assertEquals(tipsSeen[0], tipsSeen[20])
        assertEquals(tipsSeen[1], tipsSeen[21])
    }

    @Test
    fun `inferContextualHint detects 429 rate limit`() {
        val buffer = RotatingTipBuffer()
        val hint = buffer.inferContextualHint("Error 429: Too Many Requests from provider")
        assertNotNull(hint)
        assertTrue(hint.contains("Rate Limit"))
    }

    @Test
    fun `inferContextualHint detects unresolved reference`() {
        val buffer = RotatingTipBuffer()
        val hint = buffer.inferContextualHint("Compilation error: unresolved reference 'TargetWorkspaceLocator'")
        assertNotNull(hint)
        assertTrue(hint.contains("Unresolved Reference"))
    }

    @Test
    fun `inferContextualHint detects dirty working tree`() {
        val buffer = RotatingTipBuffer()
        val hint = buffer.inferContextualHint("Cannot rebase: working tree is dirty")
        assertNotNull(hint)
        assertTrue(hint.contains("Dirty Working Tree"))
    }

    @Test
    fun `inferContextualHint returns null for normal message`() {
        val buffer = RotatingTipBuffer()
        val hint = buffer.inferContextualHint("Completed stage 2 successfully.")
        assertEquals(null, hint)
    }
}
