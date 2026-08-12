/* SPDX-License-Identifier: AGPL-3.0-only */
package atropos.data.lakehouse

import atropos.core.planning.AtomContext
import atropos.core.planning.AtomContextProvider
import atropos.core.planning.InternalAtom
import atropos.core.security.RedactionFilter
import java.nio.charset.StandardCharsets

/**
 * Fetches each atom's context from the lakehouse, keyed by its own language.
 *
 * The chain is: the atom's canonical statement produces keywords, the keywords
 * select path tags from the registry, and each selected path is fetched through
 * [LakehousePathRetrieve] — which stays exact-path only, because a content
 * store that guessed would return the wrong bytes with full confidence.
 *
 * Every outcome is recorded, including misses. An atom that asked for context
 * and got none is a different situation from one that never asked, and after
 * the fact only the record distinguishes them — which matters when a generated
 * result looks uninformed and somebody has to work out whether the shelf was
 * empty or never consulted.
 *
 * Content is truncated and redacted. A lakehouse document can be large, and the
 * whole point is to give a provider call the passage it needs rather than a
 * library; an atom carrying a megabyte of context has buried the sentence that
 * mattered. Redaction applies because retrieved documents are rendered into
 * prompts and evidence like anything else.
 */
class LakehouseAtomContextProvider(
    private val index: LakehousePathIndex = LakehousePathIndex(),
    private val retrieve: LakehousePathRetrieve = LakehousePathRetrieve(),
    private val redactionFilter: RedactionFilter = RedactionFilter(),
    private val maxPaths: Int = DEFAULT_MAX_PATHS,
    private val maxContentChars: Int = DEFAULT_MAX_CONTENT_CHARS
) : AtomContextProvider {

    override fun contextFor(atom: InternalAtom): List<AtomContext> {
        if (!index.available) return emptyList()

        val keywords = AtomKeywordExtractor.keywords(atom.statement)
        val matches = index.match(keywords, limit = maxPaths)
        if (matches.isEmpty()) return emptyList()

        return matches.map { match ->
            val result = runCatching { retrieve.get(match.path) }.getOrNull()
            if (result == null) {
                // A retriever that threw is not a miss. Calling it one would
                // hide a broken mount behind a shelf that merely looked empty.
                return@map AtomContext(
                    path = match.path,
                    sha256 = null,
                    status = "ERROR",
                    reason = "retriever_failed",
                    content = ""
                )
            }

            AtomContext(
                path = result.path,
                sha256 = result.nodeId,
                status = result.status,
                reason = result.reason,
                content = result.bytes
                    ?.toString(StandardCharsets.UTF_8)
                    ?.let { redactionFilter.redact(it) }
                    ?.take(maxContentChars)
                    .orEmpty()
            )
        }
    }

    private companion object {
        /**
         * Three shelves per atom. Enough to cover a statement that spans two
         * subjects, few enough that the retrieved context stays readable.
         */
        const val DEFAULT_MAX_PATHS = 3

        /** Per document, not per atom. A passage, not a library. */
        const val DEFAULT_MAX_CONTENT_CHARS = 4_000
    }
}
