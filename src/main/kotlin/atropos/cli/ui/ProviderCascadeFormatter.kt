/* SPDX-License-Identifier: AGPL-3.0-only */
package atropos.cli.ui

object ProviderCascadeFormatter {
    fun cleanResponse(response: String, resolvedProvider: String): String {
        val failures = mutableListOf<String>()
        val body = mutableListOf<String>()

        response.lines().forEach { line ->
            val clean = TerminalText.sanitize(line).trim()
            if (isCascadeFailure(clean)) {
                failures += clean.removePrefix("-").trim()
            } else {
                body += line
            }
        }

        if (failures.isEmpty()) return response

        val summary = summarize(failures, resolvedProvider)
        val cleanBody = body.joinToString("\n").trim()
        return if (cleanBody.isBlank()) summary else "$summary\n\n$cleanBody"
    }

    fun cleanError(message: String, provider: String): String {
        val raw = TerminalText.sanitize(message).trim()
        val failures = raw
            .split('|')
            .map { it.trim().removePrefix("-").trim() }
            .filter(::isCascadeFailure)

        if (failures.size > 1) return summarize(failures, provider)

        val lower = raw.lowercase()
        val label = provider.takeIf { it.isNotBlank() }?.lowercase() ?: "provider"

        return when {
            "invalid api key" in lower || "http 401" in lower ->
                "$label auth failed\ninvalid API key\n/providers to inspect configuration"

            "model missing" in lower ->
                "$label model missing\n/providers to inspect configuration"

            "unavailable" in lower || "connection refused" in lower ->
                "$label unavailable\n/use auto or start local provider"

            "{\"error\"" in lower || "\"error\"" in lower -> {
                val extracted = Regex(""""message"\s*:\s*"([^"]+)"""")
                    .find(raw)
                    ?.groupValues
                    ?.getOrNull(1)
                    ?.replace("\\n", " ")
                    ?.takeIf(String::isNotBlank)
                    ?: "provider returned an error"
                "$label request failed\n$extracted\n/providers to inspect configuration"
            }

            raw.length > 160 ->
                "$label request failed\n${TerminalText.ellipsize(raw, 120)}"

            else -> raw
        }
    }

    private fun summarize(failures: List<String>, resolvedProvider: String): String {
        val normalized = failures.map(::normalize).distinct()

        val cloudAuth = normalized.count { it.contains("auth failed", ignoreCase = true) }
        val missing = normalized.count { it.contains("model missing", ignoreCase = true) }
        val unavailable = normalized.count { it.contains("unavailable", ignoreCase = true) }

        val parts = mutableListOf<String>()
        if (cloudAuth > 0) parts += "$cloudAuth cloud auth"
        if (missing > 0) parts += "$missing model missing"
        if (unavailable > 0) parts += "$unavailable unavailable"

        val summary = parts.joinToString(" · ").ifBlank {
            "${normalized.size} fallback failures"
        }

        val route = resolvedProvider
            .takeIf { it.isNotBlank() && it != "unknown" }
            ?.lowercase()

        return listOfNotNull(
            "provider cascade: $summary",
            route?.let { "resolved route: $it" },
            "/providers to inspect configuration"
        ).joinToString("\n")
    }

    private fun isCascadeFailure(value: String): Boolean {
        val lower = value.lowercase().removePrefix("-").trim()
        val startsWithProvider = listOf(
            "groq ",
            "openai ",
            "anthropic ",
            "xai ",
            "ollama "
        ).any { lower.startsWith(it) }

        val hasFailure =
            "auth failed" in lower ||
                "invalid api key" in lower ||
                "model missing" in lower ||
                "unavailable" in lower

        return startsWithProvider && hasFailure
    }

    private fun normalize(value: String): String {
        val clean = value.removePrefix("-").trim()
        val provider = clean.substringBefore(' ').lowercase()
        val lower = clean.lowercase()

        return when {
            "invalid api key" in lower || "auth failed" in lower ->
                "$provider auth failed"

            "model missing" in lower ->
                "$provider model missing"

            "unavailable" in lower ->
                "$provider unavailable"

            else -> clean
        }
    }
}
