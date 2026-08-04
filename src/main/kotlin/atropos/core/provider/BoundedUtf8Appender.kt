package atropos.core.provider

import java.nio.charset.StandardCharsets

/** Appends UTF-8 text without exceeding a byte budget. */
internal object BoundedUtf8Appender {
    private const val TRUNCATION_MARKER = "\n[source context truncated]\n"

    fun append(
        builder: StringBuilder,
        text: String,
        maxBytes: Int,
        onTruncated: () -> Unit
    ) {
        val currentBytes = builder.byteCount()
        val remaining = maxBytes - currentBytes
        if (remaining <= 0) {
            onTruncated()
            return
        }

        val bytes = text.toByteArray(StandardCharsets.UTF_8)
        if (bytes.size <= remaining) {
            builder.append(text)
            return
        }

        val markerBytes = TRUNCATION_MARKER.toByteArray(StandardCharsets.UTF_8).size
        val bodyLimit = (remaining - markerBytes).coerceAtLeast(0)
        builder.append(text.utf8Prefix(bodyLimit))
        if (markerBytes <= maxBytes - builder.byteCount()) {
            builder.append(TRUNCATION_MARKER)
        }
        onTruncated()
    }

    /**
     * UTF-8 size of what has been appended so far.
     *
     * Exposed because callers that bound a pack by byte budget need the same
     * accounting this object uses internally. Recomputing it at the call site
     * would put a second definition of "how big is this so far" in the tree, and
     * the two would drift the moment one of them started counting chars.
     */
    fun utf8Size(builder: StringBuilder): Int = builder.byteCount()

    private fun StringBuilder.byteCount(): Int =
        toString().toByteArray(StandardCharsets.UTF_8).size

    private fun String.utf8Prefix(maxBytes: Int): String {
        if (maxBytes <= 0) return ""
        val out = StringBuilder()
        var used = 0
        var index = 0
        while (index < length) {
            val codePoint = codePointAt(index)
            val segment = String(Character.toChars(codePoint))
            val size = segment.toByteArray(StandardCharsets.UTF_8).size
            if (used + size > maxBytes) break
            out.append(segment)
            used += size
            index += Character.charCount(codePoint)
        }
        return out.toString()
    }
}
