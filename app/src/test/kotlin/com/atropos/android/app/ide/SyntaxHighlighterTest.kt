/* SPDX-License-Identifier: AGPL-3.0-only */
package com.atropos.android.app.ide

import androidx.compose.ui.graphics.Color
import com.atropos.android.app.theme.CyberHudColors
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Test

class SyntaxHighlighterTest {

    private val testColors = CyberHudColors(
        surface = Color(0xFF0F172A),
        surfaceRaised = Color(0xFF1E293B),
        hudBorder = Color(0xFF06B6D4),
        accentCyan = Color(0xFF00E5FF),
        accentPurple = Color(0xFFB388FF),
        accentEmerald = Color(0xFF00E676),
        accentAmber = Color(0xFFFFD600),
        textPrimary = Color(0xFFFFFFFF),
        textSecondary = Color(0xFF94A3B8),
        textMuted = Color(0xFF64748B),
        terminalBg = Color(0xFF020617)
    )

    @Test
    fun testHighlightKotlinCodeProducesValidAnnotatedString() {
        val code = """
            package com.atropos.sovereign
            
            // Comment line
            class Engine(val id: String) {
                fun start(): Boolean {
                    val status = "active"
                    return true
                }
            }
        """.trimIndent()

        val highlighted = SyntaxHighlighter.highlightCode(code, "kotlin", testColors)
        assertNotNull(highlighted)
        assertEquals(code, highlighted.text)
        assertTrue(highlighted.spanStyles.isNotEmpty())
    }

    @Test
    fun testHighlightTypeScriptCodeHandlesKeywordsAndStrings() {
        val tsCode = """
            import { useState } from 'react';
            
            export const useSovereign = () => {
                const [active, setActive] = useState(true);
                return { active };
            };
        """.trimIndent()

        val highlighted = SyntaxHighlighter.highlightCode(tsCode, "typescript", testColors)
        assertNotNull(highlighted)
        assertEquals(tsCode, highlighted.text)
        assertTrue(highlighted.spanStyles.isNotEmpty())
    }

    @Test
    fun testHighlightCommentLineAppliesMutedColor() {
        val comment = "// Sovereign Merkle Audit Proof"
        val highlighted = SyntaxHighlighter.highlightCode(comment, "kotlin", testColors)

        assertEquals(comment, highlighted.text)
        assertEquals(1, highlighted.spanStyles.size)
        assertEquals(testColors.textMuted, highlighted.spanStyles[0].item.color)
    }

    @Test
    fun testHighlightEmptyCodeProducesEmptyAnnotatedString() {
        val highlighted = SyntaxHighlighter.highlightCode("", "kotlin", testColors)
        assertEquals("", highlighted.text)
    }
}
