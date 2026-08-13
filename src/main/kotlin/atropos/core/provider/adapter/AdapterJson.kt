package atropos.core.provider.adapter

import atropos.core.json.JsonStringField

import atropos.core.provider.NormalizedProviderFailureType
import atropos.core.provider.ProviderCallResult
import atropos.core.provider.ProviderErrorNormalizer
import atropos.core.provider.ProviderFailure
import atropos.core.provider.ProviderUsage

object AdapterJson {
    fun buildChatRequest(model: String, prompt: String, context: String): String = buildString {
        append("{")
        append("\"model\":\"").append(escape(model)).append("\",")
        append("\"messages\":[")
        append("{\"role\":\"system\",\"content\":\"").append(escape(context.ifBlank { "ATROPOS local-first provider adapter" })).append("\"},")
        append("{\"role\":\"user\",\"content\":\"").append(escape(prompt)).append("\"}")
        append("],")
        append("\"temperature\":0.2")
        append("}")
    }

    fun parseOpenAiCompatibleSuccess(providerId: String, json: String): ProviderCallResult {
        if (json.isBlank()) {
            return ProviderCallResult.Failure(
                ProviderFailure(providerId, NormalizedProviderFailureType.EMPTY_RESPONSE, "$providerId empty response")
            )
        }

        if (json.contains("\"error\"")) {
            return ProviderCallResult.Failure(parseOpenAiCompatibleError(providerId, json))
        }

        val content = stringField(json, "content")
        if (content.isNullOrBlank()) {
            return ProviderCallResult.Failure(
                ProviderFailure(providerId, NormalizedProviderFailureType.MALFORMED_RESPONSE, "$providerId missing assistant content")
            )
        }

        return ProviderCallResult.Success(
            providerId = providerId,
            content = content,
            usage = ProviderUsage(
                inputTokens = intField(json, "prompt_tokens") ?: 0,
                outputTokens = intField(json, "completion_tokens") ?: 0
            ),
            model = stringField(json, "model"),
            requestId = stringField(json, "id")
        )
    }

    fun parseOpenAiCompatibleError(providerId: String, json: String): ProviderFailure {
        val message = stringField(json, "message") ?: json
        return ProviderErrorNormalizer().normalize(providerId, message)
    }

    private fun stringField(json: String, name: String): String? {
        // Scanned, not matched: the regex this replaces recursed once per
        // character and overflowed the stack on long provider responses.
        return JsonStringField.value(json, name)?.let(::unescape)
    }

    private fun intField(json: String, name: String): Int? {
        val regex = Regex(""""$name"\s*:\s*([0-9]+)""")
        return regex.find(json)?.groupValues?.get(1)?.toIntOrNull()
    }

    private fun escape(value: String): String = buildString {
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
