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
                parameters = GenerationParameters(0.2, 0.9, listOf("example"))
            )
        )
    }
}
