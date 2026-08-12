package atropos.cli.input

/** Thin suggestion use-case over the canonical command registry. */
class SuggestionEngine(
    private val defaultLimit: Int = 24
) {
    init {
        require(defaultLimit > 0) { "suggestion limit must be positive" }
    }

    fun suggest(query: String, limit: Int = defaultLimit): List<CommandEntry> {
        require(limit >= 0) { "suggestion limit cannot be negative" }
        return CommandRegistry.search(query).take(limit)
    }

    fun hasSuggestions(query: String): Boolean = suggest(query, 1).isNotEmpty()
}
