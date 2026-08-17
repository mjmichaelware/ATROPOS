/* SPDX-License-Identifier: AGPL-3.0-only */
package atropos.core.ingest

import java.io.ByteArrayOutputStream
import java.util.zip.Inflater

/**
 * A PDF read as text, using nothing but the JDK.
 *
 * Same defect as `.docx`: `@spec.pdf` passed territory, passed the ceiling,
 * printed "attached", and handed the model a sentence saying a file existed.
 *
 * ## What this reads, and what it does not
 *
 * A PDF's text lives in content streams as `Tj` and `TJ` operators. Those
 * streams are usually `FlateDecode`d, which is `java.util.zip.Inflater`, and
 * the operands are PDF strings, which are a small grammar. That much is
 * tractable and deterministic.
 *
 * What is *not* tractable without a font stack is a PDF that encodes its glyphs
 * through a custom `/Differences` map or an embedded CID font — there the bytes
 * in the stream are glyph indices, not characters, and decoding them as text
 * yields confident-looking rubbish. So the result is checked before it is
 * returned: a run that comes back mostly non-alphabetic is reported as
 * unreadable rather than delivered. A refusal the operator can see beats a
 * document the model silently misreads, and this is the one place where
 * guessing would poison everything downstream — the atomiser would happily
 * turn mojibake into requirements.
 *
 * No dependency, for the reasons in [DocxTextExtractor]: PDFBox is ~10 MB into
 * a jar that runs on a phone, and a library that can change its extraction
 * between versions ends determinism.
 */
object PdfTextExtractor {

    /** @return the document's text, or null when it cannot be read honestly. */
    fun extract(bytes: ByteArray): String? {
        if (!bytes.decodeToString(0, minOf(bytes.size, 5)).startsWith("%PDF-")) return null

        val text = buildString {
            streams(bytes).forEach { stream ->
                val page = textOf(stream)
                if (page.isNotBlank()) {
                    append(page.trimEnd())
                    append('\n')
                }
            }
        }.trim()

        return text.takeIf(::readable)
    }

    /**
     * Whether the extracted run looks like language rather than glyph indices.
     *
     * A custom-encoded PDF decodes to bytes that are perfectly valid characters
     * and mean nothing. The ratio is the cheapest signal that separates the two
     * and it is checked here rather than left to the caller, because by the
     * time text reaches a prompt nobody is looking at it any more.
     */
    private fun readable(text: String): Boolean {
        if (text.length < MINIMUM_CHARACTERS) return false
        val letters = text.count { it.isLetter() || it.isWhitespace() || it.isDigit() }
        return letters.toDouble() / text.length >= MINIMUM_LETTER_RATIO
    }

    /**
     * Every content stream in the file, decompressed where it was deflated.
     *
     * The cross-reference table is deliberately not parsed. It is the correct
     * way in and it is also the part of PDF most often malformed by generators
     * — a linearised or incrementally-updated file has several, and a damaged
     * one has none that agree. Scanning for `stream` markers finds the same
     * bytes without a table that has to be right first.
     */
    private fun streams(bytes: ByteArray): List<String> {
        val results = mutableListOf<String>()
        var index = 0

        while (index < bytes.size && results.size < MAXIMUM_STREAMS) {
            val start = indexOf(bytes, STREAM, index)
            if (start < 0) break

            var from = start + STREAM.size
            // `stream` is followed by CRLF or LF, and the payload starts after.
            if (from < bytes.size && bytes[from] == '\r'.code.toByte()) from++
            if (from < bytes.size && bytes[from] == '\n'.code.toByte()) from++

            val end = indexOf(bytes, ENDSTREAM, from)
            if (end < 0) break

            val payload = bytes.copyOfRange(from, end)
            val header = bytes.decodeToString(
                (start - HEADER_LOOKBEHIND).coerceAtLeast(0),
                start
            )

            val decoded =
                if (header.contains("FlateDecode")) inflate(payload) else payload
            decoded?.let { results += it.decodeToString() }

            index = end + ENDSTREAM.size
        }

        return results
    }

    private fun inflate(payload: ByteArray): ByteArray? = runCatching {
        val inflater = Inflater()
        inflater.setInput(payload)
        val out = ByteArrayOutputStream(payload.size * 4)
        val buffer = ByteArray(16 * 1024)
        try {
            while (!inflater.finished()) {
                val produced = inflater.inflate(buffer)
                if (produced == 0 && (inflater.needsInput() || inflater.needsDictionary())) break
                out.write(buffer, 0, produced)
                if (out.size() > MAXIMUM_INFLATED_BYTES) break
            }
        } finally {
            inflater.end()
        }
        out.toByteArray().takeIf { it.isNotEmpty() }
    }.getOrNull()

    /**
     * The text a content stream shows, from its `Tj` and `TJ` operators.
     *
     * `Td`, `TD`, `T*` and `'` move to a new line, so they end one. Kerning
     * numbers inside a `TJ` array are dropped — they are horizontal
     * adjustments, and a large negative one is a space the encoder chose not to
     * write, which is why the space heuristic exists.
     */
    private fun textOf(stream: String): String {
        val out = StringBuilder()
        var index = 0

        while (index < stream.length) {
            when (stream[index]) {
                '(' -> {
                    val (literal, next) = readLiteral(stream, index)
                    out.append(literal)
                    index = next
                }
                '<' -> {
                    // A hex string, `<48656C6C6F>`. `<<` opens a dictionary and
                    // is not one.
                    if (stream.getOrNull(index + 1) == '<') {
                        index++
                    } else {
                        val close = stream.indexOf('>', index)
                        if (close < 0) return out.toString()
                        out.append(readHex(stream.substring(index + 1, close)))
                        index = close + 1
                    }
                }
                else -> {
                    val operator = OPERATOR_AT.matchAt(stream, index)
                    if (operator != null) {
                        out.append('\n')
                        index += operator.value.length
                    } else {
                        if (isKerningSpace(stream, index)) out.append(' ')
                        index++
                    }
                }
            }
        }

        return out.toString().replace(BLANK_RUN, "\n")
    }

    /**
     * Whether the number ending at [index] is a kerning gap wide enough to be
     * a word break.
     *
     * `[(Pro)-278(vider)]` has no space in it; the `-278` is the space. Below
     * the threshold the number is ordinary letter-spacing and inserting a
     * space would break words apart instead of joining them.
     */
    private fun isKerningSpace(stream: String, index: Int): Boolean {
        if (stream[index] != '-') return false
        val digits = stream.drop(index + 1).takeWhile(Char::isDigit)
        return digits.length >= 3 && (digits.toIntOrNull() ?: 0) >= KERNING_SPACE_UNITS
    }

    /** A `(...)` string, honouring escapes and nesting. */
    private fun readLiteral(stream: String, open: Int): Pair<String, Int> {
        val out = StringBuilder()
        var index = open + 1
        var depth = 1

        while (index < stream.length) {
            when (val character = stream[index]) {
                '\\' -> {
                    val escaped = stream.getOrNull(index + 1)
                    index += 2
                    when (escaped) {
                        'n' -> out.append('\n')
                        'r' -> out.append('\r')
                        't' -> out.append('\t')
                        'b', 'f' -> Unit
                        '(' -> out.append('(')
                        ')' -> out.append(')')
                        '\\' -> out.append('\\')
                        '\n' -> Unit
                        in '0'..'7' -> {
                            // An octal escape is up to three digits.
                            val octal = StringBuilder().append(escaped)
                            while (octal.length < 3 && stream.getOrNull(index) in '0'..'7') {
                                octal.append(stream[index])
                                index++
                            }
                            octal.toString().toIntOrNull(8)
                                ?.takeIf { it in 1..0x10FFFF }
                                ?.let { out.appendCodePoint(it) }
                        }
                        null -> return out.toString() to stream.length
                        else -> out.append(escaped)
                    }
                }
                '(' -> { depth++; out.append(character); index++ }
                ')' -> {
                    depth--
                    if (depth == 0) return out.toString() to index + 1
                    out.append(character)
                    index++
                }
                else -> { out.append(character); index++ }
            }
        }

        return out.toString() to stream.length
    }

    private fun readHex(value: String): String {
        val digits = value.filter { it.isDigit() || it in 'a'..'f' || it in 'A'..'F' }
        val padded = if (digits.length % 2 == 0) digits else digits + "0"
        return buildString {
            padded.chunked(2).forEach { pair ->
                pair.toIntOrNull(16)?.takeIf { it != 0 }?.let { append(it.toChar()) }
            }
        }
    }

    private fun indexOf(haystack: ByteArray, needle: ByteArray, from: Int): Int {
        outer@ for (start in from..haystack.size - needle.size) {
            for (offset in needle.indices) {
                if (haystack[start + offset] != needle[offset]) continue@outer
            }
            return start
        }
        return -1
    }

    private val STREAM = "stream".toByteArray()
    private val ENDSTREAM = "endstream".toByteArray()

    /** `Td`, `TD`, `T*`, `'` and `"` all begin a new line of text. */
    private val OPERATOR_AT = Regex("""(?:Td|TD|T\*|ET|'|")(?=[\s\[(<]|$)""")
    private val BLANK_RUN = Regex("\n{2,}")

    private const val HEADER_LOOKBEHIND = 512
    private const val MAXIMUM_STREAMS = 4_000
    private const val MAXIMUM_INFLATED_BYTES = 64 * 1024 * 1024
    private const val MINIMUM_CHARACTERS = 16
    private const val MINIMUM_LETTER_RATIO = 0.6
    private const val KERNING_SPACE_UNITS = 120
}
