/* SPDX-License-Identifier: AGPL-3.0-only */
package atropos.core.provider

import java.nio.file.Path

/**
 * Structured runtime context envelope for every provider invocation.
 *
 * All fields are mandatory except where noted.  The envelope is serialised
 * into every provider prompt so that the provider can attest to which
 * system, task, role, territory and policy it is operating under.
 *
 * The canonical [contextHash] is a SHA-256 of the serialised envelope
 * (excluding the hash field itself) and is required back in the provider's
 * response attestation.
 */
data class ContextEnvelope(
    // ── System identity ──────────────────────────────────────────────
    val systemIdentity: String = "ATROPOS",
    val systemType: String = "autonomous software engine",
    val repository: String,
    val repositoryRoot: String,
    val branch: String,
    val baselineCommit: String,

    // ── Goal / run / DAG / node (optional — "" when absent) ──────────
    val goalId: String = "",
    val runId: String = "",
    val dagId: String = "",
    val nodeId: String = "",
    val task: String = "",

    // ── Phase / pass ─────────────────────────────────────────────────
    val phaseOrPass: String = "",

    // ── Hierarchy ────────────────────────────────────────────────────
    val hierarchyRole: String = "worker",
    val authority: String = "bounded",
    val permissions: List<String> = emptyList(),

    // ── Territory ────────────────────────────────────────────────────
    val assignedTerritory: List<String> = emptyList(),
    val prohibitedActions: List<String> = emptyList(),

    // ── Policy ───────────────────────────────────────────────────────
    val activePolicy: String = "",

    // ── Provider ─────────────────────────────────────────────────────
    val providerId: String = "",
    val modelId: String = "",
    val contextVersion: String = "1.0",

    // ── Hash (computed, not passed in) ───────────────────────────────
    val canonicalContextHash: String = ""
)
