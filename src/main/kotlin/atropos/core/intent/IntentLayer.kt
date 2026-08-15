/* SPDX-License-Identifier: AGPL-3.0-only */
package atropos.core.intent

import java.util.UUID

/** The 13 canonical verbs that define the ATROPOS contract surface. */
enum class CanonicalVerb(val keyword: String, val description: String) {
    GOAL("/goal", "Examines or runs long-horizon target goals"),
    SCHEDULE("/schedule", "Schedules timer-based or cron recurring execution"),
    PLAN("/plan", "Formulates execution steps for an objective"),
    GRILL_ME("/grill-me", "Initiates interactive clarification session"),
    TEAMWORK_PREVIEW("/teamwork-preview", "Simulates multi-agent swarm interactions"),
    LEARN("/learn", "Persists learned heuristics or preferences"),
    INSPECT("/inspect", "Inspects code, file, or viewport for drift"),
    VERIFY("/verify", "Runs deterministic build and validation gates"),
    APPROVE("/approve", "Records operator approval of a proposed action"),
    REJECT("/reject", "Vetoes a proposed action or proposal"),
    STORAGE("/storage", "Examines or garbage collects disk storage"),
    HISTORY("/history", "Queries the event and provenance log"),
    STATUS("/status", "Displays current aggregate status and active provider")
}

data class CommandMetadata(
    val verb: CanonicalVerb,
    val usage: String,
    val minArgs: Int,
    val aliases: List<String>,
    val parameters: List<String>
)

object ActionRegistry {
    val commands: Map<CanonicalVerb, CommandMetadata> = mapOf(
        CanonicalVerb.GOAL to CommandMetadata(CanonicalVerb.GOAL, "/goal <run|list|resume> <goal-id>", 1, listOf("goals", "g"), listOf("subcommand", "goalId")),
        CanonicalVerb.SCHEDULE to CommandMetadata(CanonicalVerb.SCHEDULE, "/schedule <cron-expr|seconds> <prompt>", 2, listOf("cron", "sched"), listOf("scheduleExpr", "prompt")),
        CanonicalVerb.PLAN to CommandMetadata(CanonicalVerb.PLAN, "/plan <prompt>", 1, listOf("design", "p"), listOf("prompt")),
        CanonicalVerb.GRILL_ME to CommandMetadata(CanonicalVerb.GRILL_ME, "/grill-me <topic>", 1, listOf("grill", "interview"), listOf("topic")),
        CanonicalVerb.TEAMWORK_PREVIEW to CommandMetadata(CanonicalVerb.TEAMWORK_PREVIEW, "/teamwork-preview <agents-count> <task>", 2, listOf("teamwork", "swarm"), listOf("agentsCount", "task")),
        CanonicalVerb.LEARN to CommandMetadata(CanonicalVerb.LEARN, "/learn <rule-description>", 1, listOf("teach", "remember"), listOf("rule")),
        CanonicalVerb.INSPECT to CommandMetadata(CanonicalVerb.INSPECT, "/inspect <file|dag|viewport|evidence|preview> <args>", 1, listOf("check-drift", "inspect-ui"), listOf("subcommand", "args")),
        CanonicalVerb.VERIFY to CommandMetadata(CanonicalVerb.VERIFY, "/verify [paths]", 0, listOf("validate", "compile-gate"), listOf("paths")),
        CanonicalVerb.APPROVE to CommandMetadata(CanonicalVerb.APPROVE, "/approve <decision-id>", 1, listOf("allow", "yes"), listOf("decisionId")),
        CanonicalVerb.REJECT to CommandMetadata(CanonicalVerb.REJECT, "/reject <decision-id>", 1, listOf("deny", "no"), listOf("decisionId")),
        CanonicalVerb.STORAGE to CommandMetadata(CanonicalVerb.STORAGE, "/storage [gc]", 0, listOf("disk", "cleanup"), listOf("subcommand")),
        CanonicalVerb.HISTORY to CommandMetadata(CanonicalVerb.HISTORY, "/history <query>", 1, listOf("logs", "events"), listOf("query")),
        CanonicalVerb.STATUS to CommandMetadata(CanonicalVerb.STATUS, "/status", 0, listOf("info", "state"), emptyList())
    )

    fun get(verb: CanonicalVerb): CommandMetadata = commands.getValue(verb)
}

object AliasResolver {
    fun resolve(input: String): CanonicalVerb? {
        val clean = input.trim().lowercase().removePrefix("/")
        return CanonicalVerb.values().firstOrNull {
            it.keyword.removePrefix("/") == clean || 
            ActionRegistry.commands[it]?.aliases?.contains(clean) == true
        }
    }
}

object CommandConsolidator {
    fun consolidate(args: List<String>): List<String> {
        if (args.isEmpty()) return emptyList()
        val verb = AliasResolver.resolve(args.first()) ?: return args
        return listOf(verb.keyword) + args.drop(1)
    }
}

object NlPhraseMapper {
    private val mappings = mapOf(
        "run a goal" to CanonicalVerb.GOAL,
        "resume goal" to CanonicalVerb.GOAL,
        "setup timer" to CanonicalVerb.SCHEDULE,
        "every five minutes" to CanonicalVerb.SCHEDULE,
        "create a plan" to CanonicalVerb.PLAN,
        "design steps" to CanonicalVerb.PLAN,
        "ask me questions" to CanonicalVerb.GRILL_ME,
        "interview me" to CanonicalVerb.GRILL_ME,
        "simulate agents" to CanonicalVerb.TEAMWORK_PREVIEW,
        "preview swarm" to CanonicalVerb.TEAMWORK_PREVIEW,
        "remember this rule" to CanonicalVerb.LEARN,
        "heuristics" to CanonicalVerb.LEARN,
        "inspect for drift" to CanonicalVerb.INSPECT,
        "run build checks" to CanonicalVerb.VERIFY,
        "allow proposal" to CanonicalVerb.APPROVE,
        "block change" to CanonicalVerb.REJECT,
        "disk garbage collect" to CanonicalVerb.STORAGE,
        "search execution logs" to CanonicalVerb.HISTORY,
        "show engine status" to CanonicalVerb.STATUS
    )

    fun mapPhrase(phrase: String): CanonicalVerb? {
        val normalized = phrase.lowercase().trim()
        return mappings.entries.firstOrNull { normalized.contains(it.key) }?.value
    }
}

object ArgumentGuidance {
    fun getGuidance(input: String): String? {
        val verb = AliasResolver.resolve(input) ?: return null
        val meta = ActionRegistry.get(verb)
        return "Usage: ${meta.usage} (Parameters: ${meta.parameters.joinToString(", ")})"
    }
}
