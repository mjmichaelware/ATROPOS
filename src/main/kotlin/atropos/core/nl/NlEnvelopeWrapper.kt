/* SPDX-License-Identifier: AGPL-3.0-only */
package atropos.core.nl

import atropos.core.artifact.ArtifactHasher
import java.nio.charset.StandardCharsets
import java.time.Instant

/**
 * Wraps canonical natural language so its origin travels with it.
 *
 * `SUP.NL.ENVELOPE-WRAP`: "Even NL-derived context carries attestation;
 * provenance of user intent is never lost. Competitors treat NL as untracked
 * string."
 *
 * Both hashes are kept, and keeping both is the point. The canonical hash
 * identifies what the system acted on; the original hash proves what the
 * operator actually typed. With only the canonical form, a canonicalizer bug
 * that changed meaning would be undetectable after the fact — the record would
 * agree with itself and disagree with the human.
 *
 * The original text itself is not carried, only its hash. An unredacted prompt
 * is exactly the kind of string that ends up in an evidence bundle, and a
 * credential pasted into a phone prompt should not become durable because the
 * provenance record was thorough.
 */
class NlEnvelopeWrapper(private val clock: () -> Instant = { Instant.now() }) {

    fun wrap(result: NlCanonicalResult, source: NlSource): NlEnvelope = NlEnvelope(
        canonical = result.canonical,
        canonicalSha256 = sha(result.canonical),
        originalSha256 = sha(result.original),
        source = source,
        transformations = result.notes,
        wrappedAt = clock()
    )

    private fun sha(value: String): String =
        ArtifactHasher.sha256Bytes(value.toByteArray(StandardCharsets.UTF_8))
}

/**
 * Where a natural-language string entered the system.
 *
 * Recorded because the surfaces have different threat profiles and different
 * defect profiles. Text from a phone keyboard arrives with swipe artifacts;
 * text from a bridge client arrives over a port and is the only one an
 * attacker can reach without the device. Losing that distinction would make
 * both look like "the operator asked for this".
 */
enum class NlSource(val canonical: String) {
    CLI_PROMPT("cli"),
    BRIDGE_MESSAGE("bridge"),
    ANDROID_COMPOSER("android"),
    FILE_MENTION("mention")
}

data class NlEnvelope(
    val canonical: String,
    val canonicalSha256: String,
    val originalSha256: String,
    val source: NlSource,
    val transformations: List<String>,
    val wrappedAt: Instant
) {
    /** True when canonicalization changed the operator's bytes. */
    val wasTransformed: Boolean get() = canonicalSha256 != originalSha256

    /** The line that goes into an evidence bundle. */
    fun evidence(): String =
        "nl source=${source.canonical} canonical=${canonicalSha256.take(16)} " +
            "original=${originalSha256.take(16)} transformed=$wasTransformed at=$wrappedAt"
}
