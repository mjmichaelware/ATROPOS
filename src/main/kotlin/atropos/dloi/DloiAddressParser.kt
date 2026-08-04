package atropos.dloi

enum class DloiSelectorKind {
    LINE,
    PAGE,
    PARAGRAPH
}

data class DloiSelector(
    val kind: DloiSelectorKind,
    val start: Int,
    val end: Int
)

data class ParsedDloiAddress(
    val documentId: String,
    val sectionId: String?,
    val selector: DloiSelector?
)

class DloiAddressParser {
    fun parse(address: String): ParsedDloiAddress {
        val trimmed = address.trim()
        val documentAndRest = trimmed.split("@", limit = 2)
        val docAndSection = documentAndRest[0].split("#", limit = 2)
        val documentId = dloiSlug(docAndSection[0])
        require(documentId.isNotBlank()) { "missing DLOI document id" }
        val sectionId = docAndSection.getOrNull(1)?.trim()?.takeIf { it.isNotBlank() }?.let(::dloiSlug)
        val selector = parseSelector(documentAndRest.getOrNull(1)?.trim())
        return ParsedDloiAddress(documentId, sectionId, selector)
    }

    private fun parseSelector(selectorSpec: String?): DloiSelector? {
        if (selectorSpec.isNullOrBlank()) return null
        val spec = selectorSpec.trim()
        val kind = when {
            spec.startsWith("L", ignoreCase = true) -> DloiSelectorKind.LINE
            spec.startsWith("PG", ignoreCase = true) -> DloiSelectorKind.PAGE
            spec.startsWith("PARA", ignoreCase = true) -> DloiSelectorKind.PARAGRAPH
            else -> error("invalid DLOI selector: $selectorSpec")
        }
        val numeric = when (kind) {
            DloiSelectorKind.LINE -> spec.substring(1)
            DloiSelectorKind.PAGE -> spec.substring(2)
            DloiSelectorKind.PARAGRAPH -> spec.substring(4)
        }
        val parts = numeric.split("-", limit = 2)
        val start = parts[0].toIntOrNull() ?: error("invalid DLOI selector: $selectorSpec")
        val end = parts.getOrNull(1)
            ?.removePrefix("L")
            ?.removePrefix("PG")
            ?.removePrefix("PARA")
            ?.toIntOrNull()
            ?: start
        require(start <= end) { "invalid DLOI selector: $selectorSpec" }
        return DloiSelector(kind, start, end)
    }
}
