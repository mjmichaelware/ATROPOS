/* SPDX-License-Identifier: AGPL-3.0-only */
package atropos.bridge

import atropos.bridge.http.HttpRequest
import atropos.bridge.http.HttpResponse
import atropos.bridge.http.JsonWriter
import atropos.core.policy.PortCommandPolicy
import atropos.core.security.RedactionFilter

/**
 * Runs one CLI command on behalf of a client reached over a port.
 *
 * This is the route that makes the phone and the browser equal to the terminal.
 * Before it, a client could watch the engine, converse with it and decide
 * approvals — but anything expressed as a command (`/factory a notes app`,
 * `/verify wide`, `/agent queue list`) meant walking to the CLI. That is the
 * switching cost ATROPOS was built to remove, paid inside ATROPOS itself.
 *
 * What may run is [PortCommandPolicy]'s decision, not this handler's. The
 * shell family is refused there, once, for every surface — a second list here
 * would be a second answer to the same question, and the two only have to
 * disagree once for a port to reach `sh`.
 *
 * ## Output is the engine's, verbatim
 *
 * The command runs against a real renderer and its output is returned
 * unrewritten. A bridge that summarised would be reimplementing the CLI's
 * presentation on the other side of a socket, which is exactly the drift
 * `HOE-C02` forbids: the two surfaces would start describing the same run
 * differently.
 */
internal class BridgeCommandHandler(
    /**
     * Runs a command line and returns what the engine printed.
     *
     * Injected because a route table must stay constructible without a
     * provider, a config and a terminal. `AtroposBridge.server()` binds the
     * real router.
     */
    private val run: (String) -> BridgeCommandOutput,
    private val redactionFilter: RedactionFilter = RedactionFilter()
) {

    fun execute(request: HttpRequest): HttpResponse {
        val command = value(request, "command")
        val issuedBy = value(request, "issuedBy")

        if (command.isBlank()) {
            return HttpResponse.badRequest(
                "Running a command needs a 'command'.",
                "POST /v1/command with {\"command\":\"/status\",\"issuedBy\":\"<who>\"}"
            )
        }
        // Attribution for the same reason an approval decision needs it: a
        // command reached over a port can mutate the operator's workspace, and
        // "which surface did this" is the first question asked afterwards.
        if (issuedBy.isBlank()) {
            return HttpResponse.refusal(
                403,
                "attribution-required",
                "A command run over a port must name who issued it.",
                "Send issuedBy=<operator or surface>; an unattributed command cannot be audited."
            )
        }

        return when (val verdict = PortCommandPolicy.evaluate(command)) {
            is PortCommandPolicy.Verdict.Refused -> HttpResponse.refusal(
                403,
                "command-not-permitted",
                verdict.reason,
                verdict.remedy
            )

            is PortCommandPolicy.Verdict.Allowed -> {
                val output = runCatching { run(verdict.normalized) }.getOrElse { failure ->
                    // A command that threw is reported as a failed command, not
                    // as a broken bridge. The operator needs to know their
                    // command failed and why; a 500 tells them neither.
                    return HttpResponse.json(
                        JsonWriter.obj(
                            "ok" to JsonWriter.bool(false),
                            "command" to JsonWriter.str(verdict.normalized),
                            "output" to JsonWriter.str(""),
                            "failure" to JsonWriter.str(
                                "${failure.javaClass.simpleName}: ${redactionFilter.compact(failure.message.orEmpty())}"
                            )
                        )
                    )
                }
                HttpResponse.json(
                    JsonWriter.obj(
                        "ok" to JsonWriter.bool(true),
                        "command" to JsonWriter.str(verdict.normalized),
                        "output" to JsonWriter.str(redactionFilter.redact(output.text)),
                        "exited" to JsonWriter.bool(output.exited)
                    )
                )
            }
        }
    }

    /** The families this surface accepts, so a client can render its own menu. */
    fun allowed(): HttpResponse = HttpResponse.json(
        JsonWriter.obj(
            "ok" to JsonWriter.bool(true),
            "families" to JsonWriter.strArr(PortCommandPolicy.allowedFamilies()),
            "forbidden" to JsonWriter.strArr(PortCommandPolicy.FORBIDDEN_FAMILIES.sorted())
        )
    )

    private fun value(request: HttpRequest, key: String): String =
        request.query[key].orEmpty().ifBlank { field(request.body, key) }.trim()

    private fun field(body: String, key: String): String {
        Regex("\"" + Regex.escape(key) + "\"\\s*:\\s*\"((?:[^\"\\\\]|\\\\.)*)\"")
            .find(body)
            ?.let {
                return it.groupValues[1]
                    .replace("\\n", "\n")
                    .replace("\\\"", "\"")
                    .replace("\\\\", "\\")
            }
        return body.split('&')
            .firstOrNull { it.substringBefore('=') == key }
            ?.substringAfter('=', "")
            ?.let { java.net.URLDecoder.decode(it, Charsets.UTF_8) }
            .orEmpty()
    }
}
