package atropos.cli.input

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class SuggestionEngineTest {
    @Test
    fun delegates_to_canonical_registry_and_preserves_ranked_matches() {
        val engine = SuggestionEngine(defaultLimit = 4)

        val suggestions = engine.suggest("/statuz")

        assertTrue(suggestions.isNotEmpty())
        assertEquals("/status", suggestions.first().command)
        assertTrue(engine.hasSuggestions("phase11"))
    }

    @Test
    fun applies_a_bounded_limit_without_reordering_registry_results() {
        val engine = SuggestionEngine(defaultLimit = 2)

        val suggestions = engine.suggest("/", limit = 2)

        assertEquals(2, suggestions.size)
        assertEquals(
            CommandRegistry.search("/").take(2).map { it.command },
            suggestions.map { it.command }
        )
    }
}
