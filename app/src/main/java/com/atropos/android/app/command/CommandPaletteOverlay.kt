/* SPDX-License-Identifier: AGPL-3.0-only */
package com.atropos.android.app.command

import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInHorizontally
import androidx.compose.animation.slideOutHorizontally
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.focusable
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
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.ChevronRight
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Info
import androidx.compose.material.icons.filled.KeyboardArrowDown
import androidx.compose.material.icons.filled.KeyboardArrowUp
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.Search
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedTextFieldDefaults
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.input.key.Key
import androidx.compose.ui.input.key.KeyEventType
import androidx.compose.ui.input.key.key
import androidx.compose.ui.input.key.onKeyEvent
import androidx.compose.ui.input.key.type
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
    var selectedIndex by remember(navState, searchQuery) { mutableIntStateOf(0) }

    val categories = remember { CommandCategory.values().toList() }
    val searchResults = remember(searchQuery) {
        if (searchQuery.isNotBlank()) CommandCatalogRegistry.search(searchQuery) else emptyList()
    }

    val currentSubcategories = remember(navState) {
        when (val s = navState) {
            is PaletteNavState.Subcategories -> CommandCatalogRegistry.getSubcategories(s.category)
            else -> emptyList()
        }
    }

    val currentCommands = remember(navState) {
        when (val s = navState) {
            is PaletteNavState.Commands -> {
                if (s.subcategory != null) {
                    CommandCatalogRegistry.getBySubcategory(s.category, s.subcategory)
                } else {
                    CommandCatalogRegistry.getByCategory(s.category)
                }
            }
            else -> emptyList()
        }
    }

    val currentCount = when {
        searchQuery.isNotBlank() -> searchResults.size
        navState is PaletteNavState.Categories -> categories.size
        navState is PaletteNavState.Subcategories -> currentSubcategories.size
        navState is PaletteNavState.Commands -> currentCommands.size
        else -> 0
    }

    fun navigateBack() {
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
            PaletteNavState.Categories -> {
                onClose()
                PaletteNavState.Categories
            }
        }
    }

    fun activateCategory(cat: CommandCategory) {
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

    fun activateCurrentSelection() {
        if (searchQuery.isNotBlank()) {
            if (selectedIndex in searchResults.indices) {
                onSelectCommand(searchResults[selectedIndex].name)
                onClose()
            }
        } else {
            when (val s = navState) {
                PaletteNavState.Categories -> {
                    if (selectedIndex in categories.indices) {
                        activateCategory(categories[selectedIndex])
                    }
                }
                is PaletteNavState.Subcategories -> {
                    if (selectedIndex in currentSubcategories.indices) {
                        navState = PaletteNavState.Commands(s.category, currentSubcategories[selectedIndex])
                    }
                }
                is PaletteNavState.Commands -> {
                    if (selectedIndex in currentCommands.indices) {
                        onSelectCommand(currentCommands[selectedIndex].name)
                        onClose()
                    }
                }
                is PaletteNavState.Detail -> {
                    onSelectCommand(s.command.name)
                    onClose()
                }
            }
        }
    }

    Surface(
        modifier = modifier
            .fillMaxSize()
            .focusable()
            .onKeyEvent { keyEvent ->
                if (keyEvent.type == KeyEventType.KeyDown) {
                    when (keyEvent.key) {
                        Key.DirectionDown -> {
                            if (currentCount > 0) {
                                selectedIndex = (selectedIndex + 1).coerceAtMost(currentCount - 1)
                            }
                            true
                        }
                        Key.DirectionUp -> {
                            if (currentCount > 0) {
                                selectedIndex = (selectedIndex - 1).coerceAtLeast(0)
                            }
                            true
                        }
                        Key.DirectionRight, Key.Enter -> {
                            activateCurrentSelection()
                            true
                        }
                        Key.DirectionLeft, Key.Back, Key.Escape -> {
                            navigateBack()
                            true
                        }
                        else -> false
                    }
                } else false
            },
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
                        IconButton(onClick = { navigateBack() }) {
                            Icon(
                                imageVector = Icons.AutoMirrored.Filled.ArrowBack,
                                contentDescription = "Back",
                                tint = colors.accentCyan
                            )
                        }
                    }

                    Text(
                        text = when (val state = navState) {
                            PaletteNavState.Categories -> "Command Categories (/)"
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
                    .padding(bottom = 12.dp),
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
                    CommandSearchResultsView(
                        results = searchResults,
                        selectedIndex = selectedIndex,
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
                                    categories = categories,
                                    selectedIndex = selectedIndex,
                                    onSelectCategory = { cat -> activateCategory(cat) }
                                )
                            }
                            is PaletteNavState.Subcategories -> {
                                SubcategoryPickerList(
                                    category = state.category,
                                    subcategories = currentSubcategories,
                                    selectedIndex = selectedIndex,
                                    onSelectSubcategory = { sub ->
                                        navState = PaletteNavState.Commands(state.category, sub)
                                    }
                                )
                            }
                            is PaletteNavState.Commands -> {
                                CommandItemsList(
                                    commands = currentCommands,
                                    selectedIndex = selectedIndex,
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

            // Cyber-HUD On-Screen Directional Pad Bar (Touch & Keyboard Hybrid)
            if (navState !is PaletteNavState.Detail && currentCount > 0) {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(top = 10.dp)
                        .background(colors.cardSurface.copy(alpha = 0.95f), RoundedCornerShape(10.dp))
                        .border(1.dp, colors.accentCyan.copy(alpha = 0.4f), RoundedCornerShape(10.dp))
                        .padding(horizontal = 8.dp, vertical = 6.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.SpaceBetween
                ) {
                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        OutlinedButton(
                            onClick = { if (selectedIndex > 0) selectedIndex-- },
                            colors = ButtonDefaults.outlinedButtonColors(contentColor = colors.accentCyan),
                            border = androidx.compose.foundation.BorderStroke(1.dp, colors.accentCyan.copy(alpha = 0.5f)),
                            contentPadding = PaddingValues(horizontal = 10.dp, vertical = 4.dp)
                        ) {
                            Icon(imageVector = Icons.Default.KeyboardArrowUp, contentDescription = "Up", modifier = Modifier.size(16.dp))
                            Spacer(modifier = Modifier.width(4.dp))
                            Text("UP ▲", style = AtroposTypography.badgeLabel)
                        }

                        OutlinedButton(
                            onClick = { if (selectedIndex < currentCount - 1) selectedIndex++ },
                            colors = ButtonDefaults.outlinedButtonColors(contentColor = colors.accentCyan),
                            border = androidx.compose.foundation.BorderStroke(1.dp, colors.accentCyan.copy(alpha = 0.5f)),
                            contentPadding = PaddingValues(horizontal = 10.dp, vertical = 4.dp)
                        ) {
                            Icon(imageVector = Icons.Default.KeyboardArrowDown, contentDescription = "Down", modifier = Modifier.size(16.dp))
                            Spacer(modifier = Modifier.width(4.dp))
                            Text("DOWN ▼", style = AtroposTypography.badgeLabel)
                        }
                    }

                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        OutlinedButton(
                            onClick = { activateCurrentSelection() },
                            colors = ButtonDefaults.outlinedButtonColors(
                                containerColor = colors.accentCyan.copy(alpha = 0.2f),
                                contentColor = colors.accentCyan
                            ),
                            border = androidx.compose.foundation.BorderStroke(1.dp, colors.accentCyan),
                            contentPadding = PaddingValues(horizontal = 12.dp, vertical = 4.dp)
                        ) {
                            Icon(imageVector = Icons.Default.Check, contentDescription = "Select", modifier = Modifier.size(16.dp))
                            Spacer(modifier = Modifier.width(4.dp))
                            Text("SELECT ↵", style = AtroposTypography.badgeLabel, fontWeight = FontWeight.Bold)
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
    selectedIndex: Int,
    onSelectCategory: (CommandCategory) -> Unit
) {
    val colors = LocalCyberHudColors.current
    val listState = rememberLazyListState()

    LaunchedEffect(selectedIndex) {
        if (selectedIndex in categories.indices) {
            listState.animateScrollToItem(selectedIndex)
        }
    }

    LazyColumn(
        state = listState,
        verticalArrangement = Arrangement.spacedBy(10.dp),
        contentPadding = PaddingValues(bottom = 24.dp)
    ) {
        itemsIndexed(categories) { index, cat ->
            val count = CommandCatalogRegistry.getByCategory(cat).size
            val isSelected = index == selectedIndex

            Card(
                modifier = Modifier
                    .fillMaxWidth()
                    .then(
                        if (isSelected) Modifier.border(2.dp, colors.accentCyan, RoundedCornerShape(12.dp))
                        else Modifier.border(1.dp, colors.borderDim, RoundedCornerShape(12.dp))
                    )
                    .background(
                        if (isSelected) colors.accentCyan.copy(alpha = 0.22f) else colors.cardSurface,
                        RoundedCornerShape(12.dp)
                    )
                    .clickable { onSelectCategory(cat) },
                colors = CardDefaults.cardColors(containerColor = Color.Transparent)
            ) {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(16.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    if (isSelected) {
                        Text(
                            text = "❯❯ ",
                            color = colors.accentCyan,
                            fontWeight = FontWeight.Bold,
                            fontSize = 18.sp
                        )
                    }

                    Box(
                        modifier = Modifier
                            .size(44.dp)
                            .background(if (isSelected) colors.accentCyan.copy(alpha = 0.25f) else colors.panelSurface, CircleShape),
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
                                color = if (isSelected) colors.accentCyan else colors.textPrimary,
                                fontWeight = if (isSelected) FontWeight.Bold else FontWeight.SemiBold
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
                        tint = if (isSelected) colors.accentCyan else colors.textMuted
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
    selectedIndex: Int,
    onSelectSubcategory: (String) -> Unit
) {
    val colors = LocalCyberHudColors.current
    val listState = rememberLazyListState()

    LaunchedEffect(selectedIndex) {
        if (selectedIndex in subcategories.indices) {
            listState.animateScrollToItem(selectedIndex)
        }
    }

    LazyColumn(
        state = listState,
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

        itemsIndexed(subcategories) { index, sub ->
            val count = CommandCatalogRegistry.getBySubcategory(category, sub).size
            val isSelected = index == selectedIndex

            Card(
                modifier = Modifier
                    .fillMaxWidth()
                    .then(
                        if (isSelected) Modifier.border(2.dp, colors.accentCyan, RoundedCornerShape(12.dp))
                        else Modifier.border(1.dp, colors.borderDim, RoundedCornerShape(12.dp))
                    )
                    .background(
                        if (isSelected) colors.accentCyan.copy(alpha = 0.22f) else colors.cardSurface,
                        RoundedCornerShape(12.dp)
                    )
                    .clickable { onSelectSubcategory(sub) },
                colors = CardDefaults.cardColors(containerColor = Color.Transparent)
            ) {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(16.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.SpaceBetween
                ) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        if (isSelected) {
                            Text(
                                text = "❯❯ ",
                                color = colors.accentCyan,
                                fontWeight = FontWeight.Bold,
                                fontSize = 18.sp
                            )
                        }
                        Column {
                            Text(
                                text = sub,
                                style = AtroposTypography.hudSubhead,
                                color = if (isSelected) colors.accentCyan else colors.textPrimary,
                                fontWeight = if (isSelected) FontWeight.Bold else FontWeight.SemiBold
                            )
                            Text(
                                text = "$count commands in this category",
                                style = AtroposTypography.hudCaption,
                                color = colors.accentCyan
                            )
                        }
                    }

                    Icon(
                        imageVector = Icons.Default.ChevronRight,
                        contentDescription = "Open",
                        tint = if (isSelected) colors.accentCyan else colors.textMuted
                    )
                }
            }
        }
    }
}

@Composable
private fun CommandItemsList(
    commands: List<AtroposCommandSpec>,
    selectedIndex: Int,
    onSelect: (String) -> Unit,
    onInspect: (AtroposCommandSpec) -> Unit
) {
    val colors = LocalCyberHudColors.current
    val listState = rememberLazyListState()

    LaunchedEffect(selectedIndex) {
        if (selectedIndex in commands.indices) {
            listState.animateScrollToItem(selectedIndex)
        }
    }

    LazyColumn(
        state = listState,
        verticalArrangement = Arrangement.spacedBy(10.dp),
        contentPadding = PaddingValues(bottom = 24.dp)
    ) {
        itemsIndexed(commands) { index, cmd ->
            val isSelected = index == selectedIndex

            Card(
                modifier = Modifier
                    .fillMaxWidth()
                    .then(
                        if (isSelected) Modifier.border(2.dp, colors.accentCyan, RoundedCornerShape(12.dp))
                        else Modifier.border(1.dp, colors.borderDim, RoundedCornerShape(12.dp))
                    )
                    .background(
                        if (isSelected) colors.accentCyan.copy(alpha = 0.22f) else colors.cardSurface,
                        RoundedCornerShape(12.dp)
                    )
                    .clickable { onSelect(cmd.name) },
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
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            if (isSelected) {
                                Text(
                                    text = "❯❯ ",
                                    color = colors.accentCyan,
                                    fontWeight = FontWeight.Bold,
                                    fontSize = 18.sp
                                )
                            }
                            Text(
                                text = cmd.name,
                                style = AtroposTypography.commandPrompt,
                                color = colors.accentCyan,
                                fontWeight = FontWeight.Bold
                            )
                        }

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
    selectedIndex: Int,
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
            selectedIndex = selectedIndex,
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
