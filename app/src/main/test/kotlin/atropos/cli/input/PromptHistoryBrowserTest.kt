/* SPDX-License-Identifier: AGPL-3.0-only */
package atropos.cli.input

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertIs
import kotlin.test.assertTrue

class PromptHistoryBrowserTest {

    private fun ringWith(vararg entries: String): PromptHistoryRing =
        PromptHistoryRing().apply {
            entries.forEach { record(PromptHistoryLane.PROMPT, it) }
        }

    @Test
    fun `up walks backwards from the newest entry`() {
        val browser = PromptHistoryBrowser(ringWith("first", "second", "third"))

        assertEquals("third", recalled(browser.up(PromptHistoryLane.PROMPT, "")))
        assertEquals("second", recalled(browser.up(PromptHistoryLane.PROMPT, "")))
        assertEquals("first", recalled(browser.up(PromptHistoryLane.PROMPT, "")))
    }

    @Test
    fun `up stops at the oldest entry rather than falling off`() {
        val browser = PromptHistoryBrowser(ringWith("only"))

        assertEquals("only", recalled(browser.up(PromptHistoryLane.PROMPT, "")))
        assertEquals("only", recalled(browser.up(PromptHistoryLane.PROMPT, "")))
    }

    @Test
    fun `up on an empty lane does nothing`() {
        val browser = PromptHistoryBrowser(PromptHistoryRing())
        assertIs<PromptHistoryMove.None>(browser.up(PromptHistoryLane.PROMPT, "draft"))
        assertFalse(browser.isAttached)
    }

    @Test
    fun `stepping back down restores the draft that was being typed`() {
        val browser = PromptHistoryBrowser(ringWith("remembered"))

        browser.up(PromptHistoryLane.PROMPT, "half typed")
        val move = browser.down()

        val restored = assertIs<PromptHistoryMove.RestoredDraft>(move)
        assertEquals("half typed", restored.text, "recall must not destroy the operator's line")
        assertFalse(browser.isAttached)
    }

    @Test
    fun `down past the draft does nothing more`() {
        val browser = PromptHistoryBrowser(ringWith("remembered"))
        browser.up(PromptHistoryLane.PROMPT, "draft")
        browser.down()
        assertIs<PromptHistoryMove.None>(browser.down())
    }

    @Test
    fun `down without ever going up does nothing`() {
        val browser = PromptHistoryBrowser(ringWith("remembered"))
        assertIs<PromptHistoryMove.None>(browser.down())
    }

    @Test
    fun `switching lanes mid-traversal re-attaches to the new lane`() {
        val ring = PromptHistoryRing().apply {
            record(PromptHistoryLane.PROMPT, "prose")
            record(PromptHistoryLane.SLASH, "/status")
        }
        val browser = PromptHistoryBrowser(ring)

        browser.up(PromptHistoryLane.PROMPT, "")
        assertEquals(PromptHistoryLane.PROMPT, browser.lane)

        assertEquals("/status", recalled(browser.up(PromptHistoryLane.SLASH, "carried")))
        assertEquals(PromptHistoryLane.SLASH, browser.lane)

        val restored = assertIs<PromptHistoryMove.RestoredDraft>(browser.down())
        assertEquals("carried", restored.text)
    }

    @Test
    fun `detach abandons traversal so down no longer overwrites the line`() {
        val browser = PromptHistoryBrowser(ringWith("remembered"))
        browser.up(PromptHistoryLane.PROMPT, "draft")
        assertTrue(browser.isAttached)

        browser.detach()

        assertFalse(browser.isAttached)
        assertIs<PromptHistoryMove.None>(browser.down())
    }

    @Test
    fun `search jumps to the newest match and leaves traversal detached`() {
        val browser = PromptHistoryBrowser(ringWith("build one", "test", "build two"))

        assertEquals("build two", recalled(browser.search(PromptHistoryLane.PROMPT, "build")))
        assertFalse(browser.isAttached, "a search lands on a line to edit, not a position to walk")
    }

    @Test
    fun `search that matches nothing leaves the line alone`() {
        val browser = PromptHistoryBrowser(ringWith("build"))
        assertIs<PromptHistoryMove.None>(browser.search(PromptHistoryLane.PROMPT, "absent"))
    }

    private fun recalled(move: PromptHistoryMove): String =
        assertIs<PromptHistoryMove.Recalled>(move).text
}
