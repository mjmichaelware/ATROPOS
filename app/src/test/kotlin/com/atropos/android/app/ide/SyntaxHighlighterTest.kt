/* SPDX-License-Identifier: AGPL-3.0-only */
package com.atropos.android.app.ide

import androidx.compose.ui.graphics.Color
import com.atropos.android.app.theme.CyberHudColors
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Test

class SyntaxHighlighterTest {

    private val testColors = CyberHudColors()

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
