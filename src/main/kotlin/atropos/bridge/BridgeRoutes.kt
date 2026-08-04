/* SPDX-License-Identifier: AGPL-3.0-only */
package atropos.bridge

import atropos.bridge.http.HttpResponse
import atropos.bridge.http.HttpRoute
import atropos.bridge.http.HttpRouteTable
import atropos.bridge.http.JsonWriter
import atropos.bridge.projection.CommandProjection
import atropos.bridge.projection.ProjectProjection
import atropos.bridge.projection.SixAnswersProjection
import atropos.bridge.projection.VocabularyProjection
import atropos.cli.ui.HomeStateProvider

/**
 * The read-only route set the engine exposes to its clients.
 *
 * Every route here answers a question. None of them mutate, and that is a
 * boundary rather than a milestone: the existing Next.js bridge already refuses
 * argv passthrough because the CLI can reach `/shell`, `!command` and `/cd`, so
 * an open write surface on a loopback port is remote code execution against the
 * operator's own machine. Widening this set is a deliberate act that belongs
 * with an attribution and approval flow, not a convenience edit.
 *
 * The handlers hold no logic. Each one calls an existing owner and hands the
 * result to a projection — which is what `HOE-C02`'s "no business logic in Web"
 * actually requires: the logic has to be somewhere the Web cannot reimplement.
 */
class BridgeRoutes(
    private val homeState: HomeStateProvider = HomeStateProvider(),
    private val activeProvider: () -> String = { "unknown" },
    private val sixAnswers: SixAnswersProjection = SixAnswersProjection(),
    private val projects: ProjectProjection = ProjectProjection(),
    private val commands: CommandProjection = CommandProjection(),
    private val vocabulary: VocabularyProjection = VocabularyProjection()
) {
    fun table(): HttpRouteTable {
        lateinit var table: HttpRouteTable
        table = HttpRouteTable(
            listOf(
                HttpRoute("GET", "/v1/health", "liveness and engine identity") {
                    HttpResponse.json(
                        JsonWriter.obj(
                            "ok" to JsonWriter.bool(true),
                            "engine" to JsonWriter.str("atropos"),
                            "surface" to JsonWriter.str("bridge")
                        )
                    )
                },
                HttpRoute("GET", "/v1/routes", "the routes this build exposes") {
                    HttpResponse.json(table.describe())
                },
                HttpRoute("GET", "/v1/answers", "the six continuous answers") {
                    HttpResponse.json(sixAnswers.render(capture()))
                },
                HttpRoute("GET", "/v1/projects", "durable project registry") {
                    HttpResponse.json(projects.render(capture()))
                },
                HttpRoute("GET", "/v1/commands", "command registry, palette and help sections") {
                    HttpResponse.json(commands.render())
                },
                HttpRoute("GET", "/v1/vocabulary", "status and completion vocabularies") {
                    HttpResponse.json(vocabulary.render())
                }
            )
        )
        return table
    }

    /**
     * Reads durable state once per request.
     *
     * Deliberately uncached. A cockpit that shows a cached answer is a cockpit
     * that can report a finished run as still working, and §4.1 treats a stale
     * answer presented as current as a fault rather than an optimisation.
     */
    private fun capture() = homeState.capture(activeProvider())
}
