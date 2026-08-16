package atropos.core.observability

import atropos.core.AtroposConfig
import atropos.core.AtroposRepoRootLocator
import atropos.core.agent.AgentRunService
import atropos.core.agent.GoalContinuationService
import atropos.core.agent.GoalRunRecord
import atropos.core.dag.DagExecutionService
import atropos.core.dag.DagNode
import atropos.core.journal.EventCategory
import atropos.core.journal.EventJournalService
import atropos.core.memory.LocalMemoryStore
import atropos.core.security.RedactionFilter
import java.io.OutputStream
import java.net.InetSocketAddress
import java.nio.channels.AsynchronousServerSocketChannel
import java.nio.channels.AsynchronousSocketChannel
import java.nio.charset.StandardCharsets
import java.nio.file.Files
import java.nio.file.Path
import java.time.Instant
import java.util.concurrent.CopyOnWriteArrayList
import java.util.concurrent.Executors
import java.util.concurrent.atomic.AtomicBoolean

data class RunObserverState(
    val dashboardPort: Int = 4197,
    val running: Boolean = false,
    val connectedClients: Int = 0,
    val lastError: String? = null
)

class RunObserver(
    private val config: AtroposConfig = AtroposConfig.load(),
    private val repoRoot: Path = AtroposRepoRootLocator.resolve(),
    private val runService: AgentRunService = AgentRunService(config),
    private val continuationService: GoalContinuationService = GoalContinuationService(repoRoot),
    private val dagService: DagExecutionService = DagExecutionService(config, repoRoot),
    private val journal: EventJournalService = EventJournalService(repoRoot),
    private val memoryStore: LocalMemoryStore = LocalMemoryStore(repoRoot.resolve(".atropos/memory").toFile())
) {
    private val running = AtomicBoolean(false)
    private val serverRef = java.util.concurrent.atomic.AtomicReference<AsynchronousServerSocketChannel?>(null)
    private val lastErrorRef = java.util.concurrent.atomic.AtomicReference<String?>(null)
    private val clients = CopyOnWriteArrayList<AsynchronousSocketChannel>()
    private val executor = Executors.newCachedThreadPool()
    private var port = 4197

    fun start(desiredPort: Int = 4197): String {
        if (running.get()) return "observer already running on port $port"
        port = desiredPort
        lastErrorRef.set(null)
        running.set(true)
        executor.submit { runServer() }
        return "observer starting on 127.0.0.1:$port"
    }

    fun stop(): String {
        if (!running.getAndSet(false)) return "observer not running"
        serverRef.getAndSet(null)?.close()
        clients.forEach { client ->
            runCatching { client.close() }
        }
        clients.clear()
        return "observer stopped"
    }

    fun status(): RunObserverState = RunObserverState(
        dashboardPort = port,
        running = running.get(),
        connectedClients = clients.size,
        lastError = lastErrorRef.get()
    )

    fun listRuns(limit: Int = 20): String = buildString {
        val runIds = journal.latestRunIds(limit)
        if (runIds.isEmpty()) {
            appendLine("no journaled runs recorded")
            return@buildString
        }
        appendLine("journaled runs:")
        runIds.forEach { runId ->
            val summary = journal.summary(runId)
            if (summary == null) {
                appendLine("  $runId")
            } else {
                appendLine("  $runId events=${summary.eventCount} last=${summary.lastEvent ?: "none"}")
            }
        }
    }.trimEnd()

    /**
     * Returns a bounded transcript window for the CLI and bridge projections.
     * The journal remains the durable owner; virtualization only controls the
     * amount presented to a surface and therefore cannot create a second log.
     */
    fun transcript(runId: String, limit: Int = 100): String {
        val raw = journal.transcript(runId, limit)
        if (raw.isBlank()) return raw
        return VirtualizedLogEngine
            .getLogWindow(raw.lines(), offset = 0, limit = limit.coerceAtLeast(0))
            .joinToString(System.lineSeparator())
    }

    fun diffLog(runId: String, limit: Int = 50): String = buildString {
        val events = journal.diffEvents(runId, limit)
        if (events.isEmpty()) {
            appendLine("no diff events for run: $runId")
            return@buildString
        }
        events.forEach { appendLine(it.render()) }
    }.trimEnd()

    fun testLog(runId: String, limit: Int = 50): String = buildString {
        val events = journal.testEvents(runId, limit)
        if (events.isEmpty()) {
            appendLine("no test events for run: $runId")
            return@buildString
        }
        events.forEach { appendLine(it.render()) }
    }.trimEnd()

    fun tree(runId: String): String = buildString {
        val summary = journal.summary(runId)
        if (summary == null) {
            appendLine("no events for run: $runId")
            return@buildString
        }
        appendLine("run: $runId")
        appendLine("events: ${summary.eventCount}")
        appendLine("first: ${summary.firstEvent}")
        appendLine("last: ${summary.lastEvent}")
        appendLine("categories:")
        summary.categories.entries.sortedByDescending { it.value }.forEach { (cat, count) ->
            appendLine("  $cat: $count")
        }
    }.trimEnd()

    private fun runServer() {
        try {
            val server = AsynchronousServerSocketChannel.open()
            server.bind(InetSocketAddress("127.0.0.1", port))
            serverRef.set(server)
            while (running.get()) {
                val client = server.accept().get()
                clients.add(client)
                executor.submit { handleClient(client) }
            }
        } catch (e: Exception) {
            if (running.get()) {
                lastErrorRef.set(e.message ?: "unknown error")
                running.set(false)
                memoryStore.rememberDetailed(
                    kind = atropos.core.memory.MemoryKind.NOTE,
                    title = "observer server error",
                    body = e.message ?: "unknown error",
                    tags = listOf("observer", "error")
                )
            }
        }
    }

    private fun handleClient(client: AsynchronousSocketChannel) {
        try {
            val sseHeader = buildString {
                appendLine("HTTP/1.1 200 OK")
                appendLine("Content-Type: text/event-stream")
                appendLine("Cache-Control: no-cache")
                appendLine("Connection: keep-alive")
                appendLine("Access-Control-Allow-Origin: *")
                appendLine()
            }
            client.write(java.nio.ByteBuffer.wrap(sseHeader.toByteArray(StandardCharsets.UTF_8))).get()

            while (running.get() && client.isOpen) {
                val dashboard = buildDashboardHtml()
                val sse = buildString {
                    append("event: dashboard\n")
                    append("data: ")
                    append(dashboard.replace("\n", "\\n"))
                    append("\n\n")
                    append("event: heartbeat\n")
                    append("data: ${Instant.now()}\n\n")
                }
                client.write(java.nio.ByteBuffer.wrap(sse.toByteArray(StandardCharsets.UTF_8))).get()
                Thread.sleep(2000)
            }
        } catch (_: Exception) {
            // client disconnected
        } finally {
            clients.remove(client)
            runCatching { client.close() }
        }
    }

    private fun buildDashboardHtml(): String = buildString {
        appendLine("ATROPOS Observer Dashboard")
        appendLine("Port: $port")
        appendLine("Clients: ${clients.size}")
        appendLine("Running: ${running.get()}")
        appendLine("---")
        val runs = journal.latestRunIds(5)
        if (runs.isNotEmpty()) {
            appendLine("Recent runs:")
            runs.forEach { runId ->
                val summary = journal.summary(runId)
                if (summary == null) {
                    appendLine("  $runId")
                } else {
                    appendLine("  $runId: events=${summary.eventCount} last=${summary.lastEvent ?: "none"}")
                }
            }
        }
        val dags = dagService.listDags()
        if (dags.isNotEmpty()) {
            appendLine("DAGs:")
            dags.forEach { dag ->
                val status = dagService.status(dag.id)
                appendLine("  ${dag.id}: ${dag.label} nodes=${dag.nodes.size}")
            }
        }
        appendLine("---")
        appendLine("Last updated: ${Instant.now()}")
    }
}
