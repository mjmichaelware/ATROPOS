/* SPDX-License-Identifier: AGPL-3.0-only */
package atropos.core

import kotlin.test.Test
import kotlin.test.assertEquals

class GenerationParametersTest {
    @Test
    fun `base provider receives tuned parameters through the canonical overload`() {
        val provider = object : BaseHttpProvider() {
            override val name = "fixture"

            override fun complete(prompt: String, context: String): String {
                val parameters = currentGenerationParameters()
                return "${parameters.temperature}:${parameters.topP}:${parameters.fewShotExamples.single()}"
            }
        }

        assertEquals(
            "0.2:0.9:example",
            provider.complete(
                "task",
                // Named-only would not compile: the tuned overload takes context
                // positionally and has no default for it, which is what keeps a
                // caller from tuning parameters while silently dropping context.
                context = "",
                parameters = GenerationParameters(0.2, 0.9, listOf("example"))
            )
        )
    }
}
