/* SPDX-License-Identifier: AGPL-3.0-only */
package com.atropos.android.app.bridge

import com.atropos.android.app.ui.MobileMessage
import com.atropos.android.app.ui.ChatListEntry

/** What a send attempt did, so the UI can say something true about it. */
sealed class SendOutcome {
    data class Delivered(val turns: List<MobileMessage>) : SendOutcome()
    data class Refused(val detail: String) : SendOutcome()
    object EngineUnreachable : SendOutcome()
}

/**
 * What an approval decision did.
 *
 * Three outcomes rather than a boolean, for the same reason [SendOutcome] has
 * three: "the engine refused this decision" and "the engine never heard it"
 * demand different things of the operator, and collapsing them would leave a
 * decision they believe they made sitting undecided.
 */
sealed class ApprovalOutcome {
    data class Recorded(val id: String, val approved: Boolean) : ApprovalOutcome()
    data class Refused(val detail: String) : ApprovalOutcome()
    object EngineUnreachable : ApprovalOutcome()
}

/**
 * The app's whole conversation with the engine.
 *
 * The client keeps no transcript of its own. The engine's `/v1/messages` is the
 * one record, so a second copy here could disagree with it after a dropped
 * request — and the operator would have no way to tell which was real.
 */
class AndroidEngineBridge(
    private val discovery: BridgeDiscovery = BridgeDiscovery(),
    private val http: BridgeHttpApi = DefaultBridgeHttpApi
) {

    fun isOnline(): Boolean = discovery.resolve() != null

    /** The full transcript, oldest first. Empty when the engine is unreachable. */
    fun transcript(): List<MobileMessage> {
        val port = discovery.resolve() ?: return emptyList()
        return when (val result = http.get(BridgeEndpoint.url(port, "/v1/messages"))) {
            is BridgeResult.Ok -> BridgeTurnParser.parse(result.body)
            else -> emptyList()
        }
    }

    fun sessions(): List<ChatListEntry> {
        val port = discovery.resolve() ?: return emptyList()
        return when (val result = http.get(BridgeEndpoint.url(port, "/v1/sessions"))) {
            is BridgeResult.Ok -> BridgeSessionParser.parse(result.body)
            else -> emptyList()
        }
    }

    fun checkpoint(): MobileCheckpoint? {
        val port = discovery.resolve() ?: return null
        return when (val result = http.get(BridgeEndpoint.url(port, "/v1/checkpoint"))) {
            is BridgeResult.Ok -> CheckpointParser.parse(result.body)
            else -> null
        }
    }

    fun thinking(nodeId: String, depth: Int = 1): MobileThinking? {
        if (nodeId.isBlank()) return null
        val port = discovery.resolve() ?: return null
        val encoded = java.net.URLEncoder.encode(nodeId, Charsets.UTF_8.name())
        return when (val result = http.get(BridgeEndpoint.url(port, "/v1/thinking?nodeId=$encoded&depth=$depth"))) {
            is BridgeResult.Ok -> ThinkingParser.parse(result.body)
            else -> null
        }
    }

    /**
     * The six continuous answers — objective, doing, why, progress, next,
     * evidence.
     *
     * `SixAnswersParser` existed with nothing to feed it: the engine has served
     * `/v1/answers` all along and no client method fetched it, so the phone
     * showed an empty screen next to an engine that could describe its own
     * state in six lines. Null when unreachable or unreadable, never a
     * placeholder set of answers.
     */
    fun sixAnswers(): MobileSixAnswers? {
        val port = discovery.resolve() ?: return null
        return when (val result = http.get(BridgeEndpoint.url(port, "/v1/answers"))) {
            is BridgeResult.Ok -> SixAnswersParser.parse(result.body)
            else -> null
        }
    }

    /**
     * The provider the engine is actually using, from the same payload as the
     * answers.
     *
     * Read from the engine rather than remembered locally. A client that cached
     * the provider would keep showing the last one it saw after the operator
     * switched in the CLI, and the two surfaces would disagree about which
     * model answered.
     */
    fun activeProvider(): String? {
        val port = discovery.resolve() ?: return null
        return when (val result = http.get(BridgeEndpoint.url(port, "/v1/answers"))) {
            is BridgeResult.Ok ->
                runCatching { org.json.JSONObject(result.body).optString("provider") }
                    .getOrNull()
                    ?.takeIf { it.isNotBlank() }
            else -> null
        }
    }

    /** Actions the engine has stopped on, waiting for a person. */
    fun approvals(): List<MobileApproval> {
        val port = discovery.resolve() ?: return emptyList()
        return when (val result = http.get(BridgeEndpoint.url(port, "/v1/approvals"))) {
            is BridgeResult.Ok -> ApprovalParser.parse(result.body).filter { it.pending }
            else -> emptyList()
        }
    }

    /**
     * Records a decision on a pending approval.
     *
     * @param decidedBy who made the call. The engine refuses an unattributed
     *   decision with 403 rather than accepting it anonymously — an approval
     *   nobody is named for cannot be audited, and that is the whole point of
     *   stopping for one. The client must therefore always send it.
     */
    fun decideApproval(id: String, approved: Boolean, decidedBy: String): ApprovalOutcome {
        if (id.isBlank()) return ApprovalOutcome.Refused("an approval decision needs an id")
        if (decidedBy.isBlank()) {
            return ApprovalOutcome.Refused("an approval decision must name who made it")
        }
        val port = discovery.resolve() ?: return ApprovalOutcome.EngineUnreachable
        val body = "{\"id\":${JsonString.quote(id)}," +
            "\"approved\":$approved," +
            "\"decidedBy\":${JsonString.quote(decidedBy)}}"
        return when (val result = http.post(BridgeEndpoint.url(port, "/v1/approvals/decide"), body)) {
            is BridgeResult.Ok -> ApprovalOutcome.Recorded(id, approved)
            is BridgeResult.HttpError -> ApprovalOutcome.Refused(
                BridgeTurnParser.detail(result.body).ifBlank { "refused (${result.code})" }
            )
            is BridgeResult.Unreachable -> {
                discovery.forget()
                ApprovalOutcome.EngineUnreachable
            }
        }
    }

    fun send(text: String): SendOutcome {
        val port = discovery.resolve() ?: return SendOutcome.EngineUnreachable
        val body = "{\"text\":${JsonString.quote(text)}}"
        return when (val result = http.post(BridgeEndpoint.url(port, "/v1/message"), body)) {
            is BridgeResult.Ok -> SendOutcome.Delivered(BridgeTurnParser.parse(result.body))
            is BridgeResult.HttpError -> SendOutcome.Refused(
                BridgeTurnParser.detail(result.body).ifBlank { "refused (${result.code})" }
            )
            is BridgeResult.Unreachable -> {
                // The port answered health a moment ago and has now gone; drop
                // it so the next call rediscovers instead of retrying a dead one.
                discovery.forget()
                SendOutcome.EngineUnreachable
            }
        }
    }
}

/** Seam so the repository can be tested without a socket. */
interface BridgeHttpApi {
    fun get(url: String): BridgeResult
    fun post(url: String, body: String): BridgeResult
}

object DefaultBridgeHttpApi : BridgeHttpApi {
    override fun get(url: String): BridgeResult = AndroidBridge.get(url)
    override fun post(url: String, body: String): BridgeResult = AndroidBridge.post(url, body)
}
