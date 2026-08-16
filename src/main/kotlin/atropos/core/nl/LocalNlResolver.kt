/* SPDX-License-Identifier: AGPL-3.0-only */
package atropos.core.nl

import atropos.cli.input.CommandRegistry
import atropos.cli.input.FuzzyMatcher

/**
 * Resolves natural language locally before any provider is consulted.
 *
 * `SUP.NL.LOCAL-MEMORY-LOOKUP`: "Most NL intents resolve locally and
 * deterministically; model calls become the exception. Competitors send every
 * NL string to a model."
 *
 * The ladder is ordered by how certain each rung is, and it stops at the first
 * certain answer:
 *
 * 1. **Exact command.** `/status` is `/status`. Sending that to a provider to
 *    be told it means `/status` costs a round trip and a token budget to learn
 *    nothing.
 * 2. **Exact command without the slash.** `status` typed into a prompt is the
 *    single most common thing an operator does, and treating it as prose is
 *    what makes a CLI feel unresponsive. The match must cover the *whole*
 *    input: a leading slash declares intent and licenses free-form arguments
 *    after it, a bare word declares nothing, and "status of the build please"
 *    is a question rather than `/status` with noise attached.
 * 3. **Fuzzy match against the known command set.** `staus` has one plausible
 *    reading. This rung *proposes* rather than executes — it is the one place
 *    a wrong guess is possible, so the guess is handed back for confirmation
 *    rather than run.
 * 4. **Genuinely prose.** Only here does a provider call become appropriate,
 *    and it is still bounded by the gate like any other action.
 *
 * Nothing here executes anything. It classifies, and the caller decides — a
 * resolver that ran what it recognised would turn a typo into an action.
 */
class LocalNlResolver(
    private val fuzzyMatcher: FuzzyMatcher = FuzzyMatcher(),
    private val knownCommands: () -> List<String> = { CommandRegistry.commands() }
) {
    private val messyIntentParser = MessyIntentParser(emptySet())

    fun resolve(envelope: NlEnvelope): NlResolution {
        val text = envelope.canonical.trim()
        if (text.isEmpty()) return NlResolution.Empty

        val commands = knownCommands()
        val firstWord = text.substringBefore(' ')

        // A leading slash is the operator declaring intent, so the remainder
        // is arguments by definition and may be anything. Without the slash
        // nothing has been declared, and treating a trailing phrase as
        // arguments would turn "status of the build please" into `/status`
        // with three words of noise attached.
        if (text.startsWith('/')) {
            commands.firstOrNull { it.equals(text, ignoreCase = true) }
                ?.let { return NlResolution.ExactCommand(it) }

            commands.firstOrNull { it.equals(firstWord, ignoreCase = true) }
                ?.let {
                    return NlResolution.ExactCommand(it, arguments = text.removePrefix(firstWord).trim())
                }
        }

        // Un-slashed input resolves only on an exact match against the whole
        // string, which still covers multi-word commands like `verify wide`
        // because the registry holds them as whole commands. The registry
        // decides what a command is; anything else is prose.
        commands.firstOrNull { it.equals(text, ignoreCase = true) || it.equals("/$text", ignoreCase = true) }
            ?.let { return NlResolution.ExactCommand(it) }

        // Keep the byte-level messy-input owner on the same local path as the
        // command registry. It proposes a canonical command; execution still
        // remains the caller's responsibility.
        messyIntentParser.parseAgainst(text, commands)
            ?.let { return NlResolution.Suggested(it, text) }

        // Phrase mappings are the canonical verb vocabulary, not a second
        // command registry. They run before edit-distance matching so a
        // meaningful sentence such as "search execution logs" is proposed as
        // /history rather than being sent to a provider or treated as a typo.
        atropos.core.intent.NlPhraseMapper.mapPhrase(text)
            ?.keyword
            ?.takeIf { command -> commands.any { it.equals(command, ignoreCase = true) } }
            ?.let { return NlResolution.Suggested(it, text) }

        // Only single-word input is fuzzy-matched. A sentence that happens to
        // start near a command name is prose -- "status of the build please"
        // is a question, and matching it to /status would answer something the
        // operator did not ask.
        if (!text.contains(' ')) {
            val candidates = commands.filter { command ->
                fuzzyMatcher.matches(text, command.removePrefix("/"))
            }
            if (candidates.size == 1) return NlResolution.Suggested(candidates.single(), text)
            if (candidates.size > 1) {
                return NlResolution.Ambiguous(candidates.sortedBy { it.length }.take(MAX_SUGGESTIONS), text)
            }
        }

        return NlResolution.Prose(text)
    }

    private companion object {
        /** More than a handful of options is a list, not a suggestion. */
        const val MAX_SUGGESTIONS = 5
    }
}

private fun MessyIntentParser.parseAgainst(input: String, commands: List<String>): String? {
    val parser = MessyIntentParser(commands.map { it.removePrefix("/") }.toSet())
    return parser.parse(input)?.let { "/$it" }
}

sealed class NlResolution {
    /** Certain. Run it. */
    data class ExactCommand(val command: String, val arguments: String = "") : NlResolution()

    /** One plausible reading. Offer it; do not run it. */
    data class Suggested(val command: String, val typed: String) : NlResolution()

    /** Several plausible readings. The operator picks. */
    data class Ambiguous(val candidates: List<String>, val typed: String) : NlResolution()

    /** Nothing local matched. A provider call is now the right answer. */
    data class Prose(val text: String) : NlResolution()

    object Empty : NlResolution()

    /** True when no provider call is needed to act on this. */
    val resolvedLocally: Boolean get() = this !is Prose

    fun render(): String = when (this) {
        is ExactCommand -> "resolved locally to $command"
        is Suggested -> "'$typed' looks like $command — run it?"
        is Ambiguous -> "'$typed' could be ${candidates.joinToString(", ")}"
        is Prose -> "no local match; this needs a provider"
        Empty -> "nothing to resolve"
    }
}
