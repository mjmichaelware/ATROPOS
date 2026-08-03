package atropos.core.agent

/**
 * Appends text to a buffer under a hard UTF-8 byte cap.
 *
 * A context pack is bounded in bytes because that is what a provider charges
 * for and what its request limit is expressed in. Bounding by characters would
 * be wrong by up to 4x on non-ASCII source.
 *
 * ## Cutting on a codepoint boundary
 *
 * [utf8Prefix] walks codepoints and stops before the one that would cross the
 * limit, rather than slicing the byte array at the limit. A byte-level cut
 * lands inside a multi-byte sequence and produces a replacement character — or,
 * worse, a string that is no longer valid UTF-8 and that a provider may reject
 * outright or silently mangle. The last character is dropped whole instead.
 *
 * ## The truncation marker is inside the budget, not added to it
 *
 * When a section does not fit, the marker's own bytes are subtracted from the
 * remaining space before the body is cut. A marker appended afterwards would
 * push the buffer past the cap it exists to announce — the operation that
 * reports truncation would itself be the overflow.
 *
 * The marker is only written if it genuinely fits. At a budget too small to
 * hold even the marker, the truncation is silent rather than overflowing.
 */
class Utf8BoundedBuilder(private val capBytes: Int) {

    init {
        require(capBytes >= 0) { "context cap must not be negative" }
    }

    /**
     * Appends [text], truncating if needed.
     *
     * @param alreadyTruncated when true nothing is appended; once a buffer has
     *   overflowed, later sections must not be partially written into the gap
     *   left by an earlier cut, which would interleave sections out of order.
     * @return true when the buffer is now truncated.
     */
    fun append(builder: StringBuilder, text: String, alreadyTruncated: Boolean): Boolean {
        if (alreadyTruncated) return true

        val currentBytes = byteLength(builder.toString())
        val remaining = capBytes - currentBytes
        if (remaining <= 0) return true

        if (byteLength(text) <= remaining) {
            builder.append(text)
            return false
        }

        val markerBytes = byteLength(TRUNCATION_MARKER)
        val bodyLimit = (remaining - markerBytes).coerceAtLeast(0)
        builder.append(utf8Prefix(text, bodyLimit))

        val appended = byteLength(builder.toString()) - currentBytes
        if (markerBytes <= remaining - appended) {
            builder.append(TRUNCATION_MARKER)
        }
        return true
    }

    companion object {
        const val TRUNCATION_MARKER = "\n[context truncated]\n"

        fun byteLength(text: String): Int = text.toByteArray(Charsets.UTF_8).size

        /**
         * The longest prefix of [text] that fits in [maxBytes] without splitting
         * a character.
         */
        fun utf8Prefix(text: String, maxBytes: Int): String {
            if (maxBytes <= 0) return ""
            val out = StringBuilder()
            var used = 0
            var index = 0
            while (index < text.length) {
                val codePoint = text.codePointAt(index)
                val segment = String(Character.toChars(codePoint))
                val size = byteLength(segment)
                if (used + size > maxBytes) break
                out.append(segment)
                used += size
                index += Character.charCount(codePoint)
            }
            return out.toString()
        }
    }
}
