package atropos.cli.ui

import kotlin.test.Test
import kotlin.test.assertTrue

class ProviderOneLineSummaryTest {
    @Test
    fun active_provider_summary_is_one_line_and_width_bounded() {
        val rendered = ProviderOneLineSummary().render(
            "github_models",
            ProviderSummaryRenderer.ProviderHealth("groq", "healthy"),
            40
        )

        assertTrue(rendered.startsWith("● github_models"))
        assertTrue(rendered.length <= 40)
        assertTrue('\n' !in rendered)
    }
}
