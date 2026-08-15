/* SPDX-License-Identifier: AGPL-3.0-only */
package atropos.core.factory

/**
 * Renders a factory run's intent as modal requirement sentences.
 *
 * The requirements document was written entirely as `key=value` lines. That is
 * readable, but SpecGraph's `AtomService.extract_document` keys on modal
 * requirement sentences — MUST, SHALL — and a document containing none yields
 * zero atoms. Measured directly against the atomizer:
 *
 * ```
 * ATROPOS-shaped requirements -> atoms: 0
 * modal-verb requirements     -> atoms: 2
 * ```
 *
 * So the canonical atomizer could never have planned a factory run: it was
 * handed a document it is structurally unable to read, returned nothing, and the
 * internal extractor silently planned instead. The `key=value` block is kept —
 * other readers parse it — and the modal statements are added alongside it.
 *
 * Deterministic and pure. The same intent must produce byte-identical text, or
 * the document hash changes between runs and the lineage check that ties an
 * atomization to its prompt fails on a document that did not meaningfully
 * change.
 */
object FactoryRequirementStatements {

    /**
     * The intent as a `## Requirements` section.
     *
     * MUST rather than SHALL throughout. Both are modal, but mixing them implies
     * a distinction in obligation that nothing here intends, and a generated
     * document should not invent a hierarchy the prompt never stated.
     */
    fun render(intent: AppIntent): String = buildString {
        appendLine("## Requirements")
        appendLine()
        statements(intent).forEach { appendLine(it) }
    }.trimEnd()

    /**
     * The individual sentences, in a stable order.
     *
     * Ordered as: what the artifact is, then one sentence per feature, then the
     * invariants that hold regardless of the prompt. Feature order follows the
     * intent's own list rather than being sorted, so the document reads in the
     * order the operator said things.
     */
    fun statements(intent: AppIntent): List<String> = buildList {
        add("The ${intent.kind} named ${intent.name} MUST be generated from the user request.")

        intent.features.forEach { feature ->
            add("The ${intent.kind} MUST support ${feature.trim()}.")
        }

        // Invariants. Present on every run, and stated as requirements rather
        // than as metadata so they are atomized alongside the features -- these
        // are the ones that must not be quietly dropped when a plan is trimmed.
        add("The generated source MUST compile.")
        add("The generated executable tests MUST pass.")
        add("The generated project MUST NOT mutate the host repository outside its own territory.")
        add("Provider prose MUST NOT execute directly as a command.")
        add("The run MUST record evidence sufficient for an independent audit.")
    }
}
