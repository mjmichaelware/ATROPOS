/* SPDX-License-Identifier: AGPL-3.0-only */
package atropos.bridge

import atropos.bridge.http.HttpRequest
import atropos.bridge.http.HttpResponse
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
        if (!PortCommandPolicy.isPermitted(sanitized)) {
            val rule = PortCommandPolicy.prohibitionReason(sanitized)
            return HttpResponse.refusal(
                403,
                "command-forbidden-by-policy",
                "Command rejected by PortCommandPolicy: $sanitized",
                "Policy rule: $rule"
            )
        }

        val result = commandRunner(sanitized)
        val filteredOutput = redactionFilter.filter(result.stdout)
        val filteredError = redactionFilter.filter(result.stderr)

        return HttpResponse.ok(
            BridgeCommandExecutionReceipt(
                command = sanitized,
                issuedBy = issuedBy,
                exitCode = result.exitCode,
                stdout = filteredOutput,
                stderr = filteredError,
                isSimulated = false,
                transportMode = "CORE_ENGINE_DIRECT"
            ).toJson()
        )
    }
}

data class BridgeCommandExecutionReceipt(
    val command: String,
    val issuedBy: String,
    val exitCode: Int,
    val stdout: String,
    val stderr: String,
    val isSimulated: Boolean,
    val transportMode: String
) {
    fun toJson(): String {
        val escape = { s: String -> s.replace("\"", "\\\"").replace("\n", "\\n").replace("\r", "\\r") }
        return """{"command":"${escape(command)}","issuedBy":"${escape(issuedBy)}","exitCode":$exitCode,"stdout":"${escape(stdout)}","stderr":"${escape(stderr)}","isSimulated":$isSimulated,"transportMode":"$transportMode"}"""
    }
}
