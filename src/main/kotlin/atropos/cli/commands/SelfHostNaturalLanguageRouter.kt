package atropos.cli.commands

/**
 * Turns an operator's plain line into a self-host command, or leaves it alone.
 *
 * Recognition is delegated to [SelfHostChangeRequestClassifier]; this owner only
 * maps a classified utterance onto the command surface. Keeping the two apart is
 * what let the phrase list become a general rule: routing has to know about
 * `/agent self-host run` and argument passthrough, and recognition has to know
 * about English, and neither needs the other's detail.
 */
class SelfHostNaturalLanguageRouter(
    private val classifier: SelfHostChangeRequestClassifier = SelfHostChangeRequestClassifier()
) {
    fun route(tokens: List<String>): List<String>? {
        // An explicit `/agent self-host ...` is already routed; re-routing it
        // would wrap the operator's own command in a second one.
        if (tokens.size >= 2 && tokens[0].equals("/agent", ignoreCase = true) && tokens[1].equals("self-host", ignoreCase = true)) {
            return null
        }
        val text = tokens.joinToString(" ").trim()
        if (text.isBlank()) return null

        return when (classifier.classify(text)) {
            SelfHostUtterance.CONTINUATION -> listOf("/agent", "self-host", "recover")
            // The operator's words are passed through verbatim: they are the
            // change request the self-host run has to satisfy, not a trigger
            // phrase to be discarded once matched.
            SelfHostUtterance.CHANGE_REQUEST -> listOf("/agent", "self-host", "run") + tokens
            SelfHostUtterance.QUESTION, SelfHostUtterance.UNRELATED -> null
        }
    }
}
