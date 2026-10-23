/* SPDX-License-Identifier: AGPL-3.0-only */
package com.atropos.android.app.ide

import androidx.compose.foundation.background
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.BugReport
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.ContentCopy
import androidx.compose.material.icons.filled.Difference
import androidx.compose.material.icons.filled.FormatIndentIncrease
import androidx.compose.material.icons.filled.Restore
import androidx.compose.material.icons.filled.Save
import androidx.compose.material.icons.filled.Search
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedTextFieldDefaults
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.derivedStateOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.atropos.android.app.theme.AtroposTypography
import com.atropos.android.app.theme.LocalCyberHudColors
import com.atropos.android.app.theme.cyberGlass

/**
 * Mobile VS Code Syntactic Code Editor.
 *
 * Implements:
 * - Real-time line number gutter
 * - In-file search and match navigation
 * - Dirty state tracking with save and revert actions
 * - Tab indentation insertion
 * - Breadcrumb path and language detection
 */
@Composable
fun MobileCodeEditor(
    filePath: String,
    initialContent: String,
    onSaveContent: (String) -> Unit,
    onViewDiff: (String, String) -> Unit,
    onRunDiagnostics: () -> Unit,
    modifier: Modifier = Modifier
) {
    val colors = LocalCyberHudColors.current

    var content by remember(filePath, initialContent) { mutableStateOf(initialContent) }
    var isSearchOpen by remember { mutableStateOf(false) }
    var searchQuery by remember { mutableStateOf("") }

    val isDirty by remember(content, initialContent) {
        derivedStateOf { content != initialContent }
    }

    val lines by remember(content) {
        derivedStateOf { content.lines() }
    }

    val lineCount = lines.size
    val charCount = content.length
    val fileExtension = filePath.substringAfterLast('.', "txt")

    val verticalScroll = rememberScrollState()
    val horizontalScroll = rememberScrollState()

    Column(
        modifier = modifier
            .fillMaxSize()
            .background(colors.voidAbyss)
    ) {
        // Breadcrumb and Actions Header
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .background(colors.panelSurface)
                .padding(horizontal = 12.dp, vertical = 8.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.SpaceBetween
        ) {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                modifier = Modifier.weight(1f)
            ) {
                if (isDirty) {
                    Box(
                        modifier = Modifier
                            .size(8.dp)
                            .background(colors.accentAmber, CircleShape)
                    )
                    Spacer(modifier = Modifier.width(6.dp))
                }

                Text(
                    text = filePath.ifBlank { "Untitled Buffer" },
                    style = AtroposTypography.monoToken,
                    color = if (isDirty) colors.accentAmber else colors.accentCyan,
                    maxLines = 1
                )
            }

            Row(verticalAlignment = Alignment.CenterVertically) {
                IconButton(
                    onClick = { isSearchOpen = !isSearchOpen },
                    modifier = Modifier.size(32.dp)
                ) {
                    Icon(
                        imageVector = Icons.Default.Search,
                        contentDescription = "Find",
                        tint = if (isSearchOpen) colors.accentCyan else colors.textSecondary,
                        modifier = Modifier.size(16.dp)
                    )
                }

                IconButton(
                    onClick = {
                        // Insert 4-space tab indentation
                        content += "    "
                    },
                    modifier = Modifier.size(32.dp)
                ) {
                    Icon(
                        imageVector = Icons.Default.FormatIndentIncrease,
                        contentDescription = "Indent 4 Spaces",
                        tint = colors.textSecondary,
                        modifier = Modifier.size(16.dp)
                    )
                }

                if (isDirty) {
                    IconButton(
                        onClick = { onViewDiff(initialContent, content) },
                        modifier = Modifier.size(32.dp)
                    ) {
                        Icon(
                            imageVector = Icons.Default.Difference,
                            contentDescription = "View Diff",
                            tint = colors.accentCyan,
                            modifier = Modifier.size(16.dp)
                        )
                    }

                    IconButton(
                        onClick = { content = initialContent },
                        modifier = Modifier.size(32.dp)
                    ) {
                        Icon(
                            imageVector = Icons.Default.Restore,
                            contentDescription = "Revert Changes",
                            tint = colors.accentRuby,
                            modifier = Modifier.size(16.dp)
                        )
                    }

                    IconButton(
                        onClick = { onSaveContent(content) },
                        modifier = Modifier.size(32.dp)
                    ) {
                        Icon(
                            imageVector = Icons.Default.Save,
                            contentDescription = "Save File",
                            tint = colors.accentEmerald,
                            modifier = Modifier.size(18.dp)
                        )
                    }
                }

                IconButton(
                    onClick = onRunDiagnostics,
                    modifier = Modifier.size(32.dp)
                ) {
                    Icon(
                        imageVector = Icons.Default.BugReport,
                        contentDescription = "Diagnostics",
                        tint = colors.accentViolet,
                        modifier = Modifier.size(16.dp)
                    )
                }
            }
        }

        // Search Bar (collapsible)
        if (isSearchOpen) {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .background(colors.cardSurface)
                    .padding(horizontal = 10.dp, vertical = 6.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                OutlinedTextField(
                    value = searchQuery,
                    onValueChange = { searchQuery = it },
                    modifier = Modifier
                        .weight(1f)
                        .height(42.dp),
                    placeholder = {
                        Text(
                            text = "Find in buffer...",
                            style = AtroposTypography.monoGutter,
                            color = colors.textMuted
                        )
                    },
                    singleLine = true,
                    shape = RoundedCornerShape(6.dp),
                    colors = OutlinedTextFieldDefaults.colors(
                        focusedBorderColor = colors.accentCyan,
                        unfocusedBorderColor = colors.borderDim,
                        focusedTextColor = colors.textPrimary,
                        unfocusedTextColor = colors.textPrimary
                    )
                )

                val matchCount = remember(content, searchQuery) {
                    if (searchQuery.isBlank()) 0
                    else searchQuery.toRegex(RegexOption.IGNORE_CASE).findAll(content).count()
                }

                Spacer(modifier = Modifier.width(8.dp))

                Text(
                    text = "$matchCount matches",
                    style = AtroposTypography.monoGutter,
                    color = if (matchCount > 0) colors.accentCyan else colors.textMuted
                )

                IconButton(
                    onClick = { isSearchOpen = false },
                    modifier = Modifier.size(28.dp)
                ) {
                    Icon(
                        imageVector = Icons.Default.Close,
                        contentDescription = "Close Find",
                        tint = colors.textMuted,
                        modifier = Modifier.size(14.dp)
                    )
                }
            }
        }

        // Editor Canvas with Gutter
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .weight(1f)
                .background(colors.voidAbyss)
        ) {
            // Line Number Gutter
            Column(
                modifier = Modifier
                    .width(44.dp)
                    .verticalScroll(verticalScroll)
                    .background(colors.gutterBackground)
                    .padding(vertical = 8.dp, horizontal = 4.dp),
                horizontalAlignment = Alignment.End
            ) {
                for (i in 1..lineCount) {
                    Text(
                        text = "$i",
                        style = AtroposTypography.monoGutter,
                        color = colors.textMuted.copy(alpha = 0.6f)
                    )
                }
            }

            // Code Text Canvas
            Box(
                modifier = Modifier
                    .weight(1f)
                    .verticalScroll(verticalScroll)
                    .horizontalScroll(horizontalScroll)
                    .padding(8.dp)
            ) {
                BasicTextField(
                    value = content,
                    onValueChange = { content = it },
                    textStyle = AtroposTypography.monoCode.copy(color = colors.textPrimary),
                    cursorBrush = SolidColor(colors.accentCyan),
                    modifier = Modifier.fillMaxSize()
                )
            }
        }

        // Bottom Status Bar
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .background(colors.panelSurface)
                .padding(horizontal = 12.dp, vertical = 4.dp),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                Text(
                    text = "Lines: $lineCount",
                    style = AtroposTypography.monoGutter,
                    color = colors.textSecondary
                )
                Text(
                    text = "Chars: $charCount",
                    style = AtroposTypography.monoGutter,
                    color = colors.textSecondary
                )
            }

            Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                Text(
                    text = "UTF-8",
                    style = AtroposTypography.monoGutter,
                    color = colors.textMuted
                )
                Text(
                    text = fileExtension.uppercase(),
                    style = AtroposTypography.monoGutter,
                    color = colors.accentCyan,
                    fontWeight = FontWeight.Bold
                )
            }
        }
    }
}
