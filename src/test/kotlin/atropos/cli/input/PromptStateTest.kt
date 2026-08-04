package atropos.cli.input

import kotlin.test.Test
import kotlin.test.assertEquals

class PromptStateTest {
    @Test
    fun arrow_navigation_cycles_bare_command_prefix_suggestions() {
        val prompt = PromptState()
        "self-host".forEach { char ->
            prompt.apply(KeyEvent.Printable(char.toString()))
        }

        assertEquals(0, prompt.suggestionSelection())

        assertEquals(PromptEffect.Redraw, prompt.apply(KeyEvent.ArrowDown))
        assertEquals(1, prompt.suggestionSelection())

        assertEquals(PromptEffect.Redraw, prompt.apply(KeyEvent.ArrowUp))
        assertEquals(0, prompt.suggestionSelection())
    }
}
