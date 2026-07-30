/* SPDX-License-Identifier: AGPL-3.0-only */
package atropos.core.verification

/**
 * Removes lexical regions that cannot represent executable concerns while
 * preserving newlines and character positions for deterministic diagnostics.
 */
class ArchitectureSourceMasker {
    fun mask(source: String): String {
        val output = StringBuilder(source.length)
        var index = 0
        var state = State.CODE

        while (index < source.length) {
            val current = source[index]
            val next = source.getOrNull(index + 1)

            when (state) {
                State.CODE -> when {
                    current == '/' && next == '/' -> {
                        output.append("  ")
                        index += 2
                        state = State.LINE_COMMENT
                    }
                    current == '/' && next == '*' -> {
                        output.append("  ")
                        index += 2
                        state = State.BLOCK_COMMENT
                    }
                    current == '"' && source.getOrNull(index + 1) == '"' && source.getOrNull(index + 2) == '"' -> {
                        output.append("   ")
                        index += 3
                        state = State.TRIPLE_STRING
                    }
                    current == '"' -> {
                        output.append(' ')
                        index++
                        state = State.STRING
                    }
                    current == '\'' -> {
                        output.append(' ')
                        index++
                        state = State.CHARACTER
                    }
                    else -> {
                        output.append(current)
                        index++
                    }
                }

                State.LINE_COMMENT -> {
                    output.append(if (current == '\n' || current == '\r') current else ' ')
                    index++
                    if (current == '\n' || current == '\r') state = State.CODE
                }

                State.BLOCK_COMMENT -> {
                    if (current == '*' && next == '/') {
                        output.append("  ")
                        index += 2
                        state = State.CODE
                    } else {
                        output.append(if (current == '\n' || current == '\r') current else ' ')
                        index++
                    }
                }

                State.STRING, State.CHARACTER -> {
                    output.append(if (current == '\n' || current == '\r') current else ' ')
                    index++
                    when {
                        current == '\\' && index < source.length -> {
                            output.append(if (source[index] == '\n' || source[index] == '\r') source[index] else ' ')
                            index++
                        }
                        state == State.STRING && current == '"' -> state = State.CODE
                        state == State.CHARACTER && current == '\'' -> state = State.CODE
                    }
                }

                State.TRIPLE_STRING -> {
                    if (current == '"' && next == '"' && source.getOrNull(index + 2) == '"') {
                        output.append("   ")
                        index += 3
                        state = State.CODE
                    } else {
                        output.append(if (current == '\n' || current == '\r') current else ' ')
                        index++
                    }
                }
            }
        }

        return output.toString()
    }

    private enum class State {
        CODE,
        LINE_COMMENT,
        BLOCK_COMMENT,
        STRING,
        CHARACTER,
        TRIPLE_STRING
    }
}
