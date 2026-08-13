package atropos.core.provider.adapter

import atropos.core.json.JsonStringField

import atropos.core.provider.NormalizedProviderFailureType
import atropos.core.provider.ProviderCallResult
import atropos.core.provider.ProviderErrorNormalizer
import atropos.core.provider.ProviderFailure
import atropos.core.provider.ProviderUsage

object NonOpenAiJson {
    fun buildGeminiRequest(prompt: String, context: String): String = buildString {
        append("{")
        append("\"contents\":[{\"parts\":[")
        append("{\"text\":\"").append(escape(context.ifBlank { "ATROPOS local-first provider adapter" })).append("\"},")
        append("{\"text\":\"").append(escape(prompt)).append("\"}")
        append("]}],")
        append("\"generationConfig\":{\"temperature\":0.2}")
        append("}")
    }

    fun buildCloudflareAiRequest(prompt: String, context: String): String = buildString {
        append("{")
        append("\"messages\":[")
        append("{\"role\":\"system\",\"content\":\"").append(escape(context.ifBlank { "ATROPOS local-first provider adapter" })).append("\"},")
        append("{\"role\":\"user\",\"content\":\"").append(escape(prompt)).append("\"}")
        append("]}")
    }

    fun parseTextResult(providerId: String, json: String): ProviderCallResult {
        if (json.isBlank()) {
            return ProviderCallResult.Failure(
                ProviderFailure(providerId, NormalizedProviderFailureType.EMPTY_RESPONSE, "$providerId empty response")
            )
        }
        if (json.contains("\"error\"")) {
            return ProviderCallResult.Failure(ProviderErrorNormalizer().normalize(providerId, stringField(json, "message") ?: json))
        }
        val text = stringField(json, "text")
            ?: stringField(json, "response")
            ?: stringField(json, "content")
        if (text.isNullOrBlank()) {
            return ProviderCallResult.Failure(
                ProviderFailure(providerId, NormalizedProviderFailureType.MALFORMED_RESPONSE, "$providerId missing text result")
            )
        }
        return ProviderCallResult.Success(
            providerId = providerId,
            content = text,
            usage = ProviderUsage(),
            model = stringField(json, "model"),
            requestId = stringField(json, "id")
        )
    }

    private fun stringField(json: String, name: String): String? {
        // Scanned, not matched: the regex this replaces recursed once per
        // character and overflowed the stack on long provider responses.
        return JsonStringField.value(json, name)?.let(::unescape)
    }

    fun escape(value: String): String = buildString {
        value.forEach { ch ->
            when (ch) {
                '\\' -> append("\\\\")
                '"' -> append("\\\"")
                '\n' -> append("\\n")
                '\r' -> append("\\r")
                '\t' -> append("\\t")
                else -> append(ch)
            }
        }
    }

    private fun unescape(value: String): String {
        val out = StringBuilder()
        var i = 0
        while (i < value.length) {
            val ch = value[i]
            if (ch == '\\' && i + 1 < value.length) {
                when (value[i + 1]) {
                    '\\' -> out.append('\\')
                    '"' -> out.append('"')
                    'n' -> out.append('\n')
                    'r' -> out.append('\r')
                    't' -> out.append('\t')
                    else -> out.append(value[i + 1])
                }
                i += 2
            } else {
                out.append(ch)
                i += 1
            }
        }
        return out.toString()
    }
}
