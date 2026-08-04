/* SPDX-License-Identifier: AGPL-3.0-only */
package atropos.cli

sealed class LexResult {
    data class Success(val tokens: List<String>) : LexResult()
    data class Error(val message: String) : LexResult()
}

object CommandLexer {
    fun lex(input: String): LexResult {
        val tokens = mutableListOf<String>()
        val token = StringBuilder()
        var quote: Char? = null
        var started = false
        var index = 0

        while (index < input.length) {
            val ch = input[index]
            if (quote != null) {
                when {
                    ch == quote -> quote = null
                    ch == '\\' && quote == '"' -> {
                        index++
                        if (index >= input.length) return LexResult.Error("Trailing escape character")
                        token.append(input[index])
                        started = true
                    }
                    else -> {
                        token.append(ch)
                        started = true
                    }
                }
            } else {
                when {
                    ch.isWhitespace() -> {
                        if (started) {
                            tokens += token.toString()
                            token.clear()
                            started = false
                        }
                    }
                    (ch == '\'' || ch == '"') && (!started || token.isEmpty()) -> {
                        quote = ch
                        started = true
                    }
                    ch == '\\' -> {
                        index++
                        if (index >= input.length) return LexResult.Error("Trailing escape character")
                        token.append(input[index])
                        started = true
                    }
                    else -> {
                        token.append(ch)
                        started = true
                    }
                }
            }
            index++
        }

        if (quote != null) return LexResult.Error("Unterminated quote")
        if (started) tokens += token.toString()
        return LexResult.Success(tokens)
    }
}
