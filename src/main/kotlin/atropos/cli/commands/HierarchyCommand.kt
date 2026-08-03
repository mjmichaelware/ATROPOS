package atropos.cli.commands

import atropos.core.dag.DagService
import atropos.core.dag.DagStore
import atropos.core.dag.DocumentIngestionService
import atropos.core.dag.ExtractedRequirement
import atropos.core.dag.RequirementType
import atropos.core.director.DirectorService
import atropos.core.director.DirectorStore
import atropos.core.director.ObservationKind
import atropos.core.director.DriftSeverity
import atropos.core.hierarchy.HierarchyRegistry
import atropos.core.hierarchy.AgentRecord
import atropos.core.hierarchy.HierarchyRole
import atropos.core.hr.HrRouterService
import atropos.core.hr.InformationKind
import atropos.core.auditor.AuditorService
import atropos.core.custodian.CustodianService
import atropos.core.territory.TerritoryService
import atropos.core.territory.TerritoryStore
import atropos.core.multimodal.SnapshotService
import atropos.core.multimodal.InspectionService
import atropos.core.multimodal.ViewportCapture
import atropos.core.multimodal.SnapshotKind
import atropos.core.platform.Platform
import atropos.core.platform.PlatformAdapterRegistry
import atropos.core.artifact.ArtifactPipeline
import atropos.core.artifact.ArtifactVerificationService
import atropos.core.artifact.JarSwapEvidence
import atropos.core.artifact.SafeJarSwapGate
import atropos.core.autonomous.AutonomousOrchestrator
import atropos.core.autonomous.AutonomousBacklogService
import atropos.core.autonomous.AutonomousTaskKind
import atropos.core.autonomous.AutonomousTaskPriority
import java.nio.file.Path

class HierarchyCommand {
    private val directorService = DirectorService()
    private val territoryService = TerritoryService()
    private val hrRouter = HrRouterService()
    private val auditor = AuditorService()
    private val custodian = CustodianService()
    private val hierarchy = HierarchyRegistry()
    private val dagService = DagService()
    private val dagStore = DagStore()
    private val ingestion = DocumentIngestionService()
    private val snapshotService = SnapshotService()
    private val inspectionService = InspectionService()
    private val artifactPipeline = ArtifactPipeline()
    private val artifactVerification = ArtifactVerificationService()
    private val jarSwapGate = SafeJarSwapGate()
    private val autonomousOrchestrator = AutonomousOrchestrator()
    private val autonomousBacklog = AutonomousBacklogService()

    fun execute(tokens: List<String>): String {
        val cmd = tokens.firstOrNull()?.removePrefix("/") ?: return availableCommands()
        val args = tokens.drop(1)

        return when (cmd) {
            "director" -> handleDirector(args)
            "territory" -> handleTerritory(args)
            "hr" -> handleHr(args)
            "auditor" -> handleAuditor(args)
            "custodian" -> handleCustodian(args)
            "hierarchy" -> handleHierarchy(args)
            "dag" -> handleDag(args)
            "snapshot" -> handleSnapshot(args)
            "inspect" -> handleInspect(args)
            "platform" -> handlePlatform(args)
            "artifact" -> handleArtifact(args)
            "autonomous" -> handleAutonomous(args)
            else -> "unknown command: /$cmd\n${availableCommands()}"
        }
    }

    private fun availableCommands(): String = """
System commands (all phases):
  PHASE 12: /director observe|report|acknowledge|dismiss|scan
  PHASE 13: /territory assign|revoke|violations|resolve
  PHASE 14: /hr route|audit
  PHASE 15: /auditor run
  PHASE 15: /custodian clean|prune
  PHASE 16: /hierarchy register|snapshot|escalate
  PHASE 16: /dag status|ingest|runnable|cycles|hig|snapshot
  PHASE 17: /snapshot capture|compare|list
  PHASE 17: /inspect file|dag|viewport|full|report
  PHASE 18: /platform [adapters|health|env]
  PHASE 19: /artifact plan|build|verify|install|commit|gate
  PHASE 20: /autonomous init|tick|run|run-max|backlog|repairs|failovers
    """.trimIndent()

    private fun handleDirector(args: List<String>): String {
        return when (args.firstOrNull()) {
            "observe" -> {
                if (args.size < 4) "usage: /director observe <kind> <severity> <source> <details>"
                else directorObserve(args)
            }
            "report" -> {
                val report = directorService.advisoryReport()
                buildString {
                    appendLine("Director Advisory Report: ${report.summary}")
                    for (obs in report.observations) {
                        appendLine("  [${obs.severity.name}] ${obs.id}: ${obs.details} (${obs.source})")
                    }
                }.trimEnd()
            }
            "acknowledge" -> {
                if (args.size < 2) "usage: /director acknowledge <id>"
                else if (directorService.acknowledge(args[1])) "observation ${args[1]} acknowledged" else "observation not found: ${args[1]}"
            }
            "dismiss" -> {
                if (args.size < 2) "usage: /director dismiss <id>"
                else if (directorService.dismiss(args[1])) "observation ${args[1]} dismissed" else "observation not found: ${args[1]}"
            }
            "scan" -> {
                val obs = directorService.scanDiffForDrift(territoryService.getAll())
                if (obs.isEmpty()) "no drift or violations detected"
                else obs.joinToString("\n") { "  [${it.severity.name}] ${it.details}" }
            }
            else -> "director subcommand required: observe, report, acknowledge, dismiss, scan"
        }
    }

    private fun directorObserve(args: List<String>): String {
        val kind = try { ObservationKind.valueOf(args[1].uppercase()) } catch (_: Exception) { return "unknown kind: ${args[1]}; valid: ${ObservationKind.values().joinToString(", ")}" }
        val severity = try { DriftSeverity.valueOf(args[2].uppercase()) } catch (_: Exception) { return "unknown severity: ${args[2]}; valid: ${DriftSeverity.values().joinToString(", ")}" }
        val obs = directorService.observe(kind, severity, args[3], args.drop(4).joinToString(" "))
        return "observation recorded: ${obs.id} (${obs.kind.name}/${obs.severity.name})"
    }

    private fun handleTerritory(args: List<String>): String = when (args.firstOrNull()) {
        "assign" -> {
            if (args.size < 3) "usage: /territory assign <owner> <role> <prefix>"
            else {
                val t = territoryService.assign(args[1], args[2], args[3])
                "territory assigned: ${t.id} owner=${t.ownerId} role=${t.ownerRole} prefix=${t.allowedPrefix}"
            }
        }
        "revoke" -> {
            if (args.size < 2) "usage: /territory revoke <id>"
            else { territoryService.revoke(args[1]); "territory ${args[1]} revoked" }
        }
        "violations" -> {
            val v = territoryService.getViolations()
            if (v.isEmpty()) "no territory violations"
            else v.joinToString("\n") { "  ${it.id}: ${it.filePath} - ${it.reason} (resolved=${it.resolved})" }
        }
        "resolve" -> {
            if (args.size < 2) "usage: /territory resolve <violation-id>"
            else { territoryService.resolveViolation(args[1]); "violation ${args[1]} resolved" }
        }
        else -> {
            val assignments = territoryService.getAll()
            if (assignments.isEmpty()) "no territory assignments"
            else assignments.joinToString("\n") { "  ${it.id}: ${it.ownerId} (${it.ownerRole}) -> ${it.allowedPrefix}" }
        }
    }

    private fun handleHr(args: List<String>): String = when (args.firstOrNull()) {
        "route" -> {
            if (args.size < 3) "usage: /hr route <source-owner> <dest-owner> <query>"
            else {
                val resp = hrRouter.request(args[1], "terr-${args[1]}", args[2], "terr-${args[2]}", InformationKind.SOURCE_CODE, args.drop(3).joinToString(" "))
                if (resp.approved) "HR route approved: ${resp.redactedContent?.take(100)}"
                else "HR route denied: ${resp.reason}"
            }
        }
        "audit" -> {
            val log = hrRouter.auditLog()
            if (log.isEmpty()) "HR audit log empty"
            else log.joinToString("\n") { "  ${it.requestId}: ${it.sourceOwnerId}->${it.targetOwnerId} ${it.kind} risk=${it.risk} approved=${it.approved}" }
        }
        else -> hrRouter.auditSummary()
    }

    private fun handleAuditor(args: List<String>): String = when (args.firstOrNull()) {
        "run" -> {
            auditor.auditTerritories(territoryService.getAll())
            val report = auditor.report()
            "Auditor: ${report.summary}"
        }
        else -> {
            val report = auditor.report()
            "Auditor: ${report.summary}"
        }
    }

    private fun handleCustodian(args: List<String>): String = when (args.firstOrNull()) {
        "clean" -> {
            val report = custodian.cleanTempFiles()
            report.summary
        }
        "prune" -> {
            val report = custodian.pruneDeadSnapshots()
            report.summary
        }
        else -> "usage: /custodian clean|prune"
    }

    private fun handleHierarchy(args: List<String>): String {
        return when (args.firstOrNull()) {
            "register" -> {
                if (args.size < 3) "usage: /hierarchy register <name> <role>"
                else {
                    val role = try { HierarchyRole.valueOf(args[2].uppercase()) } catch (_: Exception) { return "unknown role: ${args[2]}; valid: ${HierarchyRole.values().joinToString(", ")}" }
                    val agent = AgentRecord(name = args[1], role = role)
                    hierarchy.register(agent)
                    "agent registered: ${agent.id} name=${agent.name} role=${agent.role}"
                }
            }
            "snapshot" -> {
                val snap = hierarchy.snapshot()
                if (snap.agents.isEmpty()) "no agents registered"
                else snap.agents.joinToString("\n") { "  ${it.id}: ${it.name} (${it.role}) status=${it.status}" }
            }
            "escalate" -> {
                if (args.size < 2) "usage: /hierarchy escalate <agent-id>"
                else {
                    val path = hierarchy.escalationPath(args[1])
                    if (path.isEmpty()) "agent not found: ${args[1]}"
                    else path.joinToString(" -> ") { id -> hierarchy.get(id)?.name ?: id }
                }
            }
            else -> {
                val snap = hierarchy.snapshot()
                "${snap.agents.size} agents registered"
            }
        }
    }

    private fun handleSnapshot(args: List<String>): String = when (args.firstOrNull()) {
        "capture" -> {
            val kind = args.getOrNull(1)?.lowercase()
            val source = args.getOrNull(2) ?: "default"
            when (kind) {
                "terminal" -> { val ref = snapshotService.captureTerminal("", source); "terminal snapshot: ${ref.id} hash=${ref.contentHash.take(8)}" }
                "file" -> {
                    if (args.size < 3) "usage: /snapshot capture file <path>"
                    else try { val ref = snapshotService.captureFile(args[2]); "file snapshot: ${ref.id} hash=${ref.contentHash.take(8)} bytes=${ref.byteSize}" }
                    catch (e: Exception) { "snapshot error: ${e.message}" }
                }
                else -> "usage: /snapshot capture terminal|file [source]"
            }
        }
        "compare" -> {
            if (args.size < 3) "usage: /snapshot compare <left-id> <right-id>"
            else {
                val result = snapshotService.compareSnapshots(args[1], args[2])
                "compare ${args[1]} vs ${args[2]}: ${if (result.passed) "MATCH" else "DIFFER"} (score=${result.matchScore})"
            }
        }
        "list" -> {
            val kind = args.getOrNull(1)?.let { try { SnapshotKind.valueOf(it.uppercase()) } catch (_: Exception) { null } }
            val snaps = snapshotService.recentSnapshots(kind, 20)
            if (snaps.isEmpty()) "no snapshots"
            else snaps.joinToString("\n") { "  ${it.id}: ${it.kind.name} src=${it.source.take(40)} hash=${it.contentHash.take(8)}" }
        }
        else -> {
            val count = snapshotService.listSnapshots().size
            "Snapshot service: $count snapshots recorded"
        }
    }

    private fun handleInspect(args: List<String>): String = when (args.firstOrNull()) {
        "file" -> {
            if (args.size < 2) "usage: /inspect file <path> [ref-snapshot-id]"
            else {
                val refId = args.getOrNull(2)
                val result = inspectionService.inspectFileForDrift(args[1], refId)
                "Inspection: ${result.id} ${if (result.passed) "PASS" else "FAIL"}: ${result.findings.joinToString("; ")}"
            }
        }
        "dag" -> {
            val expected = args.getOrNull(1)?.toIntOrNull() ?: 0
            val result = inspectionService.verifyDAGState(expected)
            "DAG inspection: ${result.id} ${if (result.passed) "PASS" else "FAIL"}: ${result.findings.joinToString("; ")}"
        }
        "viewport" -> {
            if (args.size < 3) "usage: /inspect viewport <content> <expected-pattern>"
            else {
                val vp = ViewportCapture(content = args[1], width = 80, height = 24)
                val result = inspectionService.verifyViewportContent(vp, args.drop(2).joinToString(" "))
                "Viewport inspection: ${result.id} ${if (result.passed) "PASS" else "FAIL"}: ${result.findings.joinToString("; ")}"
            }
        }
        "full" -> {
            val paths = if (args.size > 1) args.drop(1) else emptyList()
            val report = inspectionService.runFullInspection(paths)
            "Full inspection: ${report.summary}"
        }
        "report" -> {
            val report = inspectionService.report()
            "Inspection report: ${report.summary}"
        }
        else -> {
            val inspections = inspectionService.recent(5)
            if (inspections.isEmpty()) "no inspections recorded"
            else inspections.joinToString("\n") { "  ${it.id}: ${it.kind.name} ${if (it.passed) "PASS" else "FAIL"} sev=${it.severity}" }
        }
    }

    private fun handlePlatform(args: List<String>): String = when (args.firstOrNull()) {
        "adapters" -> {
            PlatformAdapterRegistry.renderAvailable()
        }
        "health" -> {
            val h = Platform.health
            buildString {
                appendLine("Platform health:")
                appendLine("  platform: ${h.platform}")
                appendLine("  heap: ${h.heapUsedMb}/${h.heapMaxMb} MB (${"%.1f".format(h.heapUsagePercent)}%)")
                appendLine("  threads: ${h.threadCount}")
                appendLine("  fs writable: ${h.fileSystemWritable}")
                appendLine("  network: ${h.networkReachable}")
                appendLine("  healthy: ${h.healthy}")
            }.trimEnd()
        }
        "env" -> {
            val e = Platform.environment
            buildString {
                appendLine("Platform environment:")
                appendLine("  platform: ${e.platform}")
                appendLine("  work dir: ${e.workDir}")
                appendLine("  temp dir: ${e.tempDir}")
                appendLine("  home dir: ${e.homeDir}")
                appendLine("  memory: ${e.availableMemoryMb} MB")
                appendLine("  cores: ${e.availableCores}")
            }.trimEnd()
        }
        else -> {
            val d = Platform.descriptor
            "Platform: ${d.platform} ${d.name} ${d.version} (${d.osName} ${d.osArch})"
        }
    }

    private fun handleArtifact(args: List<String>): String = when (args.firstOrNull()) {
        "plan" -> {
            val prompt = args.drop(1).joinToString(" ")
            if (prompt.isBlank()) "usage: /artifact plan <prompt>"
            else {
                val plan = artifactPipeline.plan(prompt)
                "Artifact plan: ${plan.id} intent=${plan.intent} steps=${plan.steps.size}"
            }
        }
        "build" -> {
            val prompt = args.drop(1).joinToString(" ")
            if (prompt.isBlank()) "usage: /artifact build <prompt>"
            else {
                val report = artifactPipeline.createDeliverable(prompt)
                val artifact = report.artifacts.singleOrNull()
                if (artifact == null) report.summary
                else "Artifact deliverable: ${artifact.filePath} id=${artifact.id} sha256=${artifact.sha256} (${report.summary})"
            }
        }
        "verify" -> {
            if (args.size < 2) "usage: /artifact verify <artifact-id>"
            else {
                val evidence = artifactVerification.verifyFull(args[1])
                evidence.joinToString("\n") { "  ${it.kind.name}: ${if (it.passed) "PASS" else "FAIL"} - ${it.evidence.take(80)}" }
            }
        }
        "install" -> {
            if (args.size < 3) "usage: /artifact install <artifact-id> <target-dir>"
            else {
                val proof = artifactVerification.checkInstall(args[1], args[2])
                "Install: ${if (proof.verified) "OK" else "FAIL"} -> ${proof.targetPath} (${proof.durationMs}ms)"
            }
        }
        "commit" -> {
            if (args.size < 3) "usage: /artifact commit <message> <artifact-id> [proof-id...]"
            else {
                val msg = args[1]
                val artIds = listOf(args[2])
                val proofIds = if (args.size > 3) args.drop(3) else emptyList()
                val candidate = artifactVerification.finalizeCommit(msg, artIds, proofIds, territoryCheck = true, secretCheck = true)
                "Commit candidate: ${candidate.id} ready=${candidate.readyForCommit} files=${candidate.files.size}"
            }
        }
        "gate" -> {
            if (args.size < 2) "usage: /artifact gate <artifact-id>"
            else {
                val result = artifactVerification.runAcceptanceGate(args[1])
                "Acceptance gate: ${if (result.passed) "PASS" else "FAIL"} - ${result.message}"
            }
        }
        "promote-jar" -> {
            if (args.size < 4) {
                "usage: /artifact promote-jar <candidate-jar> <target-jar> <verification-id> [verification-id...]"
            } else {
                val evidenceIds = args.drop(3).toSet()
                val evidence = artifactPipeline.report().verifications
                    .filter { it.id in evidenceIds }
                    .map { JarSwapEvidence(it.passed, it.kind.name, "${it.id}: ${it.evidence}") }
                if (evidence.size != evidenceIds.size) {
                    val found = artifactPipeline.report().verifications.map { it.id }.toSet()
                    val missing = evidenceIds.filterNot { it in found }
                    "JAR promote refused: missing verification evidence ${missing.joinToString(",")}"
                } else {
                    val result = jarSwapGate.promote(Path.of(args[1]), Path.of(args[2]), evidence)
                    "JAR promote: ${if (result.promoted) "PROMOTED" else "REFUSED"} - ${result.message}"
                }
            }
        }
        else -> {
            val report = artifactPipeline.report()
            report.summary
        }
    }

    private fun handleAutonomous(args: List<String>): String = when (args.firstOrNull()) {
        "init" -> {
            val session = autonomousOrchestrator.init()
            "Autonomous session initialized: ${session.id}"
        }
        "tick" -> {
            val result = autonomousOrchestrator.tick()
            result
        }
        "run" -> {
            val result = autonomousOrchestrator.runOnce()
            result
        }
        "run-max" -> {
            val count = args.getOrNull(1)?.toIntOrNull() ?: 3
            val result = autonomousOrchestrator.runMax(count)
            result
        }
        "backlog" -> {
            val snap = autonomousBacklog.snapshot()
            if (snap.tasks.isEmpty()) "autonomous backlog empty"
            else snap.tasks.joinToString("\n") { "  [${it.state.name}] ${it.id}: ${it.description} (${it.kind.name})" }
        }
        "repairs" -> {
            val repairs = autonomousBacklog.repairHistory()
            if (repairs.isEmpty()) "no repair records"
            else repairs.joinToString("\n") { "  ${it.id}: ${it.failureSignature.take(60)} success=${it.success} attempt=${it.attemptNumber}" }
        }
        "failovers" -> {
            val failovers = autonomousBacklog.failoverHistory()
            if (failovers.isEmpty()) "no failover events"
            else failovers.joinToString("\n") { "  ${it.id}: ${it.primaryProviderId} -> ${it.fallbackProviderId} success=${it.success}" }
        }
        else -> autonomousOrchestrator.status()
    }

    private fun handleDag(args: List<String>): String = when (args.firstOrNull()) {
        "status" -> {
            val nodes = dagService.getAllNodes()
            val runnable = dagService.runnableNodes()
            "DAG: ${nodes.size} nodes, ${runnable.size} runnable"
        }
        "ingest" -> {
            if (args.size < 2) "usage: /dag ingest <file-path>"
            else {
                val result = ingestion.ingestFile(args[1])
                if (!result.success) "ingestion failed: ${result.errors.joinToString("; ")}"
                else "ingested: ${result.document?.id} (${result.requirements.size} requirements extracted)"
            }
        }
        "runnable" -> {
            val nodes = dagService.runnableNodes()
            if (nodes.isEmpty()) "no runnable DAG nodes"
            else nodes.joinToString("\n") { "  ${it.id}: req=${it.requirementId} state=${it.state}" }
        }
        "cycles" -> {
            val cycles = dagService.detectCycles()
            if (cycles.isEmpty()) "no cycles detected"
            else cycles.joinToString("\n") { "  cycle: ${it.joinToString(" -> ")}" }
        }
        "hig" -> {
            val nodes = dagService.getAllNodes()
            if (nodes.isEmpty()) "no DAG nodes for HIG computation"
            else {
                val reqs = nodes.map { ExtractedRequirement(canonicalWording = it.requirementId, implementationState = if (it.state.name == "COMPLETED") atropos.core.dag.ImplementationState.IMPLEMENTED else atropos.core.dag.ImplementationState.ABSENT) }
                val hig = ingestion.computeHIG(reqs)
                "${hig.higFormatted} (${hig.absent} absent, ${hig.partial} partial, ${hig.implemented} implemented, ${hig.verified} verified / ${hig.total} total)"
            }
        }
        "snapshot" -> {
            val snap = dagService.dagSnapshot()
            "DAG snapshot: ${snap.nodes.size} nodes, ${snap.sourceDocumentIds.size} documents, version ${snap.version}"
        }
        else -> "usage: /dag status|ingest|runnable|cycles|hig|snapshot"
    }
}
