/* SPDX-License-Identifier: AGPL-3.0-only */
package atropos.server

import atropos.bridge.BridgeRoutes
import atropos.bridge.http.HttpRequest
import atropos.shared.PortableEngineState
import atropos.shared.PortableEngineReducer
import atropos.shared.PortableRunEvent
import atropos.shared.PortableRunStatus
import io.ktor.http.HttpStatusCode
import io.ktor.server.application.Application
import io.ktor.server.application.call
import io.ktor.server.engine.embeddedServer
import io.ktor.server.netty.Netty
import io.ktor.server.request.path
import io.ktor.server.request.receiveText
import io.ktor.server.response.respondText
import io.ktor.server.routing.routing

/**
 * Optional Ktor transport over the canonical bridge route table. Ktor owns
 * sockets and HTTP parsing; BridgeRoutes remains the single route and policy
 * owner, including exact-match and port-safety rules.
 */
fun Application.atroposBridgeModule(routes: BridgeRoutes = BridgeRoutes()) {
    val bridgeState = PortableEngineState(
        projectId = "bridge",
        runId = "http",
        status = PortableRunStatus.IDLE,
        activeProvider = null,
        checkpointId = null
    )
    routing {
        handle {
            val request = HttpRequest(
                method = call.request.local.method.value,
                path = call.request.path(),
                query = call.request.queryParameters.entries().associate { it.key to it.value.firstOrNull().orEmpty() },
                headers = call.request.headers.entries().associate { it.key.lowercase() to it.value.joinToString(",") },
                body = call.receiveText()
            )
            val response = routes.table().resolve(request)
            val observedState = PortableEngineReducer.reduce(bridgeState, PortableRunEvent.BeginPlanning)
            call.response.headers.append("X-Atropos-Run-State", observedState.status.name)
            call.respondText(
                text = response.body,
                contentType = io.ktor.http.ContentType.parse(response.contentType),
                status = HttpStatusCode.fromValue(response.status)
            )
        }
    }
}

fun main() {
    embeddedServer(Netty, port = System.getenv("ATROPOS_PORT")?.toIntOrNull() ?: 8787) {
        atroposBridgeModule()
    }.start(wait = true)
}
