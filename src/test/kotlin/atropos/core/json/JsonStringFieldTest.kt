/* SPDX-License-Identifier: AGPL-3.0-only */
package atropos.core.json

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull
import kotlin.test.assertTrue

/**
 * The regression these cover is a `StackOverflowError`, which is not a failed
 * assertion — it is the JVM giving up. Nine files carried a pattern whose match
 * recursed once per character, and it reached production as
 * `st_memory=SKIPPED_SOFT_FAIL:stackoverflowerror` on a live run.
 *
 * Sizes here are chosen to exceed the default stack. A test that only read
 * short fields would have passed against the broken implementation, which is
 * how the bug survived a green suite.
 */
class JsonStringFieldTest {

    /** Long enough to overflow the recursive matcher this replaced. */
    private val huge = "x".repeat(200_000)

    @Test
    fun `a field far larger than the stack is read, not thrown on`() {
        val json = """{"body":"$huge","kind":"NOTE"}"""

        assertEquals(huge, JsonStringField.value(json, "body"))
        assertEquals("NOTE", JsonStringField.value(json, "kind"))
    }

    @Test
    fun `a huge field full of escapes is still read`() {
        val escaped = "a\\\"b\\\\c".repeat(20_000)
        val json = """{"body":"$escaped"}"""

        assertEquals(escaped, JsonStringField.value(json, "body"))
    }

    @Test
    fun `escaped quotes do not end the string`() {
        val json = """{"title":"she said \"no\" firmly","kind":"NOTE"}"""

        assertEquals("""she said \"no\" firmly""", JsonStringField.value(json, "title"))
        assertEquals("NOTE", JsonStringField.value(json, "kind"), "the next field must still be findable")
    }

    @Test
    fun `an escaped backslash does not swallow the closing quote`() {
        val json = """{"path":"C:\\","kind":"NOTE"}"""

        assertEquals("""C:\\""", JsonStringField.value(json, "path"))
        assertEquals("NOTE", JsonStringField.value(json, "kind"))
    }

    @Test
    fun `a missing field is null rather than an error`() {
        assertNull(JsonStringField.value("""{"a":"1"}""", "b"))
    }

    @Test
    fun `an unterminated string is absent rather than the rest of the document`() {
        assertNull(JsonStringField.value("""{"body":"never closed""", "body"))
    }

    @Test
    fun `a trailing backslash cannot run past the end`() {
        assertNull(JsonStringField.value("""{"body":"oops\""", "body"))
    }

    @Test
    fun `whitespace around the colon is tolerated`() {
        assertEquals("v", JsonStringField.value("""{"k"  :   "v"}""", "k"))
    }

    /**
     * The name appears as a value before it appears as a key. The scan must
     * keep looking rather than concluding the field is absent — the regex it
     * replaces behaved this way and callers depend on it.
     */
    @Test
    fun `a name occurring inside an earlier value does not mask the real key`() {
        val json = """{"note":"see \"kind\" below","kind":"REAL"}"""

        assertEquals("REAL", JsonStringField.value(json, "kind"))
    }

    @Test
    fun `a non-string value for the name is skipped`() {
        assertEquals("yes", JsonStringField.value("""{"k":12,"other":1,"k":"yes"}""", "k"))
    }

    @Test
    fun `array bodies survive a bracket inside an element`() {
        val json = """{"command":["echo","a]b","c"],"n":1}"""

        val body = JsonStringField.arrayBody(json, "command")
        assertEquals(listOf("echo", "a]b", "c"), JsonStringField.values(body!!))
    }

    @Test
    fun `nested arrays close at the right bracket`() {
        val json = """{"a":[["x"],["y"]],"b":1}"""

        assertEquals("""["x"],["y"]""", JsonStringField.arrayBody(json, "a"))
    }

    @Test
    fun `an absent array is null, an empty one is empty`() {
        assertNull(JsonStringField.arrayBody("""{"a":1}""", "tags"))
        assertEquals("", JsonStringField.arrayBody("""{"tags":[]}""", "tags"))
        assertEquals(emptyList(), JsonStringField.values(""))
    }

    @Test
    fun `values reads every quoted run in order`() {
        assertEquals(listOf("a", "b", "c"), JsonStringField.values(""""a", "b" , "c""""))
    }

    @Test
    fun `a huge array does not overflow either`() {
        val body = (1..20_000).joinToString(",") { "\"tag$it\"" }

        assertEquals(20_000, JsonStringField.values(body).size)
    }

    @Test
    fun `reading is linear enough to finish promptly on a large document`() {
        val json = """{"body":"$huge"}"""
        val started = System.nanoTime()

        repeat(20) { JsonStringField.value(json, "body") }

        val elapsedMs = (System.nanoTime() - started) / 1_000_000
        assertTrue(elapsedMs < 5_000, "20 reads of 200k chars took ${elapsedMs}ms")
    }
}
