package atropos.cli.commands

import atropos.core.factory.AppProjectSpecParser

class SelfHostNaturalLanguageRouter(
    private val projectSpecParser: AppProjectSpecParser = AppProjectSpecParser(),
    private val classifier: SelfHostChangeRequestClassifier = SelfHostChangeRequestClassifier()
) {
    fun route(tokens: List<String>): List<String>? {
        if (tokens.size >= 2 && tokens[0].equals("/agent", ignoreCase = true) && tokens[1].equals("self-host", ignoreCase = true)) {
            return null
        }
        val text = tokens.joinToString(" ").trim()
        if (text.isBlank()) return null
        return when (classifier.classify(text)) {
            SelfHostUtterance.CHANGE_REQUEST -> listOf("/agent", "self-host", "run") + tokens
            SelfHostUtterance.CONTINUATION -> listOf("/agent", "self-host", "recover")
            SelfHostUtterance.QUESTION -> null
            SelfHostUtterance.UNRELATED -> if (projectSpecParser.isAppRequest(text)) {
                listOf("/factory", "run") + tokens
            } else null
        }
    }

}
