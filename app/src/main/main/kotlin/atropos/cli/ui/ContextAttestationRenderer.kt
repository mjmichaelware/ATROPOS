/* SPDX-License-Identifier: AGPL-3.0-only */
package atropos.cli.ui

import atropos.cli.ui.design.Glyphs
import atropos.cli.ui.design.Role
import atropos.cli.ui.design.RunState
import atropos.core.provider.ContextAttestation
import atropos.core.provider.ContextEnvelope
import atropos.core.provider.TypedContextFailure
import atropos.core.memory.LocalMemoryStore
import atropos.core.memory.MemoryRecord
import atropos.core.security.RedactionFilter

/**
 * Renders provider context attestation — Source Doc 3 requirements 1–5.
 *
 * > 1. ATROPOS identity awareness — every provider must know it is operating
 * >    inside ATROPOS.
 * > 5. Typed context failures — mythology answers, role confusion, stale
 * >    context, and mismatched hashes become **explicit** failures.
 *
 * "Explicit" is the operative word: before this renderer the attestation layer
 * detected drift and journaled it, but nothing surfaced it, so a provider that
 * answered about the Greek Fate instead of the system failed silently. A failure
 * that is only written to a log is not explicit.
 *
 * Presentation only — it makes no attestation decision and never re-runs a
 * check, per Source Doc 3 §1.1 ("no file shall mix presentation concerns with
 * core decision logic").
 */
class ContextAttestationRenderer(
    private val theme: TerminalTheme,
    private val redactionFilter: RedactionFilter = RedactionFilter()
) {
    private val surface get() = theme.surface

    /**
     * A rejected attestation, rendered as a failure block.
     *
     * The rail is tinted with the failure role so the whole block reads as
     * refused at a glance, and the state badge carries glyph plus label so the
     * signal survives `NO_COLOR` and monochrome terminals.
     */
    fun renderRejection(
        failure: TypedContextFailure,
        envelope: ContextEnvelope?,
        width: Int
    ): String {
        val railGlyph = if (asciiOnly()) Glyphs.Ascii.RAIL else Glyphs.RAIL
        val rail = theme.paint(Role.STATUS_FAILED, railGlyph)
        val pad = " ".repeat(Glyphs.RAIL_PADDING)
        val inner = (width - railGlyph.length - Glyphs.RAIL_PADDING).coerceAtLeast(12)

        fun line(text: String) = TerminalText.ellipsize(rail + pad + text, width)

        return buildList {
            add(line(theme.paint(Role.STATUS_FAILED, "CONTEXT ATTESTATION FAILED")))
            add(line(surface.runState(RunState.FAILED) + " " + theme.strong(kindLabel(failure))))
            add(line(row("provider", failure.providerId, inner)))
            add(line(row("reason", TerminalText.ellipsize(redactionFilter.redact(failure.reason), inner - 12), inner)))
            add(
                line(
                    row(
                        "retryable",
                        if (failure.retryable) theme.warning("yes") else theme.paint(Role.STATUS_FAILED, "no"),
                        inner
                    )
                )
            )
            envelope?.let {
                add(line(row("repository", it.repository, inner)))
                add(line(row("task", it.task.ifBlank { it.nodeId }, inner)))
                add(line(row("role", it.hierarchyRole, inner)))
                add(line(row("branch", it.branch, inner)))
            }
            add(line(theme.subdued(guidance(failure))))
        }.joinToString("\n")
    }

    /**
     * One-line advisory shown *above* an answer that failed attestation but is
     * still being displayed.
     *
     * Conversational prompts use this rather than [renderRejection]: the
     * envelope's purpose is to shape how a provider answers, and discarding a
     * usable answer leaves the operator with nothing. Strict rejection stays
     * where correctness is load-bearing — patch generation and apply — while
     * chat degrades to a visible warning.
     */
    fun renderAdvisory(failure: TypedContextFailure, width: Int): String {
        val railGlyph = if (asciiOnly()) Glyphs.Ascii.RAIL else Glyphs.RAIL
        return TerminalText.ellipsize(
            theme.paint(Role.STATUS_WAITING, railGlyph) + "  " +
                theme.warning("unattested") + " " +
                theme.subdued("${kindLabel(failure)} · answer shown unverified"),
            width
        )
    }

    /** A single accepted-attestation line, for status surfaces. */
    fun renderAccepted(attestation: ContextAttestation, width: Int): String =
        TerminalText.ellipsize(
            surface.runState(RunState.COMPLETE) + " " +
                theme.metadata("attested ") +
                theme.strong(attestation.systemIdentity) +
                theme.subdued("  ${shortHash(attestation.contextHash)}"),
            width
        )

    /**
     * Attestation rows for `/agent status`.
     *
     * When no attestation has been performed this renders `unknown` rather than
     * implying success — the base doc forbids fabricating state.
     */
    /**
     * Attestation rows for `/agent status`, sourced from the durable failure
     * record `AgentService` already persists.
     *
     * Reading the persisted record rather than plumbing a new field through
     * `AgentRunResult` keeps this presentation-only: no backend type changes,
     * and the rendered state is whatever actually happened, not a UI guess.
     */
    fun renderStatusRowsFromMemory(
        width: Int,
        memory: LocalMemoryStore = LocalMemoryStore()
    ): List<String> {
        val last = runCatching {
            memory.all(200).firstOrNull { record ->
                record.subjectType == "context_failure" ||
                    record.tags.containsAll(listOf("context", "attestation", "failure"))
            }
        }.getOrNull()

        return buildList {
            add(surface.sectionHeading("CONTEXT ATTESTATION", width))
            add(
                surface.row(
                    "identity",
                    theme.strong("ATROPOS") + theme.subdued(" injected into every provider call"),
                    width
                )
            )
            if (last == null) {
                add(surface.row("last failure", surface.runState(RunState.UNKNOWN), width))
                add(surface.hint("no context failure recorded", width))
            } else {
                add(surface.row("last failure", surface.runState(RunState.FAILED), width))
                add(surface.row("kind", humanKind(last.title), width))
                add(surface.row("detail", redactionFilter.redact(last.body), width))
            }
        }
    }

    /** Maps the persisted failure class name to its human label. */
    private fun humanKind(title: String): String = when {
        title.contains("Mythology", ignoreCase = true) -> "mythology answer"
        title.contains("RoleConfusion", ignoreCase = true) -> "role confusion"
        title.contains("StaleContext", ignoreCase = true) -> "stale context"
        title.contains("HashMismatch", ignoreCase = true) -> "context hash mismatch"
        else -> title
    }

    fun renderStatusRows(
        lastAttestation: ContextAttestation?,
        lastFailure: TypedContextFailure?,
        width: Int
    ): List<String> = buildList {
        add(surface.sectionHeading("CONTEXT ATTESTATION", width))
        when {
            lastFailure != null -> {
                add(surface.row("state", surface.runState(RunState.FAILED), width))
                add(surface.row("kind", kindLabel(lastFailure), width))
                add(surface.row("provider", lastFailure.providerId, width))
                add(surface.row("reason", redactionFilter.redact(lastFailure.reason), width))
            }
            lastAttestation != null -> {
                add(surface.row("state", surface.runState(RunState.COMPLETE), width))
                add(surface.row("identity", lastAttestation.systemIdentity, width))
                add(surface.row("repository", lastAttestation.repository, width))
                add(surface.row("task", lastAttestation.taskOrNodeId, width))
                add(surface.row("role", lastAttestation.role, width))
                add(surface.row("hash", shortHash(lastAttestation.contextHash), width))
            }
            else -> {
                add(surface.row("state", surface.runState(RunState.UNKNOWN), width))
                add(surface.hint("no provider call attested yet this session", width))
            }
        }
    }

    private fun row(label: String, value: String, width: Int): String =
        theme.metadata(TerminalText.padEnd(label, 11)) + " " +
            TerminalText.ellipsize(value, (width - 12).coerceAtLeast(4))

    /** Human label per failure class. */
    private fun kindLabel(failure: TypedContextFailure): String = when (failure) {
        is TypedContextFailure.MythologyAnswer -> "mythology answer"
        is TypedContextFailure.RoleConfusion -> "role confusion"
        is TypedContextFailure.StaleContext -> "stale context"
        is TypedContextFailure.HashMismatch -> "context hash mismatch"
        else -> "context failure"
    }

    /** What the operator can actually do about it. */
    private fun guidance(failure: TypedContextFailure): String = when (failure) {
        is TypedContextFailure.MythologyAnswer ->
            "provider answered about the Greek Fate, not this system · ATROPOS discarded the response"
        is TypedContextFailure.RoleConfusion ->
            "provider did not accept its assigned role · not retryable, route to another provider"
        is TypedContextFailure.StaleContext ->
            "provider replied against an older context · retry re-sends a fresh envelope"
        is TypedContextFailure.HashMismatch ->
            "context hash did not match · response rejected without execution"
        else -> "response rejected before any execution"
    }

    private fun shortHash(hash: String): String =
        if (hash.length <= 12) hash else hash.take(12) + "…"

    private fun asciiOnly(): Boolean = !System.getenv("ATROPOS_ASCII").isNullOrBlank()
}
