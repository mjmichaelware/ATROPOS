/* SPDX-License-Identifier: AGPL-3.0-only */
package com.atropos.android.app.ide

import androidx.compose.foundation.background
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Close
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.atropos.android.app.theme.AtroposTypography
import com.atropos.android.app.theme.LocalCyberHudColors
import com.atropos.android.app.theme.cyberGlass

enum class DiffLineType {
    CONTEXT,
    ADDITION,
    DELETION,
    HEADER
}

data class DiffLine(
    val type: DiffLineType,
    val oldLineNumber: Int?,
    val newLineNumber: Int?,
    val text: String
)

/**
 * Sovereign Mobile Syntactic Unified Diff Viewer.
 * Visualizes line additions (+), deletions (-), and chunk headers.
 */
@Composable
fun CodeDiffViewer(
    filePath: String,
    originalText: String,
    modifiedText: String,
    onClose: () -> Unit,
    modifier: Modifier = Modifier
) {
    val colors = LocalCyberHudColors.current

    val diffLines = remember(originalText, modifiedText) {
        computeUnifiedDiff(originalText, modifiedText)
    }

    val additions = diffLines.count { it.type == DiffLineType.ADDITION }
    val deletions = diffLines.count { it.type == DiffLineType.DELETION }

    val horizontalScroll = rememberScrollState()

    Column(
        modifier = modifier
            .fillMaxSize()
            .background(colors.voidAbyss)
            .padding(12.dp)
    ) {
        // Diff Header
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(bottom = 8.dp),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Column {
                Text(
                    text = "DIFF: $filePath",
                    style = AtroposTypography.monoToken,
                    color = colors.accentCyan
                )
                Row(
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text(
                        text = "+$additions lines",
                        style = AtroposTypography.monoGutter,
                        color = colors.accentEmerald,
                        fontWeight = FontWeight.Bold
                    )
                    Text(
                        text = "-$deletions lines",
                        style = AtroposTypography.monoGutter,
                        color = colors.accentRuby,
                        fontWeight = FontWeight.Bold
                    )
                }
            }

            IconButton(onClick = onClose, modifier = Modifier.size(32.dp)) {
                Icon(
                    imageVector = Icons.Default.Close,
                    contentDescription = "Close Diff",
                    tint = colors.textSecondary
                )
            }
        }

        // Diff Stream
        LazyColumn(
            modifier = Modifier
                .fillMaxWidth()
                .weight(1f)
                .horizontalScroll(horizontalScroll)
                .cyberGlass(backgroundColor = colors.gutterBackground, borderColor = colors.borderDim),
            contentPadding = PaddingValues(vertical = 4.dp)
        ) {
            items(diffLines) { line ->
                val (bg, textColor, prefix) = when (line.type) {
                    DiffLineType.ADDITION -> Triple(colors.diffAddBackground, colors.accentEmerald, "+")
                    DiffLineType.DELETION -> Triple(colors.diffRemoveBackground, colors.accentRuby, "-")
                    DiffLineType.HEADER -> Triple(colors.accentViolet.copy(alpha = 0.15f), colors.accentViolet, " ")
                    DiffLineType.CONTEXT -> Triple(Color.Transparent, colors.textSecondary, " ")
                }

                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .background(bg)
                        .padding(horizontal = 4.dp, vertical = 2.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    // Old line number
                    Text(
                        text = line.oldLineNumber?.toString() ?: "",
                        style = AtroposTypography.monoGutter,
                        modifier = Modifier.width(32.dp)
                    )

                    // New line number
                    Text(
                        text = line.newLineNumber?.toString() ?: "",
                        style = AtroposTypography.monoGutter,
                        modifier = Modifier.width(32.dp)
                    )

                    // Prefix (+ / -)
                    Text(
                        text = prefix,
                        style = AtroposTypography.monoCode,
                        color = textColor,
                        fontWeight = FontWeight.Bold,
                        modifier = Modifier.width(16.dp)
                    )

                    // Line Text
                    Text(
                        text = line.text,
                        style = AtroposTypography.monoCode,
                        color = if (line.type == DiffLineType.CONTEXT) colors.textPrimary else textColor
                    )
                }
            }
        }
    }
}

private fun computeUnifiedDiff(original: String, modified: String): List<DiffLine> {
    val origLines = original.lines()
    val modLines = modified.lines()
    val result = mutableListOf<DiffLine>()

    result.add(DiffLine(DiffLineType.HEADER, null, null, "@@ -1,${origLines.size} +1,${modLines.size} @@"))

    val maxLines = maxOf(origLines.size, modLines.size)
    for (i in 0 until maxLines) {
        val orig = origLines.getOrNull(i)
        val mod = modLines.getOrNull(i)

        if (orig != null && mod != null && orig == mod) {
            result.add(DiffLine(DiffLineType.CONTEXT, i + 1, i + 1, orig))
        } else {
            if (orig != null) {
                result.add(DiffLine(DiffLineType.DELETION, i + 1, null, orig))
            }
            if (mod != null) {
                result.add(DiffLine(DiffLineType.ADDITION, null, i + 1, mod))
            }
        }
    }

    return result
}
