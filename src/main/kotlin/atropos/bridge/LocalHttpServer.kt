/* SPDX-License-Identifier: AGPL-3.0-only */
package atropos.bridge

import atropos.core.contract.AtroposView
import java.util.concurrent.ConcurrentHashMap

data class Session(val id: String, val createdAt: Long)

class LocalHttpServer(
    val host: String = "127.0.0.1",
    val port: Int = 8080,
    private val authPassword: String? = null
) {
    private val sessions = ConcurrentHashMap<String, Session>()
    private val events = mutableListOf<String>()

    fun authenticate(password: String): Boolean {
        if (authPassword == null) return true
        return authPassword == password
    }

    fun handleRequest(path: String, method: String, headers: Map<String, String>): HttpResponse {
        if (authPassword != null) {
            val authHeader = headers["Authorization"] ?: return HttpResponse(401, "Unauthorized")
            if (authHeader != "Bearer $authPassword") return HttpResponse(401, "Unauthorized")
        }

        return when {
            path == "/v1/session" && method == "POST" -> {
                val id = "sess-${System.nanoTime()}"
                sessions[id] = Session(id, System.currentTimeMillis())
                HttpResponse(200, "{\"session_id\":\"$id\"}")
            }
            path.startsWith("/v1/session/") && method == "GET" -> {
                val id = path.removePrefix("/v1/session/")
                val s = sessions[id] ?: return HttpResponse(404, "Not Found")
                HttpResponse(200, "{\"id\":\"${s.id}\",\"createdAt\":${s.createdAt}}")
            }
            path == "/v1/events" && method == "GET" -> {
                HttpResponse(200, "event: message\ndata: ${events.joinToString(",")}\n\n", "text/event-stream")
            }
            path == "/v1/views" && method == "GET" -> {
                HttpResponse(200, "{\"views\":${AtroposView.values().map { "\"$it\"" }}}")
            }
            else -> HttpResponse(404, "Route Not Found")
        }
    }

    fun pushEvent(data: String) {
        events.add(data)
    }
}

data class HttpResponse(
    val statusCode: Int,
    val body: String,
    val contentType: String = "application/json"
)
