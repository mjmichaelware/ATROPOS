/* SPDX-License-Identifier: AGPL-3.0-only */
package com.atropos.android.app.console

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ContentCopy
import androidx.compose.material.icons.filled.Done
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalClipboardManager
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.atropos.android.app.theme.AtroposTypography
import com.atropos.android.app.theme.LocalCyberHudColors

sealed class MarkdownBlock {
    data class Header(val level: Int, val text: String) : MarkdownBlock()
    data class Paragraph(val text: String) : MarkdownBlock()
    data class CodeBlock(val language: String, val code: String) : MarkdownBlock()
    data class ListItem(val ordered: Boolean, val index: Int, val text: String) : MarkdownBlock()
    data class Blockquote(val text: String) : MarkdownBlock()
}

/**
 * Pure Jetpack Compose Streaming Markdown Parser & Renderer.
 *
 * Decomposes raw LLM conversation streams into structured blocks:
 * - Code blocks with copy-to-clipboard button and syntax gutter
 * - Formatted headings and lists
 * - Inline code, bold, and italic markup
 */
@Composable
fun MarkdownStreamRenderer(
    markdownText: String,
    modifier: Modifier = Modifier
) {
    val colors = LocalCyberHudColors.current
    val blocks = remember(markdownText) { parseMarkdown(markdownText) }

    Column(
        modifier = modifier.fillMaxWidth(),
        verticalArrangement = Arrangement.spacedBy(8.dp)
    ) {
        blocks.forEach { block ->
            when (block) {
                is MarkdownBlock.Header -> {
                    val fontSize = when (block.level) {
                        1 -> 18.sp
                        2 -> 16.sp
                        else -> 14.sp
                    }
                    Text(
                        text = block.text,
                        fontSize = fontSize,
                        fontWeight = FontWeight.Bold,
                        color = colors.accentCyan,
                        fontFamily = FontFamily.Monospace,
                        modifier = Modifier.padding(top = 4.dp)
                    )
                }

                is MarkdownBlock.Paragraph -> {
                    FormattedInlineText(rawText = block.text)
                }

                is MarkdownBlock.CodeBlock -> {
                    CodeBlockCard(language = block.language, code = block.code)
                }

                is MarkdownBlock.ListItem -> {
                    Row(
                        modifier = Modifier.padding(start = 8.dp),
                        verticalAlignment = Alignment.Top
                    ) {
                        Text(
                            text = if (block.ordered) "${block.index}. " else "• ",
                            style = AtroposTypography.monoGutter,
                            color = colors.accentCyan,
                            modifier = Modifier.padding(end = 4.dp)
                        )
                        FormattedInlineText(rawText = block.text)
                    }
                }

                is MarkdownBlock.Blockquote -> {
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(vertical = 2.dp)
                    ) {
                        Box(
                            modifier = Modifier
                                .size(width = 3.dp, height = 24.dp)
                                .background(colors.accentAmber, RoundedCornerShape(2.dp))
                        )
                        Text(
                            text = block.text,
                            style = AtroposTypography.hudBody,
                            color = colors.textSecondary,
                            fontStyle = FontStyle.Italic,
                            modifier = Modifier.padding(start = 8.dp)
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun CodeBlockCard(language: String, code: String) {
    val colors = LocalCyberHudColors.current
    val clipboardManager = LocalClipboardManager.current
    var copied by remember { mutableStateOf(false) }

    Column(
        modifier = Modifier
            .fillMaxWidth()
            .background(colors.gutterBackground, RoundedCornerShape(8.dp))
            .padding(8.dp)
    ) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Text(
                text = language.ifBlank { "code" }.uppercase(),
                style = AtroposTypography.monoGutter,
                color = colors.textMuted
            )

            Row(
                modifier = Modifier
                    .clickable {
                        clipboardManager.setText(AnnotatedString(code))
                        copied = true
                    }
                    .padding(2.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Icon(
                    imageVector = if (copied) Icons.Default.Done else Icons.Default.ContentCopy,
                    contentDescription = "Copy code",
                    tint = if (copied) colors.accentEmerald else colors.textMuted,
                    modifier = Modifier.size(14.dp)
                )
                Text(
                    text = if (copied) " Copied" else " Copy",
                    style = AtroposTypography.monoGutter,
                    color = if (copied) colors.accentEmerald else colors.textMuted,
                    fontSize = 10.sp
                )
            }
        }

        Spacer(modifier = Modifier.height(4.dp))

        Text(
            text = code,
            style = AtroposTypography.monoCode,
            color = colors.textPrimary,
            fontSize = 11.sp,
            lineHeight = 16.sp
        )
    }
}

@Composable
private fun FormattedInlineText(rawText: String) {
    val colors = LocalCyberHudColors.current

    val annotated = remember(rawText, colors) {
        buildAnnotatedString {
            var i = 0
            while (i < rawText.length) {
                if (rawText.startsWith("`", i) && !rawText.startsWith("```", i)) {
                    val end = rawText.indexOf("`", i + 1)
                    if (end != -1) {
                        pushStyle(
                            SpanStyle(
                                background = colors.borderDim,
                                color = colors.accentCyan,
                                fontFamily = FontFamily.Monospace,
                                fontSize = 11.sp
                            )
                        )
                        append(" ${rawText.substring(i + 1, end)} ")
                        pop()
                        i = end + 1
                        continue
                    }
                } else if (rawText.startsWith("**", i)) {
                    val end = rawText.indexOf("**", i + 2)
                    if (end != -1) {
                        pushStyle(SpanStyle(fontWeight = FontWeight.Bold, color = colors.textPrimary))
                        append(rawText.substring(i + 2, end))
                        pop()
                        i = end + 2
                        continue
                    }
                }

                append(rawText[i])
                i++
            }
        }
    }

    Text(
        text = annotated,
        style = AtroposTypography.hudBody,
        color = colors.textPrimary,
        lineHeight = 19.sp
    )
}

private fun parseMarkdown(raw: String): List<MarkdownBlock> {
    val blocks = mutableListOf<MarkdownBlock>()
    val lines = raw.lines()
    var inCodeBlock = false
    var codeLang = ""
    val codeBuffer = StringBuilder()

    var i = 0
    while (i < lines.size) {
        val line = lines[i]

        if (line.startsWith("```")) {
            if (!inCodeBlock) {
                inCodeBlock = true
                codeLang = line.removePrefix("```").trim()
                codeBuffer.clear()
            } else {
                inCodeBlock = false
                blocks.add(MarkdownBlock.CodeBlock(codeLang, codeBuffer.toString().trimEnd()))
                codeBuffer.clear()
            }
            i++
            continue
        }

        if (inCodeBlock) {
            codeBuffer.append(line).append("\n")
            i++
            continue
        }

        if (line.startsWith("# ")) {
            blocks.add(MarkdownBlock.Header(1, line.removePrefix("# ").trim()))
        } else if (line.startsWith("## ")) {
            blocks.add(MarkdownBlock.Header(2, line.removePrefix("## ").trim()))
        } else if (line.startsWith("### ")) {
            blocks.add(MarkdownBlock.Header(3, line.removePrefix("### ").trim()))
        } else if (line.startsWith("> ")) {
            blocks.add(MarkdownBlock.Blockquote(line.removePrefix("> ").trim()))
        } else if (line.startsWith("- ") || line.startsWith("* ")) {
            blocks.add(MarkdownBlock.ListItem(false, 0, line.substring(2).trim()))
        } else if (line.matches(Regex("^\\d+\\.\\s.*"))) {
            val num = line.substringBefore(".").trim().toIntOrNull() ?: 1
            blocks.add(MarkdownBlock.ListItem(true, num, line.substringAfter(". ").trim()))
        } else if (line.isNotBlank()) {
            blocks.add(MarkdownBlock.Paragraph(line))
        }

        i++
    }

    if (inCodeBlock && codeBuffer.isNotEmpty()) {
        blocks.add(MarkdownBlock.CodeBlock(codeLang, codeBuffer.toString().trimEnd()))
    }

    return blocks
}
