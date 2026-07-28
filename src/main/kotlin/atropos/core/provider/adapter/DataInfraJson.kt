package atropos.core.provider.adapter

import atropos.core.provider.NormalizedProviderFailureType
import atropos.core.provider.ProviderCallResult
import atropos.core.provider.ProviderErrorNormalizer
import atropos.core.provider.ProviderFailure
import atropos.core.provider.ProviderUsage
import java.util.Locale

object DataInfraJson {
    fun parseSearchResult(providerId: String, json: String): ProviderCallResult {
        if (json.isBlank()) {
            return ProviderCallResult.Failure(
                ProviderFailure(providerId, NormalizedProviderFailureType.EMPTY_RESPONSE, "$providerId empty response")
            )
        }
        if (json.contains("\"error\"")) {
            return ProviderCallResult.Failure(ProviderErrorNormalizer().normalize(providerId, stringField(json, "message") ?: json))
        }
        val text = stringField(json, "title")
            ?: stringField(json, "snippet")
            ?: stringField(json, "content")
            ?: stringField(json, "text")
        if (text.isNullOrBlank()) {
            return ProviderCallResult.Failure(
                ProviderFailure(providerId, NormalizedProviderFailureType.MALFORMED_RESPONSE, "$providerId missing result text")
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

    fun planResult(providerId: String, text: String, model: String): ProviderCallResult =
        ProviderCallResult.Success(
            providerId = providerId,
            content = text,
            usage = ProviderUsage(),
            model = model,
            requestId = "local-plan-$providerId"
        )

    private fun stringField(json: String, name: String): String? {
        val regex = Regex(""""$name"\s*:\s*"((?:\\.|[^"\\])*)"""")
        return regex.find(json)?.groupValues?.get(1)?.let(::unescape)
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
