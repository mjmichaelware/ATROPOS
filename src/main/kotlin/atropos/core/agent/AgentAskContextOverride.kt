package atropos.core.agent

import atropos.core.provider.ContextEnvelope

data class AgentAskContextOverride(
    val envelope: ContextEnvelope,
    val contextText: String,
    val sourcePackId: String? = null,
    val fetchReceiptId: String? = null
) {
    val byteCount: Int = contextText.toByteArray(Charsets.UTF_8).size
}
