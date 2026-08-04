/* SPDX-License-Identifier: AGPL-3.0-only */
package atropos.bridge.http

/**
 * One HTTP response, before it becomes bytes.
 *
 * Kept as data so a handler can be tested without a socket, and so the single
 * place that serialises a response ([HttpResponseWriter]) is also the single
 * place that can be audited for what leaves the process.
 */
data class HttpResponse(
    val status: Int,
    val body: String,
    val contentType: String = "application/json; charset=utf-8",
    val headers: Map<String, String> = emptyMap()
) {
    companion object {
        fun json(body: String): HttpResponse = HttpResponse(200, body)

        /**
         * A typed refusal.
         *
         * Source Doc 4 §4.1 requires a failure to state why and what to do about
         * it, so a refusal carries a remedy rather than a bare status code.
         */
        fun refusal(status: Int, reason: String, detail: String, remedy: String): HttpResponse =
            HttpResponse(
                status = status,
                body = JsonWriter.obj(
                    "ok" to JsonWriter.bool(false),
                    "reason" to JsonWriter.str(reason),
                    "detail" to JsonWriter.str(detail),
                    "remedy" to JsonWriter.str(remedy)
                )
            )

        fun notFound(path: String): HttpResponse = refusal(
            404,
            "unknown-route",
            "No engine route serves '$path'.",
            "Call GET /v1/routes for the routes this build actually exposes."
        )

        fun methodNotAllowed(method: String, path: String): HttpResponse = refusal(
            405,
            "method-not-allowed",
            "'$method' is not accepted on '$path'.",
            "Check GET /v1/routes for the method each route accepts."
        )

        fun badRequest(detail: String, remedy: String): HttpResponse =
            refusal(400, "bad-request", detail, remedy)
    }
}
