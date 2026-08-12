/* SPDX-License-Identifier: AGPL-3.0-only */
package com.atropos.android.app

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.runtime.snapshots.SnapshotStateList
import androidx.compose.runtime.toMutableStateList
import com.atropos.android.app.bridge.ConversationRepository
import com.atropos.android.app.bridge.SendOutcome
import com.atropos.android.app.ui.ConversationScreen
import com.atropos.android.app.ui.ChatListEntry
import com.atropos.android.app.ui.SessionTabModel
import com.atropos.android.app.bridge.MobileCheckpoint
import com.atropos.android.app.bridge.MobileThinking
import com.atropos.android.app.ui.MobileMessage
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

    private val repository = ConversationRepository()

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContent {
            MaterialTheme {
                Surface { AtroposConversation(repository) }
            }
        }
    }
}

@Composable
private fun AtroposConversation(repository: ConversationRepository) {
    val messages: SnapshotStateList<MobileMessage> = remember { mutableListOf<MobileMessage>().toMutableStateList() }
    var isOnline by remember { mutableStateOf(false) }
    var sessions by remember { mutableStateOf<List<ChatListEntry>>(emptyList()) }
    var sessionTabs by remember { mutableStateOf(SessionTabModel()) }
    var checkpoint by remember { mutableStateOf<MobileCheckpoint?>(null) }
    var thinking by remember { mutableStateOf<MobileThinking?>(null) }
    val oneHandDensity = remember { com.atropos.android.app.ui.OneHandDensity() }
    val scope = rememberCoroutineScope()

    // Reachability is polled rather than assumed. The engine is a separate
    // process the operator starts and stops in Termux, so it can appear or
    // vanish at any moment and the header has to stay truthful.
    LaunchedEffect(Unit) {
        while (true) {
            val online = withContext(Dispatchers.IO) { repository.isOnline() }
            if (online != isOnline) {
                isOnline = online
                // On reconnect the engine's transcript is authoritative; adopt
                // it rather than keeping a local view that may have diverged.
                if (online) {
                    val transcript = withContext(Dispatchers.IO) { repository.transcript() }
                    messages.clear()
                    messages.addAll(transcript)
                    sessions = withContext(Dispatchers.IO) { repository.sessions() }
                    sessionTabs = sessionTabs.replace(sessions)
                    checkpoint = withContext(Dispatchers.IO) { repository.checkpoint() }
                    thinking = checkpoint?.nodeId?.let { nodeId ->
                        withContext(Dispatchers.IO) { repository.thinking(nodeId) }
                    }
                }
            }
            oneHandDensity.offlineResume("default", sessions.firstOrNull()?.id, online)
            delay(POLL_INTERVAL_MS)
        }
    }

    ConversationScreen(
        messages = messages,
        isOnline = isOnline,
        onSendMessage = { text ->
            scope.launch {
                when (val outcome = withContext(Dispatchers.IO) { repository.send(text) }) {
                    is SendOutcome.Delivered -> {
                        messages.addAll(outcome.turns)
                        isOnline = true
                    }
                    is SendOutcome.Refused -> messages.add(localNotice("Refused: ${outcome.detail}"))
                    SendOutcome.EngineUnreachable -> {
                        isOnline = false
                        messages.add(localNotice("Engine not reachable — message not delivered."))
                    }
                }
            }
        },
        sessions = sessionTabs.tabs,
        onSessionSelected = { id -> sessionTabs = sessionTabs.select(id) },
        checkpoint = checkpoint,
        thinking = thinking,
        onThinkingDepthRequested = { depth ->
            scope.launch {
                val nodeId = checkpoint?.nodeId.orEmpty()
                thinking = withContext(Dispatchers.IO) { repository.thinking(nodeId, depth) }
            }
        }
    )
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
