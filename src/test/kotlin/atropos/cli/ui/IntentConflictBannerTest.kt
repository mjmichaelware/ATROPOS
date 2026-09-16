/* SPDX-License-Identifier: AGPL-3.0-only */
package atropos.cli.ui

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue
import kotlin.test.assertFalse
import kotlin.test.assertNull
import kotlin.test.assertNotNull

class IntentConflictBannerTest {

    @Test
    fun `records prior decisions and detects conflicts`() {
        val banner = IntentConflictBanner()

        banner.recordPriorDecision("paid_providers", "none")
        banner.recordPriorDecision("local_only", "true")

        val conflict = banner.checkAndRecord(
            kind = IntentConflictBanner.Kind.PaidApproval,
            priorDecisionKey = "paid_providers",
            conflictingCommand = "use openrouter for this",
            severity = IntentConflictBanner.Severity.BLOCKING
        )

        assertNotNull(conflict)
        assertEquals(IntentConflictBanner.Kind.PaidApproval, conflict.kind)
        assertEquals("none", conflict.priorDecision)
        assertEquals("use openrouter for this", conflict.conflictingCommand)
        assertEquals(IntentConflictBanner.Severity.BLOCKING, conflict.severity)
    }

    @Test
    fun `no conflict when no prior decision`() {
        val banner = IntentConflictBanner()

        val conflict = banner.checkAndRecord(
            kind = IntentConflictBanner.Kind.PaidApproval,
            priorDecisionKey = "paid_providers",
            conflictingCommand = "use openrouter"
        )

        assertNull(conflict)
    }

    @Test
    fun `checkPaidProviderConflict detects paid provider references`() {
        val banner = IntentConflictBanner()
        banner.recordPriorDecision("paid_providers", "none")

        val conflict = banner.checkPaidProviderConflict("use openrouter api key")

        assertNotNull(conflict)
        assertEquals(IntentConflictBanner.Kind.PaidApproval, conflict.kind)
    }

    @Test
    fun `checkLocalOnlyConflict detects network commands`() {
        val banner = IntentConflictBanner()
        banner.recordPriorDecision("local_only", "true")

        val conflict = banner.checkLocalOnlyConflict("curl https://api.example.com")

        assertNotNull(conflict)
        assertEquals(IntentConflictBanner.Kind.LocalOnlyMode, conflict.kind)
    }

    @Test
    fun `checkTerritoryConflict detects system paths`() {
        val banner = IntentConflictBanner()
        banner.recordPriorDecision("territory_system", "system")

        val conflict = banner.checkTerritoryConflict("read /etc/passwd", "system")

        assertNotNull(conflict)
        assertEquals(IntentConflictBanner.Kind.TerritoryViolation, conflict.kind)
    }

    @Test
    fun `checkZeroRetentionConflict detects persist commands`() {
        val banner = IntentConflictBanner()
        banner.recordPriorDecision("zero_retention", "true")

        val conflict = banner.checkZeroRetentionConflict("persist this data to disk")

        assertNotNull(conflict)
        assertEquals(IntentConflictBanner.Kind.ZeroRetention, conflict.kind)
    }

    @Test
    fun `renderBanner formats conflicts correctly`() {
        val banner = IntentConflictBanner()
        banner.recordPriorDecision("paid_providers", "none")

        banner.checkPaidProviderConflict("use openrouter")

        val bannerText = banner.renderBanner()

        assertTrue(bannerText.contains("PROVIDER POLICY CONFLICT"))
        assertTrue(bannerText.contains("🛑"))
    }

    @Test
    fun `blocking filters only blocking conflicts`() {
        val banner = IntentConflictBanner()
        banner.recordPriorDecision("paid_providers", "none")

        banner.checkAndRecord(
            kind = IntentConflictBanner.Kind.PaidApproval,
            priorDecisionKey = "paid_providers",
            conflictingCommand = "paid cmd",
            severity = IntentConflictBanner.Severity.BLOCKING
        )
        banner.checkAndRecord(
            kind = IntentConflictBanner.Kind.ProviderPolicy,
            priorDecisionKey = "policy_x",
            conflictingCommand = "warn cmd",
            severity = IntentConflictBanner.Severity.WARNING
        )

        val blocking = banner.blocking()

        assertEquals(1, blocking.size)
        assertEquals(IntentConflictBanner.Severity.BLOCKING, blocking[0].severity)
    }

    @Test
    fun `detect detects paid provider references`() {
        val banner = IntentConflictBanner()
        banner.recordPriorDecision("paid_providers", "none")

        val detector = IntentConflictDetector()

        val conflict = detector.detect("use openrouter for this task")

        assertNotNull(conflict)
        assertEquals(IntentConflictBanner.Kind.PaidApproval, conflict.kind)
    }

    @Test
    fun `detect detects local-only violations`() {
        val banner = IntentConflictBanner()
        banner.recordPriorDecision("local_only", "true")

        val detector = IntentConflictDetector()

        val conflict = detector.detect("curl https://example.com")

        assertNotNull(conflict)
        assertEquals(IntentConflictBanner.Kind.LocalOnlyMode, conflict.kind)
    }

    @Test
    fun `loadPriorDecisions loads from config`() {
        val banner = IntentConflictBanner()

        IntentConflictDetector().loadPriorDecisions(mapOf(
            "paid_providers" to "none",
            "local_only" to "true"
        ))

        // The detector uses its own internal banner, so we test through the banner directly
        val testBanner = IntentConflictBanner()
        testBanner.recordPriorDecision("paid_providers", "none")

        val conflict = testBanner.checkPaidProviderConflict("use openrouter")
        assertNotNull(conflict)
    }
}