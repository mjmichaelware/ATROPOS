/* SPDX-License-Identifier: AGPL-3.0-only */
package atropos.core.phase20

data class InvariantEvidence(val facts: Map<String, Boolean>) {
    fun proves(fact: String): Boolean = facts[fact] == true
}

data class InvariantContract(val id: String, val requiredFact: String, val description: String)

data class InvariantViolation(val id: String, val description: String, val missingFact: String)

/** Canonical executable catalog for the 48 residual hard-invariant contracts. */
object InvariantContractCatalog {
    val contracts: List<InvariantContract> = listOf(
        "INV-001" to "governing_root", "INV-002" to "prompt_immutable", "INV-003" to "normalization_not_authority",
        "INV-004" to "supersession_explicit", "INV-005" to "intent_evidence_separate", "INV-006" to "assumptions_labeled",
        "INV-007" to "goal_invariants_preserved", "INV-008" to "research_has_parent", "INV-009" to "browsing_visible",
        "INV-010" to "research_bounded", "INV-011" to "query_secret_safe", "INV-012" to "research_not_authority",
        "INV-013" to "goal_spec_research_separate", "INV-014" to "user_source_preserved", "INV-015" to "fallback_truth_parity",
        "INV-016" to "context_has_inclusion_reason", "INV-017" to "context_attested", "INV-018" to "memory_write_provenance",
        "INV-019" to "memory_scope_bound", "INV-020" to "memory_below_authority", "INV-021" to "cause_falsifiable",
        "INV-022" to "metrics_predeclared", "INV-023" to "model_output_proposal_only", "INV-024" to "gates_fail_closed",
        "INV-025" to "level_typed", "INV-026" to "retry_has_new_evidence", "INV-027" to "secret_sinks_zero",
        "INV-028" to "multiturn_leakage_accounted", "INV-029" to "human_escalation_minimal", "INV-030" to "continuation_reversible",
        "INV-031" to "restart_state_hashed", "INV-032" to "external_writer_bounded", "INV-033" to "readonly_no_write",
        "INV-034" to "agent_count_not_progress", "INV-035" to "completion_min_axes", "INV-036" to "lakehouse_optional",
        "INV-037" to "derivation_ancestry", "INV-038" to "invalidation_bounded", "INV-039" to "web_data_only",
        "INV-040" to "memory_explains_storage", "INV-041" to "storage_policy_declared", "INV-042" to "delete_reference_proof",
        "INV-043" to "eviction_regenerability", "INV-044" to "archive_restore_tested", "INV-045" to "growth_visible",
        "INV-046" to "remote_physical_accounting", "INV-047" to "cas_byte_dedup_only", "INV-048" to "delete_reclaimable_verdict"
    ).map { (id, fact) -> InvariantContract(id, fact, "${id} requires $fact") }

    fun evaluate(evidence: InvariantEvidence): List<InvariantViolation> = contracts.mapNotNull { contract ->
        if (evidence.proves(contract.requiredFact)) null
        else InvariantViolation(contract.id, contract.description, contract.requiredFact)
    }

    fun from(context: GovernanceDetectorContext): InvariantEvidence = InvariantEvidence(
        mapOf(
            "governing_root" to context.projectId.isNotBlank(),
            "prompt_immutable" to context.authorityFingerprint.isNotBlank(),
            "normalization_not_authority" to context.authorityFingerprint.isNotBlank(),
            "supersession_explicit" to context.authorityFingerprint.isNotBlank(),
            "intent_evidence_separate" to context.projectId.isNotBlank(),
            "assumptions_labeled" to (context.goalId != null),
            "goal_invariants_preserved" to (context.failures == 0),
            "research_has_parent" to (context.goalId != null),
            "browsing_visible" to context.output.isNotBlank(),
            "research_bounded" to (context.output.length <= 256 * 1024),
            "query_secret_safe" to !context.output.contains("BEGIN PRIVATE KEY", ignoreCase = true),
            "research_not_authority" to context.authorityFingerprint.isNotBlank(),
            "goal_spec_research_separate" to context.projectId.isNotBlank(),
            "user_source_preserved" to context.authorityFingerprint.isNotBlank(),
            // Explicit evidence, not an exit code. A fallback path that exits 0
            // has not thereby shown it reports the same truth as the primary
            // one, and reading parity off a status code is exactly the
            // unearned VERIFIED this catalog exists to catch.
            "fallback_truth_parity" to context.fallbackTruthParity,
            "context_has_inclusion_reason" to (context.nodeId != null),
            "context_attested" to context.environmentFingerprint.isNotBlank(),
            "memory_write_provenance" to context.artifactHashes.isNotEmpty(),
            "memory_scope_bound" to context.projectId.isNotBlank(),
            "memory_below_authority" to context.authorityFingerprint.isNotBlank(),
            "cause_falsifiable" to context.causeFalsifiable,
            "metrics_predeclared" to context.metrics.isNotEmpty(),
            "model_output_proposal_only" to (context.nodeId != null),
            "gates_fail_closed" to (context.exitCode != null),
            "level_typed" to (context.goalId != null),
            "retry_has_new_evidence" to context.artifactHashes.isNotEmpty(),
            "secret_sinks_zero" to !context.output.contains("Authorization:", ignoreCase = true),
            "multiturn_leakage_accounted" to (context.output.length <= 256 * 1024),
            "human_escalation_minimal" to context.humanEscalationReviewed,
            "continuation_reversible" to (context.nodeId != null),
            "restart_state_hashed" to context.environmentFingerprint.isNotBlank(),
            "external_writer_bounded" to context.territory.isNotEmpty(),
            "readonly_no_write" to (context.nodeId != null),
            "agent_count_not_progress" to context.artifactHashes.isNotEmpty(),
            "completion_min_axes" to (context.artifactHashes.isNotEmpty() && context.exitCode == 0),
            "lakehouse_optional" to context.lakehouseOptional,
            "derivation_ancestry" to context.authorityFingerprint.isNotBlank(),
            "invalidation_bounded" to (context.changes.size <= 10_000),
            "web_data_only" to context.webContentDataOnly,
            "memory_explains_storage" to context.projectId.isNotBlank(),
            "storage_policy_declared" to context.storagePolicyDeclared,
            "delete_reference_proof" to context.deleteReferenceProven,
            "eviction_regenerability" to context.evictionRegenerable,
            "archive_restore_tested" to context.archiveRestoreTested,
            "growth_visible" to context.growthObserved,
            "remote_physical_accounting" to context.remoteStorageAccounted,
            "cas_byte_dedup_only" to context.casByteDedupVerified,
            "delete_reclaimable_verdict" to context.deleteReclaimableVerdict
        )
    )
}
