package atropos.cli.input

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class FuzzyMatcherTest {
    private val matcher = FuzzyMatcher(maximumDistance = 2)

    @Test
    fun matches_deterministic_misspellings_without_locale_rules() {
        assertTrue(matcher.matches("statuz", "/status"))
        assertTrue(matcher.matches("PROVIDERS", "/providers"))
        assertFalse(matcher.matches("status", "/self-host"))
    }

    @Test
    fun uses_utf8_byte_edit_distance_and_rejects_far_values() {
        assertEquals(1, matcher.distance("cat", "cut"))
        assertEquals(1, matcher.distance("status", "statuz"))
        assertFalse(matcher.matches("completely-different", "/status"))
    }

    @Test
    fun command_registry_uses_fuzzy_match_as_final_search_fallback() {
        assertTrue(CommandRegistry.search("/statuz").any { it.command == "/status" })
    }
}
