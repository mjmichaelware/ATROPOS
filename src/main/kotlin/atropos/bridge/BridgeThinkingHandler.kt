package atropos.bridge

import atropos.bridge.http.HttpRequest
import atropos.bridge.http.HttpResponse
import atropos.bridge.projection.ThinkingProjection
import atropos.core.thinking.ThinkingDepth
import atropos.core.thinking.ThinkingRecord

internal class BridgeThinkingHandler(
    private val thinkingView: ThinkingProjection,
    private val thinking: (String) -> ThinkingRecord?
) {
    fun handle(request: HttpRequest): HttpResponse {
        val nodeId = request.query["nodeId"].orEmpty()
        if (nodeId.isBlank()) {
            return HttpResponse.badRequest(
                "Reasoning is stored per node, so a nodeId is required.",
                "GET /v1/thinking?nodeId=<id>&depth=1"
            )
        }

        val rawDepth = request.query["depth"].orEmpty()
        val depth = if (rawDepth.isBlank()) {
            ThinkingDepth.DEFAULT
        } else {
            rawDepth.toIntOrNull()?.let(ThinkingDepth::fromLevel)
                ?: return HttpResponse.badRequest(
                    "'$rawDepth' is not one of the thinking depths this build serves.",
                    "Use depth=1, 2 or 3; omitting it collapses to the outline."
                )
        }

        return HttpResponse.json(thinkingView.render(thinking(nodeId), depth))
    }
}
