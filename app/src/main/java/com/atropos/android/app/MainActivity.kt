/* SPDX-License-Identifier: AGPL-3.0-only */
package com.atropos.android.app

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.navigation.NavType
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.rememberNavController
import androidx.navigation.navArgument
import com.atropos.android.app.bridge.AndroidEngineBridge
import com.atropos.android.app.bridge.SendOutcome
import com.atropos.android.app.ui.ConversationScreen
import com.atropos.android.app.ui.MobileAppIntent
import com.atropos.android.app.ui.MobileAppMviStore
import com.atropos.android.app.bridge.ApprovalOutcome
import com.atropos.android.app.bridge.MobileApproval
import com.atropos.android.app.bridge.MobileCheckpoint
import com.atropos.android.app.bridge.MobileSelfHostRun
import com.atropos.android.app.bridge.MobileSixAnswers
import com.atropos.android.app.bridge.CommandOutcome
import com.atropos.android.app.bridge.SelfHostOutcome
import com.atropos.android.app.bridge.MobileThinking
import com.atropos.android.app.ui.MobileMessage
import com.atropos.android.app.ui.ChatListScreen
import com.atropos.android.app.ui.OfflineScreen
import com.atropos.android.app.ui.SettingsScreen
import com.atropos.android.app.ui.FileTreeScreen
import com.atropos.android.app.ui.ComposerScreen
import com.atropos.android.app.ui.ConversationListScreen
import com.atropos.android.app.ui.ToolsTimelineSheet
import com.atropos.android.app.ui.ThinkingSheet
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

/**
 * HOE-D01: the app shell.
 *
 * A thin client. It holds no engine state of its own beyond what it is
 * currently drawing, and every fact it shows came from the bridge — which is
 * what "never embed full engine" means in practice.
 */
class MainActivity : ComponentActivity() {

    private val repository = AndroidEngineBridge()

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContent {
            MaterialTheme {
                Surface { ComposeAppShell(repository) }
            }
        }
    }
}

@Composable
private fun ComposeAppShell(repository: AndroidEngineBridge) {
    val navController = rememberNavController()
    val mvi = remember { MobileAppMviStore() }
    val state by mvi.state.collectAsState()

    // The build panel's state is held here rather than in [MobileAppState]
    // because it is not a projection of the engine's conversation: `selfHostBusy`
    // is true only while this screen has a request in flight, which no reducer
    // can know. `selfHostRun` sits beside it so the two move together.
    var selfHostRun by remember { mutableStateOf<MobileSelfHostRun?>(null) }
    var selfHostBusy by remember { mutableStateOf(false) }
    val oneHandDensity = remember { com.atropos.android.app.ui.OneHandDensity() }
    val scope = rememberCoroutineScope()

    NavHost(navController, startDestination = "conversation_list") {
        composable("conversation_list") {
            ConversationListScreen(
                repository = repository,
                onConversationSelected = { sessionId ->
                    navController.navigate("conversation/$sessionId")
                },
                onNewConversation = {
                    navController.navigate("conversation/new")
                }
            )
        }
        composable(
            route = "conversation/{sessionId}",
            arguments = listOf(navArgument("sessionId") { type = NavType.StringType })
        ) { backStackEntry ->
            val sessionId = backStackEntry.getString() ?: ""
            ConversationScreen(
                sessionId = sessionId,
                onBack = { navController.popBackStack() }
            )
        }
        composable("conversation/new") {
            ConversationScreen(
                sessionId = "",
                onBack = { navController.popBackStack() }
            )
        }
        composable("files") {
            FileTreeScreen(repository = repository)
        }
        composable("composer") {
            ComposerScreen(
                onBack = { navController.popBackStack() }
            )
        }
        composable("tools") {
            ToolsTimelineSheet(
                onDismiss = { navController.popBackStack() }
            )
        }
        composable("thinking") {
            ThinkingSheet(
                onDismiss = { navController.popBackStack() }
            )
        }
        composable("settings") {
            SettingsScreen(
                onNavigateBack = { navController.popBackStack() }
            )
        }
        composable("offline") {
            OfflineScreen(
                onGoOnline = { navController.popBackStack() }
            )
        }
    }

    // The build panel's state is held here rather than in [MobileAppState]
    // because it is not a projection of the engine's conversation: `selfHostBusy`
    // is true only while this screen has a request in flight, which no reducer
    // can know. `selfHostRun` sits beside it so the two move together.
    var selfHostRun by remember { mutableStateOf<MobileSelfHostRun?>(null) }
    var selfHostBusy by remember { mutableStateOf(false) }
    val oneHandDensity = remember { com.atropos.android.app.ui.OneHandDensity() }
    val scope = rememberCoroutineScope()

    // Reachability is polled rather than assumed. The engine is a separate
    // process the operator starts and stops in Termux, so it can appear or
    // vanish at any moment and the header has to stay truthful.
    LaunchedEffect(Unit) {
        while (true) {
            val online = withContext(Dispatchers.IO) { repository.isOnline() }
            val wasOnline = mvi.state.value.isOnline

            // Answers and approvals are refreshed on every tick, not only on
            // the reachability edge. They are the two things that change while
            // the engine stays up -- a run advances, an action stops for a
            // decision -- so binding them to the transition would leave the
            // screen frozen for exactly as long as the engine kept working.
            mvi.dispatch(MobileAppIntent.ReachabilityChanged(online))
            if (online) {
                mvi.dispatch(MobileAppIntent.AnswersLoaded(withContext(Dispatchers.IO) { repository.sixAnswers() }))
                mvi.dispatch(MobileAppIntent.ApprovalsLoaded(withContext(Dispatchers.IO) { repository.approvals() }))
                mvi.dispatch(MobileAppIntent.ProviderLoaded(withContext(Dispatchers.IO) { repository.activeProvider() }))
                // Refreshed from the engine rather than only from the last
                // advance's reply: the CLI may be driving the same goal, and
                // two surfaces disagreeing about a build in progress is worse
                // than a slightly stale one.
                selfHostRun?.let { current ->
                    if (!selfHostBusy) {
                        withContext(Dispatchers.IO) { repository.selfHostStatus(current.goalId) }
                            ?.let { selfHostRun = it }
                    }
                }
            } else {
                // Cleared rather than kept. A stale answer panel beside an
                // offline badge reads as current state, and the operator would
                // act on a progress figure the engine stopped confirming.
                mvi.dispatch(MobileAppIntent.AnswersLoaded(null))
                mvi.dispatch(MobileAppIntent.ApprovalsLoaded(emptyList()))
                mvi.dispatch(MobileAppIntent.ProviderLoaded(null))
            }

            if (online != wasOnline) {
                // On reconnect the engine's transcript is authoritative; adopt
                // it rather than keeping a local view that may have diverged.
                if (online) {
                    val transcript = withContext(Dispatchers.IO) { repository.transcript() }
                    mvi.dispatch(MobileAppIntent.TranscriptLoaded(transcript))
                    mvi.dispatch(MobileAppIntent.SessionsLoaded(withContext(Dispatchers.IO) { repository.sessions() }))
                    val nextCheckpoint = withContext(Dispatchers.IO) { repository.checkpoint() }
                    mvi.dispatch(MobileAppIntent.CheckpointLoaded(nextCheckpoint))
                    val nextThinking = nextCheckpoint?.nodeId?.let { nodeId ->
                        withContext(Dispatchers.IO) { repository.thinking(nodeId) }
                    }
                    mvi.dispatch(MobileAppIntent.ThinkingLoaded(nextThinking))

                    // The queue drains in order, one confirmed delivery at a
                    // time. The head is dropped only after the engine accepted
                    // it, so a send that fails mid-drain leaves the remaining
                    // messages queued rather than silently discarded — which
                    // is the failure the queue exists to survive.
                    while (true) {
                        val next = mvi.state.value.outbox.head() ?: break
                        val outcome = withContext(Dispatchers.IO) { repository.send(next) }
                        if (outcome is SendOutcome.Delivered) {
                            mvi.dispatch(MobileAppIntent.TranscriptLoaded(mvi.state.value.messages + outcome.turns))
                            mvi.dispatch(MobileAppIntent.QueueHeadDelivered)
                        } else {
                            if (outcome is SendOutcome.Refused) {
                                // A refusal is final for that message: the
                                // engine read it and said no, so replaying it
                                // forever would block every message behind it.
                                mvi.dispatch(MobileAppIntent.Notice(localNotice("Queued message refused: ${outcome.detail}")))
                                mvi.dispatch(MobileAppIntent.QueueHeadDelivered)
                                continue
                            }
                            mvi.dispatch(MobileAppIntent.ReachabilityChanged(false))
                            break
                        }
                    }
                }
            }
            oneHandDensity.offlineResume("default", mvi.state.value.sessions.firstOrNull()?.id, online)
            delay(POLL_INTERVAL_MS)
        }
    }
}

/**
 * A turn the client produced itself. Marked as engine-side so it renders in the
 * response column, but it is never confused with an engine answer because the
 * engine's own turns always arrive from the bridge with a `turn-` id.
 */
private fun localNotice(text: String) = MobileMessage(
    id = "local-${System.nanoTime()}",
    text = text,
    isUser = false,
    timestamp = System.currentTimeMillis()
)

private const val POLL_INTERVAL_MS = 3_000L

/**
 * Who the engine records as having made an approval decision from this app.
 *
 * The engine refuses an unattributed decision with 403, and it is right to: an
 * approval nobody is named for cannot be audited, which is the whole reason
 * the engine stopped to ask. This names the surface rather than a person
 * because the app has no identity yet -- it is honest about being "whoever held
 * the phone", and it must be replaced with a real operator identity before this
 * client is used by more than one person.
 */
private const val DECIDED_BY = "android-client"
