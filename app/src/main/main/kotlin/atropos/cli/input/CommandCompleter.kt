/* SPDX-License-Identifier: AGPL-3.0-only */
package atropos.cli.input

import java.nio.file.Files
import java.nio.file.Path

data class Completion(
    val insertion: String = "",
    val preview: String = "",
    val selectedIndex: Int = 0,
    val options: List<String> = emptyList()
)

class CommandCompleter(
    workspace: Path = Path.of(".")
) {
    private val root =
        workspace.toAbsolutePath().normalize()

    private val providers: List<String> =
        CommandRegistry.providers

    fun complete(
        buffer: String,
        cursor: Int,
        selectedIndex: Int = 0
    ): Completion {
        val position = cursor.coerceIn(
            0,
            buffer.length
        )
        val prefix = buffer.substring(
            0,
            position
        )

        val commandPrefix = prefix.takeIf {
            it.none(Char::isWhitespace) &&
                (it.startsWith("/") || CommandRegistry.search(it).isNotEmpty())
        }
        if (commandPrefix != null) {
            return completeCommandPrefix(
                commandPrefix,
                selectedIndex
            )
        }

        if (prefix.startsWith("/verify ")) {
            val scopePrefix =
                prefix.removePrefix("/verify ")

            if (scopePrefix.none(Char::isWhitespace)) {
                return select(
                    scopePrefix,
                    listOf("narrow", "wide"),
                    selectedIndex
                )
            }
        }

        if (prefix.startsWith("/use ")) {
            val providerPrefix =
                prefix.removePrefix("/use ")

            if (providerPrefix.none(Char::isWhitespace)) {
                return select(
                    providerPrefix,
                    providers,
                    selectedIndex,
                    fuzzy = true
                )
            }
        }

        return completePath(
            prefix,
            selectedIndex
        )
    }

    fun resolveSubmission(
        buffer: String,
        cursor: Int,
        selectedIndex: Int = 0
    ): String? {
        val position = cursor.coerceIn(0, buffer.length)
        val prefix = buffer.substring(0, position)
        val suffix = buffer.substring(position)
        val trimmed = prefix.trim()
        if (trimmed.isBlank()) return null

        // Enter must never reinterpret ordinary natural language as a slash
        // command. Multi-word aliases are limited to the explicit self-host
        // vocabulary; app prompts such as "build a notes CLI" remain NL.
        val commandLikeNaturalLanguage = trimmed.startsWith("self-host", ignoreCase = true)
        if (!trimmed.startsWith("/") &&
            !commandLikeNaturalLanguage &&
            trimmed.contains(Regex("\\s"))
        ) return null

        val parts = trimmed.split(" ", limit = 2)
        val head = parts.first()
        val tail = parts.getOrNull(1).orEmpty()
        val command = resolveCommand(head, selectedIndex) ?: return null
        val resolved = if (tail.isBlank()) command else "$command $tail"
        return if (suffix.isBlank()) resolved else resolved + suffix
    }

    private fun completePath(
        prefix: String,
        selectedIndex: Int
    ): Completion {
        val marker = prefix.lastIndexOf('@')
        if (marker < 0) return Completion()

        val fragment = prefix.substring(marker + 1)
        if (fragment.any(Char::isWhitespace)) {
            return Completion()
        }

        val slash = fragment.lastIndexOf('/')
        val parent =
            if (slash >= 0) fragment.substring(0, slash + 1)
            else ""

        val namePrefix =
            if (slash >= 0) fragment.substring(slash + 1)
            else fragment

        val directory =
            root.resolve(parent.ifEmpty { "." }).normalize()

        if (
            !directory.startsWith(root) ||
            !Files.isDirectory(directory)
        ) {
            return Completion()
        }

        val stream = try {
            Files.list(directory)
        } catch (_: Exception) {
            return Completion()
        }

        val candidates = try {
            stream.map { path ->
                val name = path.fileName.toString()
                if (Files.isDirectory(path)) "$name/" else name
            }.filter {
                it.startsWith(namePrefix)
            }.sorted().toList()
        } finally {
            stream.close()
        }

        return select(
            namePrefix,
            candidates,
            selectedIndex
        )
    }

    private fun completeCommandPrefix(
        prefix: String,
        selectedIndex: Int
    ): Completion {
        val candidates = commandCompletionOptions(prefix)
        if (candidates.isEmpty()) return Completion()

        val selected =
            selectedIndex.coerceIn(0, candidates.lastIndex)
        val target = candidates[selected]
        val insertion =
            if (target.startsWith(prefix, ignoreCase = true)) {
                target.substring(prefix.length)
            } else {
                ""
            }

        return Completion(
            insertion = insertion,
            preview = if (insertion.isEmpty()) target else insertion,
            selectedIndex = selected,
            options = candidates
        )
    }

    private fun select(
        prefix: String,
        values: List<String>,
        selectedIndex: Int = 0,
        fuzzy: Boolean = false
    ): Completion {
        val matches = values.filter {
            it.startsWith(prefix) ||
                (
                    fuzzy &&
                        it.contains(
                            prefix,
                            ignoreCase = true
                        )
                    )
        }

        if (matches.isEmpty()) return Completion()

        val selected =
            selectedIndex.coerceIn(0, matches.lastIndex)

        val common = matches.drop(1).fold(matches.first()) {
                left,
                right ->

            commonPrefix(left, right)
        }

        val target =
            if (
                common.length > prefix.length &&
                matches.all { it.startsWith(prefix) }
            ) {
                common
            } else {
                matches[selected]
            }

        val insertion =
            if (target.startsWith(prefix)) {
                target.substring(prefix.length)
            } else {
                ""
            }

        return Completion(
            insertion = insertion,
            preview = if (insertion.isEmpty()) target else insertion,
            selectedIndex = selected,
            options = matches
        )
    }

    private fun commandCompletionOptions(prefix: String): List<String> {
        val searchMatches = CommandRegistry.search(prefix).map { it.command }
        if (searchMatches.isEmpty()) return emptyList()

        val resolved = resolveCommand(prefix, 0)
        return if (resolved == null) {
            searchMatches
        } else {
            listOf(resolved) + searchMatches.filterNot { it == resolved }
        }
    }

    private fun resolveCommand(
        input: String,
        selectedIndex: Int
    ): String? {
        val normalized = input.trim()
        if (normalized.isBlank()) return null

        return when (normalized.lowercase()) {
            "?" -> "/help"
            "help", "usage", "/?", "/help", "/usage" -> "/help"
            else -> {
                val matches = CommandRegistry.search(normalized)
                if (matches.isEmpty()) {
                    null
                } else {
                    matches[selectedIndex.coerceIn(0, matches.lastIndex)].command
                }
            }
        }
    }

    private fun commonPrefix(
        left: String,
        right: String
    ): String {
        val limit = minOf(left.length, right.length)
        var index = 0

        while (
            index < limit &&
            left[index] == right[index]
        ) {
            index++
        }

        return left.substring(0, index)
    }
}
