package atropos.cli.input

/** Selects a ranked canonical command when Enter is pressed on a partial token. */
class PartialCommandEnterToSelect(
    private val suggestions: SuggestionEngine = SuggestionEngine()
) {
    fun resolve(input: String, selectedIndex: Int = 0): String? {
        val normalized = input.trim()
        if (normalized.isBlank()) return null

        return when (normalized.lowercase()) {
            "?", "help", "usage", "/?", "/help", "/usage" -> "/help"
            else -> suggestions.suggest(normalized).getOrNull(selectedIndex.coerceAtLeast(0))?.command
        }
    }
}
