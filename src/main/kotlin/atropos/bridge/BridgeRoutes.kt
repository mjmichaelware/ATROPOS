/* SPDX-License-Identifier: AGPL-3.0-only */
package atropos.bridge

import atropos.bridge.conversation.BridgeConversationResponder
import atropos.bridge.conversation.BridgeConversationStore
import atropos.bridge.conversation.BridgeSessionStore
import atropos.bridge.conversation.UnwiredConversationResponder
import atropos.bridge.menu.BridgeMenuCatalog
import atropos.bridge.projection.CommandMenuProjection
import atropos.bridge.queue.ConversationWorkRunner
import atropos.bridge.http.HttpResponse
import atropos.bridge.http.HttpRoute
import atropos.bridge.http.HttpRouteTable
import atropos.bridge.http.HttpStreamRoute
import atropos.bridge.http.JsonWriter
import atropos.bridge.projection.ActivityProjection
import atropos.bridge.projection.ApprovalProjection
import atropos.bridge.projection.AuthorityProjection
import atropos.bridge.projection.CheckpointProjection
import atropos.bridge.projection.CommandProjection
import atropos.bridge.projection.ExportProjection
import atropos.bridge.projection.GovernanceProjection
import atropos.bridge.projection.StorageProjection
import atropos.bridge.projection.ThinkingProjection
import atropos.bridge.projection.ProjectProjection
import atropos.bridge.projection.SixAnswersProjection
import atropos.bridge.projection.VocabularyProjection
import atropos.bridge.projection.WelcomeProjection
import atropos.bridge.projection.RecoveryProjection
import atropos.cli.ui.HomeStateProvider
import atropos.core.approval.PendingApprovalStore
import atropos.core.artifact.export.ArtifactLandingResolver
import atropos.core.auth.AttestationResult
import atropos.core.auth.CascadeResolution
import atropos.core.checkpoint.CheckpointSummary
import atropos.core.monitor.ActivityStream
import atropos.core.phase20.AuthorityAmendment
import atropos.core.phase20.GovernanceCounts
import atropos.core.phase20.GovernanceMetrics
import atropos.core.phase20.ImprovementProposal
import atropos.core.phase20.ObservationPeriod
import atropos.core.storage.StorageConstitution
import atropos.core.thinking.ThinkingRecord
import atropos.core.welcome.WelcomeArtifact
import atropos.core.parity.SurfaceParityProbe
import atropos.core.recovery.StateSnapshot
import java.time.Instant

/**
 * The route set the engine exposes to its clients.
 *
 * Reads answer a question and mutate nothing. The four writes — approval
 * decisions, a conversation turn, and running or cancelling queued work — are
 * each deliberate, and each is narrow in the same specific way: none of them
 * accepts a command, a path or an argv.
 *
 * That restriction is the whole boundary. The CLI can reach `/shell`,
 * `!command` and `/cd`, so a route that passed text through to it would be
 * remote code execution against the operator's own machine over a loopback
 * port. A conversation turn becomes *queued work*, which inherits the attempt
 * limits, policy gate and evidence trail every CLI-originated task passes
 * through; running an entry advances work that already exists and was already
 * admitted. Widening this further — a command surface, a file write — is a
 * decision that belongs with an attribution and approval flow, not a
 * convenience edit.
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
    private val vocabulary: VocabularyProjection = VocabularyProjection(),
    private val approvals: PendingApprovalStore = PendingApprovalStore(),
    private val approvalView: ApprovalProjection = ApprovalProjection(),
    private val governanceView: GovernanceProjection = GovernanceProjection(),
    private val storageView: StorageProjection = StorageProjection(),
    private val checkpointView: CheckpointProjection = CheckpointProjection(),
    private val activityView: ActivityProjection = ActivityProjection(),
    private val exportView: ExportProjection = ExportProjection(),
    private val welcomeView: WelcomeProjection = WelcomeProjection(),
    private val thinkingView: ThinkingProjection = ThinkingProjection(),
    private val authorityView: AuthorityProjection = AuthorityProjection(),
    /**
     * Governance state sources.
     *
     * Injected as suppliers rather than read from a store here so the routes
     * stay constructible without a repository root — a test checking one
     * projection should not have to own a filesystem. `AtroposBridge` binds
     * them to the durable [atropos.core.phase20.GovernanceLedger] for the
     * running engine.
     *
     * The defaults are empty, which is the truthful answer for a system that
     * has not proposed anything. What must never happen is a placeholder
     * proposal appearing because the surface wanted something to render.
     */
    private val proposals: () -> List<ImprovementProposal> = { emptyList() },
    private val amendments: () -> List<AuthorityAmendment> = { emptyList() },
    private val observationPeriods: () -> List<ObservationPeriod> = { emptyList() },
    private val governanceCounts: () -> GovernanceCounts = { GovernanceCounts() },
    private val storage: () -> StorageConstitution? = { null },
    /**
     * The resume checkpoint, the activity stream and the export landing zones.
     *
     * Suppliers for the same reason as the governance state above: null and
     * empty are the truthful answers for a workspace that has not run anything
     * yet, and each projection renders that absence as absence. The export
     * resolver is nullable because a runtime with no repository root has no
     * landing zone to offer — refusing is correct, inventing one is not.
     */
    private val checkpoint: () -> CheckpointSummary? = { null },
    private val activity: () -> ActivityStream = { ActivityStream(emptyList()) },
    private val exportResolver: () -> ArtifactLandingResolver? = { null },
    private val exportTerritory: () -> List<java.nio.file.Path> = { emptyList() },
    /**
     * The first-boot welcome.
     *
     * Free providers are supplied rather than discovered because discovery is a
     * provider concern and a welcome that probed the network would be neither
     * deterministic nor zero-cost. An empty list is rendered honestly by the
     * artifact — claiming a free path exists when none is configured would
     * strand the operator at the first prompt.
     */
    private val freeProviders: () -> List<String> = { emptyList() },
    /**
     * Stored reasoning, looked up by node.
     *
     * Always the full record: the depth filter belongs to the read, not to the
     * lookup. A supplier that returned a shallower record for a collapsed
     * surface would make `HOE-B03`'s rule unenforceable at this boundary.
     */
    private val thinking: (String) -> ThinkingRecord? = { null },
    /**
     * Authority attestation and the resolved cascade.
     *
     * Empty by default, and an empty attestation list resolves to *not*
     * resolved — absence of a grant is never permission, so a runtime that has
     * attested nothing must not read as one operating under intact authority.
     */
    private val attestations: () -> List<AttestationResult> = { emptyList() },
    private val cascade: () -> List<CascadeResolution> = { emptyList() },
    /**
     * HOE-D02 conversation surface. Defaulted so every existing construction
     * site keeps working and so routes stay buildable without a repository:
     * the store is in memory and the responder does not execute anything until
     * an execution path is deliberately supplied.
     */
    private val conversation: BridgeConversationStore = BridgeConversationStore(),
    private val responder: BridgeConversationResponder = UnwiredConversationResponder(),
    private val parityProbe: SurfaceParityProbe = SurfaceParityProbe(),
    /**
     * Work a client can watch and advance. Null when this build was not given
     * a queue, in which case the queue routes answer 501 rather than pretending
     * an empty queue — "no work" and "no queue wired" are different facts.
     */
    private val work: ConversationWorkRunner? = null,
    /**
     * Conversations. A chat list and an explicit resume both need many, and a
     * single global transcript could express neither.
     */
    private val sessions: BridgeSessionStore = BridgeSessionStore(),
    private val menuView: CommandMenuProjection = CommandMenuProjection(),
    private val clock: () -> Instant = { Instant.now() },
    private val quotaSummary: () -> String = { "{\"readable\":false,\"reason\":\"quota-ledger-not-wired\"}" },
    private val mcpBridge: atropos.core.integration.McpTerritoryBridge = atropos.core.integration.McpTerritoryBridge(setOf("inspect", "verify")),
    /**
     * The self-build service, when this build has a repository to build in.
     *
     * Null by default for the same reason the export resolver is: a runtime
     * with no repository root has nothing to self-build, and refusing is
     * correct where inventing a workspace is not. `AtroposBridge.server()`
     * binds it for the running engine.
     */
    private val selfHost: atropos.core.agent.SelfHostGoalService? = null,
    /**
     * Runs one CLI command, when this build has a router to run it with.
     *
     * Null by default so the route table stays constructible without a
     * provider, a config and a terminal. `AtroposBridge.server()` binds it.
     */
    private val commandRunner: ((String) -> BridgeCommandOutput)? = null,
    private val mcpHost: atropos.core.integration.McpHostManager? = null,
    private val recoverySnapshot: () -> StateSnapshot? = { null },
    private val recoveryView: RecoveryProjection = RecoveryProjection()
) {
    private val approvalHandler = BridgeApprovalHandler(approvals)
    private val thinkingHandler = BridgeThinkingHandler(thinkingView, thinking)
    private val conversationHandler = BridgeConversationHandler(conversation, responder, sessions = sessions)
    private val queueHandler = work?.let { BridgeQueueHandler(it) }
    private val sessionHandler = BridgeSessionHandler(sessions)
    private val statusHandler = BridgeStatusHandler(homeState, activeProvider, sixAnswers, checkpoint, checkpointView, work, quotaSummary = quotaSummary, clock = clock)
    private val evidenceHandler = BridgeEvidenceHandler(work)
    private val eventsHandler = BridgeEventsHandler(work, approvals, sessions, conversation)
    private val filesHandler = BridgeFilesHandler()
    private val mcpHandler = BridgeMcpHandler(mcpBridge, mcpHost)
    private val computerUseHandler = BridgeComputerUseHandler()
    private val selfHostHandler = selfHost?.let { BridgeSelfHostHandler(it) }
    private val commandHandler = commandRunner?.let { BridgeCommandHandler(it) }

    /** Present either way, so what a client discovers does not change with
     *  configuration; without a router it says so rather than 404ing. */
    private fun withCommands(action: (BridgeCommandHandler) -> HttpResponse): HttpResponse =
        commandHandler?.let(action) ?: HttpResponse.refusal(
            501,
            "command-surface-not-wired",
            "This engine build did not attach a command router to the bridge.",
            "Start the engine normally; the router is wired by AtroposBridge.server()."
        )

    /** Self-build routes are present either way, so what a client discovers
     *  does not change with configuration; without a service they say plainly
     *  that this build has no repository to build in. */
    private fun withSelfHost(action: (BridgeSelfHostHandler) -> HttpResponse): HttpResponse =
        selfHostHandler?.let(action) ?: HttpResponse.refusal(
            501,
            "selfhost-not-wired",
            "This engine build has no repository bound to self-build in.",
            "Start the engine from a checkout; AtroposBridge.server() binds it."
        )

    /** Queue routes exist in the table either way, so the surface a client
     *  discovers does not change with configuration; without a runner they
     *  state plainly that this build has no queue wired. */
    private fun withQueue(action: (BridgeQueueHandler) -> HttpResponse): HttpResponse =
        queueHandler?.let(action) ?: HttpResponse.refusal(
            501,
            "queue-not-wired",
            "This engine build did not attach a work queue to the bridge.",
            "Start the engine normally; the queue is wired by AtroposBridge.server()."
        )

    fun table(): HttpRouteTable {
        lateinit var table: HttpRouteTable
        table = HttpRouteTable(
            listOf(
                HttpRoute("GET", "/v1/health", "liveness and engine identity") {
                    HttpResponse.json(
                        JsonWriter.obj(
                            "ok" to JsonWriter.bool(true),
                            "engine" to JsonWriter.str("atropos"),
                            "surface" to JsonWriter.str("bridge"),
                            "parityDanglingActions" to JsonWriter.num(parityProbe.danglingActions().size),
                            "parityForbiddenPortEntries" to JsonWriter.num(parityProbe.forbiddenOnPort().size)
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
                },
                HttpRoute("GET", "/v1/approvals", "actions waiting on a human decision") {
                    HttpResponse.json(approvalView.render(approvals.pending()))
                },
                HttpRoute("POST", "/v1/approvals/decide", "record a human approval decision") { request ->
                    approvalHandler.decideApproval(request)
                },
                HttpRoute("GET", "/v1/messages", "conversation transcript for a client surface") { request ->
                    conversationHandler.getMessages(request)
                },
                HttpRoute("POST", "/v1/message", "append an operator turn and return the engine's reply") { request ->
                    conversationHandler.postMessage(request)
                },
                HttpRoute("GET", "/v1/sessions", "conversations, or one with ?id=") { request ->
                    sessionHandler.list(request)
                },
                HttpRoute("POST", "/v1/sessions", "start a new conversation") { request ->
                    sessionHandler.create(request)
                },
                HttpRoute("GET", "/v1/sessions/recent", "the last conversation, offered not opened") {
                    sessionHandler.recent()
                },
                HttpRoute("POST", "/v1/sessions/delete", "delete a conversation by ?id=") { request ->
                    sessionHandler.delete(request)
                },
                HttpRoute("GET", "/v1/menu", "commands as selectable actions for a graphical client") {
                    HttpResponse.json(menuView.render(BridgeMenuCatalog.actions()))
                },
                HttpRoute("GET", "/v1/queue", "queued work, or one entry with ?id=") { request ->
                    withQueue { it.list(request) }
                },
                HttpRoute("POST", "/v1/queue/run", "run the next entry, or ?id= to run a named one") { request ->
                    withQueue { it.run(request) }
                },
                HttpRoute("POST", "/v1/queue/cancel", "cancel a queue entry by ?id=") { request ->
                    withQueue { it.cancel(request) }
                },
                HttpRoute("POST", "/v1/command", "run one CLI command, shell families excluded") { request ->
                    withCommands { it.execute(request) }
                },
                HttpRoute("GET", "/v1/command/allowed", "command families this surface accepts") {
                    withCommands { it.allowed() }
                },
                HttpRoute("POST", "/v1/selfhost/start", "open a self-build goal from a prompt") { request ->
                    withSelfHost { it.start(request) }
                },
                HttpRoute("POST", "/v1/selfhost/advance", "run one advance of a self-build goal") { request ->
                    withSelfHost { it.advance(request) }
                },
                HttpRoute("GET", "/v1/selfhost/status", "the state of a self-build goal and its DAG") { request ->
                    withSelfHost { it.status(request) }
                },
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
                HttpRoute("GET", "/v1/quota", "provider quota and billing metadata") {
                    HttpResponse.json(quotaSummary())
                },
                HttpRoute("GET", "/v1/evidence", "durable evidence contents") { request ->
                    evidenceHandler.getEvidence(request)
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
                    // Advertised in /v1/routes and reachable as a stream; this
                    // request-path entry exists so a client that asks without
                    // an event-stream connection is told what it is rather
                    // than getting a 404 for a route that plainly exists.
                    HttpResponse.refusal(
                        400,
                        "stream-required",
                        "/v1/answers/stream is a server-sent event stream.",
                        "Open it with an EventSource, or call GET /v1/answers for a single snapshot."
                    )
                }
            )
        )
        return table
    }

    /**
     * The streaming half of the bridge.
     *
     * Source Doc 4 calls the six answers *continuous*, and a surface that has
     * to poll for them is showing a snapshot with a timestamp it cannot see.
     * This pushes a fresh answer set on an interval and stops the moment the
     * client leaves.
     *
     * It reuses [SixAnswersProjection] rather than shaping its own payload:
     * a stream that disagreed with `GET /v1/answers` would be a second source
     * of truth for the same six questions.
     */
    fun streamRoutes(
        intervalMillis: Long = 2_000,
        maxFrames: Int = Int.MAX_VALUE,
        sleep: (Long) -> Unit = Thread::sleep
    ): List<HttpStreamRoute> = listOf(
        HttpStreamRoute("GET", "/v1/answers/stream", "six continuous answers, pushed") { _, sink ->
            var frames = 0
            // The first frame is sent immediately: a stream that waits one
            // interval before saying anything is indistinguishable from a
            // stream that failed to start.
            while (sink.isOpen() && frames < maxFrames) {
                if (!sink.emit("answers", sixAnswers.render(capture()))) return@HttpStreamRoute
                frames += 1
                if (frames >= maxFrames) return@HttpStreamRoute
                sleep(intervalMillis)
            }
        },
        HttpStreamRoute("GET", "/v1/events/stream", "session-scoped events, pushed") { request, sink ->
            eventsHandler.streamEvents(request, sink, intervalMillis, maxFrames, sleep)
        }
    )

    /**
     * Reads durable state once per request.
     *
     * Deliberately uncached. A cockpit that shows a cached answer is a cockpit
     * that can report a finished run as still working, and §4.1 treats a stale
     * answer presented as current as a fault rather than an optimisation.
     */
    private fun capture() = homeState.capture(activeProvider())
}
