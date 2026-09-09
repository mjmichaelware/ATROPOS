/* SPDX-License-Identifier: AGPL-3.0-only */
package atropos.cli.ui

import atropos.core.security.RedactionFilter
import java.time.Instant

/**
 * Intent-Conflict Banner (F-X-08 / F-SUP-027).
 *
 * Surfaces prior prohibitions when a new command conflicts with
 * previously established constraints. This prevents the operator
 * from accidentally violating their own prior decisions.
 *
 * Examples:
 * - Operator previously set "no paid providers" → new command tries paid provider
 * - Operator set "local-only mode" → new command tries network access
 * - Operator approved specific territory → new command targets outside it
 * - Operator set "zero-retention" → new command tries to persist data
 */
class IntentConflictBanner(
    private val redactionFilter: RedactionFilter = RedactionFilter(),
) {
    data class Conflict(
        val id: String,
        val kind: Kind,
        val priorDecision: String,
        val conflictingCommand: String,
        val prohibitedBy: String,
        val severity: Severity,
        val timestamp: Instant = Instant.now(),
    ) {
        val bannerText: String
            get() = when (kind) {
                is Kind.ProviderPolicy -> "PROVIDER POLICY CONFLICT"
                is Kind.TerritoryViolation -> "TERRITORY VIOLATION"
                is Kind.RetentionPolicy -> "RETENTION POLICY CONFLICT"
                is Kind.LocalOnlyMode -> "LOCAL-ONLY MODE VIOLATION"
                is Kind.PaidApproval -> "PAID APPROVAL REQUIRED"
                is Kind.ZeroRetention -> "ZERO-RETENTION VIOLATION"
                is Kind.VerificationGate -> "VERIFICATION GATE CONFLICT"
                is Kind.Custom -> "INTENT CONFLICT"
            }
    }

    sealed class Kind {
        object ProviderPolicy : Kind()
        object TerritoryViolation : Kind()
        object RetentionPolicy : Kind()
        object LocalOnlyMode : Kind()
        object PaidApproval : Kind()
        object ZeroRetention : Kind()
        object VerificationGate : Kind()
        data class Custom(val name: String) : Kind()
    }

    enum class Severity {
        BLOCKING,    // Command will be refused
        WARNING,     // Command will proceed but with notice
        INFORMATIONAL, // Just informational
    }

    private val conflicts = mutableListOf<Conflict>()
    private val priorDecisions = mutableMapOf<String, String>()

    fun recordPriorDecision(key: String, value: String) {
        priorDecisions[key] = value
    }

    fun checkAndRecord(
        kind: Kind,
        priorDecisionKey: String,
        conflictingCommand: String,
        severity: Severity = Severity.BLOCKING,
    ): Conflict? {
        val priorDecision = priorDecisions[priorDecisionKey]
            ?: return null // No prior decision, no conflict

        val prohibitedBy = "Prior decision: $priorDecisionKey = $priorDecision"
        val conflict = Conflict(
            id = java.util.UUID.randomUUID().toString(),
            kind = kind,
            priorDecision = priorDecision,
            conflictingCommand = redactionFilter.redact(conflictingCommand),
            prohibitedBy = prohibitedBy,
            severity = severity,
        )
        conflicts.add(conflict)
        return conflict
    }

    /** Check if a command conflicts with "no paid providers" decision. */
    fun checkPaidProviderConflict(command: String): Conflict? {
        return checkAndRecord(
            kind = Kind.PaidApproval,
            priorDecisionKey = "paid_providers",
            conflictingCommand = command,
            severity = Severity.BLOCKING,
        )
    }

    /** Check if a command conflicts with "local-only" mode. */
    fun checkLocalOnlyConflict(command: String): Conflict? {
        return checkAndRecord(
            kind = Kind.LocalOnlyMode,
            priorDecisionKey = "local_only",
            conflictingCommand = command,
            severity = Severity.BLOCKING,
        )
    }

    /** Check if a command conflicts with territory restrictions. */
    fun checkTerritoryConflict(command: String, territory: String): Conflict? {
        return checkAndRecord(
            kind = Kind.TerritoryViolation,
            priorDecisionKey = "territory_$territory",
            conflictingCommand = command,
            severity = Severity.BLOCKING,
        )
    }

    /** Check if a command conflicts with zero-retention mode. */
    fun checkZeroRetentionConflict(command: String): Conflict? {
        return checkAndRecord(
            kind = Kind.ZeroRetention,
            priorDecisionKey = "zero_retention",
            conflictingCommand = command,
            severity = Severity.BLOCKING,
        )
    }

    /** Check if a command conflicts with provider policy. */
    fun checkProviderPolicyConflict(command: String, policy: String): Conflict? {
        return checkAndRecord(
            kind = Kind.ProviderPolicy,
            priorDecisionKey = "provider_policy_$policy",
            conflictingCommand = command,
            severity = Severity.BLOCKING,
        )
    }

    /** Check if a command conflicts with verification gate. */
    fun checkVerificationGateConflict(command: String, gate: String): Conflict? {
        return checkAndRecord(
            kind = Kind.VerificationGate,
            priorDecisionKey = "verification_gate_$gate",
            conflictingCommand = command,
            severity = Severity.BLOCKING,
        )
    }

    /** Record a custom prior decision and check for conflicts. */
    fun checkCustomConflict(
        kindName: String,
        priorDecisionKey: String,
        conflictingCommand: String,
        severity: Severity = Severity.WARNING,
    ): Conflict? {
        return checkAndRecord(
            kind = Kind.Custom(kindName),
            priorDecisionKey = priorDecisionKey,
            conflictingCommand = conflictingCommand,
            severity = severity,
        )
    }

    fun all(): List<Conflict> = conflicts.toList()

    fun blocking(): List<Conflict> = conflicts.filter { it.severity == Severity.BLOCKING }

    fun clear() { conflicts.clear() }

    /** Render all conflicts as a banner. */
    fun renderBanner(): String {
        if (conflicts.isEmpty()) return ""
        return conflicts.map { c ->
            val icon = when (c.severity) {
                Severity.BLOCKING -> "🛑"
                Severity.WARNING -> "⚠️"
                Severity.INFORMATIONAL -> "ℹ️"
            }
            "$icon ${c.bannerText}: $c.conflictingCommand\n   $c.prohibitedBy"
        }.joinToString("\n\n")
    }
}

/**
 * Renderer for Intent-Conflict Banner in the CLI.
 */
class IntentConflictBannerRenderer(
    private val theme: ThemePalette,
) {
    fun render(conflict: IntentConflictBanner.Conflict): String {
        val sb = StringBuilder()
        val (icon, color) = when (conflict.severity) {
            IntentConflictBanner.Severity.BLOCKING -> "🛑" to theme.error
            IntentConflictBanner.Severity.WARNING -> "⚠️" to theme.warning
            IntentConflictBanner.Severity.INFORMATIONAL -> "ℹ️" to theme.metadata
        }

        sb.append(color("$icon ${conflict.bannerText}"))
        sb.append("\n")
        sb.append(theme.metadata("Command: ${conflict.conflictingCommand}"))
        sb.append("\n")
        sb.append(theme.metadata("Prohibited by: ${conflict.prohibitedBy}"))

        return sb.toString()
    }

    fun renderAll(conflicts: List<IntentConflictBanner.Conflict>): String {
        if (conflicts.isEmpty()) return ""
        return conflicts.map { render(it) }.joinToString("\n\n")
    }
}

/**
 * Integration with CommandRouter for automatic conflict detection.
 */
class IntentConflictDetector(
    private val banner: IntentConflictBanner,
    private val redactionFilter: RedactionFilter = RedactionFilter(),
) {
    /**
     * Analyzes a command for intent conflicts before execution.
     * Returns a conflict if one is detected, null otherwise.
     */
    fun detect(command: String): IntentConflictBanner.Conflict? {
        val redacted = redactionFilter.redact(command)
        val lower = command.lowercase()

        // Paid provider check
        if (lower.contains("openrouter") || lower.contains("anthropic") || lower.contains("openai")) {
            banner.checkPaidProviderConflict(redacted)?.also { return it }
        }

        // Local-only check
        if (lower.contains("http") || lower.contains("curl") || lower.contains("wget") || lower.contains("fetch")) {
            banner.checkLocalOnlyConflict(redacted)?.also { return it }
        }

        // Territory check (simplified - real impl would check actual paths)
        if (lower.contains("/etc/") || lower.contains("/root/") || lower.contains("/home/")) {
            banner.checkTerritoryConflict(redacted, "system")?.also { return it }
        }

        // Zero-retention check
        if (lower.contains("persist") || lower.contains("save") || lower.contains("store")) {
            banner.checkZeroRetentionConflict(redacted)?.also { return it }
        }

        return null
    }

    /** Record prior decisions from config/environment. */
    fun loadPriorDecisions(config: Map<String, String>) {
        config.forEach { (key, value) ->
            banner.recordPriorDecision(key, value)
        }
    }
}