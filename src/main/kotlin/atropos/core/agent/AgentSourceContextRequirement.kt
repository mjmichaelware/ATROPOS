package atropos.core.agent

object AgentSourceContextRequirement {
    data class Refusal(
        val operation: String,
        val code: Code,
        val detail: String
    ) {
        enum class Code {
            MISSING_SOURCE_PACK,
            MISSING_FETCH_RECEIPT,
            PACK_RECEIPT_MISMATCH,
            TRUNCATED_SOURCE_PACK
        }

        val message: String
            get() = "provider $operation refused: $detail"
    }

    fun requiredForAsk(task: String): Boolean {
        val lower = task.lowercase()
        return listOf(
            "self-host",
            "self host",
            "build yourself",
            "improve yourself",
            "atropos",
            "source",
            "code",
            ".kt",
            ".kts",
            ".java",
            ".gradle"
        ).any { it in lower }
    }

    fun refusalFor(
        operation: String,
        task: String,
        sourcePackId: String?,
        fetchReceiptId: String?,
        sourcePackContentHash: String? = null,
        sourceTreeHash: String? = null,
        sourceBindingKind: atropos.core.provider.SourceBindingKind? = null,
        context: String? = null,
        truncated: Boolean = false
    ): Refusal? {
        if (!requiredForAsk(task)) return null
        if (sourcePackId.isNullOrBlank()) {
            return Refusal(
                operation = operation,
                code = Refusal.Code.MISSING_SOURCE_PACK,
                detail = "source context pack unavailable for code-aware operation"
            )
        }
        if (fetchReceiptId.isNullOrBlank()) {
            return Refusal(
                operation = operation,
                code = Refusal.Code.MISSING_FETCH_RECEIPT,
                detail = "source context fetch receipt unavailable for code-aware operation"
            )
        }
        val contextRefusal = context?.let {
            AgentProviderContextBoundary.validateSourcePack(
                context = it,
                sourcePackId = sourcePackId,
                fetchReceiptId = fetchReceiptId,
                sourcePackContentHash = sourcePackContentHash,
                sourceTreeHash = sourceTreeHash,
                sourceBindingKind = sourceBindingKind,
                truncated = truncated
            )
        }
        if (contextRefusal != null) {
            return Refusal(
                operation = operation,
                code = if (contextRefusal.code == AgentProviderContextBoundary.Refusal.Code.TRUNCATED_SOURCE_PACK) {
                    Code.TRUNCATED_SOURCE_PACK
                } else {
                    Code.PACK_RECEIPT_MISMATCH
                },
                detail = contextRefusal.detail
            )
        }
        return null
    }

    fun refusal(operation: String): String =
        Refusal(
            operation = operation,
            code = Refusal.Code.MISSING_SOURCE_PACK,
            detail = "source context pack unavailable for code-aware operation"
        ).message
}
