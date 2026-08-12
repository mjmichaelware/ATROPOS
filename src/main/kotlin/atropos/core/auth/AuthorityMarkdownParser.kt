/* SPDX-License-Identifier: AGPL-3.0-only */
package atropos.core.auth

/**
 * The one grammar every authority document is read through.
 *
 * `SUP.AUTH.AGENTS-MD` requires a "minimal schema" for HIG=0 compliance, and
 * `SUP.AUTH.CASCADE-PRECEDENCE` requires the cascade to compare keys across
 * documents. Both need the documents to yield the *same* key space; two
 * parsers would mean `Agents.md` and `Swarm.md` could disagree about what a key
 * even is, and the cascade would then be resolving different things under one
 * name.
 *
 * The grammar is deliberately tiny and deterministic — no model call, no
 * heuristics. Three forms are recognised anywhere in the document:
 *
 * ```
 * key: value            a bare colon line
 * - key: value          a list item
 * **key**: value        an emphasised key
 * ```
 *
 * Prose is ignored rather than rejected. These files are written by people and
 * mostly consist of explanation; a parser that failed on the explanation would
 * make the attested-document path unusable for the documents that actually
 * exist.
 *
 * First occurrence wins within one document. A file that states a key twice has
 * a defect, and taking the later value would let an instruction appended to the
 * bottom of a long file quietly beat the one at the top that a reader would
 * see first.
 */
object AuthorityMarkdownParser {

    fun parse(text: String): Map<String, String> {
        val values = LinkedHashMap<String, String>()
        var inFencedBlock = false

        for (raw in text.lineSequence()) {
            val line = raw.trim()

            // Fenced code is example text, not instruction. A snippet showing
            // `boundedAgencyGate: off` must not turn the gate off.
            if (line.startsWith("```") || line.startsWith("~~~")) {
                inFencedBlock = !inFencedBlock
                continue
            }
            if (inFencedBlock || line.isEmpty() || line.startsWith("#")) continue

            val (key, value) = splitKeyValue(line) ?: continue
            if (key.isEmpty() || value.isEmpty()) continue
            values.putIfAbsent(key, value)
        }
        return values
    }

    private fun splitKeyValue(line: String): Pair<String, String>? {
        val stripped = line.removePrefix("-").removePrefix("*").trim()
        val colon = stripped.indexOf(':')
        if (colon <= 0 || colon == stripped.length - 1) return null

        val key = stripped.take(colon).trim().trim('*', '_', '`', '"').trim()
        val value = stripped.substring(colon + 1).trim().trim('`', '"').trim()

        // A sentence containing a colon is prose, not a setting. Keys are
        // identifiers; anything with whitespace inside it is a phrase.
        if (key.isBlank() || key.any { it.isWhitespace() }) return null
        if (!key.first().isLetter()) return null
        return key to value
    }

    /**
     * Lines under a `## <heading>` section, with the heading itself dropped.
     *
     * [SwarmMdLoader] needs node lists as a sequence rather than as a key map —
     * a swarm names many nodes and they are not distinguished by key.
     */
    fun section(text: String, heading: String): List<String> {
        val wanted = heading.trim().lowercase()
        val out = mutableListOf<String>()
        var inside = false

        for (raw in text.lineSequence()) {
            val line = raw.trim()
            if (line.startsWith("#")) {
                inside = line.trimStart('#').trim().lowercase() == wanted
                continue
            }
            if (inside && line.isNotEmpty()) out += line.removePrefix("-").removePrefix("*").trim()
        }
        return out
    }
}
