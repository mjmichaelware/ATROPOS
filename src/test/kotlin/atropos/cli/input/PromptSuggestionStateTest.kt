/* SPDX-License-Identifier: AGPL-3.0-only */
package atropos.cli.input

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class PromptSuggestionStateTest {

    private fun alwaysMatching() = PromptSuggestionState { true }

    @Test
    fun `a single leading token opens the palette`() {
        assertTrue(alwaysMatching().isActive("/sta"))
    }

    @Test
    fun `an empty line has nothing to suggest`() {
        assertFalse(alwaysMatching().isActive(""))
        assertFalse(alwaysMatching().isActive("   "))
    }

    @Test
    fun `a line with arguments no longer suggests`() {
        assertFalse(
            alwaysMatching().isActive("/agent run"),
            "once a command has arguments the operator has already chosen it"
        )
    }

    @Test
    fun `leading whitespace does not suppress the palette`() {
        assertTrue(alwaysMatching().isActive("  /sta"))
    }

    @Test
    fun `no registry match means no palette`() {
        assertFalse(PromptSuggestionState { false }.isActive("/nonsense"))
    }

    @Test
    fun `escape dismisses until the line changes again`() {
        val state = alwaysMatching()
        state.dismiss()
        assertFalse(state.isActive("/sta"))

        state.onTextChanged()
        assertTrue(state.isActive("/sta"), "typing another character reopens the palette")
    }

    @Test
    fun `selection moves down and back up without going negative`() {
        val state = alwaysMatching()
        state.moveSelectionDown()
        state.moveSelectionDown()
        assertEquals(2, state.selectionFor("/s"))

        state.moveSelectionUp()
        assertEquals(1, state.selectionFor("/s"))

        repeat(5) { state.moveSelectionUp() }
        assertEquals(0, state.selectionFor("/s"))
    }

    @Test
    fun `selection reads as zero when the palette is closed`() {
        val state = alwaysMatching()
        state.moveSelectionDown()
        assertEquals(0, state.selectionFor("/agent run"))
    }

    @Test
    fun `clamping pulls the highlight back into a shrunken list`() {
        val state = alwaysMatching()
        repeat(9) { state.moveSelectionDown() }
        state.clampSelection(2)
        assertEquals(2, state.selectionFor("/s"))
    }

    @Test
    fun `clamping to an empty list selects the first row`() {
        val state = alwaysMatching()
        state.moveSelectionDown()
        state.clampSelection(-1)
        assertEquals(0, state.selectionFor("/s"))
    }

    @Test
    fun `an edit resets the highlight to the top`() {
        val state = alwaysMatching()
        state.moveSelectionDown()
        state.onTextChanged()
        assertEquals(0, state.selectionFor("/s"))
    }
}
