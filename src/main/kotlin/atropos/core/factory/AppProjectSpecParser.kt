package atropos.core.factory

class AppProjectSpecParser {
    private val intentParser = IntentParser()

    fun isAppRequest(prompt: String): Boolean = intentParser.isAppRequest(prompt)

    fun parse(prompt: String): AppProjectSpec {
        val clean = prompt.trim().ifBlank { "build a local app" }
        return AppProjectSpec(clean, intentParser.parse(clean), testRequired = true)
    }
}
