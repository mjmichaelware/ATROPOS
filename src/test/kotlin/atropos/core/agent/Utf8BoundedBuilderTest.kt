package atropos.core.agent

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class Utf8BoundedBuilderTest {

    private fun bytes(text: String) = Utf8BoundedBuilder.byteLength(text)

    @Test
    fun `text within the cap is appended whole`() {
        val builder = StringBuilder()
        val truncated = Utf8BoundedBuilder(1024).append(builder, "hello", alreadyTruncated = false)
        assertFalse(truncated)
        assertEquals("hello", builder.toString())
    }

    @Test
    fun `an already-truncated buffer accepts nothing more`() {
        val builder = StringBuilder("existing")
        val truncated = Utf8BoundedBuilder(1024).append(builder, "more", alreadyTruncated = true)
        assertTrue(truncated)
        assertEquals("existing", builder.toString(), "later sections must not fill an earlier cut's gap")
    }

    @Test
    fun `the total never exceeds the cap, marker included`() {
        val cap = 64
        val builder = StringBuilder()
        val truncated = Utf8BoundedBuilder(cap).append(builder, "x".repeat(500), alreadyTruncated = false)
        assertTrue(truncated)
        assertTrue(
            bytes(builder.toString()) <= cap,
            "the marker announcing truncation must not itself overflow the cap"
        )
    }

    @Test
    fun `truncated output carries the marker when it fits`() {
        val builder = StringBuilder()
        Utf8BoundedBuilder(128).append(builder, "y".repeat(500), alreadyTruncated = false)
        assertTrue(builder.toString().contains("[context truncated]"))
    }

    @Test
    fun `a cap too small for the marker truncates silently instead of overflowing`() {
        val cap = 8
        val builder = StringBuilder()
        Utf8BoundedBuilder(cap).append(builder, "z".repeat(200), alreadyTruncated = false)
        assertTrue(bytes(builder.toString()) <= cap)
    }

    @Test
    fun `a full buffer reports truncation without appending`() {
        val builder = StringBuilder("0123456789")
        val truncated = Utf8BoundedBuilder(4).append(builder, "more", alreadyTruncated = false)
        assertTrue(truncated)
        assertEquals("0123456789", builder.toString())
    }

    @Test
    fun `a multi-byte character is dropped whole rather than split`() {
        // Each rocket is 4 UTF-8 bytes; a cap of 6 fits exactly one.
        val prefix = Utf8BoundedBuilder.utf8Prefix("🚀🚀", 6)
        assertEquals("🚀", prefix)
        assertEquals(4, bytes(prefix))
    }

    @Test
    fun `a prefix too small for even one character is empty`() {
        assertEquals("", Utf8BoundedBuilder.utf8Prefix("🚀", 3))
        assertEquals("", Utf8BoundedBuilder.utf8Prefix("abc", 0))
    }

    @Test
    fun `a truncated multi-byte section stays valid UTF-8`() {
        val builder = StringBuilder()
        Utf8BoundedBuilder(40).append(builder, "🚀".repeat(100), alreadyTruncated = false)
        val rendered = builder.toString()
        assertFalse(
            rendered.contains('�'),
            "a byte-level cut would land inside a surrogate pair and yield a replacement char"
        )
        assertEquals(rendered, String(rendered.toByteArray(Charsets.UTF_8), Charsets.UTF_8))
    }

    @Test
    fun `byte length counts UTF-8 bytes rather than characters`() {
        assertEquals(4, bytes("🚀"))
        assertEquals(2, bytes("é"))
        assertEquals(3, bytes("abc"))
    }
}
