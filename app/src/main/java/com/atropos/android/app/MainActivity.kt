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
import com.atropos.android.app.bridge.AndroidEngineBridge
import com.atropos.android.app.bridge.SendOutcome
import com.atropos.android.app.ui.ConversationScreen
import com.atropos.android.app.ui.ChatListEntry
import com.atropos.android.app.ui.ComposerOutbox
import com.atropos.android.app.ui.SessionTabModel
import com.atropos.android.app.bridge.ApprovalOutcome
import com.atropos.android.app.bridge.MobileApproval
import com.atropos.android.app.bridge.MobileCheckpoint
import com.atropos.android.app.bridge.MobileSixAnswers
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
    val messages: SnapshotStateList<MobileMessage> = remember { mutableListOf<MobileMessage>().toMutableStateList() }
    var isOnline by remember { mutableStateOf(false) }
    var sessions by remember { mutableStateOf<List<ChatListEntry>>(emptyList()) }
    var sessionTabs by remember { mutableStateOf(SessionTabModel()) }
    var checkpoint by remember { mutableStateOf<MobileCheckpoint?>(null) }
    var thinking by remember { mutableStateOf<MobileThinking?>(null) }
    var answers by remember { mutableStateOf<MobileSixAnswers?>(null) }
    var approvals by remember { mutableStateOf<List<MobileApproval>>(emptyList()) }
    var activeProvider by remember { mutableStateOf<String?>(null) }
    var outbox by remember { mutableStateOf(ComposerOutbox()) }
    val oneHandDensity = remember { com.atropos.android.app.ui.OneHandDensity() }
    val scope = rememberCoroutineScope()

    // Reachability is polled rather than assumed. The engine is a separate
    // process the operator starts and stops in Termux, so it can appear or
    // vanish at any moment and the header has to stay truthful.
    LaunchedEffect(Unit) {
        while (true) {
            val online = withContext(Dispatchers.IO) { repository.isOnline() }

            // Answers and approvals are refreshed on every tick, not only on
            // the reachability edge. They are the two things that change while
            // the engine stays up -- a run advances, an action stops for a
            // decision -- so binding them to the transition would leave the
            // screen frozen for exactly as long as the engine kept working.
            if (online) {
                answers = withContext(Dispatchers.IO) { repository.sixAnswers() }
                approvals = withContext(Dispatchers.IO) { repository.approvals() }
                activeProvider = withContext(Dispatchers.IO) { repository.activeProvider() }
            } else {
                // Cleared rather than kept. A stale answer panel beside an
                // offline badge reads as current state, and the operator would
                // act on a progress figure the engine stopped confirming.
                answers = null
                approvals = emptyList()
                activeProvider = null
            }

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

                    // The queue drains in order, one confirmed delivery at a
                    // time. The head is dropped only after the engine accepted
                    // it, so a send that fails mid-drain leaves the remaining
                    // messages queued rather than silently discarded — which
                    // is the failure the queue exists to survive.
                    while (true) {
                        val next = outbox.head() ?: break
                        val outcome = withContext(Dispatchers.IO) { repository.send(next) }
                        if (outcome is SendOutcome.Delivered) {
                            messages.addAll(outcome.turns)
                            outbox = outbox.dropHead()
                        } else {
                            if (outcome is SendOutcome.Refused) {
                                // A refusal is final for that message: the
                                // engine read it and said no, so replaying it
                                // forever would block every message behind it.
                                messages.add(localNotice("Queued message refused: ${outcome.detail}"))
                                outbox = outbox.dropHead()
                                continue
                            }
                            isOnline = false
                            break
                        }
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
                        // Queued, not lost. The composer has always said a
                        // message would queue while the engine was down; this
                        // is the code that makes that true.
                        isOnline = false
                        outbox = outbox.queue(text)
                        messages.add(
                            localNotice("Engine not reachable — message queued and will send on reconnect.")
                        )
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
        },
        answers = answers,
        approvals = approvals,
        onApprovalDecided = { id, approved ->
            scope.launch {
                val outcome = withContext(Dispatchers.IO) {
                    repository.decideApproval(id, approved, DECIDED_BY)
                }
                when (outcome) {
                    is ApprovalOutcome.Recorded -> {
                        // Removed locally so the card cannot be pressed twice
                        // while the next poll is in flight; the poll is still
                        // what re-establishes the truth.
                        approvals = approvals.filterNot { it.id == outcome.id }
                        messages.add(
                            localNotice(
                                "Approval ${outcome.id}: " +
                                    if (outcome.approved) "approved." else "rejected."
                            )
                        )
                    }
                    is ApprovalOutcome.Refused ->
                        messages.add(localNotice("Approval not recorded: ${outcome.detail}"))
                    ApprovalOutcome.EngineUnreachable -> {
                        isOnline = false
                        messages.add(
                            localNotice("Engine not reachable — the approval decision was not recorded.")
                        )
                    }
                }
            }
        },
        activeProvider = activeProvider,
        queuedNotice = outbox.describe()
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

