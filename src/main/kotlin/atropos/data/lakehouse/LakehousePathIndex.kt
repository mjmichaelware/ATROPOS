/* SPDX-License-Identifier: AGPL-3.0-only */
package atropos.data.lakehouse

import atropos.core.AtroposConfig
import java.io.File

/**
 * The registry of lakehouse path tags, searchable by keyword.
 *
 * [LakehousePathRetrieve] is exact-path only, by design — it is a content
 * store, and a store that guessed at paths would return the wrong bytes with
 * full confidence. Keyword search is a separate concern and this is where it
 * lives, so the retriever keeps its narrow contract.
 *
 * Matching is against path *segments*: `E/networking/http` contributes
 * `networking` and `http`, and an atom mentioning either scores. The leading
 * domain letter is dropped by position — it is a shelf marker, and keeping it
 * would let a stray `e` token match a quarter of the registry. By position and
 * not by length: `C/languages/c/syntax` names the C language in a
 * single-character segment, and a length filter would remove exactly the token
 * that identifies it.
 *
 * The registry is read once and cached. It is a few hundred lines and every
 * atom in a plan queries it; re-reading per atom would turn one plan into
 * hundreds of file reads on phone storage.
 */
class LakehousePathIndex(
    private val config: AtroposConfig = AtroposConfig.load()
) {
    private val paths: List<String> by lazy { readRegistry() }

    /** Every registered path tag, or empty when no mount is present. */
    fun registry(): List<String> = paths

    val available: Boolean get() = paths.isNotEmpty()

    /**
     * Path tags whose segments overlap [keywords], best first.
     *
     * @param limit how many to return. Small on purpose: each hit becomes a
     *   content fetch, and an atom carrying twenty documents of context has
     *   buried the one that mattered.
     */
    fun match(keywords: List<String>, limit: Int = DEFAULT_LIMIT): List<PathMatch> {
        if (keywords.isEmpty() || paths.isEmpty()) return emptyList()
        val wanted = keywords.toSet()

        return paths.asSequence()
            .map { path -> PathMatch(path, score(path, wanted)) }
            .filter { it.score > 0 }
            // Score first, then the shorter path. A shorter tag is the more
            // general shelf, and when two score equally the general one is the
            // safer context to attach.
            .sortedWith(compareByDescending<PathMatch> { it.score }.thenBy { it.path.length })
            .take(limit)
            .toList()
    }

    /**
     * How well a path matches.
     *
     * A whole-segment hit counts double a partial one. `http` matching the
     * segment `http` is a different claim from `http` appearing inside
     * `http_legacy_notes`, and collapsing the two would rank incidental
     * substring hits alongside exact subject matches.
     */
    private fun score(path: String, keywords: Set<String>): Int {
        val segments = segmentsOf(path)
        var total = 0
        for (segment in segments) {
            if (segment in keywords) {
                total += EXACT_SEGMENT_WEIGHT
                continue
            }
            // Compound segments like `a11y_design` should match `a11y`.
            val parts = segment.split('_').filter { it.length >= 3 }
            if (parts.any { it in keywords }) total += PARTIAL_SEGMENT_WEIGHT
        }
        return total
    }

    /**
     * The matchable segments of a path.
     *
     * Only the *first* segment is dropped, and only because that position is
     * the domain shelf marker — `A`, `B`, `C`, `E`. Filtering by length
     * instead looks equivalent and is not: `C/languages/c/syntax` names the C
     * language in its third segment, and a length filter silently removed the
     * one token that identified it. Dropping by position removes the shelf
     * marker and nothing else.
     */
    private fun segmentsOf(path: String): List<String> =
        path.split('/')
            .map { it.trim().lowercase() }
            .filter { it.isNotEmpty() }
            .drop(1)

    private fun readRegistry(): List<String> = runCatching {
        val file = File(File(config.lakehouse.mountPath), REGISTRY_RELATIVE)
        if (!file.isFile) return@runCatching emptyList()
        file.readLines()
            .map { it.trim() }
            .filter { it.isNotEmpty() && !it.startsWith("#") }
            .distinct()
    }.getOrDefault(emptyList())

    private companion object {
        const val REGISTRY_RELATIVE = "index/paths.txt"
        const val DEFAULT_LIMIT = 3
        const val EXACT_SEGMENT_WEIGHT = 2
        const val PARTIAL_SEGMENT_WEIGHT = 1
    }
}

data class PathMatch(val path: String, val score: Int)
