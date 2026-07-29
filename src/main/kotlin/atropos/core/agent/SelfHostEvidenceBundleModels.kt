package atropos.core.agent

import java.nio.file.Path

data class SelfHostEvidenceBundleResult(
    val ok: Boolean,
    val message: String,
    val markdownPath: Path?,
    val jsonPath: Path?,
    val markdownSha256: String?,
    val jsonSha256: String?
)
