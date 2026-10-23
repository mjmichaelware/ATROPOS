/* SPDX-License-Identifier: AGPL-3.0-only */
package com.atropos.android.app.ide

import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.font.FontWeight
import com.atropos.android.app.theme.CyberHudColors

enum class SyntaxTokenType {
    KEYWORD,
    TYPE,
    FUNCTION,
    STRING,
    NUMBER,
    COMMENT,
    ANNOTATION,
    OPERATOR,
    PLAIN
}

data class SyntaxToken(
    val type: SyntaxTokenType,
    val text: String
)

/**
 * High-Performance Deterministic Syntax Tokenizer for Kotlin, TypeScript, Python, and JSON.
 */
object SyntaxHighlighter {

    private val KOTLIN_KEYWORDS = setOf(
        "package", "import", "class", "interface", "object", "enum", "fun",
        "val", "var", "override", "private", "protected", "public", "internal",
        "if", "else", "when", "for", "while", "return", "sealed", "data",
        "suspend", "null", "true", "false", "this", "super", "try", "catch", "finally"
    )

    private val TS_KEYWORDS = setOf(
        "import", "from", "export", "default", "function", "const", "let", "var",
        "class", "interface", "type", "return", "if", "else", "switch", "case",
        "async", "await", "true", "false", "null", "undefined", "new", "typeof"
    )

    fun highlightCode(code: String, language: String, colors: CyberHudColors): AnnotatedString {
        val keywords = if (language.contains("ts") || language.contains("js")) TS_KEYWORDS else KOTLIN_KEYWORDS

        return buildAnnotatedString {
            val lines = code.split("\n")
            lines.forEachIndexed { lineIdx, line ->
                highlightLine(line, keywords, colors)
                if (lineIdx < lines.size - 1) {
                    append("\n")
                }
            }
        }
    }

    private fun AnnotatedString.Builder.highlightLine(
        line: String,
        keywords: Set<String>,
        colors: CyberHudColors
    ) {
        var idx = 0
        val trimmed = line.trimStart()

        // Line comment
        if (trimmed.startsWith("//") || trimmed.startsWith("#")) {
            pushStyle(SpanStyle(color = colors.textMuted))
            append(line)
            pop()
            return
        }

        while (idx < line.length) {
            val ch = line[idx]

            // String literal
            if (ch == '"' || ch == '\'') {
                val quote = ch
                val start = idx
                idx++
                while (idx < line.length && line[idx] != quote) {
                    if (line[idx] == '\\' && idx + 1 < line.length) {
                        idx++
                    }
                    idx++
                }
                if (idx < line.length) idx++ // consume end quote
                pushStyle(SpanStyle(color = colors.accentEmerald))
                append(line.substring(start, idx))
                pop()
                continue
            }

            // Annotation
            if (ch == '@' && idx + 1 < line.length && line[idx + 1].isLetter()) {
                val start = idx
                idx++
                while (idx < line.length && (line[idx].isLetterOrDigit() || line[idx] == '.')) {
                    idx++
                }
                pushStyle(SpanStyle(color = colors.accentAmber))
                append(line.substring(start, idx))
                pop()
                continue
            }

            // Identifiers / Keywords
            if (ch.isLetter() || ch == '_') {
                val start = idx
                while (idx < line.length && (line[idx].isLetterOrDigit() || line[idx] == '_')) {
                    idx++
                }
                val word = line.substring(start, idx)

                if (keywords.contains(word)) {
                    pushStyle(SpanStyle(color = colors.accentViolet, fontWeight = FontWeight.Bold))
                    append(word)
                    pop()
                } else if (word[0].isUpperCase()) {
                    pushStyle(SpanStyle(color = colors.accentCyan))
                    append(word)
                    pop()
                } else {
                    pushStyle(SpanStyle(color = colors.textPrimary))
                    append(word)
                    pop()
                }
                continue
            }

            // Number literal
            if (ch.isDigit()) {
                val start = idx
                while (idx < line.length && (line[idx].isLetterOrDigit() || line[idx] == '.')) {
                    idx++
                }
                pushStyle(SpanStyle(color = colors.accentAmber))
                append(line.substring(start, idx))
                pop()
                continue
            }

            // Punctuation / Operator / Whitespace
            pushStyle(SpanStyle(color = colors.textSecondary))
            append(ch.toString())
            pop()
            idx++
        }
    }
}
