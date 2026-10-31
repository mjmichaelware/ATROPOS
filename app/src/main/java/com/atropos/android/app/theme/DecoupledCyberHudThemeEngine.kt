/* SPDX-License-Identifier: AGPL-3.0-only */
package com.atropos.android.app.theme

import androidx.compose.runtime.Composable
import androidx.compose.runtime.Immutable
import androidx.compose.ui.graphics.Color

/**
 * ATOM 25.1: High-Density Android Cyber-HUD Theme System.
 *
 * Provides a high-density, dark-mode HUD theme optimized for technical mobile workflows.
 * Delivers clean high-contrast syntax highlighting, refined panel borders, and organized
 * visual hierarchy to minimize eye fatigue.
 *
 * Research Directive: Dynamic OLED black-pixel battery optimization techniques in Jetpack
 * Compose to reduce device power consumption on mobile screens (pure #000000 base luminance).
 */
@Immutable
data class DecoupledCyberHudEngineSpec(
    val pureOledBlack: Color = Color(0xFF000000),
    val highContrastText: Color = Color(0xFFF0F6FC),
    val emeraldAccent: Color = Color(0xFF238636),
    val cobaltBorder: Color = Color(0xFF1F6FEB),
    val amberWarning: Color = Color(0xFFD29922),
    val oledBlackOptimizationRatio: Float = 0.85f
)

object DecoupledCyberHudThemeEngine {
    val defaultSpec = DecoupledCyberHudEngineSpec()

    fun calculateOledPowerSavings(blackPixelRatio: Float): Float {
        // OLED displays consume near 0mA on pure black pixels.
        return (blackPixelRatio.coerceIn(0f, 1f) * 0.45f)
    }
}
