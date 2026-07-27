package atropos.core.policy

import atropos.core.security.RedactionFilter
import java.nio.charset.StandardCharsets
import java.nio.file.Files
import java.nio.file.Path
import java.nio.file.StandardCopyOption
import java.time.Instant
import java.util.UUID

enum class AutonomyActionClass {
    READ_FILE,
    WRITE_FILE,
    EDIT_FILE,
    SEARCH_CODE,
    RUN_COMMAND,
    RUN_TEST,
    RUN_BUILD,
    CREATE_PATCH,
    APPLY_PATCH,
    VERIFY_PATCH,
    COMMIT_CHANGES,
    PUSH_CHANGES,
    FORCE_PUSH,
    HARD_RESET,
    GIT_CLEAN,
    SECRET_OUTPUT,
    EXTERNAL_PATH,
    PAID_PROVIDER,
    NETWORK_ACCESS,
    DAEMON_CONTROL,
    QUEUE_CONTROL,
    DAG_CONTROL
}

data class AutonomyPolicyRule(
    val actionClass: AutonomyActionClass,
    val allowed: Boolean,
    val reason: String,
    val requiresExplicitOverride: Boolean = false
)

data class AutonomyPolicyDecision(
    val id: String,
    val actionClass: AutonomyActionClass,
    val allowed: Boolean,
    val policyBlocked: Boolean,
    val reason: String,
    val decodedAt: Instant
)

data class AutonomyPolicyAuditRecord(
    val decidedAt: Instant,
    val actionClass: AutonomyActionClass,
    val decisionId: String,
    val allowed: Boolean,
    val policyBlocked: Boolean,
    val reason: String,
    val metadata: Map<String, String> = emptyMap()
)

class AutonomyPolicyEngine(
    private val repoRoot: Path = Path.of(System.getProperty("user.dir")).toAbsolutePath().normalize(),
    private val redactionFilter: RedactionFilter = RedactionFilter()
) {
    private val policyRoot = repoRoot.resolve(".atropos/policy").normalize()
    private val auditLog = policyRoot.resolve("autonomy-audit.log")

    private val defaultRules: Map<AutonomyActionClass, AutonomyPolicyRule> = mapOf(
        AutonomyActionClass.READ_FILE to AutonomyPolicyRule(AutonomyActionClass.READ_FILE, true, "safe read operation"),
        AutonomyActionClass.SEARCH_CODE to AutonomyPolicyRule(AutonomyActionClass.SEARCH_CODE, true, "safe search operation"),
        AutonomyActionClass.RUN_COMMAND to AutonomyPolicyRule(AutonomyActionClass.RUN_COMMAND, true, "bounded local command"),
        AutonomyActionClass.RUN_TEST to AutonomyPolicyRule(AutonomyActionClass.RUN_TEST, true, "bounded test execution"),
        AutonomyActionClass.RUN_BUILD to AutonomyPolicyRule(AutonomyActionClass.RUN_BUILD, true, "bounded build execution"),
        AutonomyActionClass.CREATE_PATCH to AutonomyPolicyRule(AutonomyActionClass.CREATE_PATCH, true, "patch creation allowed"),
        AutonomyActionClass.APPLY_PATCH to AutonomyPolicyRule(AutonomyActionClass.APPLY_PATCH, true, "patch apply allowed"),
        AutonomyActionClass.VERIFY_PATCH to AutonomyPolicyRule(AutonomyActionClass.VERIFY_PATCH, true, "patch verification allowed"),
        AutonomyActionClass.WRITE_FILE to AutonomyPolicyRule(AutonomyActionClass.WRITE_FILE, true, "file write allowed within territory"),
        AutonomyActionClass.EDIT_FILE to AutonomyPolicyRule(AutonomyActionClass.EDIT_FILE, true, "file edit allowed within territory"),
        AutonomyActionClass.DAEMON_CONTROL to AutonomyPolicyRule(AutonomyActionClass.DAEMON_CONTROL, true, "daemon control allowed"),
        AutonomyActionClass.QUEUE_CONTROL to AutonomyPolicyRule(AutonomyActionClass.QUEUE_CONTROL, true, "queue control allowed"),
        AutonomyActionClass.DAG_CONTROL to AutonomyPolicyRule(AutonomyActionClass.DAG_CONTROL, true, "DAG control allowed"),
        AutonomyActionClass.COMMIT_CHANGES to AutonomyPolicyRule(AutonomyActionClass.COMMIT_CHANGES, true, "commit requires user confirmation", true),
        AutonomyActionClass.PUSH_CHANGES to AutonomyPolicyRule(AutonomyActionClass.PUSH_CHANGES, false, "push changes denied"),
        AutonomyActionClass.FORCE_PUSH to AutonomyPolicyRule(AutonomyActionClass.FORCE_PUSH, false, "force push explicitly denied"),
        AutonomyActionClass.HARD_RESET to AutonomyPolicyRule(AutonomyActionClass.HARD_RESET, false, "hard reset explicitly denied"),
        AutonomyActionClass.GIT_CLEAN to AutonomyPolicyRule(AutonomyActionClass.GIT_CLEAN, false, "git clean explicitly denied"),
        AutonomyActionClass.SECRET_OUTPUT to AutonomyPolicyRule(AutonomyActionClass.SECRET_OUTPUT, false, "secret output explicitly denied"),
        AutonomyActionClass.EXTERNAL_PATH to AutonomyPolicyRule(AutonomyActionClass.EXTERNAL_PATH, false, "external path access denied"),
        AutonomyActionClass.PAID_PROVIDER to AutonomyPolicyRule(AutonomyActionClass.PAID_PROVIDER, false, "paid provider auto-spend denied"),
        AutonomyActionClass.NETWORK_ACCESS to AutonomyPolicyRule(AutonomyActionClass.NETWORK_ACCESS, false, "network access not auto-allowed")
    )

    fun evaluate(actionClass: AutonomyActionClass, metadata: Map<String, String> = emptyMap()): AutonomyPolicyDecision {
        val rule = defaultRules[actionClass]
            ?: AutonomyPolicyRule(actionClass, false, "unknown action class, denied by default")
        val id = "apol-" + UUID.randomUUID().toString().take(12)
        val now = Instant.now()
        val decision = AutonomyPolicyDecision(
            id = id,
            actionClass = actionClass,
            allowed = rule.allowed && !rule.requiresExplicitOverride,
            policyBlocked = !rule.allowed,
            reason = if (rule.allowed) rule.reason else rule.reason,
            decodedAt = now
        )
        audit(AutonomyPolicyAuditRecord(now, actionClass, id, decision.allowed, decision.policyBlocked, decision.reason, metadata))
        return decision
    }

    fun evaluateExecutionPolicy(request: ExecutionPolicyRequest, autonomyContext: Map<String, String> = emptyMap()): AutonomyPolicyDecision {
        val actionClass = mapExecutionToAutonomy(request.actionClass, request)
        return evaluate(actionClass, autonomyContext + request.metadata)
    }

    private fun mapExecutionToAutonomy(actionClass: PolicyActionClass, request: ExecutionPolicyRequest): AutonomyActionClass {
        return when (actionClass) {
            PolicyActionClass.SHELL -> {
                val cmd = request.command.joinToString(" ").lowercase()
                when {
                    cmd.contains("git push --force") || cmd.contains("git force") -> AutonomyActionClass.FORCE_PUSH
                    cmd.contains("git reset --hard") -> AutonomyActionClass.HARD_RESET
                    cmd.contains("git clean") -> AutonomyActionClass.GIT_CLEAN
                    cmd.contains("git commit") -> AutonomyActionClass.COMMIT_CHANGES
                    cmd.contains("git push") -> AutonomyActionClass.PUSH_CHANGES
                    cmd.startsWith("git ") -> AutonomyActionClass.RUN_COMMAND
                    else -> AutonomyActionClass.RUN_COMMAND
                }
            }
            PolicyActionClass.GIT -> AutonomyActionClass.RUN_COMMAND
            PolicyActionClass.FILE_MUTATION -> AutonomyActionClass.WRITE_FILE
            PolicyActionClass.PATCH_APPLY -> AutonomyActionClass.APPLY_PATCH
            PolicyActionClass.BUILD_TEST -> {
                when {
                    request.command.firstOrNull()?.lowercase()?.contains("test") == true -> AutonomyActionClass.RUN_TEST
                    else -> AutonomyActionClass.RUN_BUILD
                }
            }
            PolicyActionClass.PROVIDER_CALL -> {
                if (request.paidProvider) AutonomyActionClass.PAID_PROVIDER
                else AutonomyActionClass.RUN_COMMAND
            }
            PolicyActionClass.NETWORK -> AutonomyActionClass.NETWORK_ACCESS
            PolicyActionClass.DAEMON -> AutonomyActionClass.DAEMON_CONTROL
            PolicyActionClass.QUEUE -> AutonomyActionClass.QUEUE_CONTROL
            PolicyActionClass.SMOKE -> AutonomyActionClass.RUN_TEST
        }
    }

    fun policyBlockedReason(actionClass: AutonomyActionClass, metadata: Map<String, String> = emptyMap()): String {
        return "POLICY_BLOCKED: $actionClass is not permitted by autonomy policy (${defaultRules[actionClass]?.reason ?: "denied by default"})"
    }

    fun latestAudit(limit: Int = 50): List<AutonomyPolicyAuditRecord> {
        if (!Files.isRegularFile(auditLog)) return emptyList()
        return Files.readAllLines(auditLog, StandardCharsets.UTF_8)
            .mapNotNull { line ->
                val parts = line.split("\t", limit = 6)
                if (parts.size < 5) return@mapNotNull null
                AutonomyPolicyAuditRecord(
                    decidedAt = runCatching { Instant.parse(parts[0]) }.getOrNull() ?: return@mapNotNull null,
                    actionClass = runCatching { AutonomyActionClass.valueOf(parts[1]) }.getOrNull() ?: return@mapNotNull null,
                    decisionId = parts[2],
                    allowed = parts[3].toBooleanStrictOrNull() ?: return@mapNotNull null,
                    policyBlocked = parts[4].toBooleanStrictOrNull() ?: return@mapNotNull null,
                    reason = parts.getOrElse(5) { "" }
                )
            }
            .takeLast(limit.coerceIn(1, 500))
    }

    private fun audit(record: AutonomyPolicyAuditRecord) {
        Files.createDirectories(policyRoot)
        val line = buildString {
            append(record.decidedAt)
            append('\t').append(record.actionClass.name)
            append('\t').append(record.decisionId)
            append('\t').append(record.allowed)
            append('\t').append(record.policyBlocked)
            append('\t').append(redactionFilter.redact(record.reason).replace('\t', ' '))
            if (record.metadata.isNotEmpty()) {
                append('\t').append(
                    record.metadata.entries.joinToString(",") { (k, v) -> "$k=${redactionFilter.redact(v).replace('\t', ' ')}" }
                )
            }
            append('\n')
        }
        val current = if (Files.isRegularFile(auditLog)) Files.readString(auditLog, StandardCharsets.UTF_8) else ""
        val tmp = policyRoot.resolve("autonomy-audit.${System.nanoTime()}.tmp")
        Files.writeString(tmp, current + line, StandardCharsets.UTF_8)
        try {
            Files.move(tmp, auditLog, StandardCopyOption.REPLACE_EXISTING, StandardCopyOption.ATOMIC_MOVE)
        } catch (_: Exception) {
            Files.move(tmp, auditLog, StandardCopyOption.REPLACE_EXISTING)
        }
    }
}
