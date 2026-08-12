/* SPDX-License-Identifier: AGPL-3.0-only */
package atropos.bridge.projection

import atropos.bridge.http.JsonWriter
import atropos.cli.ui.design.CompletionState
import atropos.cli.ui.design.HoeStatusVocabulary

/**
 * Publishes both status vocabularies to every surface.
 *
 * `HOE-F01` requires the same status vocabulary on CLI, Web and Android. The
 * cheapest way to guarantee that is to stop each surface from owning a copy:
 * the engine serves the terms, and a client that renders a word this endpoint
 * did not send is visibly wrong rather than quietly divergent.
 *
 * The two vocabularies are emitted separately and must stay that way.
 * [HoeStatusVocabulary] answers "what is this work doing"; [CompletionState]
 * answers "how far has this claim been proven". `P20-G09` names their collapse
 * as a governance deficiency — merging them here would hand every surface the
 * collapsed form and make the deficiency structural.
 *
 * Every term carries its non-colour `signal`, so §E's requirement that colour
 * pair with a redundant channel can be met by a client that never receives a
 * colour at all.
 */
class VocabularyProjection {

    fun render(): String = JsonWriter.obj(
        "ok" to JsonWriter.bool(true),
        "status" to JsonWriter.obj(
            "purpose" to JsonWriter.str("what the work is doing"),
            "terms" to JsonWriter.arr(
                HoeStatusVocabulary.CANONICAL_TERMS.map { term ->
                    val signal = HoeStatusVocabulary.signalFor(term)
                    JsonWriter.obj(
                        "term" to JsonWriter.str(term),
                        "icon" to JsonWriter.nullable(signal?.icon),
                        "signal" to JsonWriter.nullable(signal?.text)
                    )
                }
            )
        ),
        "completion" to JsonWriter.obj(
            "purpose" to JsonWriter.str("how far a completion claim has been proven"),
            "terms" to JsonWriter.arr(
                CompletionState.ORDER.map { state ->
                    JsonWriter.obj(
                        "term" to JsonWriter.str(state.canonical),
                        "meaning" to JsonWriter.str(state.meaning),
                        "signal" to JsonWriter.str(state.signal),
                        // Only VERIFIED may render as a positive claim. Sent
                        // explicitly so a client cannot decide otherwise.
                        "isPositiveClaim" to JsonWriter.bool(state.isPositiveClaim)
                    )
                }
            )
        )
    )
}
