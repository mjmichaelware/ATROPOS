/* SPDX-License-Identifier: AGPL-3.0-only */
/**
 * B-SUP-017: Prompt-Injection Typed Envelope
 *
 * Implements typed envelopes for prompt injection prevention,
 * ensuring all prompts are properly typed and validated.
 */
package atropos.core.supremacy

import java.nio.file.Files
import java.nio.file.Path
import java.time.Instant

object PromptInjectionEnvelope {
    sealed class EnvelopeType {
        object USER_INPUT : EnvelopeType()
        object SYSTEM_PROMPT : EnvelopeType()
        object TOOL_OUTPUT : EnvelopeType()
        object AGENT_RESPONSE : EnvelopeType()
        data class CUSTOM(val name: String) : EnvelopeType()
    }

    data class TypedEnvelope(
        val id: String = java.util.UUID.randomUUID().toString(),
        val type: EnvelopeType,
        val content: String,
        val metadata: Map<String, String> = emptyMap(),
        val timestamp: Instant = Instant.now(),
        val signature: String? = null
    ) {
        fun withSignature(sig: String): TypedEnvelope = copy(signature = sig)
    }

    /**
     * Creates a typed envelope for user input.
     */
    fun userInput(content: String, metadata: Map<String, String> = emptyMap()): TypedEnvelope {
        return TypedEnvelope(type = EnvelopeType.USER_INPUT, content = content, metadata = metadata)
    }

    /**
     * Creates a typed envelope for system prompt.
     */
    fun systemPrompt(content: String, metadata: Map<String, String> = emptyMap()): TypedEnvelope {
        return TypedEnvelope(type = EnvelopeType.SYSTEM_PROMPT, content = content, metadata = metadata)
    }

    /**
     * Validates an envelope for prompt injection attempts.
     */
    fun validate(envelope: TypedEnvelope): ValidationResult {
        val suspiciousPatterns = listOf(
            "ignore previous instructions",
            "ignore all previous",
            "forget everything",
            "you are now",
            "act as",
            "pretend to be",
            "override",
            "bypass",
            "jailbreak"
        )

        val contentLower = envelope.content.lowercase()
        val detected = suspiciousPatterns.filter { contentLower.contains(it) }

        return if (detected.isNotEmpty()) {
            ValidationResult.FAIL("Prompt injection detected: ${detected.joinToString(", ")}")
        } else {
            ValidationResult.PASS
        }
    }

    sealed class ValidationResult {
        object PASS : ValidationResult()
        data class FAIL(val reason: String) : ValidationResult()
    }

    /**
     * Signs an envelope for integrity.
     */
    fun sign(envelope: TypedEnvelope, key: String): TypedEnvelope {
        val toSign = "${envelope.id}|${envelope.type}|${envelope.content}|${envelope.timestamp}"
        val signature = hmacSha256(toSign, key)
        return envelope.withSignature(signature)
    }

    /**
     * Verifies an envelope signature.
     */
    fun verify(envelope: TypedEnvelope, key: String): Boolean {
        return envelope.signature != null && envelope.signature == hmacSha256(
            "${envelope.id}|${envelope.type}|${envelope.content}|${envelope.timestamp}", key)
    }

    private fun hmacSha256(data: String, key: String): String {
        val mac = javax.crypto.Mac.getInstance("HmacSHA256")
        mac.init(javax.crypto.spec.SecretKeySpec(key.toByteArray(), "HmacSHA256"))
        return mac.doFinal(data.toByteArray()).joinToString("") { "%02x".format(it) }
    }

    /**
     * Persists envelope schema to CAS.
     */
    fun persistSchema(casDir: Path): Path {
        Files.createDirectories(casDir)
        val file = casDir.resolve("prompt-envelope-schema.json")
        val json = """
            {
                "types": ["USER_INPUT", "SYSTEM_PROMPT", "TOOL_OUTPUT", "AGENT_RESPONSE"],
                "validation": "injection-detection",
                "signing": "HMAC-SHA256"
            }
        """.trimIndent()
        Files.writeString(file, json.trimIndent())
        return file
    }

    /**
     * CLI command to validate envelope.
     */
    fun validateAndShow(envelope: TypedEnvelope): String {
        val result = validate(envelope)
        return when (result) {
            is ValidationResult.PASS -> "Envelope valid: ${envelope.type}"
            is ValidationResult.FAIL -> "Envelope REJECTED: ${result.reason}"
        }
    }
}