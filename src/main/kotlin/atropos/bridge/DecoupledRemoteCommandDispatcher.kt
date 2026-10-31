/* SPDX-License-Identifier: AGPL-3.0-only */
package atropos.bridge

import atropos.bridge.http.HttpRequest
import atropos.bridge.http.HttpResponse
import atropos.bridge.http.JsonWriter
import atropos.core.policy.PortCommandPolicy
import atropos.core.security.RedactionFilter

/**
 * ATOM 21.1: Unified Client-Engine Command Execution Pipeline.
 *
 * Routes all commands initiated from Android or Web clients directly to the core
 * engine process. Never simulates command responses locally on client surfaces.
 * Ensures execution results, exit codes, stdout, and stderr travel back to the client
 * interface accurately without distortion.
 *
 * Research Directive: Binary multiplexing protocols for full-duplex interactive
 * terminal streaming over standard WebSocket connections on mobile networks.
 */
class DecoupledRemoteCommandDispatcher(
    private val commandRunner: (String) -> BridgeCommandOutput,
    private val redactionFilter: RedactionFilter = RedactionFilter()
) {
    data class MultiplexedChannelFrame(
        val channelId: Byte,
        val streamType: String, // STDOUT, STDERR, EXIT, CONTROL
        val sequenceNumber: Long,
        val payload: ByteArray
    ) {
        override fun equals(other: Any?): Boolean {
            if (this === other) return true
            if (other !is MultiplexedChannelFrame) return false
            return channelId == other.channelId && sequenceNumber == other.sequenceNumber
        }

        override fun hashCode(): Int = (channelId.toInt() * 31) + sequenceNumber.hashCode()
    }

    fun dispatchClientCommand(request: HttpRequest, rawCommand: String, issuedBy: String): HttpResponse {
        val sanitized = rawCommand.trim()
        return when (val verdict = PortCommandPolicy.evaluate(sanitized)) {
            is PortCommandPolicy.Verdict.Refused -> HttpResponse.refusal(
                403,
                "command-forbidden-by-policy",
                verdict.reason,
                verdict.remedy
            )

            is PortCommandPolicy.Verdict.Allowed -> {
                val output = runCatching { commandRunner(verdict.normalized) }.getOrElse { failure ->
                    return HttpResponse.json(
                        JsonWriter.obj(
                            "ok" to JsonWriter.bool(false),
                            "command" to JsonWriter.str(verdict.normalized),
                            "output" to JsonWriter.str(""),
                            "failure" to JsonWriter.str(
                                "${failure.javaClass.simpleName}: ${redactionFilter.compact(failure.message.orEmpty())}"
                            ),
                            "isSimulated" to JsonWriter.bool(false)
                        )
                    )
                }

                HttpResponse.json(
                    JsonWriter.obj(
                        "ok" to JsonWriter.bool(true),
                        "command" to JsonWriter.str(verdict.normalized),
                        "output" to JsonWriter.str(redactionFilter.redact(output.text)),
                        "exited" to JsonWriter.bool(output.exited),
                        "issuedBy" to JsonWriter.str(issuedBy),
                        "isSimulated" to JsonWriter.bool(false),
                        "transportMode" to JsonWriter.str("CORE_ENGINE_DIRECT")
                    )
                )
            }
        }
    }
}
