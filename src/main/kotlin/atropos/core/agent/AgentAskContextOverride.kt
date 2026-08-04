package atropos.core.agent

import atropos.core.provider.ContextEnvelope
import atropos.core.provider.SourceBindingKind

data class AgentAskContextOverride(
    val envelope: ContextEnvelope,
    val contextText: String,
    val sourcePackId: String? = null,
    val fetchReceiptId: String? = null,
    val sourcePackContentHash: String? = null,
    val sourceTreeHash: String? = null,
    val sourceBindingKind: SourceBindingKind? = null
) {
    val byteCount: Int = contextText.toByteArray(Charsets.UTF_8).size
}
