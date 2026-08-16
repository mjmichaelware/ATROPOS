/* SPDX-License-Identifier: AGPL-3.0-only */
package atropos.cli.input

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

/**
 * Hiding a pasted document without losing it.
 *
 * The property that matters is round-tripping: whatever the composer shows, the
 * committed line must carry the bytes the operator pasted. A summary that
 * expanded to less than it stood for would be worse than no summary at all.
 */
class CollapsedPasteRegistryTest {

    private fun document(words: Int): String = (1..words).joinToString(" ") { "word$it" }

    @Test
    fun a_short_paste_is_left_alone() {
        val registry = CollapsedPasteRegistry()

        // Typing and small pastes must behave exactly as before; a mechanism
        // that triggered on ordinary input would be a regression, not a feature.
        assertFalse(registry.shouldCollapse("just a sentence"))
        assertFalse(registry.shouldCollapse(""))
    }

    @Test
    fun a_long_paste_collapses_by_word_count_even_on_one_line() {
        val registry = CollapsedPasteRegistry()

        assertTrue(registry.shouldCollapse(document(300)))
    }

    @Test
    fun a_tall_paste_collapses_by_line_count_even_when_short() {
        val registry = CollapsedPasteRegistry()

        assertTrue(registry.shouldCollapse((1..12).joinToString("\n") { "line $it" }))
    }

    @Test
    fun the_placeholder_says_how_much_it_stands_for() {
        val registry = CollapsedPasteRegistry()
        val text = (1..10).joinToString("\n") { "line $it here" }

        val token = registry.collapse(text)

        assertTrue(token.contains("10 lines"), token)
        assertTrue(token.contains("30 words"), token)
    }

    @Test
    fun expanding_restores_the_exact_bytes() {
        val registry = CollapsedPasteRegistry()
        val text = document(400)
        val token = registry.collapse(text)

        assertEquals("summarise $text please", registry.expand("summarise $token please"))
    }

    @Test
    fun two_pastes_expand_independently_and_in_place() {
        val registry = CollapsedPasteRegistry()
        val first = registry.collapse(document(300))
        val second = registry.collapse((1..20).joinToString("\n") { "row $it" })

        val expanded = registry.expand("compare $first with $second")

        assertTrue(expanded.startsWith("compare word1 "))
        assertTrue(expanded.contains(" with row 1\n"))
        assertFalse(expanded.contains("[#"), "no placeholder may survive expansion")
    }

    @Test
    fun deleting_the_placeholder_deletes_the_paste() {
        val registry = CollapsedPasteRegistry()
        registry.collapse(document(300))

        // The operator removed the visible summary. Re-inserting the document
        // behind their back would be worse than dropping it: they would send a
        // prompt containing something they had explicitly taken out.
        assertEquals("never mind", registry.expand("never mind"))
    }

    @Test
    fun a_paste_beyond_the_retention_bound_is_inserted_whole_rather_than_lost() {
        val registry = CollapsedPasteRegistry(maxRetainedChars = 64)

        // Degrading to the old behaviour is acceptable; dropping the text is
        // not, so the answer is "do not collapse" rather than "refuse".
        assertFalse(registry.shouldCollapse(document(500)))
    }

    @Test
    fun clearing_forgets_everything_so_no_placeholder_outlives_its_line() {
        val registry = CollapsedPasteRegistry()
        val token = registry.collapse(document(300))

        registry.clear()

        assertEquals(token, registry.expand(token))
        assertFalse(registry.hasCollapsedText(token))
    }
}
