package atropos.core.verification

import atropos.core.security.RedactionFilter

data class OutputValidationResult(
    val accepted: Boolean,
    val redactedOutput: String,
    val reason: String
)

/** Validates provider output before parser or renderer code treats it as trusted. */
class OutputValidator(
    private val redactionFilter: RedactionFilter = RedactionFilter(),
    private val maximumBytes: Int = DEFAULT_MAXIMUM_BYTES
) {
    fun validate(output: String): OutputValidationResult {
        if (output.isBlank()) return rejected(output, "provider output is blank")
        val redacted = redactionFilter.redact(output)
        if (output.toByteArray(Charsets.UTF_8).size > maximumBytes) {
            return rejected(redacted, "provider output exceeds the bounded output limit")
        }
        if (redacted != output) {
            return rejected(redacted, "provider output contained secret-like material")
        }
        return OutputValidationResult(true, redacted, "provider output accepted")
    }

    private fun rejected(output: String, reason: String) =
        OutputValidationResult(false, redactionFilter.redact(output), reason)

    companion object {
        const val DEFAULT_MAXIMUM_BYTES: Int = 1_048_576
    }
}
