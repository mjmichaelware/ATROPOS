/* SPDX-License-Identifier: AGPL-3.0-only */
package com.atropos.android.app.command

import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInHorizontally
import androidx.compose.animation.slideOutHorizontally
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
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
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.ChevronRight
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Info
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.Search
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedTextFieldDefaults
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.atropos.android.app.theme.AtroposTypography
import com.atropos.android.app.theme.LocalCyberHudColors
import com.atropos.android.app.theme.cyberGlass

sealed class PaletteNavState {
    object Categories : PaletteNavState()
    data class Subcategories(val category: CommandCategory) : PaletteNavState()
    data class Commands(val category: CommandCategory, val subcategory: String? = null) : PaletteNavState()
    data class Detail(val command: AtroposCommandSpec, val previousState: PaletteNavState) : PaletteNavState()
}

/**
 * Interactive Slash (/) Command Category Palette.
 *
 * Implements the non-overload navigation rule:
 * - Typing '/' opens category picker
 * - If category contains <= 8 commands, shows all commands directly
 * - If category contains > 8 commands, shows subcategories first
 * - Detailed information sheet available for every command
 */
@Composable
fun CommandPaletteOverlay(
    isOpen: Boolean,
    onClose: () -> Unit,
    onSelectCommand: (String) -> Unit,
    onExecuteCommand: (String) -> Unit,
    modifier: Modifier = Modifier
) {
    if (!isOpen) return

    val colors = LocalCyberHudColors.current
    var navState by remember { mutableStateOf<PaletteNavState>(PaletteNavState.Categories) }
    var searchQuery by remember { mutableStateOf("") }

    Surface(
        modifier = modifier.fillMaxSize(),
        color = colors.voidAbyss.copy(alpha = 0.95f)
    ) {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(16.dp)
        ) {
            // Header Bar
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(bottom = 12.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    if (navState !is PaletteNavState.Categories) {
                        IconButton(
                            onClick = {
                                navState = when (val current = navState) {
                                    is PaletteNavState.Detail -> current.previousState
                                    is PaletteNavState.Commands -> {
                                        if (current.subcategory != null) {
                                            PaletteNavState.Subcategories(current.category)
                                        } else {
                                            PaletteNavState.Categories
                                        }
                                    }
                                    is PaletteNavState.Subcategories -> PaletteNavState.Categories
                                    PaletteNavState.Categories -> PaletteNavState.Categories
                                }
                            }
                        ) {
                            Icon(
                                imageVector = Icons.AutoMirrored.Filled.ArrowBack,
                                contentDescription = "Back",
                                tint = colors.accentCyan
                            )
                        }
                    }

                    Text(
                        text = when (val state = navState) {
                            PaletteNavState.Categories -> "Command Categories"
                            is PaletteNavState.Subcategories -> state.category.title
                            is PaletteNavState.Commands -> state.subcategory ?: state.category.title
                            is PaletteNavState.Detail -> state.command.name
                        },
                        style = AtroposTypography.hudHeader,
                        color = colors.textPrimary
                    )
                }

                IconButton(onClick = onClose) {
                    Icon(
                        imageVector = Icons.Default.Close,
                        contentDescription = "Close",
                        tint = colors.textSecondary
                    )
                }
            }

            // Search Bar
            OutlinedTextField(
                value = searchQuery,
                onValueChange = { searchQuery = it },
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(bottom = 16.dp),
                placeholder = {
                    Text(
                        text = "Filter commands or search across categories...",
                        style = AtroposTypography.hudBody,
                        color = colors.textMuted
                    )
                },
                leadingIcon = {
                    Icon(
                        imageVector = Icons.Default.Search,
                        contentDescription = "Search",
                        tint = colors.accentCyan
                    )
                },
                trailingIcon = {
                    if (searchQuery.isNotEmpty()) {
                        IconButton(onClick = { searchQuery = "" }) {
                            Icon(
                                imageVector = Icons.Default.Close,
                                contentDescription = "Clear",
                                tint = colors.textSecondary
                            )
                        }
                    }
                },
                singleLine = true,
                shape = RoundedCornerShape(12.dp),
                colors = OutlinedTextFieldDefaults.colors(
                    focusedBorderColor = colors.accentCyan,
                    unfocusedBorderColor = colors.borderDim,
                    focusedTextColor = colors.textPrimary,
                    unfocusedTextColor = colors.textPrimary
                )
            )

            // Content Area with animated transitions
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .weight(1f)
            ) {
                if (searchQuery.isNotBlank()) {
                    val searchResults = remember(searchQuery) {
                        CommandCatalogRegistry.search(searchQuery)
                    }
                    CommandSearchResultsView(
                        results = searchResults,
                        onSelect = {
                            onSelectCommand(it)
                            onClose()
                        },
                        onInspect = { navState = PaletteNavState.Detail(it, navState) }
                    )
                } else {
                    AnimatedContent(
                        targetState = navState,
                        transitionSpec = {
                            slideInHorizontally { width -> width / 3 } + fadeIn() togetherWith
                                slideOutHorizontally { width -> -width / 3 } + fadeOut()
                        },
                        label = "PaletteNav"
                    ) { state ->
                        when (state) {
                            PaletteNavState.Categories -> {
                                CategoryPickerList(
                                    categories = CommandCategory.values().toList(),
                                    onSelectCategory = { cat ->
                                        val commands = CommandCatalogRegistry.getByCategory(cat)
                                        if (commands.size <= 8) {
                                            navState = PaletteNavState.Commands(cat)
                                        } else {
                                            val subs = CommandCatalogRegistry.getSubcategories(cat)
                                            if (subs.isNotEmpty()) {
                                                navState = PaletteNavState.Subcategories(cat)
                                            } else {
                                                navState = PaletteNavState.Commands(cat)
                                            }
                                        }
                                    }
                                )
                            }
                            is PaletteNavState.Subcategories -> {
                                val subs = CommandCatalogRegistry.getSubcategories(state.category)
                                SubcategoryPickerList(
                                    category = state.category,
                                    subcategories = subs,
                                    onSelectSubcategory = { sub ->
                                        navState = PaletteNavState.Commands(state.category, sub)
                                    }
                                )
                            }
                            is PaletteNavState.Commands -> {
                                val list = if (state.subcategory != null) {
                                    CommandCatalogRegistry.getBySubcategory(state.category, state.subcategory)
                                } else {
                                    CommandCatalogRegistry.getByCategory(state.category)
                                }
                                CommandItemsList(
                                    commands = list,
                                    onSelect = {
                                        onSelectCommand(it)
                                        onClose()
                                    },
                                    onInspect = { navState = PaletteNavState.Detail(it, state) }
                                )
                            }
                            is PaletteNavState.Detail -> {
                                CommandDetailSheetContent(
                                    command = state.command,
                                    onInsert = {
                                        onSelectCommand(state.command.name)
                                        onClose()
                                    },
                                    onExecute = {
                                        onExecuteCommand(state.command.name)
                                        onClose()
                                    }
                                )
                            }
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun CategoryPickerList(
    categories: List<CommandCategory>,
    onSelectCategory: (CommandCategory) -> Unit
) {
    val colors = LocalCyberHudColors.current

    LazyColumn(
        verticalArrangement = Arrangement.spacedBy(10.dp),
        contentPadding = PaddingValues(bottom = 24.dp)
    ) {
        items(categories) { cat ->
            val count = CommandCatalogRegistry.getByCategory(cat).size
            Card(
                modifier = Modifier
                    .fillMaxWidth()
                    .clickable { onSelectCategory(cat) }
                    .cyberGlass(backgroundColor = colors.cardSurface, borderColor = colors.borderDim),
                colors = CardDefaults.cardColors(containerColor = Color.Transparent)
            ) {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(16.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Box(
                        modifier = Modifier
                            .size(44.dp)
                            .background(colors.panelSurface, CircleShape),
                        contentAlignment = Alignment.Center
                    ) {
                        Text(
                            text = cat.iconGlyph,
                            fontSize = 20.sp
                        )
                    }

                    Spacer(modifier = Modifier.width(16.dp))

                    Column(modifier = Modifier.weight(1f)) {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Text(
                                text = cat.title,
                                style = AtroposTypography.hudSubhead,
                                color = colors.textPrimary
                            )
                            Spacer(modifier = Modifier.width(8.dp))
                            Box(
                                modifier = Modifier
                                    .background(colors.accentCyan.copy(alpha = 0.15f), RoundedCornerShape(4.dp))
                                    .padding(horizontal = 6.dp, vertical = 2.dp)
                            ) {
                                Text(
                                    text = "$count commands",
                                    style = AtroposTypography.badgeLabel,
                                    color = colors.accentCyan
                                )
                            }
                        }

                        Spacer(modifier = Modifier.height(4.dp))

                        Text(
                            text = cat.description,
                            style = AtroposTypography.hudCaption,
                            color = colors.textSecondary
                        )
                    }

                    Icon(
                        imageVector = Icons.Default.ChevronRight,
                        contentDescription = "Open",
                        tint = colors.textMuted
                    )
                }
            }
        }
    }
}

@Composable
private fun SubcategoryPickerList(
    category: CommandCategory,
    subcategories: List<String>,
    onSelectSubcategory: (String) -> Unit
) {
    val colors = LocalCyberHudColors.current

    LazyColumn(
        verticalArrangement = Arrangement.spacedBy(10.dp),
        contentPadding = PaddingValues(bottom = 24.dp)
    ) {
        item {
            Text(
                text = "Select a subcategory to browse commands (${category.title}):",
                style = AtroposTypography.hudCaption,
                color = colors.textSecondary,
                modifier = Modifier.padding(bottom = 6.dp)
            )
        }

        items(subcategories) { sub ->
            val count = CommandCatalogRegistry.getBySubcategory(category, sub).size
            Card(
                modifier = Modifier
                    .fillMaxWidth()
                    .clickable { onSelectSubcategory(sub) }
                    .cyberGlass(backgroundColor = colors.cardSurface, borderColor = colors.borderDim),
                colors = CardDefaults.cardColors(containerColor = Color.Transparent)
            ) {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(16.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.SpaceBetween
                ) {
                    Column {
                        Text(
                            text = sub,
                            style = AtroposTypography.hudSubhead,
                            color = colors.textPrimary
                        )
                        Text(
                            text = "$count commands in this category",
                            style = AtroposTypography.hudCaption,
                            color = colors.accentCyan
                        )
                    }

                    Icon(
                        imageVector = Icons.Default.ChevronRight,
                        contentDescription = "Open",
                        tint = colors.accentCyan
                    )
                }
            }
        }
    }
}

@Composable
private fun CommandItemsList(
    commands: List<AtroposCommandSpec>,
    onSelect: (String) -> Unit,
    onInspect: (AtroposCommandSpec) -> Unit
) {
    val colors = LocalCyberHudColors.current

    LazyColumn(
        verticalArrangement = Arrangement.spacedBy(10.dp),
        contentPadding = PaddingValues(bottom = 24.dp)
    ) {
        items(commands) { cmd ->
            Card(
                modifier = Modifier
                    .fillMaxWidth()
                    .cyberGlass(backgroundColor = colors.cardSurface, borderColor = colors.borderDim),
                colors = CardDefaults.cardColors(containerColor = Color.Transparent)
            ) {
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(14.dp)
                ) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.SpaceBetween
                    ) {
                        Text(
                            text = cmd.name,
                            style = AtroposTypography.commandPrompt,
                            color = colors.accentCyan,
                            fontWeight = FontWeight.Bold
                        )

                        SafetyBadge(tier = cmd.safetyTier)
                    }

                    Spacer(modifier = Modifier.height(4.dp))

                    Text(
                        text = cmd.synopsis,
                        style = AtroposTypography.hudBody,
                        color = colors.textSecondary
                    )

                    Spacer(modifier = Modifier.height(6.dp))

                    Text(
                        text = cmd.usage,
                        style = AtroposTypography.monoCode,
                        color = colors.textMuted
                    )

                    Spacer(modifier = Modifier.height(10.dp))

                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.End,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        IconButton(
                            onClick = { onInspect(cmd) },
                            modifier = Modifier.size(32.dp)
                        ) {
                            Icon(
                                imageVector = Icons.Default.Info,
                                contentDescription = "Inspect Documentation",
                                tint = colors.textSecondary,
                                modifier = Modifier.size(18.dp)
                            )
                        }

                        Spacer(modifier = Modifier.width(8.dp))

                        Box(
                            modifier = Modifier
                                .background(colors.accentCyan.copy(alpha = 0.2f), RoundedCornerShape(6.dp))
                                .clickable { onSelect(cmd.name) }
                                .padding(horizontal = 10.dp, vertical = 6.dp)
                        ) {
                            Text(
                                text = "Use Command",
                                style = AtroposTypography.badgeLabel,
                                color = colors.accentCyan
                            )
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun CommandSearchResultsView(
    results: List<AtroposCommandSpec>,
    onSelect: (String) -> Unit,
    onInspect: (AtroposCommandSpec) -> Unit
) {
    val colors = LocalCyberHudColors.current

    if (results.isEmpty()) {
        Box(
            modifier = Modifier.fillMaxSize(),
            contentAlignment = Alignment.Center
        ) {
            Text(
                text = "No matching commands found.",
                style = AtroposTypography.hudBody,
                color = colors.textMuted
            )
        }
    } else {
        CommandItemsList(
            commands = results,
            onSelect = onSelect,
            onInspect = onInspect
        )
    }
}

@Composable
fun SafetyBadge(tier: SafetyTier) {
    val colors = LocalCyberHudColors.current
    val (bg, fg) = when (tier) {
        SafetyTier.READ_ONLY -> Pair(colors.accentEmerald.copy(alpha = 0.15f), colors.accentEmerald)
        SafetyTier.LOCAL_MUTATION -> Pair(colors.accentAmber.copy(alpha = 0.15f), colors.accentAmber)
        SafetyTier.NETWORK_EGRESS -> Pair(colors.accentViolet.copy(alpha = 0.15f), colors.accentViolet)
        SafetyTier.CRITICAL_GOVERNANCE -> Pair(colors.accentRuby.copy(alpha = 0.15f), colors.accentRuby)
    }

    Box(
        modifier = Modifier
            .background(bg, RoundedCornerShape(4.dp))
            .padding(horizontal = 6.dp, vertical = 2.dp)
    ) {
        Text(
            text = tier.label,
            style = AtroposTypography.badgeLabel,
            color = fg
        )
    }
}
