/* SPDX-License-Identifier: AGPL-3.0-only */
package atropos.cli.input

import java.nio.file.Files
import java.nio.file.Path

data class Completion(
    val insertion: String = "",
    val preview: String = ""
)

class CommandCompleter(
    workspace: Path = Path.of(".")
) {
    private val root = workspace.toAbsolutePath().normalize()

    private val commands = listOf(
        "/exit",
        "/help",
        "/paid",
        "/paid status",
        "/paid lock",
        "/paid unlock",
        "/providers",
        "/providers descriptors",
        "/providers validate",
        "/quit",
        "/route",
        "/status",
        "/status adapters",
        "/status assets",
        "/status ci",
        "/status endpoints",
        "/status factory",
        "/status failures",
        "/status memory",
        "/status paid",
        "/status quota",
        "/status queue",
        "/status route",
        "/status security",
        "/status tests",
        "/status ops",
        "/security",
        "/security redact",
        "/keys",
        "/keys status",
        "/tests",
        "/tests matrix",
        "/ops",
        "/ops export",
        "/ops status",
        "/assets",
        "/assets ansi",
        "/assets status",
        "/assets svg",
        "/assets text",
        "/ci",
        "/ci local compile",
        "/ci run next",
        "/factory",
        "/factory plan",
        "/factory run",
        "/factory status",
        "/memory",
        "/memory remember",
        "/memory search",
        "/swarm",
        "/use",
        "/verify"
    )

    private val providers = listOf("anthropic", "groq", "ollama", "openai", "xai")

    fun complete(buffer: String, cursor: Int): Completion {
        val position = cursor.coerceIn(0, buffer.length)
        val prefix = buffer.substring(0, position)

        if (prefix.startsWith("/") && prefix.none(Char::isWhitespace)) return select(prefix, commands)

        if (prefix.startsWith("/verify ")) {
            val scopePrefix = prefix.removePrefix("/verify ")
            if (scopePrefix.none(Char::isWhitespace)) return select(scopePrefix, listOf("narrow", "wide"))
        }

        if (prefix.startsWith("/use ")) {
            val providerPrefix = prefix.removePrefix("/use ")
            if (providerPrefix.none(Char::isWhitespace)) return select(providerPrefix, providers)
        }

        return completePath(prefix)
    }

    private fun completePath(prefix: String): Completion {
        val marker = prefix.lastIndexOf('@')
        if (marker < 0) return Completion()

        val fragment = prefix.substring(marker + 1)
        if (fragment.any(Char::isWhitespace)) return Completion()

        val slash = fragment.lastIndexOf('/')
        val parent = if (slash >= 0) fragment.substring(0, slash + 1) else ""
        val namePrefix = if (slash >= 0) fragment.substring(slash + 1) else fragment
        val directory = root.resolve(parent.ifEmpty { "." }).normalize()

        if (!directory.startsWith(root) || !Files.isDirectory(directory)) return Completion()

        val stream = try {
            Files.list(directory)
        } catch (_: Exception) {
            return Completion()
        }

        val candidates = try {
            stream.map { path ->
                val name = path.fileName.toString()
                if (Files.isDirectory(path)) "$name/" else name
            }.filter { it.startsWith(namePrefix) }
                .sorted()
                .toList()
        } finally {
            stream.close()
        }

        return select(namePrefix, candidates)
    }

    private fun select(prefix: String, values: List<String>): Completion {
        val matches = values.filter { it.startsWith(prefix) }
        if (matches.isEmpty()) return Completion()

        val common = matches.drop(1).fold(matches.first()) { left, right -> commonPrefix(left, right) }
        if (common.length <= prefix.length) return Completion()

        val insertion = common.substring(prefix.length)
        return Completion(insertion, insertion)
    }

    private fun commonPrefix(left: String, right: String): String {
        val limit = minOf(left.length, right.length)
        var index = 0
        while (index < limit && left[index] == right[index]) index++
        return left.substring(0, index)
    }
}
