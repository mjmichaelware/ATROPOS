// SPDX-License-Identifier: AGPL-3.0-only
package atropos.core.intent

data class Mention(
    val token: String,
    val startIndex: Int,
    val endIndex: Int,
    val resolvedPath: String?
)

class MentionExtractor(private val validRoots: Set<String>) {
    
    fun extractMentions(input: String): List<Mention> {
        val mentions = mutableListOf<Mention>()
        val regex = Regex("@([A-Za-z0-9_-]+)")
        val matches = regex.findAll(input)
        
        for (match in matches) {
            val token = match.value
            val name = match.groupValues[1]
            val resolvedPath = if (validRoots.contains(name)) name else null
            
            mentions.add(
                Mention(
                    token = token,
                    startIndex = match.range.first,
                    endIndex = match.range.last,
                    resolvedPath = resolvedPath
                )
            )
        }
        
        return mentions
    }
}
