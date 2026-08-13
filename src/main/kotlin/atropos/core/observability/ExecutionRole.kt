/* SPDX-License-Identifier: AGPL-3.0-only */
package atropos.core.observability

/**
 * Who acted, as one of a closed set.
 *
 * Source Doc 3 §5.1 requires every visible event to carry a role alongside its
 * timestamp, provider, task, requirement, source and state. The journal records
 * thirteen correlation ids and no role at all, so an event can say which run,
 * goal, dag node and provider it belongs to while leaving unanswered the first
 * question an operator asks of a change: *who did this*.
 *
 * A closed enum rather than a free string, because the point of the field is to
 * be filtered on. `ExecutionHistoryStore` filters by agent, and a free-text role
 * makes that filter a guess — `worker`, `Worker` and `worker-3` are three
 * different agents to a string comparison and one agent to a human.
 *
 * The names track the Source Doc 3 §2.1 hierarchy exactly, which is what lets a
 * filter written against the hierarchy return the events that hierarchy
 * produced. [SYSTEM] covers deterministic machinery with no agent behind it —
 * a gate refusing, a GC reclaiming — and exists so that such events are not
 * mislabelled as the agent that happened to trigger them.
 */
enum class ExecutionRole(val canonical: String, val level: Int) {

    /** Level 1. The only role outside agentification. */
    HUMAN("human", 1),

    /** Level 2. Decomposition, territory assignment, drift detection. */
    DIRECTOR("director", 2),

    /** Level 3. Owns a capability domain. */
    DIVISION_VP("division-vp", 3),

    /** Level 4. Dispatches within a division. */
    MANAGER("manager", 4),

    /** Level 5. Deep expertise, tight territory, higher verification bar. */
    SPECIALIST("specialist", 5),

    /** Level 6. Executes strictly inside a granted territory. */
    WORKER("worker", 6),

    /** Level 7. State hygiene. Deterministic, minimal model involvement. */
    CUSTODIAN("custodian", 7),

    /** Level 8. Independent verification with its own reporting line. */
    AUDITOR("auditor", 8),

    /** Cross-cutting. The only channel for information crossing a boundary. */
    HR_ROUTER("hr-router", 0),

    /**
     * Deterministic machinery acting on its own rules.
     *
     * A gate that refuses, a retention policy that reclaims, a verifier that
     * blocks. Attributing these to the agent whose action triggered them would
     * make an agent look responsible for a decision it did not make and could
     * not have made differently.
     */
    SYSTEM("system", 0),

    /** A provider call. Named separately so provider events filter as provider events. */
    PROVIDER("provider", 0);

    companion object {
        private val BY_CANONICAL = entries.associateBy { it.canonical }

        /**
         * Resolves a wire form, defaulting to [SYSTEM].
         *
         * Defaulting rather than throwing: an unreadable role on a historical
         * event should not make the event unreadable. An event attributed to
         * the system is less informative than the truth and more informative
         * than a parse failure that discards it.
         */
        fun of(value: String?): ExecutionRole =
            BY_CANONICAL[value?.trim()?.lowercase()] ?: SYSTEM

        /** Roles that may execute work, in decreasing scope. */
        fun executing(): List<ExecutionRole> =
            entries.filter { it.level in 2..6 }.sortedBy { it.level }
    }
}
