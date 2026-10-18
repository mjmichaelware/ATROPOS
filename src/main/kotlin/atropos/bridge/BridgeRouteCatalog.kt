/* SPDX-License-Identifier: AGPL-3.0-only */
package atropos.bridge

import atropos.bridge.http.HttpResponse
import atropos.bridge.http.HttpRoute
import atropos.bridge.http.HttpRouteTable
import atropos.bridge.http.JsonWriter
import atropos.bridge.menu.BridgeMenuCatalog
import atropos.core.phase20.GovernanceMetrics
import atropos.core.welcome.WelcomeArtifact
import java.time.Instant

/**
 * Catalog builder for Bridge HTTP routes, decoupling route definitions from BridgeRoutes state management.
 */
internal object BridgeRouteCatalog {

    fun buildGovernanceAndLifecycleRoutes(
        governanceView: atropos.bridge.projection.GovernanceProjection,
        storageView: atropos.bridge.projection.StorageProjection,
        checkpointView: atropos.bridge.projection.CheckpointProjection,
        recoveryView: atropos.bridge.projection.RecoveryProjection,
        activityView: atropos.bridge.projection.ActivityProjection,
        exportView: atropos.bridge.projection.ExportProjection,
        welcomeView: atropos.bridge.projection.WelcomeProjection,
        proposals: () -> List<atropos.core.phase20.ImprovementProposal>,
        amendments: () -> List<atropos.core.phase20.AuthorityAmendment>,
        observationPeriods: () -> List<atropos.core.phase20.ObservationPeriod>,
        governanceCounts: () -> atropos.core.phase20.GovernanceCounts,
        storage: () -> atropos.core.storage.StorageConstitution?,
        checkpoint: () -> atropos.core.checkpoint.CheckpointSummary?,
        recoverySnapshot: () -> atropos.core.recovery.StateSnapshot?,
        activity: () -> atropos.core.monitor.ActivityStream,
        exportResolver: () -> atropos.core.artifact.export.ArtifactLandingResolver?,
        exportTerritory: () -> List<java.nio.file.Path>,
        freeProviders: () -> List<String>,
        clock: () -> Instant
    ): List<HttpRoute> = listOf(
        HttpRoute("GET", "/v1/proposals", "self-improvement proposals and cooldowns") {
            HttpResponse.json(
                governanceView.renderProposals(proposals(), observationPeriods(), clock())
            )
        },
        HttpRoute("GET", "/v1/amendments", "accepted authority amendments") {
            HttpResponse.json(governanceView.renderAmendments(amendments()))
        },
        HttpRoute("GET", "/v1/metrics", "governance metric dashboard") {
            HttpResponse.json(governanceView.renderMetrics(GovernanceMetrics(governanceCounts())))
        },
        HttpRoute("GET", "/v1/storage", "storage constitution and reclaimable bytes") {
            storage()?.let { HttpResponse.json(storageView.render(it)) }
                ?: HttpResponse.refusal(
                    503,
                    "storage-unmeasured",
                    "No storage ceiling is declared for this runtime.",
                    "Declare a ceiling before relying on storage reporting; an undeclared ceiling is not an unlimited one."
                )
        },
        HttpRoute("GET", "/v1/checkpoint", "the resume checkpoint and its primary action") {
            HttpResponse.json(checkpointView.render(checkpoint(), clock()))
        },
        HttpRoute("GET", "/v1/recovery", "durable restart recovery state for the recovery ribbon") {
            HttpResponse.json(recoveryView.render(recoverySnapshot()))
        },
        HttpRoute("GET", "/v1/activity", "one ordered stream of pipeline state changes") {
            HttpResponse.json(activityView.render(activity()))
        },
        HttpRoute("GET", "/v1/exports", "landing zones an export may actually use") {
            exportResolver()?.let { HttpResponse.json(exportView.render(it, exportTerritory())) }
                ?: HttpResponse.refusal(
                    503,
                    "export-unrooted",
                    "This runtime has no repository root, so no landing zone can be resolved.",
                    "Open a workspace before exporting; an unresolved zone is not the current directory."
                )
        },
        HttpRoute("GET", "/v1/welcome", "deterministic first-boot welcome") {
            HttpResponse.json(
                welcomeView.render(
                    WelcomeArtifact(freeProviders(), storage()?.ceilingBytes)
                )
            )
        }
    )

    fun buildWorkspaceAndEvidenceRoutes(
        routes: BridgeRoutes,
        workspaceView: atropos.bridge.projection.WorkspaceProjection,
        visualComparisonView: atropos.bridge.projection.VisualComparisonProjection,
        factoryPreviewView: atropos.bridge.projection.FactoryPreviewProjection,
        evidenceLedgerView: atropos.bridge.projection.EvidenceLedgerProjection,
        reproducibilityView: atropos.bridge.projection.ReproducibilityProjection,
        territoryView: atropos.bridge.projection.TerritoryProjection,
        authorityView: atropos.bridge.projection.AuthorityProjection,
        thinkingHandler: BridgeThinkingHandler,
        statusHandler: BridgeStatusHandler,
        editorHandler: BridgeEditorHandler,
        evidenceHandler: BridgeEvidenceHandler,
        workspaceHandler: BridgeWorkspaceHandler,
        eventsHandler: BridgeEventsHandler,
        filesHandler: BridgeFilesHandler,
        mcpHandler: BridgeMcpHandler,
        computerUseHandler: BridgeComputerUseHandler,
        attestations: () -> List<atropos.core.auth.AttestationResult>,
        cascade: () -> List<atropos.core.auth.CascadeResolution>,
        quotaSummary: () -> String,
        repositoryBinding: () -> atropos.core.project.RepositoryBinding
    ): List<HttpRoute> = listOf(
        HttpRoute("GET", "/v1/workspace/tree", "project file tree") { _ ->
            val binding = repositoryBinding()
            HttpResponse.json(workspaceView.renderTree(binding))
        },
        HttpRoute("GET", "/v1/workspace/file", "read a file from the project") { request ->
            val path = request.query["path"] ?: ""
            val binding = repositoryBinding()
            if (path.isBlank()) {
                HttpResponse.refusal(400, "missing-path", "Query parameter 'path' is required", "Provide a 'path' query parameter")
            } else {
                HttpResponse.json(workspaceView.readFile(binding, path))
            }
        },
        HttpRoute("POST", "/v1/workspace/file", "write a file to the project") { request ->
            val body = request.bodyJsonOrNull()
            val path = body?.getString("path") ?: ""
            val content = body?.getString("content") ?: ""
            val binding = repositoryBinding()
            if (path.isBlank()) {
                HttpResponse.refusal(400, "missing-path", "Request body must contain 'path'", "Include 'path' in request body")
            } else {
                HttpResponse.json(workspaceView.writeFile(binding, path, content))
            }
        },
        HttpRoute("GET", "/v1/terminal", "terminal WebSocket (PTY)") { _ ->
            HttpResponse.refusal(
                426,
                "upgrade-required",
                "This endpoint requires a WebSocket upgrade",
                "Connect via WebSocket to /v1/terminal"
            )
        },
        HttpRoute("POST", "/v1/visual/compare", "compare two screenshots") { request ->
            val body = request.bodyJsonOrNull()
            val baseline = body?.getString("baseline") ?: ""
            val current = body?.getString("current") ?: ""
            if (baseline.isBlank() || current.isBlank()) {
                HttpResponse.refusal(400, "missing-path", "Request body must contain 'baseline' and 'current' paths", "Provide 'baseline' and 'current' paths in request body")
            } else {
                HttpResponse.json(visualComparisonView.compare(baseline, current))
            }
        },
        HttpRoute("GET", "/v1/preview", "factory live preview state") { _ ->
            HttpResponse.json(factoryPreviewView.render(null))
        },
        HttpRoute("GET", "/v1/evidence/ledger", "evidence ledger browser") { _ ->
            HttpResponse.json(evidenceLedgerView.render(
                atropos.core.evaluation.EvidenceStore(),
                atropos.core.phase20.EvidenceLedger()
            ))
        },
        HttpRoute("POST", "/v1/reproducibility", "evaluate or snapshot reproducibility") { request ->
            val body = request.bodyJsonOrNull()
            val action = body?.getString("action") ?: "evaluate"
            if (action == "snapshot") {
                val files: List<String> = (body?.getJSONArray("files")?.toList() as? List<Any>)?.mapNotNull { it?.toString() } ?: emptyList()
                HttpResponse.json(reproducibilityView.snapshot(files))
            } else {
                val filesObj = body?.getJSONObject("files")
                val files = if (filesObj != null) {
                    val list = mutableListOf<String>()
                    val iterator = filesObj.keys()
                    while (iterator.hasNext()) {
                        list.add(iterator.next())
                    }
                    list.mapNotNull { k ->
                        val v = filesObj.getString(k)
                        if (v != null) k to v else null
                    }.toMap()
                } else emptyMap()
                HttpResponse.json(reproducibilityView.evaluate(files))
            }
        },
        HttpRoute("GET", "/v1/territory", "list all territory assignments") { _ ->
            HttpResponse.json(territoryView.renderAssignments(atropos.core.territory.TerritoryService()))
        },
        HttpRoute("GET", "/v1/territory/check", "check if a path is within territory") { request ->
            val path = request.query["path"] ?: ""
            if (path.isBlank()) {
                HttpResponse.refusal(400, "missing-path", "Query parameter 'path' is required", "Provide a 'path' query parameter")
            } else {
                HttpResponse.json(territoryView.checkMembership(atropos.core.territory.TerritoryService(), path))
            }
        },
        HttpRoute("GET", "/v1/territory/violations", "list territory violations") { _ ->
            HttpResponse.json(territoryView.renderViolations(atropos.core.territory.TerritoryService()))
        },
        HttpRoute("GET", "/v1/authority", "which authority is in force and whether it is intact") {
            HttpResponse.json(authorityView.render(attestations(), cascade()))
        },
        HttpRoute("GET", "/v1/thinking", "stored reasoning at the requested depth") { request ->
            thinkingHandler.handle(request)
        },
        HttpRoute("GET", "/v1/status", "composite engine liveness and cockpit status") {
            statusHandler.getStatus()
        },
        HttpRoute("GET", "/v1/editor/context", "editor extension status, answers, and checkpoint") { _ ->
            editorHandler.getContext()
        },
        HttpRoute("POST", "/v1/editor/selection", "send a bounded editor selection to the engine") { request ->
            editorHandler.sendSelection(request)
        },
        HttpRoute("GET", "/v1/quota", "provider quota and billing metadata") {
            HttpResponse.json(quotaSummary())
        },
        HttpRoute("GET", "/v1/evidence", "durable evidence contents") { request ->
            evidenceHandler.getEvidence(request)
        },
        HttpRoute("GET", "/v1/workspace/tree", "bounded project file tree") { request ->
            workspaceHandler.tree(request)
        },
        HttpRoute("GET", "/v1/workspace/file", "read one bounded workspace file") { request ->
            workspaceHandler.file(request)
        },
        HttpRoute("PUT", "/v1/workspace/file", "write one bounded workspace file") { request ->
            workspaceHandler.file(request)
        },
        HttpRoute("GET", "/v1/workspace/territory", "workspace path and surface territory membership") { request ->
            workspaceHandler.territory(request)
        },
        HttpRoute("GET", "/v1/events", "cursor-based poll for event hub notifications") { request ->
            eventsHandler.getEvents(request)
        },
        HttpRoute("GET", "/v1/events/stream", "session-scoped event stream") {
            HttpResponse.refusal(
                400,
                "stream-required",
                "/v1/events/stream is a server-sent event stream.",
                "Open it with an EventSource, optionally using ?session=<session-id>."
            )
        },
        HttpRoute("POST", "/v1/files", "base64 file upload under session folder") { request ->
            filesHandler.upload(request)
        },
        HttpRoute("GET", "/v1/files", "list uploads under session folder") { request ->
            filesHandler.list(request)
        },
        HttpRoute("POST", "/v1/mcp/judge", "evaluate MCP action proposal") { request ->
            mcpHandler.judge(request)
        },
        HttpRoute("POST", "/v1/mcp/call", "call one bounded local MCP tool") { request ->
            mcpHandler.call(request)
        },
        HttpRoute("GET", "/v1/mcp/status", "configured MCP server health") {
            mcpHandler.status()
        },
        HttpRoute("POST", "/v1/computer-use/judge", "evaluate computer-use action proposal") { request ->
            computerUseHandler.judge(request)
        },
        HttpRoute("GET", "/v1/answers/stream", "six continuous answers, pushed") {
            HttpResponse.refusal(
                400,
                "stream-required",
                "/v1/answers/stream is a server-sent event stream.",
                "Open it with an EventSource, or call GET /v1/answers for a single snapshot."
            )
        }
    )
}
