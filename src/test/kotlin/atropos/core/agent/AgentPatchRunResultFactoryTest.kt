package atropos.core.agent

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNull
import kotlin.test.assertTrue

class AgentPatchRunResultFactoryTest {

    @Test
    fun `no-repair-target keeps the patch id it was asked about`() {
        val result = AgentPatchRunResultFactory.noRepairTarget("patch-7")
        assertEquals("patch-7", result.patchId)
        assertEquals("none", result.providerName)
    }

    @Test
    fun `every empty result names no file on disk`() {
        val results = listOf(
            AgentPatchRunResultFactory.noRepairTarget(null),
            AgentPatchRunResultFactory.missingPatch("nope"),
            AgentPatchRunResultFactory.localFailure(
                providerName = "groq",
                contextByteCount = 10,
                retryAttempted = true,
                failureSummary = "no diff"
            )
        )
        results.forEach { result ->
            assertNull(result.patchPath, "a run that produced nothing must not name a patch file")
            assertNull(result.checkResult, "a run that produced nothing has no apply check")
            assertEquals(0, result.diffByteCount)
        }
    }

    @Test
    fun `a blank reference is reported as having no patch id at all`() {
        assertEquals("no patch id exists", AgentPatchRunResultFactory.refusalForMissingPatch("   "))
    }

    @Test
    fun `a named reference is echoed back trimmed`() {
        assertEquals(
            "patch not found: patch-42",
            AgentPatchRunResultFactory.refusalForMissingPatch("  patch-42  ")
        )
    }

    @Test
    fun `missing patch carries its reason in every operator-facing field`() {
        val result = AgentPatchRunResultFactory.missingPatch("patch-42")
        val reason = "patch not found: patch-42"
        assertEquals(reason, result.failureSummary)
        assertEquals(reason, result.rejectionReason)
        assertEquals(reason, result.message)
    }

    @Test
    fun `local failure says plainly that nothing was applied`() {
        val result = AgentPatchRunResultFactory.localFailure(
            providerName = "groq",
            contextByteCount = 128,
            retryAttempted = true,
            failureSummary = "provider returned prose",
            rejectionReason = "no unified diff found",
            responsePreview = "I would change…"
        )
        assertTrue(result.message?.contains("did not apply anything") == true)
        assertEquals("provider returned prose", result.failureSummary)
        assertEquals(128, result.contextByteCount)
        assertTrue(result.retryAttempted)
    }

    @Test
    fun `no-repair-target is not marked as a retry`() {
        assertFalse(AgentPatchRunResultFactory.noRepairTarget(null).retryAttempted)
    }
}
