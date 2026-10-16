/* SPDX-License-Identifier: AGPL-3.0-only */
package atropos.core.research

import java.nio.file.Files
import java.nio.file.Path
import java.security.MessageDigest
import java.time.Instant

enum class SpanType {
    PROSE,
    SURFACE_WORD,
    FEATURE,
    FUNCTION,
    CONSTRAINT,
    NON_GOAL,
    ACCEPTANCE
}

data class PromptSpan(
    val spanId: String,
    val text: String,
    val type: SpanType
)

data class PromptArtifact(
    val h0: String,
    val timestampUtc: Instant,
    val fingerprintId: String,
    val projectId: String,
    val rawPromptText: String,
    val spans: List<PromptSpan>
)

object PromptArtifactStore {
    fun persist(prompt: String, projectId: String, dir: Path): PromptArtifact {
        Files.createDirectories(dir)
        val h0 = sha256(prompt)
        val spans = extractSpans(prompt)
        val timestamp = Instant.now()
        val fingerprint = "fp-${h0.take(12)}"
        val artifact = PromptArtifact(
            h0 = h0,
            timestampUtc = timestamp,
            fingerprintId = fingerprint,
            projectId = projectId,
            rawPromptText = prompt,
            spans = spans
        )
        val file = dir.resolve("prompt_$h0.json")
        val json = serialize(artifact)
        Files.writeString(file, json)
        return artifact
    }

    fun read(h0: String, dir: Path): PromptArtifact? {
        val file = dir.resolve("prompt_$h0.json")
        if (!Files.exists(file)) return null
        val json = Files.readString(file)
        val artifact = deserialize(json) ?: return null
        val verifiedH0 = sha256(artifact.rawPromptText)
        if (verifiedH0 != artifact.h0 || verifiedH0 != h0) {
            throw IllegalStateException("CAS hash mismatch for prompt artifact: expected $h0 but verified $verifiedH0")
        }
        return artifact
    }

    private fun extractSpans(text: String): List<PromptSpan> {
        val rawClauses = text.split(Regex("(?<=[.!?;\n])\\s+")).map { it.trim() }.filter { it.isNotBlank() }
        if (rawClauses.isEmpty()) {
            return listOf(PromptSpan("span-0", text.trim(), SpanType.PROSE))
        }

        return rawClauses.mapIndexed { idx, clause ->
            val lower = clause.lowercase()
            val type = when {
                lower.contains("must") || lower.contains("shall") || lower.contains("cannot") ||
                    lower.contains("never") || lower.contains("required") || lower.contains("limit") ||
                    lower.contains("bounded") || lower.contains("invariant") -> SpanType.CONSTRAINT

                lower.contains("non-goal") || lower.contains("out of scope") || lower.contains("not support") ||
                    lower.contains("skip") -> SpanType.NON_GOAL

                lower.contains("acceptance") || lower.contains("verify") || lower.contains("assert") ||
                    lower.contains("test") -> SpanType.ACCEPTANCE

                lower.contains("function") || lower.contains("api") || lower.contains("endpoint") ||
                    lower.contains("method") || lower.contains("service") -> SpanType.FUNCTION

                lower.contains("feature") || lower.contains("screen") || lower.contains("view") ||
                    lower.contains("dashboard") || lower.contains("ui") -> SpanType.FEATURE

                clause.split(Regex("\\s+")).size <= 2 -> SpanType.SURFACE_WORD

                else -> SpanType.PROSE
            }
            PromptSpan(spanId = "span-$idx", text = clause, type = type)
        }
    }

    private fun sha256(input: String): String {
        val md = MessageDigest.getInstance("SHA-256")
        return md.digest(input.toByteArray(Charsets.UTF_8)).joinToString("") { "%02x".format(it) }
    }

    private fun escape(s: String): String = s
        .replace("\\", "\\\\")
        .replace("\"", "\\\"")
        .replace("\n", "\\n")
        .replace("\r", "\\r")
        .replace("\t", "\\t")

    private fun serialize(a: PromptArtifact): String {
        val spansJson = a.spans.joinToString(",") { s ->
            """{"spanId":"${escape(s.spanId)}","text":"${escape(s.text)}","type":"${s.type.name}"}"""
        }
        return """{
  "h0": "${escape(a.h0)}",
  "timestampUtc": "${a.timestampUtc}",
  "fingerprintId": "${escape(a.fingerprintId)}",
  "projectId": "${escape(a.projectId)}",
  "rawPromptText": "${escape(a.rawPromptText)}",
  "spans": [$spansJson]
}"""
    }

    private fun unescape(s: String): String = s
        .replace("\\n", "\n")
        .replace("\\r", "\r")
        .replace("\\t", "\t")
        .replace("\\\"", "\"")
        .replace("\\\\", "\\")

    private fun deserialize(json: String): PromptArtifact? {
        val h0 = Regex("\"h0\":\\s*\"([^\"]*)\"").find(json)?.groupValues?.get(1)?.let { unescape(it) } ?: return null
        val ts = Regex("\"timestampUtc\":\\s*\"([^\"]*)\"").find(json)?.groupValues?.get(1) ?: return null
        val fp = Regex("\"fingerprintId\":\\s*\"([^\"]*)\"").find(json)?.groupValues?.get(1)?.let { unescape(it) } ?: ""
        val pid = Regex("\"projectId\":\\s*\"([^\"]*)\"").find(json)?.groupValues?.get(1)?.let { unescape(it) } ?: ""
        val prompt = Regex("\"rawPromptText\":\\s*\"((?:[^\"\\\\]|\\\\.)*)\"").find(json)?.groupValues?.get(1)?.let { unescape(it) } ?: ""

        val spanMatches = Regex("""\{"spanId":"([^"]*)","text":"((?:[^"\\]|\\.)*)","type":"([^"]*)"\}""").findAll(json)
        val spans = spanMatches.map { match ->
            val spanId = unescape(match.groupValues[1])
            val text = unescape(match.groupValues[2])
            val type = runCatching { SpanType.valueOf(match.groupValues[3]) }.getOrDefault(SpanType.PROSE)
            PromptSpan(spanId, text, type)
        }.toList()

        return PromptArtifact(
            h0 = h0,
            timestampUtc = Instant.parse(ts),
            fingerprintId = fp,
            projectId = pid,
            rawPromptText = prompt,
            spans = spans
        )
    }
}
