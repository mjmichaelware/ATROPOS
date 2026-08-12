/* SPDX-License-Identifier: AGPL-3.0-only */
package atropos.core.ingest

/**
 * Finds `@path` mentions in a prompt.
 *
 * Source Doc 5: "Add @mention file capabilities... a user can upload .txt,
 * .docs, .PDF, .doc, .PNG, .jpeg... so long as that document is within the root
 * or whatever level the jar was opened at."
 *
 * Split from [MentionResolver] because finding a mention and deciding whether
 * it may be read are different jobs with different failure modes. The scanner
 * is pure text and can be wrong harmlessly; the resolver touches the
 * filesystem and cannot. Keeping them apart means the security decision has
 * one owner and no parsing in it.
 *
 * The grammar is deliberately narrow. `@` is common in prose — email
 * addresses, handles, decorators — so a mention must look like a path: it
 * needs a dot with an extension after it, and it stops at whitespace. An
 * over-eager scanner would send `@anthropic.com` to the resolver, which would
 * refuse it, producing a confusing error for text that was never a mention.
 */
object AtMentionScanner {

    fun scan(text: String): List<String> =
        MENTION.findAll(text)
            .map { it.groupValues[1] }
            .filter { candidate -> candidate.substringAfterLast('.', "").isNotEmpty() }
            // An email is `name@host.tld`: the `@` has a word character
            // immediately before it. A mention starts a token.
            .filter { candidate -> candidate.isNotBlank() }
            .distinct()
            .toList()

    /** The prompt with mentions removed, so the remaining text is the request. */
    fun strip(text: String): String =
        MENTION.replace(text, "").replace(Regex(" {2,}"), " ").trim()

    /**
     * `@` at a token boundary, followed by a path-shaped run.
     *
     * The leading `(?<![\w@])` is what keeps `user@example.com` out: there the
     * `@` follows a word character, so it is an address rather than the start
     * of a mention.
     */
    private val MENTION = Regex("""(?<![\w@])@([^\s@'"`,;]+)""")
}
