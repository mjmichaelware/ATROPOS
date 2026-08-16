/* SPDX-License-Identifier: AGPL-3.0-only */
package atropos.core.integration

import atropos.core.ast.AttentionRole
import atropos.core.ast.DecomposedAttentionNode
import atropos.core.parser.KotlinLexicalMasker
import java.io.File

data class ValidationResult(
    val syntaxValid: Boolean,
    val missingImports: List<String>,
    val attentionContexts: List<String> = emptyList(),
    val executionManifest: String = "",
    val fanOutSizes: List<Int> = emptyList()
)

object OnDeviceAdversarialValidator {
    fun validate(code: String): ValidationResult {
        val syntaxInput = if (code.looksLikeUnifiedDiff()) {
            code.lineSequence()
                .filter { it.startsWith("+") && !it.startsWith("+++") }
                .map { it.removePrefix("+") }
                .joinToString("\n")
        } else {
            code
        }
        val masked = KotlinLexicalMasker.maskNonCode(syntaxInput)
        val hasSyntaxError = hasKnownMalformedConstruct(masked) ||
            (!code.looksLikeUnifiedDiff() && hasUnbalancedDelimiters(masked))
        val imports = mutableListOf<String>()
        if (code.contains("import ") && !code.contains("import java.") && !code.contains("import kotlin.")) {
            imports.add("unknown.dependency")
        }
        val attentionContexts = listOf(
            DecomposedAttentionNode("viewer", AttentionRole.VIEWER, syntaxInput).processContext(),
            DecomposedAttentionNode("editor", AttentionRole.EDITOR, code).processContext()
        )
        val executionManifest = ManifestOrchestrator().generateExecutionManifest(
            nodes = listOf("syntax", "imports"),
            dependencies = mapOf("imports" to listOf("syntax"))
        )
        val fanOutSizes = AsyncFanOutController().fanOutAndCombine(
            listOf(syntaxInput, code),
            String::length
        )
        return ValidationResult(!hasSyntaxError, imports, attentionContexts, executionManifest, fanOutSizes)
    }

    private fun hasKnownMalformedConstruct(masked: String): Boolean {
        if (Regex("\\bclass\\s*}").containsMatchIn(masked)) return true
        return Regex("\\bfun\\s*(?:[A-Za-z_]\\w*\\s*)?\\([^)]*$", RegexOption.MULTILINE)
            .containsMatchIn(masked)
    }

    private fun hasUnbalancedDelimiters(masked: String): Boolean {
        val stack = ArrayDeque<Char>()
        masked.forEach { character ->
            when (character) {
                '(', '[', '{' -> stack.addLast(character)
                ')' -> if (stack.removeLastOrNull() != '(') return true
                ']' -> if (stack.removeLastOrNull() != '[') return true
                '}' -> if (stack.removeLastOrNull() != '{') return true
            }
        }
        return stack.isNotEmpty()
    }

    private fun String.looksLikeUnifiedDiff(): Boolean =
        lineSequence().any { it.startsWith("@@") } &&
            lineSequence().any { it.startsWith("+") || it.startsWith("-") }
}

class AsyncFanOutController {
    fun <T, R> fanOutAndCombine(items: List<T>, workerFn: (T) -> R): List<R> {
        // Async fan-out/fan-in decoupling to bypass context dilution
        return items.parallelStream().map(workerFn).toList()
    }
}

class ManifestOrchestrator {
    fun generateExecutionManifest(nodes: List<String>, dependencies: Map<String, List<String>>): String {
        // Orchestrator emitting a JSON manifest of execution nodes ordered topologically
        val manifest = nodes.sortedWith(Comparator { a, b ->
            val depsA = dependencies[a] ?: emptyList()
            if (depsA.contains(b)) 1 else -1
        })
        return "{\"nodes\": [${manifest.joinToString(",") { "\"$it\"" }}]}"
    }
}
