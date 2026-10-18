/* SPDX-License-Identifier: AGPL-3.0-only */
package atropos.cli.ui

import atropos.cli.input.KeyEvent
import atropos.core.config.AtroposUnifiedConfig
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNotNull
import kotlin.test.assertTrue

/**
 * Unit test suite for CliModalStateMachine and SettingsModalRenderer.
 */
class CliModalStateMachineTest {

    private val theme = TerminalTheme()
    private val stateMachine = CliModalStateMachine(theme)

    @Test
    fun `checkAutoTrigger activates correct modal types`() {
        assertTrue(stateMachine.checkAutoTrigger("?"))
        assertEquals(CliModalStateMachine.ModalType.QUESTION_MARK, stateMachine.activeModal)

        assertTrue(stateMachine.checkAutoTrigger("/?"))
        assertEquals(CliModalStateMachine.ModalType.CATEGORY_INFO, stateMachine.activeModal)

        assertTrue(stateMachine.checkAutoTrigger("/"))
        assertEquals(CliModalStateMachine.ModalType.CATEGORIZED_TRIE, stateMachine.activeModal)

        assertTrue(stateMachine.checkAutoTrigger("+"))
        assertEquals(CliModalStateMachine.ModalType.INTERRUPT, stateMachine.activeModal)

        assertTrue(stateMachine.checkAutoTrigger("$"))
        assertEquals(CliModalStateMachine.ModalType.COST_PEEK, stateMachine.activeModal)

        assertTrue(stateMachine.checkAutoTrigger("/settings"))
        assertEquals(CliModalStateMachine.ModalType.SETTINGS, stateMachine.activeModal)

        assertTrue(stateMachine.checkAutoTrigger("/config"))
        assertEquals(CliModalStateMachine.ModalType.SETTINGS, stateMachine.activeModal)

        assertFalse(stateMachine.checkAutoTrigger("hello world"))
        assertEquals(CliModalStateMachine.ModalType.NONE, stateMachine.activeModal)
    }

    @Test
    fun `settings modal navigates and cycles options`() {
        stateMachine.checkAutoTrigger("/settings")
        assertEquals(CliModalStateMachine.ModalType.SETTINGS, stateMachine.activeModal)

        val initialLines = stateMachine.render(80)
        assertTrue(initialLines.isNotEmpty())
        assertTrue(initialLines.first().contains("╭"))
        assertTrue(initialLines.last().contains("╰"))

        // Navigate down
        val outcomeDown = stateMachine.handleKey(KeyEvent.ArrowDown, 80)
        assertEquals(CliModalStateMachine.ModalOutcome.Redraw, outcomeDown)
        assertEquals(1, stateMachine.settingsSelectedIndex)

        // Navigate up
        val outcomeUp = stateMachine.handleKey(KeyEvent.ArrowUp, 80)
        assertEquals(CliModalStateMachine.ModalOutcome.Redraw, outcomeUp)
        assertEquals(0, stateMachine.settingsSelectedIndex)

        // Cycle option right
        val outcomeRight = stateMachine.handleKey(KeyEvent.ArrowRight, 80)
        assertEquals(CliModalStateMachine.ModalOutcome.Redraw, outcomeRight)

        // Cycle option left
        val outcomeLeft = stateMachine.handleKey(KeyEvent.ArrowLeft, 80)
        assertEquals(CliModalStateMachine.ModalOutcome.Redraw, outcomeLeft)

        // Escape dismisses
        val outcomeEsc = stateMachine.handleKey(KeyEvent.Escape, 80)
        assertEquals(CliModalStateMachine.ModalOutcome.Dismiss, outcomeEsc)
        assertFalse(stateMachine.isModalActive())
    }

    @Test
    fun `question mark modal switches view tabs and expands`() {
        stateMachine.checkAutoTrigger("?")
        assertEquals(CliModalStateMachine.ModalType.QUESTION_MARK, stateMachine.activeModal)
        assertEquals(QuestionMarkModalRenderer.ActiveView.SHORTCUTS, stateMachine.questionActiveView)

        // Tab switches to commands
        val outcomeTab = stateMachine.handleKey(KeyEvent.Tab, 80)
        assertEquals(CliModalStateMachine.ModalOutcome.Redraw, outcomeTab)
        assertEquals(QuestionMarkModalRenderer.ActiveView.COMMANDS, stateMachine.questionActiveView)

        // ArrowRight on commands expands leaf doc
        val outcomeRight = stateMachine.handleKey(KeyEvent.ArrowRight, 80)
        assertEquals(CliModalStateMachine.ModalOutcome.Redraw, outcomeRight)
        assertTrue(stateMachine.questionIsExpanded)

        // Escape closes
        val outcomeDismiss = stateMachine.handleKey(KeyEvent.Escape, 80)
        assertEquals(CliModalStateMachine.ModalOutcome.Dismiss, outcomeDismiss)
        assertFalse(stateMachine.isModalActive())
    }

    @Test
    fun `settings modal renderer formats responsive box`() {
        val renderer = SettingsModalRenderer(theme)
        val config = AtroposUnifiedConfig()

        val widths = listOf(60, 80, 120)
        for (w in widths) {
            val lines = renderer.render(config, selectedIndex = 0, width = w)
            assertTrue(lines.size >= 8)
            assertTrue(lines.first().contains("╭"))
            assertTrue(lines.last().contains("╰"))
            assertTrue(lines.any { it.contains("Agent_Mode") })
        }
    }
}
