/* SPDX-License-Identifier: AGPL-3.0-only */
package atropos.core.endpoint

import org.json.JSONObject

/**
 * ATOM EX12: THUNDER CLIENT POSTMAN-STYLE GUI ROUTE TESTER
 * Path: src/main/kotlin/atropos/core/endpoint/VisualApiRouteTester.kt
 *
 * Clean visual API testing interface embedded within the IDE, crafting requests,
 * configuring parameters and auth headers, testing payloads, and inspecting formatted bodies.
 */
data class VisualApiRequestSpec(
    val id: String,
    val name: String,
    val method: String,
    val endpointUrl: String,
    val queryParams: Map<String, String> = emptyMap(),
    val headers: Map<String, String> = emptyMap(),
    val bodyJson: String? = null
)

data class VisualApiResponseView(
    val status: Int,
    val timeMillis: Long,
    val sizeBytes: Long,
    val formattedBody: String,
    val headers: Map<String, String>
)

class VisualApiRouteTester {
    fun buildFullUrl(spec: VisualApiRequestSpec): String {
        if (spec.queryParams.isEmpty()) return spec.endpointUrl
        val queryString = spec.queryParams.entries.joinToString("&") { (k, v) ->
            "${java.net.URLEncoder.encode(k, "UTF-8")}=${java.net.URLEncoder.encode(v, "UTF-8")}"
        }
        val separator = if (spec.endpointUrl.contains("?")) "&" else "?"
        return "${spec.endpointUrl}$separator$queryString"
    }

    fun formatResponseJson(rawBody: String): String {
        val trimmed = rawBody.trim()
        return try {
            if (trimmed.startsWith("{")) {
                JSONObject(trimmed).toString(2)
            } else if (trimmed.startsWith("[")) {
                org.json.JSONArray(trimmed).toString(2)
            } else {
                trimmed
            }
        } catch (_: Exception) {
            trimmed
        }
    }

    companion object {
        val defaultTester = VisualApiRouteTester()
    }
}
