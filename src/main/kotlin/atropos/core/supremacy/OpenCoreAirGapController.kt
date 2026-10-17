package atropos.core.supremacy

import java.net.URI

data class GovernanceMode(val isLocalOnly: Boolean, val isZeroRetention: Boolean, val airGapped: Boolean)

class AirGapViolationException(message: String) : SecurityException(message)

object OpenCoreAirGapController {
    fun currentMode(): GovernanceMode {
        val env = System.getenv()
        val localOnly = env["ATROPOS_LOCAL_ONLY"] == "1" || env["ATROPOS_LOCAL_ONLY"]?.equals("true", ignoreCase = true) == true
        val zeroRet = env["ATROPOS_ZERO_RETENTION"] == "1" || env["ATROPOS_ZERO_RETENTION"]?.equals("true", ignoreCase = true) == true
        val airGap = env["ATROPOS_AIR_GAP"] == "1" || env["ATROPOS_AIR_GAP"]?.equals("true", ignoreCase = true) == true
        return GovernanceMode(isLocalOnly = localOnly, isZeroRetention = zeroRet, airGapped = airGap)
    }

    fun assertNetworkCallPermitted(targetUri: URI) {
        val mode = currentMode()
        if (!mode.airGapped && !mode.isLocalOnly) return

        val host = targetUri.host?.lowercase() ?: throw AirGapViolationException("URI missing valid host: $targetUri")
        val isLoopback = host == "localhost" || host == "127.0.0.1" || host == "::1"

        if (!isLoopback) {
            throw AirGapViolationException("Network call blocked by Open-Core Air-Gap policy: Destination [$host] is non-local.")
        }
    }

    fun appendZeroRetentionHeaders(existingHeaders: Map<String, String>): Map<String, String> {
        val mode = currentMode()
        if (!mode.isZeroRetention) return existingHeaders

        val enriched = existingHeaders.toMutableMap()
        enriched["X-Data-Retention"] = "zero"
        enriched["OpenAI-Organization-Store"] = "false"
        enriched["anthropic-beta"] = "no-log-2024-01-01"
        return enriched
    }
}
