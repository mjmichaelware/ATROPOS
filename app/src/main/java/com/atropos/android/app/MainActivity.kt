/* SPDX-License-Identifier: AGPL-3.0-only */
package com.atropos.android.app

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.AccountTree
import androidx.compose.material.icons.filled.Code
import androidx.compose.material.icons.filled.Info
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material.icons.filled.Terminal
import androidx.compose.material.icons.filled.Widgets
import androidx.compose.material3.Icon
import androidx.compose.material3.NavigationBar
import androidx.compose.material3.NavigationBarItem
import androidx.compose.material3.NavigationBarItemDefaults
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.atropos.android.app.about.AboutSovereignManifestScreen
import com.atropos.android.app.bridge.AndroidEngineBridge
import com.atropos.android.app.bridge.ApprovalOutcome
import com.atropos.android.app.bridge.BridgeResult
import com.atropos.android.app.bridge.CommandOutcome
import com.atropos.android.app.bridge.DirectInProcessEngineBridge
import com.atropos.android.app.bridge.SendOutcome
import com.atropos.android.app.command.CommandPaletteOverlay
import com.atropos.android.app.console.ClaudeCodeWorkstationScreen
import com.atropos.android.app.ide.MobileVsCodeScreen
import com.atropos.android.app.ide.VirtualFileEntry
import com.atropos.android.app.settings.SettingsMatrixScreen
import com.atropos.android.app.specgraph.SpecGraphMobileViewer
import com.atropos.android.app.theme.AtroposCyberTheme
import com.atropos.android.app.theme.AtroposTypography
import com.atropos.android.app.theme.LocalCyberHudColors
import com.atropos.android.app.ui.MobileAppIntent
import com.atropos.android.app.ui.MobileAppMviStore
import com.atropos.android.app.ui.MobileMessage
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

/**
 * ATROPOS Sovereign Android Application.
 *
 * Implements a dual-personality mobile IDE and autonomous console:
 * - Claude Code conversational workstation with real-time reasoning drawer
 * - Mobile VS Code IDE with file tree, syntactic editor, and unified diff viewer
 * - Non-overload Slash (/) Command Category Launcher
 * - SpecGraph Foundry lattice visualizer
 * - Multi-column Settings Matrix
 * - Cryptographic Sovereignty Manifest
 */
class MainActivity : ComponentActivity() {

    private val repository = AndroidEngineBridge()
    private val inProcessBridge = DirectInProcessEngineBridge()

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContent {
            AtroposCyberTheme {
                Surface(
                    modifier = Modifier.fillMaxSize(),
                    color = LocalCyberHudColors.current.voidAbyss
                ) {
                    ComposeAppShell(repository)
                }
            }
        }
    }
}

@Composable
private fun ComposeAppShell(repository: AndroidEngineBridge) {
    val colors = LocalCyberHudColors.current
    val mvi = remember { MobileAppMviStore() }
    val state by mvi.state.collectAsState()
    val scope = rememberCoroutineScope()

    // Active Navigation Tab
    var currentTab by remember { mutableStateOf(0) }
    var isCommandPaletteOpen by remember { mutableStateOf(false) }

    // VS Code Editor State
    var activeFilePath by remember { mutableStateOf("src/main/kotlin/atropos/Main.kt") }
    var activeFileContent by remember {
        mutableStateOf(
            """/* SPDX-License-Identifier: AGPL-3.0-only */
package atropos

import atropos.core.AtroposConfig
import atropos.core.autonomous.AutonomousOrchestrator

fun main(args: Array<String>) {
    println("ATROPOS Sovereign Engine Initializing...")
    val config = AtroposConfig.load()
    val orchestrator = AutonomousOrchestrator(config)
    orchestrator.start()
}
"""
        )
    }
    var originalFileContent by remember { mutableStateOf(activeFileContent) }

    val sampleFileTree = remember {
        listOf(
            VirtualFileEntry(
                path = "src",
                name = "src",
                isDirectory = true,
                children = listOf(
                    VirtualFileEntry(
                        path = "src/main",
                        name = "main",
                        isDirectory = true,
                        children = listOf(
                            VirtualFileEntry(
                                path = "src/main/kotlin",
                                name = "kotlin",
                                isDirectory = true,
                                children = listOf(
                                    VirtualFileEntry("src/main/kotlin/atropos/Main.kt", "Main.kt", false, sizeBytes = 384),
                                    VirtualFileEntry("src/main/kotlin/atropos/core/Config.kt", "Config.kt", false, sizeBytes = 1420),
                                    VirtualFileEntry("src/main/kotlin/atropos/core/Router.kt", "Router.kt", false, sizeBytes = 2890)
                                )
                            )
                        )
                    ),
                    VirtualFileEntry(
                        path = "src/test",
                        name = "test",
                        isDirectory = true,
                        children = listOf(
                            VirtualFileEntry("src/test/kotlin/atropos/RouterTest.kt", "RouterTest.kt", false, sizeBytes = 940)
                        )
                    )
                )
            ),
            VirtualFileEntry("build.gradle.kts", "build.gradle.kts", false, sizeBytes = 3210),
            VirtualFileEntry("settings.gradle.kts", "settings.gradle.kts", false, sizeBytes = 850),
            VirtualFileEntry("README.md", "README.md", false, sizeBytes = 4120)
        )
    }

    val diagnostics = remember { mutableStateListOf<String>() }

    val tabs = listOf(
        TabItem(0, "Console", Icons.Default.Terminal),
        TabItem(1, "VS Code", Icons.Default.Code),
        TabItem(2, "Commands", Icons.Default.Widgets),
        TabItem(3, "SpecGraph", Icons.Default.AccountTree),
        TabItem(4, "Settings", Icons.Default.Settings),
        TabItem(5, "Manifest", Icons.Default.Info)
    )

    Box(modifier = Modifier.fillMaxSize()) {
        Column(
            modifier = Modifier.fillMaxSize(),
            verticalArrangement = Arrangement.spacedBy(0.dp)
        ) {
            // Main View Area
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .weight(1f)
            ) {
                when (currentTab) {
                    0 -> ClaudeCodeWorkstationScreen(
                        messages = state.messages,
                        isOnline = state.isOnline,
                        onSendMessage = { text ->
                            scope.launch(Dispatchers.IO) {
                                when (val outcome = repository.send(text)) {
                                    is SendOutcome.Delivered -> {
                                        mvi.dispatch(MobileAppIntent.TranscriptLoaded(mvi.state.value.messages + outcome.turns))
                                        mvi.dispatch(MobileAppIntent.QueueHeadDelivered)
                                    }
                                    is SendOutcome.Refused -> mvi.dispatch(MobileAppIntent.Notice(localNotice("Refused: ${outcome.detail}")))
                                    SendOutcome.EngineUnreachable -> mvi.dispatch(MobileAppIntent.Notice(localNotice("Engine bridge unreachable")))
                                }
                            }
                        },
                        onOpenCommandPalette = { isCommandPaletteOpen = true },
                        onInterrupt = {
                            scope.launch(Dispatchers.IO) {
                                repository.runCommand("/cancel", DECIDED_BY)
                            }
                        },
                        answers = state.answers,
                        thinking = state.thinking,
                        approvals = state.approvals,
                        onApprove = { id, approve ->
                            scope.launch(Dispatchers.IO) {
                                repository.decideApproval(id, approve, DECIDED_BY)
                                mvi.dispatch(MobileAppIntent.ApprovalRemoved(id, localNotice("Decision recorded")))
                            }
                        },
                        activeProvider = state.activeProvider ?: "claude-3-7-sonnet"
                    )

                    1 -> MobileVsCodeScreen(
                        currentFile = activeFilePath,
                        fileContent = activeFileContent,
                        originalContent = originalFileContent,
                        fileTree = sampleFileTree,
                        onOpenFile = { entry ->
                            activeFilePath = entry.path
                            scope.launch(Dispatchers.IO) {
                                when (val outcome = repository.readWorkspaceFile(entry.path)) {
                                    is com.atropos.android.app.bridge.WorkspaceFileOutcome.Read -> {
                                        withContext(Dispatchers.Main) {
                                            activeFileContent = outcome.content
                                            originalFileContent = outcome.content
                                        }
                                    }
                                    else -> {
                                        withContext(Dispatchers.Main) {
                                            activeFileContent = "// Buffer: ${entry.path}\n// Local cached mode."
                                            originalFileContent = activeFileContent
                                        }
                                    }
                                }
                            }
                        },
                        onSaveFile = { path, newContent ->
                            activeFileContent = newContent
                            originalFileContent = newContent
                            scope.launch(Dispatchers.IO) {
                                repository.writeWorkspaceFile(path, newContent)
                            }
                        },
                        onRefreshTree = {
                            scope.launch(Dispatchers.IO) {
                                repository.fetchWorkspaceTree()
                            }
                        },
                        diagnosticsList = diagnostics
                    )

                    2 -> CommandPaletteOverlay(
                        isOpen = true,
                        onClose = { currentTab = 0 },
                        onSelectCommand = { cmd ->
                            currentTab = 0
                        },
                        onExecuteCommand = { cmd ->
                            currentTab = 0
                            scope.launch(Dispatchers.IO) {
                                when (val outcome = repository.runCommand(cmd, DECIDED_BY)) {
                                    is CommandOutcome.Ran -> mvi.dispatch(MobileAppIntent.Notice(localNotice(outcome.output)))
                                    is CommandOutcome.Refused -> mvi.dispatch(MobileAppIntent.Notice(localNotice("Refused: ${outcome.detail}")))
                                    CommandOutcome.EngineUnreachable -> mvi.dispatch(MobileAppIntent.Notice(localNotice("Bridge unreachable")))
                                }
                            }
                        }
                    )

                    3 -> SpecGraphMobileViewer(
                        onOpenVercelProduction = {
                            // Opens the independent SpecGraph Vercel production deployment
                        }
                    )

                    4 -> SettingsMatrixScreen(
                        currentHost = "127.0.0.1",
                        currentPort = 4317,
                        onSaveBridgeSettings = { host, port ->
                            // Update bridge endpoint configuration
                        },
                        onTestPing = { host, port, callback ->
                            scope.launch(Dispatchers.IO) {
                                val start = System.currentTimeMillis()
                                val reachable = repository.isOnline()
                                val latency = System.currentTimeMillis() - start
                                withContext(Dispatchers.Main) {
                                    callback(reachable, latency)
                                }
                            }
                        }
                    )

                    5 -> AboutSovereignManifestScreen()
                }
            }

            // Bottom Navigation Bar
            NavigationBar(
                modifier = Modifier
                    .fillMaxWidth()
                    .height(60.dp),
                containerColor = colors.panelSurface
            ) {
                tabs.forEachIndexed { index, tab ->
                    val selected = currentTab == index
                    NavigationBarItem(
                        selected = selected,
                        onClick = { currentTab = index },
                        icon = {
                            Icon(
                                imageVector = tab.icon,
                                contentDescription = tab.label,
                                modifier = Modifier.size(20.dp)
                            )
                        },
                        label = {
                            Text(
                                text = tab.label,
                                style = AtroposTypography.monoGutter,
                                fontSize = 10.sp,
                                fontWeight = if (selected) FontWeight.Bold else FontWeight.Normal
                            )
                        },
                        colors = NavigationBarItemDefaults.colors(
                            selectedIconColor = colors.accentCyan,
                            selectedTextColor = colors.accentCyan,
                            unselectedIconColor = colors.textMuted,
                            unselectedTextColor = colors.textMuted,
                            indicatorColor = colors.accentCyan.copy(alpha = 0.15f)
                        )
                    )
                }
            }
        }

        // Global Command Palette Overlay (if triggered from Workstation or shortcut)
        if (isCommandPaletteOpen && currentTab != 2) {
            CommandPaletteOverlay(
                isOpen = true,
                onClose = { isCommandPaletteOpen = false },
                onSelectCommand = { cmd ->
                    isCommandPaletteOpen = false
                },
                onExecuteCommand = { cmd ->
                    isCommandPaletteOpen = false
                    scope.launch(Dispatchers.IO) {
                        repository.runCommand(cmd, DECIDED_BY)
                    }
                }
            )
        }
    }

    // Engine Polling Loop
    LaunchedEffect(Unit) {
        while (true) {
            val online = withContext(Dispatchers.IO) { repository.isOnline() }
            val wasOnline = mvi.state.value.isOnline

            mvi.dispatch(MobileAppIntent.ReachabilityChanged(online))
            if (online) {
                mvi.dispatch(MobileAppIntent.AnswersLoaded(withContext(Dispatchers.IO) { repository.sixAnswers() }))
                mvi.dispatch(MobileAppIntent.ApprovalsLoaded(withContext(Dispatchers.IO) { repository.approvals() }))
                mvi.dispatch(MobileAppIntent.ProviderLoaded(withContext(Dispatchers.IO) { repository.activeProvider() }))
            } else {
                mvi.dispatch(MobileAppIntent.AnswersLoaded(null))
                mvi.dispatch(MobileAppIntent.ApprovalsLoaded(emptyList()))
            }

            if (online && !wasOnline) {
                val transcript = withContext(Dispatchers.IO) { repository.transcript() }
                mvi.dispatch(MobileAppIntent.TranscriptLoaded(transcript))
                mvi.dispatch(MobileAppIntent.SessionsLoaded(withContext(Dispatchers.IO) { repository.sessions() }))
            }

            delay(POLL_INTERVAL_MS)
        }
    }
}

private data class TabItem(
    val index: Int,
    val label: String,
    val icon: androidx.compose.ui.graphics.vector.ImageVector
)

private fun localNotice(text: String) = MobileMessage(
    id = "local-${System.nanoTime()}",
    text = text,
    isUser = false,
    timestamp = System.currentTimeMillis()
)

private const val POLL_INTERVAL_MS = 3_000L
private const val DECIDED_BY = "android-client"
