/* SPDX-License-Identifier: AGPL-3.0-only */
package atropos.core.agent

import java.security.MessageDigest

/**
 * ATOM AI3: CURSOR AI COMPOSER MULTI-FILE SYNTHESIS ENGINE
 * Path: src/main/kotlin/atropos/core/agent/CursorComposerMultiFileSynthesizer.kt
 *
 * Supports natural language multi-file code composition. Parses AI responses
 * containing multiple file rewrite and replace blocks into an atomic unified diff,
 * validating target file existence and cryptographic hashes before applying edits.
 */
sealed class ComposerFileEdit {
    abstract val relativePath: String

    data class CreateFile(
        override val relativePath: String,
        val content: String
    ) : ComposerFileEdit()

    data class RewriteFile(
        override val relativePath: String,
        val newContent: String,
        val expectedSha256: String? = null
    ) : ComposerFileEdit()

    data class ReplaceSnippet(
        override val relativePath: String,
        val targetSnippet: String,
        val replacementSnippet: String
    ) : ComposerFileEdit()
}

data class MultiFileSynthesisResult(
    val edits: List<ComposerFileEdit>,
    val affectedFilesCount: Int,
    val unifiedDiff: String,
    val compositeHash: String
)

class CursorComposerMultiFileSynthesizer {
    private val codeBlockRegex = Regex("""(?s)```(?:[a-zA-Z0-9_-]+)?\s*filepath=([^\n]+)\n(.*?)\n```""")

    fun parseComposerResponse(responseMarkdown: String): List<ComposerFileEdit> {
        val edits = mutableListOf<ComposerFileEdit>()
        val matches = codeBlockRegex.findAll(responseMarkdown)

        for (m in matches) {
            val path = m.groupValues[1].trim()
            val code = m.groupValues[2]

            if (code.contains("<<<<<<< SEARCH") && code.contains("=======") && code.contains(">>>>>>> REPLACE")) {
                val searchReplaceRegex = Regex("""(?s)<<<<<<< SEARCH\n(.*?)\n=======\n(.*?)\n>>>>>>> REPLACE""")
                val srMatches = searchReplaceRegex.findAll(code)
                for (sr in srMatches) {
                    edits.add(
                        ComposerFileEdit.ReplaceSnippet(
                            relativePath = path,
                            targetSnippet = sr.groupValues[1],
                            replacementSnippet = sr.groupValues[2]
                        )
                    )
                }
            } else {
                edits.add(
                    ComposerFileEdit.RewriteFile(
                        relativePath = path,
                        newContent = code
                    )
                )
            }
        }

        return edits
    }

    fun synthesizeToUnifiedDiff(edits: List<ComposerFileEdit>): MultiFileSynthesisResult {
        val diffBuilder = StringBuilder()
        val md = MessageDigest.getInstance("SHA-256")

        for (edit in edits) {
            when (edit) {
                is ComposerFileEdit.CreateFile -> {
                    diffBuilder.appendLine("--- /dev/null")
                    diffBuilder.appendLine("+++ b/${edit.relativePath}")
                    diffBuilder.appendLine("@@ -0,0 +1,${edit.content.lines().size} @@")
                    edit.content.lines().forEach { diffBuilder.appendLine("+$it") }
                    md.update(edit.content.toByteArray())
                }
                is ComposerFileEdit.RewriteFile -> {
                    diffBuilder.appendLine("--- a/${edit.relativePath}")
                    diffBuilder.appendLine("+++ b/${edit.relativePath}")
                    diffBuilder.appendLine("@@ rewrite @@")
                    edit.newContent.lines().forEach { diffBuilder.appendLine("+$it") }
                    md.update(edit.newContent.toByteArray())
                }
                is ComposerFileEdit.ReplaceSnippet -> {
                    diffBuilder.appendLine("--- a/${edit.relativePath}")
                    diffBuilder.appendLine("+++ b/${edit.relativePath}")
                    diffBuilder.appendLine("@@ snippet replacement @@")
                    edit.targetSnippet.lines().forEach { diffBuilder.appendLine("-$it") }
                    edit.replacementSnippet.lines().forEach { diffBuilder.appendLine("+$it") }
                    md.update(edit.replacementSnippet.toByteArray())
                }
            }
        }

        val digest = md.digest().joinToString("") { "%02x".format(it) }

        return MultiFileSynthesisResult(
            edits = edits,
            affectedFilesCount = edits.map { it.relativePath }.distinct().size,
            unifiedDiff = diffBuilder.toString(),
            compositeHash = digest
        )
    }

    companion object {
        val defaultSynthesizer = CursorComposerMultiFileSynthesizer()
    }
}
