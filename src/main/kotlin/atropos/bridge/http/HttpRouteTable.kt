/* SPDX-License-Identifier: AGPL-3.0-only */
package atropos.bridge.http

/** One route: an exact method and path, and the handler that answers it. */
data class HttpRoute(
    val method: String,
    val path: String,
    val summary: String,
    val handler: (HttpRequest) -> HttpResponse
)

/**
 * Exact-match routing.
 *
 * There are no path parameters and no prefix matching on purpose. A prefix
 * router on a surface reachable from a browser turns every unhandled path into
 * somebody's handler; exact match means an unknown path is a 404 and nothing
 * else. Routes are data, so [describe] can answer "what does this build
 * actually expose" without a second hand-maintained list to drift from.
 */
class HttpRouteTable(private val routes: List<HttpRoute>) {

    private val byKey: Map<String, HttpRoute> =
        routes.associateBy { key(it.method, it.path) }

    private val paths: Set<String> = routes.map { it.path }.toSet()

    fun resolve(request: HttpRequest): HttpResponse {
        byKey[key(request.method, request.path)]?.let { return it.handler(request) }
        // A known path with the wrong verb is a different fault from an unknown
        // path, and the operator can only fix the one they are told about.
        if (request.path in paths) return HttpResponse.methodNotAllowed(request.method, request.path)
        return HttpResponse.notFound(request.path)
    }

    fun describe(): String = JsonWriter.obj(
        "ok" to JsonWriter.bool(true),
        "routes" to JsonWriter.arr(
            routes.map {
                JsonWriter.obj(
                    "method" to JsonWriter.str(it.method),
                    "path" to JsonWriter.str(it.path),
                    "summary" to JsonWriter.str(it.summary)
                )
            }
        )
    )

    private fun key(method: String, path: String) = "${method.uppercase()} $path"
}
