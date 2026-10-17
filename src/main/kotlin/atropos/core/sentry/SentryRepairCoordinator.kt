/* SPDX-License-Identifier: AGPL-3.0-only */
package atropos.core.sentry

import atropos.core.AtroposRepoRootLocator
import atropos.core.agent.WorkerCodeProposal
import atropos.core.agent.WorkerCodeProposalService
import atropos.core.evaluation.EvidenceKind
import atropos.core.evaluation.EvidenceStore
import atropos.core.security.RedactionFilter
import atropos.core.territory.TerritoryEnforcer
import java.net.HttpURLConnection
import java.net.URI
import java.nio.charset.StandardCharsets
import java.nio.file.Files
import java.nio.file.Path
import org.json.JSONArray
import org.json.JSONObject

data class SentryTraceFrame(val filename: String, val lineno: Int, val colno: Int, val function: String, val inApp: Boolean)
data class SentryCrashContext(val issueId: String, val title: String, val culpritPath: String, val errorType: String, val frames: List<SentryTraceFrame>)
data class SentryPromptRepairResult(val issueId: String, val targetFile: String, val patchPrompt: String, val proposedFixLocation: String)

data class SentryRepairContext(
    val issue: SentryIssue,
    val relativeFile: String,
    val lineNumber: Int?,
    val territory: List<String>,
    val evidenceHash: String,
    val goal: String
)

data class SentryRepairResult(
    val context: SentryRepairContext,
    val proposal: WorkerCodeProposal,
    val proposalEvidenceHash: String
)

/** Composes Sentry evidence with the existing worker proposal/gate path. */
class SentryRepairCoordinator(
    private val repoRoot: Path = AtroposRepoRootLocator.resolve(),
    private val evidenceStore: EvidenceStore = EvidenceStore(repoRoot),
    private val workerProposals: WorkerCodeProposalService = WorkerCodeProposalService(repoRoot = repoRoot),
    private val redactionFilter: RedactionFilter = RedactionFilter()
) {
    fun prepare(issue: SentryIssue, declaredTerritory: List<String>): SentryRepairContext {
        require(declaredTerritory.isNotEmpty()) { "Sentry repair requires declared territory" }
        val frame = issue.frames.firstOrNull { it.filename.isNotBlank() }
            ?: error("Sentry issue has no usable stack frame; repair cannot be proposed")
        val relative = mapFrame(frame.filename)
        val outside = TerritoryEnforcer(declaredTerritory).firstOutside(listOf(relative))
        require(outside == null) { "Sentry stack frame is outside declared territory: $relative" }
        val evidenceHash = evidenceStore.put(
            redactionFilter.redact(
                "sentry_issue=${issue.id}\ntitle=${issue.title}\nculprit=${issue.culprit}\nframe=$relative:${frame.lineNumber ?: "?"}"
            ),
            EvidenceKind.RECEIPT
        )
        atropos.core.thinking.Thinking.step("repair", "Prepared Sentry repair context for issue ${issue.id}")
        atropos.core.thinking.Thinking.detail("repair", "Target frame: $relative:${frame.lineNumber ?: "?"}, evidence=$evidenceHash")
        return SentryRepairContext(
            issue = issue,
            relativeFile = relative,
            lineNumber = frame.lineNumber,
            territory = declaredTerritory,
            evidenceHash = evidenceHash,
            goal = "Repair Sentry issue ${issue.id}: ${issue.title}; culprit=${issue.culprit}; " +
                "top frame=$relative:${frame.lineNumber ?: "?"}; evidence=$evidenceHash"
        )
    }

    fun propose(context: SentryRepairContext, activeProvider: String): SentryRepairResult {
        atropos.core.thinking.Thinking.step("repair", "Proposing fix for Sentry issue ${context.issue.id}")
        val proposal = workerProposals.propose(
            workerId = "sentry-${context.issue.id}",
            activeProvider = activeProvider,
            task = context.goal,
            territory = context.territory
        )
        val proposalEvidenceHash = evidenceStore.put(
            redactionFilter.redact(
                "sentry_issue=${context.issue.id}\nproposal_accepted=${proposal.accepted}\n" +
                    "proposal_sha256=${proposal.proposalSha256 ?: "none"}\nreason=${proposal.reason}"
            ),
            EvidenceKind.RECEIPT
        )
        atropos.core.thinking.Thinking.detail("repair", "Proposal accepted=${proposal.accepted}, evidence=$proposalEvidenceHash")
        return SentryRepairResult(context, proposal, proposalEvidenceHash)
    }

    private fun mapFrame(raw: String): String {
        var candidate = raw.trim()
            .removePrefix("file://")
            .replace('\\', '/')
        val root = repoRoot.toAbsolutePath().normalize()
        val absolute = runCatching { Path.of(candidate).toAbsolutePath().normalize() }.getOrNull()
        if (absolute != null && absolute.startsWith(root)) {
            candidate = root.relativize(absolute).toString().replace('\\', '/')
        } else {
            candidate = candidate.substringAfterLast("/src/", candidate).let { value ->
                if (value == candidate) candidate.trimStart('/') else "src/$value"
            }
        }
        require(candidate.isNotBlank() && !candidate.startsWith("../") && !candidate.contains("/../")) {
            "Sentry stack frame cannot be mapped inside the repository"
        }
        return candidate
    }

    fun fetchIssueDetails(organizationSlug: String, issueId: String, authToken: String): SentryCrashContext {
        atropos.core.thinking.Thinking.outline("repair", "Fetching Sentry issue details for $issueId")
        atropos.core.thinking.Thinking.step("repair", "Querying Sentry API for issue $issueId in $organizationSlug")
        val uri = URI("https://sentry.io/api/0/issues/$issueId/events/latest/").toURL()
        val conn = uri.openConnection() as HttpURLConnection
        conn.requestMethod = "GET"
        conn.setRequestProperty("Authorization", "Bearer $authToken")
        conn.setRequestProperty("Accept", "application/json")
        conn.connectTimeout = 8000
        conn.readTimeout = 8000

        val status = conn.responseCode
        if (status !in 200..299) {
            val err = conn.errorStream?.bufferedReader()?.readText() ?: "HTTP $status"
            throw IllegalStateException("Sentry API Error ($status): $err")
        }

        val body = conn.inputStream.bufferedReader(StandardCharsets.UTF_8).use { it.readText() }
        val root = JSONObject(body)
        val title = root.optString("title", "Sentry Error $issueId")
        val culprit = root.optString("culprit", "")

        val frames = mutableListOf<SentryTraceFrame>()
        val entries = root.optJSONArray("entries") ?: JSONArray()
        for (i in 0 until entries.length()) {
            val entry = entries.getJSONObject(i)
            if (entry.optString("type") == "exception") {
                val data = entry.optJSONObject("data")
                val values = data?.optJSONArray("values") ?: JSONArray()
                for (j in 0 until values.length()) {
                    val ex = values.getJSONObject(j)
                    val stack = ex.optJSONObject("stacktrace")
                    val rawFrames = stack?.optJSONArray("frames") ?: JSONArray()
                    for (k in 0 until rawFrames.length()) {
                        val f = rawFrames.getJSONObject(k)
                        frames.add(
                            SentryTraceFrame(
                                filename = f.optString("filename", ""),
                                lineno = f.optInt("lineno", 0),
                                colno = f.optInt("colno", 0),
                                function = f.optString("function", "anonymous"),
                                inApp = f.optBoolean("inApp", false)
                            )
                        )
                    }
                }
            }
        }

        atropos.core.thinking.Thinking.detail("repair", "Retrieved ${frames.size} stack frames for issue $issueId: $title")
        return SentryCrashContext(issueId, title, culprit, root.optString("message", "Exception"), frames)
    }

    fun extractInAppCulprit(context: SentryCrashContext): SentryTraceFrame? =
        context.frames.reversed().firstOrNull { it.inApp && it.filename.isNotBlank() }
            ?: context.frames.lastOrNull()

    fun generateRepairPrompt(context: SentryCrashContext): SentryPromptRepairResult {
        val culprit = extractInAppCulprit(context)
            ?: throw IllegalStateException("Could not identify culprit frame for Sentry issue ${context.issueId}")
        atropos.core.thinking.Thinking.step("repair", "Culprit identified: ${culprit.filename}:${culprit.lineno}")
        atropos.core.thinking.Thinking.detail("repair", "Synthesizing minimal patch repair prompt for ${culprit.function}")

        val sourceLines = try {
            val file = repoRoot.resolve(culprit.filename).normalize()
            if (Files.exists(file)) Files.readAllLines(file) else emptyList()
        } catch (_: Exception) {
            emptyList()
        }

        val snippet = if (sourceLines.isNotEmpty() && culprit.lineno > 0) {
            val start = maxOf(0, culprit.lineno - 10)
            val end = minOf(sourceLines.size, culprit.lineno + 10)
            sourceLines.subList(start, end).mapIndexed { idx, line ->
                val curr = start + idx + 1
                val marker = if (curr == culprit.lineno) ">> " else "   "
                "$marker$curr: $line"
            }.joinToString("\n")
        } else {
            "[Source file ${culprit.filename} not found or line unknown]"
        }

        val prompt = """
            SENTRY ISSUE ${context.issueId}: ${context.title}
            Culprit: ${culprit.filename}:${culprit.lineno} in ${culprit.function}

            SURROUNDING CODE:
            $snippet

            OBJECTIVE: Generate minimal unified diff resolving this crash without altering unaffected contracts.
        """.trimIndent()

        return SentryPromptRepairResult(context.issueId, culprit.filename, prompt, "${culprit.filename}:${culprit.lineno}")
    }
}
