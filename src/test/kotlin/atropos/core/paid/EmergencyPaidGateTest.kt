package atropos.core.paid

import atropos.core.AtroposRepoRootLocator
import java.nio.file.Files
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class EmergencyPaidGateTest {
    @Test
    fun default_root_is_under_atropos_root() {
        assertEquals(
            AtroposRepoRootLocator.resolve().resolve(".atropos/paid").toFile().absoluteFile,
            EmergencyPaidGate.defaultRoot().absoluteFile
        )
    }

    @Test
    fun `persists only a redacted reason while keeping the paid unlock active`() {
        val root = Files.createTempDirectory("atropos-paid-gate-").toFile()
        val secretReason = "Bearer phase4-secret-token evidence: https://example.test/private"
        val gate = EmergencyPaidGate(root) { 1_000L }

        val unlock = gate.unlock("openai", "1m", secretReason)

        assertEquals(secretReason, unlock.reason)
        assertTrue(gate.isProviderUnlocked("openai"))
        val state = root.resolve("paid-unlock.state").readText()
        val audit = root.resolve("paid-audit.jsonl").readText()
        assertFalse(state.contains(secretReason))
        assertFalse(audit.contains(secretReason))
        assertTrue(state.contains("[redacted]"))
        assertTrue(audit.contains("[redacted]"))
        assertEquals("[redacted]", gate.status().active?.reason)
    }

    @Test
    fun `lock removes an active paid unlock after redacted evidence is written`() {
        val root = Files.createTempDirectory("atropos-paid-gate-lock-").toFile()
        val gate = EmergencyPaidGate(root) { 1_000L }

        gate.unlock("openai", "1m", "api_key=phase4-lock-secret")

        assertFalse(gate.status().locked)
        assertTrue(gate.lock())
        assertTrue(gate.status().locked)
        assertFalse(gate.isProviderUnlocked("openai"))
        assertFalse(root.resolve("paid-audit.jsonl").readText().contains("phase4-lock-secret"))
    }
}
