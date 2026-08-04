/* SPDX-License-Identifier: AGPL-3.0-only */
package atropos.cli.input

import kotlin.test.Test
import kotlin.test.assertEquals

class PromptHistoryLaneTest {

    @Test
    fun `bang escapes to the shell lane`() {
        assertEquals(PromptHistoryLane.SHELL, PromptHistoryLane.classify("!ls -la"))
    }

    @Test
    fun `working-directory slash commands are shell, not slash`() {
        listOf("/shell echo hi", "/pwd", "/cd ..", "/ls src", "/git status").forEach { line ->
            assertEquals(
                PromptHistoryLane.SHELL,
                PromptHistoryLane.classify(line),
                "$line acts on the working directory and belongs in the shell lane"
            )
        }
    }

    @Test
    fun `other slash commands stay in the slash lane`() {
        assertEquals(PromptHistoryLane.SLASH, PromptHistoryLane.classify("/status"))
        assertEquals(PromptHistoryLane.SLASH, PromptHistoryLane.classify("/agent run"))
    }

    @Test
    fun `prose is the prompt lane`() {
        assertEquals(PromptHistoryLane.PROMPT, PromptHistoryLane.classify("what changed today"))
    }

    @Test
    fun `leading whitespace does not change the lane`() {
        assertEquals(PromptHistoryLane.SLASH, PromptHistoryLane.classify("   /status"))
        assertEquals(PromptHistoryLane.SHELL, PromptHistoryLane.classify("  !ls"))
    }

    @Test
    fun `an empty line is prompt rather than a crash`() {
        assertEquals(PromptHistoryLane.PROMPT, PromptHistoryLane.classify(""))
    }
}
