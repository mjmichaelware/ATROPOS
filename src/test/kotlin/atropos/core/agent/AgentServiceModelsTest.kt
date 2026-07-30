package atropos.core.agent

import kotlin.test.Test
import kotlin.test.assertTrue

class AgentServiceModelsTest {
    @Test
    fun patch_result_renders_source_pack_and_fetch_receipt_ids() {
        val rendered = AgentPatchRunResult(
            providerName = "test-provider",
            contextByteCount = 128,
            diffByteCount = 64,
            patchId = "patch-1",
            patchPath = null,
            checkResult = AgentPatchCheckResult(passed = true, exitCode = 0, output = ""),
            sourcePackId = "pack-abc",
            fetchReceiptId = "fetch-def"
        ).render()

        assertTrue(rendered.contains("Source pack: pack-abc"), rendered)
        assertTrue(rendered.contains("Fetch receipt: fetch-def"), rendered)
    }
}
