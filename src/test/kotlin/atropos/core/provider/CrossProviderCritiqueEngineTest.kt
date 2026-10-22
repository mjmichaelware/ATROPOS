/* SPDX-License-Identifier: AGPL-3.0-only */
package atropos.core.provider

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class CrossProviderCritiqueEngineTest {

    @Test
    fun `dense packet prepares compressed context and original snippet`() {
        val engine = CrossProviderCritiqueEngine()
        val context = mapOf("fileA.kt" to "fun main() {\n    println(\"hello\")\n}\n")
        val diff = "@@ -1,3 +1,3 @@\n-    println(\"hello\")\n+    println(\"world\")\n"

        val packet = engine.prepareDensePacket(context, diff)
        assertTrue(packet.compressedContext.contains("fileA.kt"))
        assertTrue(packet.proposedDiff.contains("println(\"world\")"))
    }

    @Test
    fun `dispatch audit approves minimal patch and flags cosmetic over editing`() {
        val engine = CrossProviderCritiqueEngine()
        
        // Minimal clean patch
        val cleanDiff = "@@ -10,3 +10,3 @@\n- return x > 0\n+ return x >= 0\n"
        val cleanPacket = CritiquePacket("issue-1", "return x > 0", cleanDiff, "context")
        val cleanVerdict = engine.dispatchAudit(cleanPacket, "claude-sonnet")
        assertTrue(cleanVerdict.approved)
        assertFalse(cleanVerdict.cosmeticOverEdit)
        assertTrue(cleanVerdict.riskScore <= 0.70)

        // Patch with 20 lines of pure comments/whitespace (cosmetic over-edit)
        val cosmeticHunks = buildString {
            append("@@ -1,5 +1,25 @@\n")
            for (i in 1..20) {
                append("+ // Just another empty cosmetic comment $i\n")
            }
        }
        val noisyPacket = CritiquePacket("issue-2", "base", cosmeticHunks, "context")
        val noisyVerdict = engine.dispatchAudit(noisyPacket, "groq-llama")
        assertFalse(noisyVerdict.approved)
        assertTrue(noisyVerdict.cosmeticOverEdit)
    }

    @Test
    fun `filterStrictMinimalPatch strips trailing whitespace`() {
        val engine = CrossProviderCritiqueEngine()
        val diffWithWhitespace = "@@ -1,2 +1,2 @@   \n- old text    \n+ new text  \n"
        val filtered = engine.filterStrictMinimalPatch(diffWithWhitespace)
        assertFalse(filtered.contains("    \n"))
    }
}
