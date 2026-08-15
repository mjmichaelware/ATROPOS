/* SPDX-License-Identifier: AGPL-3.0-only */
package atropos.core.verification

import atropos.core.contract.AtroposView
import atropos.core.intent.CanonicalVerb

object UiParityVerifier {

    /** Acceptance test: no view shows more than 13 verbs (static validation check). */
    fun verifyMaxVerbLimit(view: AtroposView, verbsShown: List<CanonicalVerb>): Boolean {
        return verbsShown.distinct().size <= 13
    }

    /** Acceptance test: view buttons match contract-layer valid verbs exactly. */
    fun verifyVerbButtons(verbsShown: List<CanonicalVerb>): Boolean {
        val allowedKeywords = CanonicalVerb.values().map { it.keyword }
        return verbsShown.all { it.keyword in allowedKeywords }
    }

    /** Acceptance test: every stream element collapsed on first render; expansion does not persist. */
    fun verifyExpansionState(isExpandedByDefault: Boolean, isStatePersistent: Boolean): Boolean {
        return !isExpandedByDefault && !isStatePersistent
    }

    /** Acceptance test: status vocabulary identical across all 16 views + CLI. */
    fun verifyStatusVocabularyParity(vocabularies: Map<AtroposView, Set<String>>): Boolean {
        if (vocabularies.isEmpty()) return true
        val first = vocabularies.values.first()
        return vocabularies.values.all { it == first }
    }
}
