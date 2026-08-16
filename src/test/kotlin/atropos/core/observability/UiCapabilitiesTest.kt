/* SPDX-License-Identifier: AGPL-3.0-only */
package atropos.core.observability

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class UiCapabilitiesTest {

    @Test
    fun `TabRestorationService saves and restores tab state`() {
        val state = TabState("GOVERNANCE", 120)
        TabRestorationService.saveState("project-1", state)
        assertEquals(state, TabRestorationService.restoreState("project-1"))
    }

    @Test
    fun `ResponsiveBranding adjusts logo on narrow viewport`() {
        assertEquals("ATROPOS", ResponsiveBranding.renderBrandingLogo(640))
        assertEquals("ATRO", ResponsiveBranding.renderBrandingLogo(240))
    }

    @Test
    fun `AccessibilitySettings preserves reduced motion and contrast decisions`() {
        val settings = AccessibilitySettings(
            isReducedMotionEnabled = true,
            isHighContrastEnabled = true,
            hasFocusVisibility = true
        )
        assertTrue(settings.isReducedMotionEnabled)
        assertTrue(settings.isHighContrastEnabled)
        assertTrue(settings.hasFocusVisibility)
        assertEquals("Calculator", settings.label("title", "Calculator"))
        assertEquals("[focus] Calculator", settings.focus("Calculator"))
    }

    @Test
    fun `AccessibilitySettings reads deterministic environment flags`() {
        val settings = AccessibilitySettings.fromEnvironment { key ->
            mapOf(
                "ATROPOS_REDUCED_MOTION" to "1",
                "ATROPOS_HIGH_CONTRAST" to "1",
                "ATROPOS_FOCUS_VISIBILITY" to "0"
            )[key]
        }

        assertTrue(settings.isReducedMotionEnabled)
        assertTrue(settings.isHighContrastEnabled)
        assertFalse(settings.hasFocusVisibility)
        assertEquals("value", settings.label("missing", "value"))
        assertEquals("value", settings.focus("value"))
    }

    @Test
    fun `TouchAutocomplete returns matching suggestion prefixes`() {
        val suggestions = TouchAutocomplete.getSuggestions("/st")
        assertEquals(listOf("/status"), suggestions)
    }

    @Test
    fun `FuzzyExecutionGate verifies confirmation matching`() {
        val gate = FuzzyExecutionGate()
        assertTrue(gate.requestConfirmation("verify", "verify"))
        assertFalse(gate.requestConfirmation("verify", "validate"))
        assertTrue(gate.requestConfirmation(" /GIT PUSH ", "/git push"))
    }

    @Test
    fun `VirtualizedLogEngine returns bounded slice of logs`() {
        val logs = listOf("line1", "line2", "line3", "line4")
        val window = VirtualizedLogEngine.getLogWindow(logs, 1, 2)
        assertEquals(listOf("line2", "line3"), window)
    }
}
