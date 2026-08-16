package atropos.core.parser

/**
 * Blanks comments and string literals so a brace scanner sees only code.
 *
 * Everything masked is replaced by spaces of the same length and newlines are
 * preserved, so every offset in the output still names the same character in
 * the input. Callers report coordinates against the original file.
 *
 * ## Interpolation is string, not code
 *
 * `${...}` is masked along with the string that contains it. It holds real
 * Kotlin — often a lambda — but nothing inside a string literal is ever a
 * declaration, and masking it keeps braces balanced. Leaving it out of the
 * mask was a parse failure, not a missed feature: in
 *
 *     "{\"views\":${AtroposView.values().map { "\"$it\"" }}}"
 *
 * the quote opening the nested string ended the outer one, and the two closing
 * braces then arrived with nothing open to match, refusing the whole file.
 */
object KotlinLexicalMasker {
    fun maskNonCode(source: String): String {
        val out = StringBuilder(source.length)
        var i = 0
        var state = State.CODE
        // Interpolation tracking. Only one set is needed because an
        // interpolation inside an interpolation still returns to a string, and
        // the depth counter already spans both.
        var interpolationDepth = 0
        var returnState = State.STRING
        var nestedStringOpen = false
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
                    c == '`' -> {
                        // A backtick-quoted identifier is a name, not a
                        // literal, and Kotlin test names are full of
                        // apostrophes: `a prompt's hash ...`. Read as code, that
                        // apostrophe opened a char literal that ran to the next
                        // one and swallowed every brace in between.
                        out.append(' ')
                        i += 1
                        state = State.BACKTICK_NAME
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
                        c == '$' && next == '{' -> {
                            out.append("  ")
                            i += 2
                            interpolationDepth = 1
                            returnState = State.STRING
                            state = State.INTERPOLATION
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
                        c == '$' && next == '{' -> {
                            out.append("  ")
                            i += 2
                            interpolationDepth = 1
                            returnState = State.TRIPLE_STRING
                            state = State.INTERPOLATION
                        }
                        c == '"' -> {
                            // A raw string ends at the LAST three quotes of a
                            // run, not the first. `Regex(""""message"..."""")`
                            // ends in four: one is content and three close the
                            // literal. Terminating on the first three left a
                            // stray quote in code, which opened a phantom
                            // string and unbalanced every brace after it.
                            var run = 0
                            while (i + run < source.length && source[i + run] == '"') run += 1
                            if (run >= 3) {
                                out.append(" ".repeat(run))
                                i += run
                                state = State.CODE
                            } else {
                                out.append(" ".repeat(run))
                                i += run
                            }
                        }
                        else -> {
                            out.append(if (c == '\n') '\n' else ' ')
                            i += 1
                        }
                    }
                }
                State.INTERPOLATION -> {
                    when {
                        // A string inside the expression is skipped whole, so a
                        // brace in its text cannot close the interpolation.
                        nestedStringOpen -> {
                            when {
                                c == '\\' && next != null -> {
                                    out.append("  ")
                                    i += 2
                                }
                                c == '"' -> {
                                    nestedStringOpen = false
                                    out.append(' ')
                                    i += 1
                                }
                                else -> {
                                    out.append(if (c == '\n') '\n' else ' ')
                                    i += 1
                                }
                            }
                        }
                        c == '"' -> {
                            nestedStringOpen = true
                            out.append(' ')
                            i += 1
                        }
                        c == '{' -> {
                            interpolationDepth += 1
                            out.append(' ')
                            i += 1
                        }
                        c == '}' -> {
                            interpolationDepth -= 1
                            out.append(' ')
                            i += 1
                            if (interpolationDepth == 0) state = returnState
                        }
                        else -> {
                            out.append(if (c == '\n') '\n' else ' ')
                            i += 1
                        }
                    }
                }
                State.BACKTICK_NAME -> {
                    out.append(if (c == '\n') '\n' else ' ')
                    i += 1
                    if (c == '`') state = State.CODE
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
        INTERPOLATION,
        BACKTICK_NAME,
        CHAR
    }
}
