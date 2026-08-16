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
import com.atropos.android.app.bridge.AndroidEngineBridge
import com.atropos.android.app.bridge.SendOutcome
import com.atropos.android.app.ui.ConversationScreen
import com.atropos.android.app.ui.ChatListEntry
import com.atropos.android.app.ui.ComposerOutbox
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

    ConversationScreen(
        messages = state.messages,
        isOnline = state.isOnline,
        onSendMessage = { text ->
            scope.launch {
                when (val outcome = withContext(Dispatchers.IO) { repository.send(text) }) {
                    is SendOutcome.Delivered -> {
                        mvi.dispatch(MobileAppIntent.TranscriptLoaded(mvi.state.value.messages + outcome.turns))
                        mvi.dispatch(MobileAppIntent.ReachabilityChanged(true))
                    }
                    is SendOutcome.Refused -> mvi.dispatch(MobileAppIntent.Notice(localNotice("Refused: ${outcome.detail}")))
                    SendOutcome.EngineUnreachable -> {
                        // Queued, not lost. The composer has always said a
                        // message would queue while the engine was down; this
                        // is the code that makes that true.
                        mvi.dispatch(MobileAppIntent.ReachabilityChanged(false))
                        mvi.dispatch(MobileAppIntent.MessageQueued(text))
                        mvi.dispatch(MobileAppIntent.Notice(localNotice("Engine not reachable — message queued and will send on reconnect.")))
                    }
                }
            }
        },
        sessions = state.sessionTabs.tabs,
        onSessionSelected = { id -> mvi.dispatch(MobileAppIntent.SessionSelected(id)) },
        checkpoint = state.checkpoint,
        thinking = state.thinking,
        onThinkingDepthRequested = { depth ->
            scope.launch {
                val nodeId = mvi.state.value.checkpoint?.nodeId.orEmpty()
                mvi.dispatch(MobileAppIntent.ThinkingLoaded(withContext(Dispatchers.IO) { repository.thinking(nodeId, depth) }))
            }
        },
        answers = state.answers,
        approvals = state.approvals,
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
                        mvi.dispatch(MobileAppIntent.ApprovalRemoved(
                            outcome.id,
                            localNotice(
                                "Approval ${outcome.id}: " +
                                    if (outcome.approved) "approved." else "rejected."
                            )
                        ))
                    }
                    is ApprovalOutcome.Refused ->
                        mvi.dispatch(MobileAppIntent.Notice(localNotice("Approval not recorded: ${outcome.detail}")))
                    ApprovalOutcome.EngineUnreachable -> {
                        mvi.dispatch(MobileAppIntent.ReachabilityChanged(false))
                        mvi.dispatch(MobileAppIntent.Notice(localNotice("Engine not reachable — the approval decision was not recorded.")))
                    }
                }
            }
        },
        activeProvider = state.activeProvider,
        queuedNotice = state.outbox.describe(),
        selfHostRun = selfHostRun,
        selfHostBusy = selfHostBusy,
        onBuildRequested = { prompt ->
            scope.launch {
                selfHostBusy = true
                val outcome = withContext(Dispatchers.IO) {
                    repository.startSelfHost(prompt, DECIDED_BY)
                }
                selfHostBusy = false
                when (outcome) {
                    is SelfHostOutcome.Started -> {
                        selfHostRun = outcome.run
                        mvi.dispatch(
                            MobileAppIntent.Notice(
                                localNotice(
                                    "Build opened: ${outcome.run.goalId}. " +
                                        "Nothing is written until you take the next step."
                                )
                            )
                        )
                    }
                    is SelfHostOutcome.Advanced -> selfHostRun = outcome.run
                    is SelfHostOutcome.Refused ->
                        mvi.dispatch(MobileAppIntent.Notice(localNotice("Build refused: ${outcome.detail}")))
                    SelfHostOutcome.EngineUnreachable -> {
                        mvi.dispatch(MobileAppIntent.ReachabilityChanged(false))
                        mvi.dispatch(MobileAppIntent.Notice(localNotice("Engine not reachable — no build was started.")))
                    }
                }
            }
        },
        onAdvanceBuild = { goalId ->
            scope.launch {
                selfHostBusy = true
                val outcome = withContext(Dispatchers.IO) { repository.advanceSelfHost(goalId) }
                selfHostBusy = false
                when (outcome) {
                    is SelfHostOutcome.Advanced -> selfHostRun = outcome.run
                    is SelfHostOutcome.Started -> selfHostRun = outcome.run
                    is SelfHostOutcome.Refused -> {
                        // The refusal is shown and the run is re-read: the goal
                        // may have completed or hit a gate, and the panel must
                        // reflect which rather than freezing on the last step.
                        mvi.dispatch(MobileAppIntent.Notice(localNotice("Build step refused: ${outcome.detail}")))
                        withContext(Dispatchers.IO) { repository.selfHostStatus(goalId) }
                            ?.let { selfHostRun = it }
                    }
                    SelfHostOutcome.EngineUnreachable -> {
                        mvi.dispatch(MobileAppIntent.ReachabilityChanged(false))
                        mvi.dispatch(MobileAppIntent.Notice(localNotice("Engine not reachable — the build did not advance.")))
                    }
                }
            }
        },
        onDismissBuild = { selfHostRun = null },
        onCommand = { command ->
            scope.launch {
                // Echoed as the operator's own turn first, so the transcript
                // reads the way the terminal does: what was typed, then what
                // came back.
                mvi.dispatch(
                    MobileAppIntent.TranscriptLoaded(
                        mvi.state.value.messages + MobileMessage(
                            id = "cmd-${System.nanoTime()}",
                            text = command,
                            isUser = true,
                            timestamp = System.currentTimeMillis()
                        )
                    )
                )
                when (val outcome = withContext(Dispatchers.IO) { repository.runCommand(command, DECIDED_BY) }) {
                    is CommandOutcome.Ran ->
                        mvi.dispatch(
                            MobileAppIntent.Notice(
                                localNotice(outcome.output.ifBlank { "(the command produced no output)" })
                            )
                        )
                    is CommandOutcome.Refused ->
                        mvi.dispatch(MobileAppIntent.Notice(localNotice("Refused: ${outcome.detail}")))
                    CommandOutcome.EngineUnreachable -> {
                        mvi.dispatch(MobileAppIntent.ReachabilityChanged(false))
                        mvi.dispatch(MobileAppIntent.MessageQueued(command))
                        mvi.dispatch(
                            MobileAppIntent.Notice(
                                localNotice("Engine not reachable — command queued and will send on reconnect.")
                            )
                        )
                    }
                }
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
