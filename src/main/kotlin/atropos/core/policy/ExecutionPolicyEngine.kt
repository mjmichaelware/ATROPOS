package atropos.core.policy

import atropos.core.security.RedactionFilter
import java.nio.charset.StandardCharsets
import java.nio.file.Files
import java.nio.file.Path
import java.nio.file.StandardCopyOption
import java.time.Instant
import java.util.UUID

enum class PolicyActionClass {
    SHELL,
    GIT,
    FILE_MUTATION,
    PATCH_APPLY,
    BUILD_TEST,
    PROVIDER_CALL,
    NETWORK,
    DAEMON,
    QUEUE,
    SMOKE
}

enum class PolicyDecisionType {
    ALLOW,
    DENY,
    APPROVAL_REQUIRED
}

data class ExecutionPolicyRequest(
    val actionClass: PolicyActionClass,
    val command: List<String> = emptyList(),
    val cwd: Path? = null,
    val targetPaths: List<String> = emptyList(),
    val providerId: String? = null,
    val networkTarget: String? = null,
    val paidProvider: Boolean = false,
    val metadata: Map<String, String> = emptyMap()
)

data class ExecutionPolicyDecision(
    val id: String,
    val decision: PolicyDecisionType,
    val actionClass: PolicyActionClass,
    val destructive: Boolean,
    val reason: String,
    val timeoutMillis: Long? = null,
    val maxOutputBytes: Int? = null
) {
    val allowed: Boolean get() = decision == PolicyDecisionType.ALLOW
}

data class ExecutionPolicyAuditRecord(
    val decidedAt: Instant,
    val request: ExecutionPolicyRequest,
    val decision: ExecutionPolicyDecision
) {
    fun encode(redactionFilter: RedactionFilter): String = buildString {
        append("ts=").append(decidedAt)
        append("\taction=").append(request.actionClass.name)
        append("\tdecision=").append(decision.decision.name)
        append("\tdestructive=").append(decision.destructive)
        append("\treason=").append(redactionFilter.redact(decision.reason).replace('\t', ' '))
        append("\tprovider=").append(request.providerId.orEmpty())
        append("\tnetwork=").append(redactionFilter.redact(request.networkTarget.orEmpty()).replace('\t', ' '))
        append("\tcommand=").append(redactionFilter.redact(request.command.joinToString(" ")).replace('\t', ' '))
        append("\tpaths=").append(redactionFilter.redact(request.targetPaths.joinToString(",")).replace('\t', ' '))
        append("\tmeta=").append(
            redactionFilter.redact(
                request.metadata.entries.joinToString(",") { (key, value) ->
                    "${key.trim()}=${value.replace(',', ' ')}"
                }
            )
        )
    }
}

class ExecutionPolicyAuditStore(
    private val repoRoot: Path = Path.of(System.getProperty("user.dir")).toAbsolutePath().normalize(),
    private val redactionFilter: RedactionFilter = RedactionFilter()
) {
    private val policyRoot = repoRoot.resolve(".atropos/policy").normalize()
    private val auditLog = policyRoot.resolve("audit.log").normalize()

    fun append(record: ExecutionPolicyAuditRecord) {
        Files.createDirectories(policyRoot)
        val line = record.encode(redactionFilter) + "\n"
        val current = if (Files.isRegularFile(auditLog)) Files.readString(auditLog, StandardCharsets.UTF_8) else ""
        val tmp = policyRoot.resolve("audit.${System.nanoTime()}.tmp")
        Files.writeString(tmp, current + line, StandardCharsets.UTF_8)
        try {
            Files.move(tmp, auditLog, StandardCopyOption.REPLACE_EXISTING, StandardCopyOption.ATOMIC_MOVE)
        } catch (_: Exception) {
            Files.move(tmp, auditLog, StandardCopyOption.REPLACE_EXISTING)
        }
    }

    fun latest(limit: Int = 50): List<String> {
        if (!Files.isRegularFile(auditLog)) return emptyList()
        return Files.readAllLines(auditLog, StandardCharsets.UTF_8).takeLast(limit.coerceIn(1, 500))
    }
}

/**
 * The single policy authority. There is exactly one of these in the tree and
 * there must never be a second.
 *
 * `evaluate` is `open` purely so tests can drive a caller through a chosen
 * disposition — `APPROVAL_REQUIRED` is unreachable for the SHELL and GIT action
 * classes, so without this seam the approval branch of a shell caller could not
 * be exercised. Overriding it in production code would be a second policy
 * engine and is prohibited.
 */
open class ExecutionPolicyEngine(
    private val repoRoot: Path = Path.of(System.getProperty("user.dir")).toAbsolutePath().normalize(),
    private val auditStore: ExecutionPolicyAuditStore = ExecutionPolicyAuditStore(repoRoot),
    private val redactionFilter: RedactionFilter = RedactionFilter()
) {
    open fun evaluate(request: ExecutionPolicyRequest): ExecutionPolicyDecision {
        val decision = decide(request)
        auditStore.append(ExecutionPolicyAuditRecord(Instant.now(), request, decision))
        return decision
    }

    private fun decide(request: ExecutionPolicyRequest): ExecutionPolicyDecision {
        val destructive = request.actionClass in setOf(
            PolicyActionClass.FILE_MUTATION,
            PolicyActionClass.PATCH_APPLY,
            PolicyActionClass.DAEMON
        ) || request.command.any { it.lowercase() in destructiveTokens }

        fun allow(reason: String, timeoutMillis: Long? = null, maxOutputBytes: Int? = null) =
            ExecutionPolicyDecision(nextId(), PolicyDecisionType.ALLOW, request.actionClass, destructive, reason, timeoutMillis, maxOutputBytes)
        fun deny(reason: String) =
            ExecutionPolicyDecision(nextId(), PolicyDecisionType.DENY, request.actionClass, destructive, reason)
        fun approve(reason: String) =
            ExecutionPolicyDecision(nextId(), PolicyDecisionType.APPROVAL_REQUIRED, request.actionClass, destructive, reason)

        val rawCommand = request.command.joinToString(" ").trim()
        val loweredCommand = rawCommand.lowercase()

        if (request.command.any { it.contains('\n') || it.contains('\r') }) {
            return deny("multiline commands are not allowed")
        }

        if (request.targetPaths.any(::forbiddenPath)) {
            return deny("forbidden target path")
        }

        when (request.actionClass) {
            PolicyActionClass.SHELL,
            PolicyActionClass.SMOKE -> {
                if (loweredCommand.contains("rm -rf /") || loweredCommand.contains("mkfs") || loweredCommand.contains("shutdown")) {
                    return deny("destructive shell command refused")
                }
                if (shellControlOperators.any { loweredCommand.contains(it) }) {
                    return deny("shell chaining or redirects refused")
                }
                if (networkTokens.any { loweredCommand.contains(it) }) {
                    return deny("network shell command refused")
                }
                return allow("bounded local shell command allowed", timeoutMillis = 120_000L, maxOutputBytes = 64 * 1024)
            }

            PolicyActionClass.GIT -> {
                val subcommand = request.command.getOrNull(1)?.lowercase().orEmpty()
                if (subcommand in setOf("commit", "push", "reset", "clean", "checkout", "fetch", "pull")) {
                    return deny("git mutation or remote command refused")
                }
                return allow("read-only git command allowed", timeoutMillis = 30_000L, maxOutputBytes = 128 * 1024)
            }

            PolicyActionClass.FILE_MUTATION,
            PolicyActionClass.PATCH_APPLY -> {
                if (request.targetPaths.isEmpty()) return deny("mutation requires explicit target paths")
                return allow("repository-scoped mutation allowed", timeoutMillis = 60_000L, maxOutputBytes = 128 * 1024)
            }

            PolicyActionClass.BUILD_TEST -> {
                val first = request.command.firstOrNull().orEmpty()
                if (first !in setOf("./gradlew", "gradlew", "java")) {
                    return deny("build/test command refused")
                }
                return allow("bounded build/test command allowed", timeoutMillis = 900_000L, maxOutputBytes = 256 * 1024)
            }

            PolicyActionClass.PROVIDER_CALL -> {
                if (request.paidProvider) {
                    return deny("paid provider remains locked")
                }
                if (request.providerId.isNullOrBlank()) {
                    return deny("provider call requires provider id")
                }
                return allow("provider call allowed under free/local policy", timeoutMillis = 240_000L, maxOutputBytes = 128 * 1024)
            }

            PolicyActionClass.NETWORK -> {
                if (request.networkTarget.isNullOrBlank()) return deny("network action requires target")
                return approve("network action requires explicit integration ownership")
            }

            PolicyActionClass.DAEMON -> {
                return allow("local daemon control allowed", timeoutMillis = 30_000L, maxOutputBytes = 64 * 1024)
            }

            PolicyActionClass.QUEUE -> {
                return allow("durable queue action allowed", timeoutMillis = 30_000L, maxOutputBytes = 64 * 1024)
            }
        }
    }

    private fun forbiddenPath(raw: String): Boolean {
        val normalized = raw.replace('\\', '/').trim()
        if (normalized.isBlank()) return false
        if (normalized.startsWith(".git/") || normalized == ".git") return true
        if (normalized.startsWith(".atropos/secrets/") || normalized == ".atropos/secrets") return true
        if (normalized.startsWith("build/") || normalized == "build") return true
        if (normalized.startsWith(".gradle/") || normalized == ".gradle") return true
        if (normalized.endsWith(".jar") || normalized.endsWith(".class")) return true
        return false
    }

    private fun nextId(): String = "policy-" + UUID.randomUUID().toString().take(12)

    private companion object {
        val destructiveTokens = setOf("rm", "mkfs", "shutdown", "reboot", "dd")
        val shellControlOperators = listOf("&&", "||", ";", "|", ">", "<", "`", "\$(")
        val networkTokens = listOf("curl ", "wget ", "ssh ", "scp ", "nc ", "ncat ", "netcat ")
    }
}
