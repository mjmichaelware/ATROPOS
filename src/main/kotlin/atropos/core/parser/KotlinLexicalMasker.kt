package atropos.core.parser

object KotlinLexicalMasker {
    fun maskNonCode(source: String): String {
        val out = StringBuilder(source.length)
        var i = 0
        var state = State.CODE
        while (i < source.length) {
            val c = source[i]
            val next = source.getOrNull(i + 1)
            val third = source.getOrNull(i + 2)
            when (state) {
                State.CODE -> when {
                    c == '/' && next == '/' -> {
                        out.append("  ")
                        i += 2
                        state = State.LINE_COMMENT
                    }
                    c == '/' && next == '*' -> {
                        out.append("  ")
                        i += 2
                        state = State.BLOCK_COMMENT
                    }
                    c == '"' && next == '"' && third == '"' -> {
                        out.append("   ")
                        i += 3
                        state = State.TRIPLE_STRING
                    }
                    c == '"' -> {
                        out.append(' ')
                        i += 1
                        state = State.STRING
                    }
                    c == '\'' -> {
                        out.append(' ')
                        i += 1
                        state = State.CHAR
                    }
                    else -> {
                        out.append(c)
                        i += 1
                    }
                }
                State.LINE_COMMENT -> {
                    out.append(if (c == '\n') '\n' else ' ')
                    i += 1
                    if (c == '\n') state = State.CODE
                }
                State.BLOCK_COMMENT -> {
                    when {
                        c == '*' && next == '/' -> {
                            out.append("  ")
                            i += 2
                            state = State.CODE
                        }
                        else -> {
                            out.append(if (c == '\n') '\n' else ' ')
                            i += 1
                        }
                    }
                }
                State.STRING -> {
                    when {
                        c == '\\' && next != null -> {
                            out.append("  ")
                            i += 2
                        }
                        c == '"' -> {
                            out.append(' ')
                            i += 1
                            state = State.CODE
                        }
                        else -> {
                            out.append(if (c == '\n') '\n' else ' ')
                            i += 1
                        }
                    }
                }
                State.TRIPLE_STRING -> {
                    when {
                        c == '"' && next == '"' && third == '"' -> {
                            out.append("   ")
                            i += 3
                            state = State.CODE
                        }
                        else -> {
                            out.append(if (c == '\n') '\n' else ' ')
                            i += 1
                        }
                    }
                }
                State.CHAR -> {
                    when {
                        c == '\\' && next != null -> {
                            out.append("  ")
                            i += 2
                        }
                        c == '\'' -> {
                            out.append(' ')
                            i += 1
                            state = State.CODE
                        }
                        else -> {
                            out.append(if (c == '\n') '\n' else ' ')
                            i += 1
                        }
                    }
                }
            }
        }
        return out.toString()
    }

    private enum class State {
        CODE,
        LINE_COMMENT,
        BLOCK_COMMENT,
        STRING,
        TRIPLE_STRING,
        CHAR
    }
}
