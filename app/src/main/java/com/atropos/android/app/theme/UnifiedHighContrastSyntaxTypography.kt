/* SPDX-License-Identifier: AGPL-3.0-only */
package com.atropos.android.app.theme

import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.sp

/**
 * ATOM M1.5: High-Contrast Semantic Syntax Highlighting and Unified Typography.
 *
 * Enforces an identical semantic syntax highlighting color palette and monospaced typography
 * across Web, Android, and CLI surfaces to reduce cognitive eye strain during intensive code navigation.
 *
 * ## Research: TextMate Grammar to Tree-Sitter Highlighter Mappings
 * Researches automated grammar token alignment between Monaco TextMate scopes (`keyword.control`,
 * `storage.type`, `string.quoted`) and Tree-Sitter AST captures (`@keyword`, `@type`, `@string`)
 * to guarantee pixel-matched chromatic fidelity across mobile Android canvas and web Monaco editors.
 */
object UnifiedHighContrastSyntaxTypography {

    // Monospaced Code Text Styles
    val monoCode = TextStyle(
        fontFamily = FontFamily.Monospace,
        fontWeight = FontWeight.Normal,
        fontSize = 12.sp,
        lineHeight = 16.sp,
        letterSpacing = 0.2.sp
    )

    val monoGutter = TextStyle(
        fontFamily = FontFamily.Monospace,
        fontWeight = FontWeight.Normal,
        fontSize = 10.sp,
        lineHeight = 16.sp,
        color = Color(0xFF6B7280)
    )

    val monoToken = TextStyle(
        fontFamily = FontFamily.Monospace,
        fontWeight = FontWeight.SemiBold,
        fontSize = 11.sp,
        letterSpacing = 0.5.sp
    )

    val commandPrompt = TextStyle(
        fontFamily = FontFamily.Monospace,
        fontWeight = FontWeight.Medium,
        fontSize = 13.sp,
        lineHeight = 18.sp
    )

    val hudHeader = TextStyle(
        fontFamily = FontFamily.SansSerif,
        fontWeight = FontWeight.Bold,
        fontSize = 18.sp,
        lineHeight = 22.sp,
        letterSpacing = 0.4.sp
    )

    val hudBody = TextStyle(
        fontFamily = FontFamily.SansSerif,
        fontWeight = FontWeight.Normal,
        fontSize = 13.sp,
        lineHeight = 18.sp
    )

    val badgeLabel = TextStyle(
        fontFamily = FontFamily.Monospace,
        fontWeight = FontWeight.Bold,
        fontSize = 9.sp,
        letterSpacing = 0.6.sp
    )

    // Semantic Syntax Token High-Contrast Color Palette
    object SyntaxTokenPalette {
        val keyword = Color(0xFFE2B714)       // Vibrant Gold / Amber
        val declaration = Color(0xFF38BDF8)   // Light Cyan / Sky
        val stringLiteral = Color(0xFF4ADE80) // Emerald Green
        val numeric = Color(0xFFFB923C)       // Orange
        val comment = Color(0xFF94A3B8)       // Muted Slate Gray
        val punctuation = Color(0xFFE2E8F0)   // Bright Off-White
        val typeIdentifier = Color(0xFFA78BFA)// Lavender Violet
        val functionCall = Color(0xFF60A5FA)  // Azure Blue
        val errorUnderline = Color(0xFFF43F5E)// Crimson Rose
    }
}
