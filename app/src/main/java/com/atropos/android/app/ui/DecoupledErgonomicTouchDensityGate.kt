/* SPDX-License-Identifier: AGPL-3.0-only */
package com.atropos.android.app.ui

/**
 * ATOM 25.3: Accessible Touch Density and Non-Color State Indicators.
 *
 * Enforces minimum 44dp touch target bounds across all interactive controls
 * while maintaining high visual density. Accompanies all color-coded status badges
 * with distinct geometric glyphs and text labels to satisfy accessibility standards.
 *
 * Research Directive: Thumb-zone ergonomic mapping algorithms that dynamically
 * position primary action controls within comfortable one-handed reach on large mobile screens.
 */
object DecoupledErgonomicTouchDensityGate {

    const val MINIMUM_TOUCH_TARGET_DP: Int = 44

    enum class AccessibleGlyph(val symbol: String, val semanticLabel: String) {
        SUCCESS("✓", "Success / Verified"),
        WARNING("⚠", "Warning / Action Required"),
        FAILURE("✕", "Failure / Non-zero Exit"),
        IN_PROGRESS("↻", "Executing / Syncing"),
        OFFLINE("◌", "Offline / Sandboxed")
    }

    data class AccessibleStatusDescriptor(
        val glyph: AccessibleGlyph,
        val textLabel: String,
        val isCompliantWithoutColor: Boolean = true
    )

    fun resolveAccessibleIndicator(isSuccess: Boolean, isRunning: Boolean): AccessibleStatusDescriptor {
        return when {
            isRunning -> AccessibleStatusDescriptor(AccessibleGlyph.IN_PROGRESS, "RUNNING")
            isSuccess -> AccessibleStatusDescriptor(AccessibleGlyph.SUCCESS, "VERIFIED")
            else -> AccessibleStatusDescriptor(AccessibleGlyph.FAILURE, "FAILED")
        }
    }

    fun assertCompliantTouchTarget(dp: Int) {
        require(dp >= MINIMUM_TOUCH_TARGET_DP) {
            "ATOM 25.3 Violation: Interactive touch target of ${dp}dp violates minimum ${MINIMUM_TOUCH_TARGET_DP}dp accessibility bound."
        }
    }
}
