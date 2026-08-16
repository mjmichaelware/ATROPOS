package atropos.core.provider

import java.nio.file.Path
import java.nio.charset.StandardCharsets
import java.security.MessageDigest
import java.time.Instant

enum class SourceBindingKind {
    GIT,
    LOCAL_PATH,
    ARCHIVE,
    HTTP_BUNDLE
}

data class SourceBinding(
    val kind: SourceBindingKind,
    val uri: String,
    val ref: String? = null,
    val expectedSha256: String? = null
) {
    companion object {
        fun localPath(path: Path): SourceBinding =
            SourceBinding(SourceBindingKind.LOCAL_PATH, path.toAbsolutePath().normalize().toString())

        fun git(uri: String, ref: String? = null): SourceBinding =
            SourceBinding(SourceBindingKind.GIT, uri, ref)

        fun archive(path: Path, expectedSha256: String? = null): SourceBinding =
            SourceBinding(SourceBindingKind.ARCHIVE, path.toAbsolutePath().normalize().toString(), expectedSha256 = expectedSha256)

        fun httpBundle(uri: String, expectedSha256: String): SourceBinding =
            SourceBinding(SourceBindingKind.HTTP_BUNDLE, uri, expectedSha256 = expectedSha256)
    }
}

data class FetchReceipt(
    val id: String,
    val bindingKind: SourceBindingKind,
    val repository: String,
    val ref: String,
    val treeHash: String,
    val contentRoot: Path,
    val paths: List<String>,
    val fetchedAt: Instant = Instant.now(),
    val degraded: Boolean = false,
    val message: String = ""
)

sealed class SourceFetchResult {
    data class Fetched(val receipt: FetchReceipt) : SourceFetchResult()
    data class Unsupported(val reason: String) : SourceFetchResult()
    data class Failed(val reason: String) : SourceFetchResult()
}

data class SourcePackRequest(
    val binding: SourceBinding,
    val allowedPaths: List<String>,
    val maxBytes: Int = 64 * 1024,
    val maxFileBytes: Int = 16 * 1024
)

data class CodebaseContextPack(
    val id: String,
    val fetchReceipt: FetchReceipt,
    val contentHash: String,
    val includedPaths: List<String>,
    val byteCount: Int,
    val truncated: Boolean,
    val redacted: Boolean,
    val text: String,
    val metrics: SourceContextMetrics = SourceContextMetrics(0, byteCount, null),
) {
    fun hasValidContentHash(): Boolean {
        val canonical = text
            .replaceFirst("SOURCE_PACK_ID=$id", "SOURCE_PACK_ID=${"pack-0000000000000000"}")
            .replaceFirst("PACK_CONTENT_HASH=$contentHash", "PACK_CONTENT_HASH=${"0".repeat(64)}")
        val observed = MessageDigest.getInstance("SHA-256")
            .digest(canonical.toByteArray(StandardCharsets.UTF_8))
            .joinToString("") { "%02x".format(it) }
        return observed == contentHash && id == "pack-${contentHash.take(16)}" &&
            byteCount == text.toByteArray(StandardCharsets.UTF_8).size
    }

    fun provenance(): SourcePackProvenance = SourcePackProvenance(
        packId = id,
        contentHash = contentHash,
        fetchReceiptId = fetchReceipt.id,
        treeHash = fetchReceipt.treeHash,
        bindingKind = fetchReceipt.bindingKind,
        includedPaths = includedPaths,
        redacted = redacted,
        truncated = truncated,
        metrics = metrics,
    )
}

data class SourcePackProvenance(
    val packId: String,
    val contentHash: String,
    val fetchReceiptId: String,
    val treeHash: String,
    val bindingKind: SourceBindingKind,
    val includedPaths: List<String>,
    val redacted: Boolean,
    val truncated: Boolean,
    val metrics: SourceContextMetrics,
)

sealed class SourcePackResult {
    data class Packed(val pack: CodebaseContextPack) : SourcePackResult()
    data class Refused(val reason: String) : SourcePackResult()
}
