package atropos.core.agent

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class AgentRunPromptComposerTest {

    private val composer = AgentRunPromptComposer()

    @Test
    fun `the plan prompt forbids a diff explicitly`() {
        val prompt = composer.planPrompt("add a timeout")
        assertTrue(prompt.contains("no diff"), "stage one must not return a patch")
        assertTrue(prompt.contains("add a timeout"))
    }

    @Test
    fun `the patch task leads with the task, not the plan`() {
        val task = composer.patchTask("add a timeout", "first do this, then that")
        assertTrue(
            task.startsWith("add a timeout"),
            "leading with the plan invites patching the plan rather than the repository"
        )
        assertTrue(task.contains("Plan context:"))
    }

    @Test
    fun `a blank plan contributes no section`() {
        val task = composer.patchTask("add a timeout", "   ")
        assertEquals("add a timeout", task)
        assertFalse(task.contains("Plan context:"))
    }

    @Test
    fun `a runaway plan is capped so it cannot crowd out repository context`() {
        val task = composer.patchTask("do the thing", "x".repeat(10_000))
        assertTrue(task.length < 3_000, "plan length is model-controlled and must be bounded")
    }

    @Test
    fun `the success line names every id that exists`() {
        assertEquals(
            "completed patch=p1 repair=p2 verification=v1",
            composer.successResult("p1", verificationId = "v1", repairPatchId = "p2")
        )
    }

    @Test
    fun `absent ids are omitted rather than rendered as null`() {
        val line = composer.successResult("p1", verificationId = null, repairPatchId = null)
        assertEquals("completed patch=p1", line)
        assertFalse(line.contains("null"))
    }

    @Test
    fun `a verification without a repair is still named`() {
        assertEquals(
            "completed patch=p1 verification=v1",
            composer.successResult("p1", verificationId = "v1", repairPatchId = null)
        )
    }
}
