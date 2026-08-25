package atropos.core.autonomous

import atropos.core.artifact.ArtifactPipeline
import atropos.core.artifact.ArtifactVerificationService
import atropos.core.auditor.AuditorService
import atropos.core.custodian.CustodianService
import atropos.core.dag.DAGNodeState
import atropos.core.dag.DagService
import atropos.core.dag.DagExecutionService
import atropos.core.dag.DocumentIngestionService
import atropos.core.director.DirectorService
import atropos.core.director.DirectorDagSupervisor
import atropos.core.director.DriftSeverity
import atropos.core.director.ObservationKind
import atropos.core.hierarchy.HierarchyRegistry
import atropos.core.hr.HrRouterService
import atropos.core.hr.CrossBoundaryResponse
import atropos.core.hr.InformationKind
import atropos.core.memory.LocalMemoryStore
import atropos.core.multimodal.InspectionService
import atropos.core.platform.Platform
import atropos.core.provider.ProviderTruthService
import atropos.core.provider.ProviderOnboardingService
import atropos.core.territory.TerritoryService
import java.time.Instant

class AutonomousOrchestrator(
    private val backlog: AutonomousBacklogService = AutonomousBacklogService(),
    private val dagService: DagService = DagService(),
    private val dagExecutionService: DagExecutionService = DagExecutionService(),
    private val ingestion: DocumentIngestionService = DocumentIngestionService(),
    private val directorService: DirectorService = DirectorService(),
    private val directorDagSupervisor: DirectorDagSupervisor = DirectorDagSupervisor(
        dagExecution = dagExecutionService,
        director = directorService
    ),
    private val territoryService: TerritoryService = TerritoryService(),
    private val hrRouter: HrRouterService = HrRouterService(),
    private val auditor: AuditorService = AuditorService(),
    private val custodian: CustodianService = CustodianService(),
    private val hierarchy: HierarchyRegistry = HierarchyRegistry(),
    private val artifactPipeline: ArtifactPipeline = ArtifactPipeline(),
    private val artifactVerification: ArtifactVerificationService = ArtifactVerificationService(),
    private val inspectionService: InspectionService = InspectionService(),
    private val memory: LocalMemoryStore = LocalMemoryStore(),
    private val learningAdvisor: AutonomousLearningAdvisor = AutonomousLearningAdvisor(),
    private val onboarding: ProviderOnboardingService = ProviderOnboardingService(),
    private val providerTruth: ProviderTruthService = ProviderTruthService(onboarding = onboarding)
) {
    private var session = AutonomousSession()
    private val stopConditions = mutableListOf<StopCondition>()
    private val providerWorkers = ProviderWorkerDirector(onboarding = onboarding)

    fun runProviderWorkers(tasks: List<ProviderWorkerTask>): ProviderWorkerBatchReport =
        providerWorkers.run(tasks)

    fun init(): AutonomousSession {
        directorService.observe(
            kind = ObservationKind.DIFF_DRIFT,
            severity = DriftSeverity.ADVISORY,
            source = "autonomous/orchestrator",
            details = "Autonomous session started: ${session.id}"
        )

        backlog.enqueue(AutonomousTaskKind.AUDIT_RUN, "Initial audit of territories and secrets", AutonomousTaskPriority.HIGH)
        backlog.enqueue(AutonomousTaskKind.DAG_INGESTION, "Ingest pending source documents into DAG", AutonomousTaskPriority.HIGH)
        backlog.enqueue(AutonomousTaskKind.MEMORY_COMPACTION, "Compact persisted memory state", AutonomousTaskPriority.LOW)
        backlog.enqueue(AutonomousTaskKind.CUSTODIAN_CLEAN, "Clean temp files and prune dead snapshots", AutonomousTaskPriority.LOW)

        addStopCondition(StopCondition(
            kind = "HIG_ZERO", description = "HIG=0: all requirements implemented",
            threshold = 0.0, currentValue = 1.0
        ))
        addStopCondition(StopCondition(
            kind = "HEAP_CRITICAL", description = "JVM heap usage > 90%",
            threshold = 90.0, currentValue = Platform.health.heapUsagePercent
        ))
        addStopCondition(StopCondition(
            kind = "TASKS_EXHAUSTED", description = "No eligible tasks remain in backlog"
        ))

        return session
    }

    fun tick(): String {
        if (!session.active) return "Session inactive"

        val stopReasons = stopConditions.filter { it.met }
        if (stopReasons.isNotEmpty()) {
            return "STOP: ${stopReasons.joinToString("; ") { it.description }}"
        }

        val eligible = backlog.eligible()
        if (eligible.isEmpty()) {
            stopConditions.find { it.kind == "TASKS_EXHAUSTED" }?.let {
                stopConditions[stopConditions.indexOf(it)] = it.copy(triggered = true, currentValue = 1.0)
            }
            return "No eligible tasks; checking for new work..."
        }

        val task = learningAdvisor.rank(eligible, backlog.repairHistory(), backlog.failoverHistory()).first()
        val claimed = backlog.claim(task.id) ?: return "Task ${task.id} already claimed"

        val result = execute(claimed)
        return result
    }

    fun runOnce(blocking: Boolean = false): String {
        val eligible = backlog.eligible()
        if (eligible.isEmpty()) return "No eligible tasks"
        val task = learningAdvisor.rank(eligible, backlog.repairHistory(), backlog.failoverHistory()).first()
        val claimed = backlog.claim(task.id) ?: return "Task ${task.id} already claimed"
        return execute(claimed)
    }

    fun runMax(count: Int): String {
        val results = mutableListOf<String>()
        val learnedCount = learningAdvisor.recommendedBatchSize(count, backlog.repairHistory())
        repeat(learnedCount) {
            val eligible = backlog.eligible()
            if (eligible.isEmpty()) return@repeat
            val task = learningAdvisor.rank(eligible, backlog.repairHistory(), backlog.failoverHistory()).first()
            val claimed = backlog.claim(task.id) ?: return@repeat
            results += execute(claimed)
        }
        return results.joinToString("\n")
    }

    fun status(): String {
        val snap = backlog.snapshot()
        return buildString {
            appendLine("Autonomous session: ${session.id}")
            appendLine("  ${snap.summary}")
            appendLine("  Repairs: ${backlog.repairHistory().size} total")
            appendLine("  Failovers: ${backlog.failoverHistory().size} total")
            appendLine("  Stop conditions:")
            stopConditions.forEach { appendLine("    ${it.kind}: ${if (it.met) "TRIGGERED" else "waiting"} (${it.currentValue}/${it.threshold})") }
        }.trimEnd()
    }

    fun sessionSummary(): String = session.summary

    fun addStopCondition(condition: StopCondition) {
        stopConditions += condition
        session = session.copy(stopConditions = stopConditions.toList())
    }

    fun updateStopCondition(kind: String, currentValue: Double) {
        val idx = stopConditions.indexOfFirst { it.kind == kind }
        if (idx >= 0) {
            val updated = stopConditions[idx].copy(
                currentValue = currentValue,
                triggered = currentValue >= stopConditions[idx].threshold
            )
            stopConditions[idx] = updated
            session = session.copy(stopConditions = stopConditions.toList())
        }
    }

    private fun execute(task: AutonomousTask): String {
        val startTime = System.currentTimeMillis()
        val learningDecision = learningAdvisor.inspect(task, backlog.repairHistory(), backlog.failoverHistory())
        if (!learningDecision.accepted) {
            backlog.skip(task.id, learningDecision.reason)
            memory.rememberFailure(
                subjectType = "autonomous",
                subjectId = task.id,
                title = "autonomous-learning-stop",
                body = learningDecision.reason,
                tags = listOf("autonomous", "learning", "invariant", "stop")
            )
            return "[STOP] ${task.kind.name}: ${learningDecision.reason}"
        }

        return try {
            val boundary = routeDeclaredBoundary(task)
            if (boundary != null && !boundary.approved) {
                backlog.skip(task.id, boundary.reason)
                return "[STOP] ${task.kind.name}: HR Router refused cross-boundary context: ${boundary.reason}"
            }
            val result = when (task.kind) {
                AutonomousTaskKind.DAG_INGESTION -> executeDagIngestion(task)
                AutonomousTaskKind.DAG_CONTINUATION -> executeDagContinuation(task)
                AutonomousTaskKind.PROVIDER_FAILOVER -> executeProviderFailover(task)
                AutonomousTaskKind.REPAIR_RETRY -> executeRepairRetry(task)
                AutonomousTaskKind.TERRITORY_SYNC -> executeTerritorySync(task)
                AutonomousTaskKind.MEMORY_COMPACTION -> executeMemoryCompaction(task)
                AutonomousTaskKind.HIG_REDUCTION -> executeHigReduction(task)
                AutonomousTaskKind.POLICY_APPLICATION -> executePolicyApplication(task)
                AutonomousTaskKind.AUDIT_RUN -> executeAuditRun(task)
                AutonomousTaskKind.CUSTODIAN_CLEAN -> executeCustodianClean(task)
                AutonomousTaskKind.VERIFICATION_GATE -> executeVerificationGate(task)
                AutonomousTaskKind.ARTIFACT_BUILD -> executeArtifactBuild(task)
            }

            val duration = System.currentTimeMillis() - startTime
            backlog.complete(task.id, result)
            updateSessionCounts(true)
            memory.rememberToolResult(
                subjectId = task.id,
                title = "auto-${task.kind.name}",
                body = "$result\nlearning=${learningDecision.reason}",
                tags = listOf("autonomous", task.kind.name, "learning")
            )
            memory.rememberReward(
                subjectId = task.id,
                title = "autonomous-learning-evidence",
                body = "success=true durationMs=$duration ${learningDecision.reason}",
                tags = listOf("autonomous", "learning", task.kind.name)
            )
            updateHigStopCondition()
            "[OK] ${task.kind.name}: $result (${duration}ms)"
        } catch (e: Exception) {
            val duration = System.currentTimeMillis() - startTime
            backlog.fail(task.id, e.message ?: e.javaClass.simpleName)
            backlog.recordRepair(task.id, e.message ?: "unknown", "auto-retry on failure", false, duration)
            memory.rememberReward(
                subjectId = task.id,
                title = "autonomous-learning-evidence",
                body = "success=false durationMs=$duration ${learningDecision.reason} failure=${e.message ?: e.javaClass.simpleName}",
                tags = listOf("autonomous", "learning", task.kind.name, "failure")
            )
            updateSessionCounts(false)
            "[FAIL] ${task.kind.name}: ${e.message} (${duration}ms)"
        }
    }

    private fun executeDagIngestion(task: AutonomousTask): String {
        val docPath = task.context["path"] ?: "AGENTS.md"
        val result = ingestion.ingestFile(docPath)
        if (!result.success) throw RuntimeException("ingestion failed: ${result.errors.joinToString("; ")}")
        val dag = ingestion.buildDAG(result.requirements)
        return "Ingested ${result.document?.id}: ${result.requirements.size} requirements, ${dag.nodes.size} DAG nodes"
    }

    private fun executeDagContinuation(task: AutonomousTask): String {
        val dagId = task.context["dagId"]?.trim().orEmpty()
        if (dagId.isBlank()) {
            return "DAG continuation deferred: canonical dagId is required"
        }
        val result = directorDagSupervisor.supervise(dagId)
        if (!result.allowed) {
            throw IllegalStateException("DAG continuation refused by Director: ${result.message}")
        }
        val execution = result.execution ?: throw IllegalStateException("DAG continuation returned no execution result")
        return "DAG continuation supervised: ${execution.completedNodes} completed, " +
            "${execution.failedNodes} failed, ${execution.blockedNodes} blocked"
    }

    private fun executeProviderFailover(task: AutonomousTask): String {
        val primary = task.context["primary"]?.trim()?.takeIf { it.isNotBlank() }
            ?: providerTruth.snapshot().selectedProvider
        val service = ProviderFailoverService(backlog = backlog)
        val plan = service.assess(primary) ?: throw RuntimeException("no failover route available for $primary")
        val requestedFallback = task.context["fallback"]?.trim()?.takeIf { it.isNotBlank() }
        val selectedFallback = requestedFallback
            ?.takeIf { it != plan.primaryId && it in plan.availableAlternatives }
            ?: plan.fallbackId
        val failoverEvent = service.failover(primary, selectedFallback, plan.reason)
        return "Failover: $primary -> $selectedFallback (${failoverEvent.id})"
    }

    private fun executeRepairRetry(task: AutonomousTask): String {
        val signatures = backlog.repairHistory().map { it.failureSignature }.distinct()
        return "Repair retry: ${signatures.size} distinct failure signatures in history"
    }

    private fun executeTerritorySync(task: AutonomousTask): String {
        val assignments = territoryService.getAll()
        val hierarchyAgents = hierarchy.getAll()
        return "Territory sync: ${assignments.size} assignments, ${hierarchyAgents.size} agents"
    }

    private fun executeMemoryCompaction(task: AutonomousTask): String {
        val count = memory.compact()
        return "Memory compaction: $count records compacted"
    }

    private fun executeHigReduction(task: AutonomousTask): String {
        val allNodes = dagService.getAllNodes()
        val reqs = allNodes.map {
            atropos.core.dag.ExtractedRequirement(
                canonicalWording = it.requirementId,
                implementationState = if (it.state == DAGNodeState.COMPLETED) atropos.core.dag.ImplementationState.IMPLEMENTED
                    else atropos.core.dag.ImplementationState.ABSENT
            )
        }
        val hig = ingestion.computeHIG(reqs)
        updateStopCondition("HIG_ZERO", hig.hig)
        return "HIG report: ${hig.higFormatted} (${hig.absent} absent, ${hig.partial} partial, ${hig.implemented} implemented, ${hig.verified} verified / ${hig.total} total)"
    }

    private fun executePolicyApplication(task: AutonomousTask): String {
        val violations = territoryService.getViolations()
        val unacknowledged = directorService.advisoryReport().observations
        return "Policy: ${violations.size} territory violations, ${unacknowledged.size} unacknowledged observations"
    }

    private fun executeAuditRun(task: AutonomousTask): String {
        auditor.auditTerritories(territoryService.getAll())
        val report = auditor.report()
        val decision = auditor.blockPromotion(
            report = report,
            claimedBy = "autonomous-orchestrator",
            auditedBy = "auditor"
        )
        memory.rememberVerification(
            subjectId = task.id,
            title = "autonomous-audit-report",
            body = "report_sha256=${decision.reportEvidenceSha256} summary=${report.summary} " +
                "promotion_allowed=${decision.allowed} decision=${decision.message}",
            tags = listOf("autonomous", "audit", "custodian-boundary")
        )
        check(decision.allowed) { decision.message }
        return "Audit: ${report.summary} evidence_sha256=${decision.reportEvidenceSha256}"
    }

    private fun executeCustodianClean(task: AutonomousTask): String {
        val clean = custodian.cleanTempFiles()
        val prune = custodian.pruneDeadSnapshots()
        return "Custodian: ${clean.summary}; ${prune.summary}"
    }

    private fun executeVerificationGate(task: AutonomousTask): String {
        val dagId = task.context["dagId"]?.trim().orEmpty()
        if (dagId.isBlank()) {
            return "Verification gate deferred: canonical dagId is required"
        }
        val result = directorDagSupervisor.supervise(dagId)
        if (!result.allowed) {
            throw IllegalStateException("verification gate refused by Director: ${result.message}")
        }
        val execution = result.execution ?: throw IllegalStateException("verification gate returned no execution result")
        return "Verification gate supervised: ${execution.completedNodes} completed nodes"
    }

    private fun executeArtifactBuild(task: AutonomousTask): String {
        val report = artifactPipeline.createDeliverable(task.description)
        check(report.buildFailCount == 0 && report.artifacts.isNotEmpty()) {
            "artifact deliverable failed: ${report.summary}"
        }
        return "Artifact deliverable: ${report.summary}"
    }

    /**
     * Autonomous work is normally local to its assigned owner. When a task
     * explicitly carries cross-boundary context, make that boundary visible
     * to the sole HR owner before any task executor can consume it.
     */
    private fun routeDeclaredBoundary(task: AutonomousTask): CrossBoundaryResponse? {
        val context = task.context
        val declared = context.keys.any { key ->
            key in setOf(
                "sourceOwner", "sourceTerritory", "targetOwner", "targetTerritory",
                "informationKind", "sourceCoordinates", "needToKnow", "requestedPaths"
            )
        }
        if (!declared) return null

        val kind = runCatching {
            InformationKind.valueOf(context["informationKind"].orEmpty().uppercase())
        }.getOrNull()
        if (kind == null) {
            return hrRouter.request(
                sourceOwner = "",
                sourceTerr = "",
                targetOwner = "",
                targetTerr = "",
                kind = InformationKind.SOURCE_CODE,
                query = "invalid autonomous cross-boundary information kind",
                taskId = task.id,
                needToKnow = "invalid boundary metadata must be refused"
            )
        }
        return hrRouter.request(
            sourceOwner = context["sourceOwner"].orEmpty(),
            sourceTerr = context["sourceTerritory"].orEmpty(),
            targetOwner = context["targetOwner"].orEmpty(),
            targetTerr = context["targetTerritory"].orEmpty(),
            kind = kind,
            query = context["query"] ?: task.description,
            paths = context["requestedPaths"]?.split('|').orEmpty().filter(String::isNotBlank),
            taskId = task.id,
            sourceCoordinates = context["sourceCoordinates"]?.split('|').orEmpty().filter(String::isNotBlank),
            needToKnow = context["needToKnow"].orEmpty()
        )
    }

    private fun updateSessionCounts(success: Boolean) {
        session = session.copy(
            tasksAttempted = session.tasksAttempted + 1,
            tasksCompleted = session.tasksCompleted + if (success) 1 else 0,
            tasksFailed = session.tasksFailed + if (success) 0 else 1
        )
    }

    private fun updateHigStopCondition() {
        val allNodes = dagService.getAllNodes()
        val reqs = allNodes.map {
            atropos.core.dag.ExtractedRequirement(
                canonicalWording = it.requirementId,
                implementationState = if (it.state == DAGNodeState.COMPLETED) atropos.core.dag.ImplementationState.IMPLEMENTED
                    else atropos.core.dag.ImplementationState.ABSENT
            )
        }
        val hig = ingestion.computeHIG(reqs)
        updateStopCondition("HIG_ZERO", hig.hig)
    }
}
