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
 * The app's whole conversation with the engine.
 *
 * The client keeps no transcript of its own. The engine's `/v1/messages` is the
 * one record, so a second copy here could disagree with it after a dropped
 * request — and the operator would have no way to tell which was real.
 */
class ConversationRepository(
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
    override fun get(url: String): BridgeResult = BridgeHttp.get(url)
    override fun post(url: String, body: String): BridgeResult = BridgeHttp.post(url, body)
}
