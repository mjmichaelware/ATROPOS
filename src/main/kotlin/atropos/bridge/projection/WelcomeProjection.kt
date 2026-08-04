/* SPDX-License-Identifier: AGPL-3.0-only */
package atropos.bridge.projection

import atropos.bridge.http.JsonWriter
import atropos.core.welcome.WelcomeArtifact

/**
 * Projects the first-boot welcome onto the wire.
 *
 * `SUP.UX.FREE-PROVIDER-WELCOME` requires onboarding to be deterministic and
 * zero-cost after first view. The content id travels with the body so the
 * surface can decide "already seen" by comparing hashes rather than by setting
 * a flag — a flag says the operator saw *a* welcome, the hash says they saw
 * *this* one, and only the second survives the welcome changing.
 *
 * The rendered text is emitted verbatim rather than decomposed into fields. The
 * artifact is content-addressed; a surface that rebuilt the prose from parts
 * would produce something whose hash no longer matches what it claims to show.
 */
class WelcomeProjection {
    fun render(artifact: WelcomeArtifact): String = JsonWriter.obj(
        "ok" to JsonWriter.bool(true),
        // The address, so "seen" is a statement about this exact content.
        "contentId" to JsonWriter.str(artifact.contentId()),
        "body" to JsonWriter.str(artifact.render())
    )
}
