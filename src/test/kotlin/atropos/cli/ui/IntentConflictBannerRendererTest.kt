/* SPDX-License-Identifier: AGPL-3.0-only */
package atropos.cli.ui

import atropos.cli.ui.design.ThemePalette
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class IntentConflictBannerRendererTest {
    private val theme = ThemePalette()

    @Test
    fun `renders blocking conflict with stop icon`() {
        val renderer = IntentConflictBannerRenderer(theme)
        val conflict = IntentConflictBanner.Conflict(
            id = "test-1",
            kind = IntentConflictBanner.Kind.PaidApproval,
            priorDecision = "none",
            conflictingCommand = "use openrouter",
            prohibitedBy = "Prior decision: paid_providers = none",
            severity = IntentConflictBanner.Severity.BLOCKING
        )

        val output = renderer.render(conflict)

        assertTrue(output.contains("🛑"))
        assertTrue(output.contains("PROVIDER POLICY CONFLICT"))
        assertTrue(output.contains("use openrouter"))
        assertTrue(output.contains("Prior decision"))
    }

    @Test
    fun `renders warning with warning icon`() {
        val renderer = IntentConflictBannerRenderer(theme)
        val conflict = IntentConflictBanner.Conflict(
            id = "test-2",
            kind = IntentConflictBanner.Kind.ProviderPolicy,
            priorDecision = "policy_x",
            conflictingCommand = "warn cmd",
            prohibitedBy = "Prior decision: provider_policy_x = policy_x",
            severity = IntentConflictBanner.Severity.WARNING
        )

        val output = renderer.render(conflict)

        assertTrue(output.contains("⚠️"))
        assertTrue(output.contains("PROVIDER POLICY CONFLICT"))
    }

    @Test
    fun `renders informational with info icon`() {
        val renderer = IntentConflictBannerRenderer(theme)
        val conflict = IntentConflictBanner.Conflict(
            id = "test-3",
            kind = IntentConflictBanner.Kind.Custom("Custom"),
            priorDecision = "custom",
            conflictingCommand = "info cmd",
            prohibitedBy = "Prior decision: custom = custom",
            severity = IntentConflictBanner.Severity.INFORMATIONAL
        )

        val output = renderer.render(conflict)

        assertTrue(output.contains("ℹ️"))
        assertTrue(output.contains("INTENT CONFLICT"))
    }

    @Test
    fun `renderAll joins multiple conflicts`() {
        val renderer = IntentConflictBannerRenderer(theme)
        val conflicts = listOf(
            IntentConflictBanner.Conflict(
                id = "1",
                kind = IntentConflictBanner.Kind.PaidApproval,
                priorDecision = "none",
                conflictingCommand = "cmd1",
                prohibitedBy = "prior1",
                severity = IntentConflictBanner.Severity.BLOCKING
            ),
            IntentConflictBanner.Conflict(
                id = "2",
                kind = IntentConflictBanner.Kind.ProviderPolicy,
                priorDecision = "policy",
                conflictingCommand = "cmd2",
                prohibitedBy = "prior2",
                severity = IntentConflictBanner.Severity.WARNING
            )
        )

        val output = renderer.renderAll(conflicts)

        assertTrue(output.contains("PROVIDER POLICY CONFLICT"))
        assertTrue(output.contains("PROVIDER POLICY CONFLICT"))
    }

    @Test
    fun `empty list renders empty string`() {
        val renderer = IntentConflictBannerRenderer(theme)
        val output = renderer.renderAll(emptyList())

        assertEquals("", output)
    }
}