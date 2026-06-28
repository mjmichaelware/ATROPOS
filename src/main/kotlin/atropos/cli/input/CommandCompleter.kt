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

    private val commands: List<String> =
        CommandRegistry.commands()

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

        if (
            prefix.startsWith("/") &&
            prefix.none(Char::isWhitespace)
        ) {
            return select(
                prefix,
                commands,
                selectedIndex,
                fuzzy = true
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
            target.substring(
                prefix.length.coerceAtMost(target.length)
            )

        return Completion(
            insertion = insertion,
            preview = insertion,
            selectedIndex = selected,
            options = matches
        )
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
