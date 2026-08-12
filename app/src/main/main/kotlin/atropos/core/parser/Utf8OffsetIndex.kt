package atropos.core.parser

import java.nio.charset.StandardCharsets

/** Converts source character positions to stable UTF-8 byte positions. */
class Utf8OffsetIndex(private val source: String) {
    private val byteOffsets = IntArray(source.length + 1).also { offsets ->
        var byteOffset = 0
        offsets[0] = 0
        var index = 0
        while (index < source.length) {
            val codePoint = source.codePointAt(index)
            val width = Character.charCount(codePoint)
            byteOffset += String(Character.toChars(codePoint))
                .toByteArray(StandardCharsets.UTF_8)
                .size
            if (width == 2) offsets[index + 1] = byteOffset
            offsets[index + width] = byteOffset
            index += width
        }
    }

    fun atCharacterOffset(characterOffset: Int): Int {
        require(characterOffset in byteOffsets.indices) { "character offset outside source" }
        return byteOffsets[characterOffset]
    }
}
