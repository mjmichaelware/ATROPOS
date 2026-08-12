/* SPDX-License-Identifier: AGPL-3.0-only */
package atropos.core.provider

import atropos.core.artifact.ArtifactHasher
import java.nio.charset.StandardCharsets
import java.time.Instant

/**
 * A governing prompt that cannot be edited after it is attested.
 *
 * `SUP.VERIF.PROMPT-IMMUTABILITY`: "Governing prompts are immutable after
 * attestation; injection via prompt rewrite is structurally impossible.
 * Competitors allow runtime prompt mutation."
 *
 * Structurally is the claim, so the type has to carry it. [text] is a `val` on
 * a class with no copy and no setters, and [ancestrySha256] is computed at
 * construction from the bytes themselves — there is no sequence of calls that
 * produces an [ImmutablePrompt] whose hash does not describe its text.
 *
 * That is stronger than validating a prompt before sending it. A validated
 * mutable prompt is correct at the moment it is checked and can be different
 * one line later; the window between check and use is exactly where an
 * injection lands.
 *
 * @param sourceSha256 the hash of the document this was derived from, when it
 *   came from one. Null for a prompt assembled from parts, which is honest —
 *   claiming a source that does not exist would make the ancestry chain a
 *   decoration.
 */
class ImmutablePrompt private constructor(
    val text: String,
    val role: PromptRole,
    val sourceSha256: String?,
    val loadedAt: Instant
) {
    /** The hash of this prompt's own bytes. Computed once, from [text]. */
    val ancestrySha256: String =
        ArtifactHasher.sha256Bytes(text.toByteArray(StandardCharsets.UTF_8))

    /**
     * Whether [candidate] is the text this prompt attests.
     *
     * Used at the provider boundary. A caller that reassembled the prompt from
     * strings can prove it did not change it, without this class needing to
     * know how the reassembly worked.
     */
    fun attests(candidate: String): Boolean =
        ArtifactHasher.sha256Bytes(candidate.toByteArray(StandardCharsets.UTF_8)) == ancestrySha256

    /** The line that goes into an evidence bundle. */
    fun evidence(): String =
        "prompt role=${role.canonical} sha256=${ancestrySha256.take(16)} " +
            "source=${sourceSha256?.take(16) ?: "assembled"} at=$loadedAt"

    override fun toString(): String = evidence()

    companion object {
        /**
         * @return null for blank text. A governing prompt that says nothing
         *   cannot govern, and returning an empty one would let a provider call
         *   satisfy the "has a valid prompt" gate with no instruction at all.
         */
        fun of(
            text: String,
            role: PromptRole,
            sourceSha256: String? = null,
            loadedAt: Instant = Instant.now()
        ): ImmutablePrompt? {
            if (text.isBlank()) return null
            return ImmutablePrompt(text, role, sourceSha256, loadedAt)
        }
    }
}

/**
 * What a governing prompt governs.
 *
 * [SYSTEM] and [AUTHORITY] are the two that must be immutable — the first
 * because it defines the model's role, the second because it carries what an
 * attested document said. [TASK] is included so the same attestation machinery
 * covers the whole message set rather than half of it; a task rewritten between
 * check and send is an injection whatever it is called.
 */
enum class PromptRole(val canonical: String) {
    SYSTEM("system"),
    AUTHORITY("authority"),
    TASK("task")
}
