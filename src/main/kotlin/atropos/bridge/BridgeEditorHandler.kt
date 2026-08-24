/* SPDX-License-Identifier: AGPL-3.0-only */
package atropos.bridge

import atropos.bridge.http.HttpRequest
import atropos.bridge.http.HttpResponse
import atropos.bridge.http.JsonWriter
import atropos.core.security.RedactionFilter

/**
 * Thin editor-extension boundary. It exposes existing bridge projections and
 * forwards a bounded selection through the existing conversation owner.
 *
 * VS Code, JetBrains, and Neovim clients remain windows over the engine: this
 * handler owns no provider, queue, status, or orchestration state.
 */
internal class BridgeEditorHandler(
    private val context: () -> String,
    private val sendMessage: (HttpRequest) -> HttpResponse,
    private val redactionFilter: RedactionFilter = RedactionFilter()
) {
    fun context(): HttpResponse = HttpResponse.json(context())

    fun sendSelection(request: HttpRequest): HttpResponse {
        val issuedBy = value(request, "issuedBy")
        if (issuedBy.isBlank()) {
            return HttpResponse.refusal(
                403,
                "attribution-required",
                "An editor selection must identify the issuing surface.",
                "Send issuedBy=vs-code, jetbrains, neovim, or another operator identity."
            )
        }
        val selection = value(request, "selection")
        if (selection.isBlank()) {
            return HttpResponse.badRequest(
                "An editor selection needs non-empty 'selection'.",
                "POST /v1/editor/selection with selection, path, and issuedBy."
            )
        }
        if (selection.length > MAX_SELECTION_CHARS) {
            return HttpResponse.badRequest(
                "Editor selection is longer than $MAX_SELECTION_CHARS characters.",
                "Send a smaller bounded selection."
            )
        }
        val path = value(request, "path")
        if (!validRelativePath(path)) {
            return HttpResponse.refusal(
                403,
                "selection-outside-workspace",
                "Editor selection path must be relative and traversal-free.",
                "Send a workspace-relative path without '..' or a leading slash."
            )
        }
        val start = positiveOrNull(value(request, "startLine"))
        val end = positiveOrNull(value(request, "endLine"))
        if ((value(request, "startLine").isNotBlank() && start == null) ||
            (value(request, "endLine").isNotBlank() && end == null) ||
            (start != null && end != null && end < start)
        ) {
            return HttpResponse.badRequest(
                "Editor selection line range is invalid.",
                "Use positive startLine/endLine values with endLine >= startLine."
            )
        }

        val location = buildString {
            append(path)
            if (start != null) {
                append(":").append(start)
                if (end != null && end != start) append("-").append(end)
            }
        }
        val message = redactionFilter.redact("[editor selection by $issuedBy $location]\n$selection")
        val forwarded = request.copy(
            path = "/v1/message",
            query = request.query + ("text" to message)
        )
        return sendMessage(forwarded)
    }

    private fun value(request: HttpRequest, key: String): String =
        request.query[key].orEmpty().ifBlank { field(request.body, key) }.trim()

    private fun field(body: String, key: String): String =
        Regex("\\\"" + Regex.escape(key) + "\\\"\\s*:\\s*\\\"((?:[^\\\"\\\\]|\\\\.)*)\\\"")
            .find(body)?.groupValues?.getOrNull(1)
            ?.replace("\\\\n", "\n")
            ?.replace("\\\\\"", "\"")
            .orEmpty()

    private fun positiveOrNull(value: String): Int? = value.toIntOrNull()?.takeIf { it > 0 }

    private fun validRelativePath(path: String): Boolean =
        path.isNotBlank() && !path.startsWith('/') && !path.startsWith('\\') &&
            !path.split('/', '\\').contains("..")

    private companion object {
        const val MAX_SELECTION_CHARS = 8_000
    }
}
