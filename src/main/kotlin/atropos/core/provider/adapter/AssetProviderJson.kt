package atropos.core.provider.adapter

import atropos.core.provider.NormalizedProviderFailureType
import atropos.core.provider.ProviderCallResult
import atropos.core.provider.ProviderErrorNormalizer
import atropos.core.provider.ProviderFailure
import atropos.core.provider.ProviderUsage

object AssetProviderJson {
    fun buildPromptRequest(prompt: String): String = buildString {
        append("{")
        append("\"input\":{\"prompt\":\"").append(escape(prompt.ifBlank { "ATROPOS local asset" })).append("\"}")
        append("}")
    }

    fun parseAssetResult(providerId: String, json: String): ProviderCallResult {
        if (json.isBlank()) {
            return ProviderCallResult.Failure(
                ProviderFailure(providerId, NormalizedProviderFailureType.EMPTY_RESPONSE, "$providerId empty response")
            )
        }
        if (json.contains("\"error\"")) {
            return ProviderCallResult.Failure(ProviderErrorNormalizer().normalize(providerId, stringField(json, "message") ?: json))
        }
        val output = stringField(json, "url")
            ?: stringField(json, "output")
            ?: stringField(json, "artifact")
        if (output.isNullOrBlank()) {
            return ProviderCallResult.Failure(
                ProviderFailure(providerId, NormalizedProviderFailureType.MALFORMED_RESPONSE, "$providerId missing asset output")
            )
        }
        return ProviderCallResult.Success(
            providerId = providerId,
            content = output,
            usage = ProviderUsage(),
            model = stringField(json, "model"),
            requestId = stringField(json, "id")
        )
    }

    private fun stringField(json: String, name: String): String? {
        val regex = Regex(""""$name"\s*:\s*"((?:\\.|[^"\\])*)"""")
        return regex.find(json)?.groupValues?.get(1)?.let(::unescape)
    }

    private fun escape(value: String): String =
        value.replace("\\", "\\\\").replace("\"", "\\\"").replace("\n", "\\n").replace("\r", "\\r").replace("\t", "\\t")

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
