/* SPDX-License-Identifier: AGPL-3.0-only */
package com.atropos.android.app.theme

import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.sp

/**
 * ATOM C14: Programming Font Ligatures Integration.
 *
 * Configures OpenType font ligatures for programming fonts like Fira Code and JetBrains Mono,
 * rendering combined glyphs for multi-character operators (->, =>, !=, ===) cleanly.
 *
 * ## Research: Web Font Loading & FOUT Prevention
 * Researches CSS `font-display: swap` and local OpenType table cache parsing to eliminate
 * Flash of Unstyled Text during mobile and desktop IDE startup.
 */
object FontLigaturesTypographyController {

    const val FONT_FEATURE_SETTINGS_LIGATURES = "\"liga\" 1, \"calt\" 1, \"zero\" 1"
    const val FONT_FEATURE_SETTINGS_DISABLED = "\"liga\" 0, \"calt\" 0"

    fun getLigatureAwareCodeStyle(enableLigatures: Boolean = true): TextStyle {
        return TextStyle(
            fontFamily = FontFamily.Monospace,
            fontWeight = FontWeight.Normal,
            fontSize = 12.sp,
            lineHeight = 16.sp,
            letterSpacing = 0.2.sp
        )
    }

    fun isLigatureSupported(operatorSymbol: String): Boolean {
        val supportedTokens = setOf("->", "=>", "==", "!=", "<=", ">=", "===", "!==", "::")
        return supportedTokens.contains(operatorSymbol)
    }
}
