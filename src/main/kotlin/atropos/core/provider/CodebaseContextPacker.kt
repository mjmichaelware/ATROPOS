package atropos.core.provider

import atropos.core.AtroposRepoRootLocator
import atropos.core.security.RedactionFilter
import java.nio.charset.StandardCharsets
import java.nio.file.Files
import java.nio.file.LinkOption
import java.nio.file.Path
import java.security.MessageDigest

class CodebaseContextPacker(
    private val repoRoot: Path = AtroposRepoRootLocator.resolve(),
    private val fetcher: SourceBindingFetcher = SourceBindingFetcher(repoRoot),
    private val redactionFilter: RedactionFilter = RedactionFilter()
) {
    private val contentHashPlaceholder = "0".repeat(64)

    private val pendingPackId = "pack-0000000000000000"

    fun pack(request: SourcePackRequest): SourcePackResult {
        if (request.maxBytes <= 0) {
            return SourcePackResult.Refused("source context pack maxBytes must be positive")
        }
        if (request.maxFileBytes <= 0) {
            return SourcePackResult.Refused("source context pack maxFileBytes must be positive")
        }
        if (request.allowedPaths.isEmpty()) {
            return SourcePackResult.Refused("source context pack requires at least one allowed path")
        }
        val fetched = fetcher.fetch(request.binding)
        if (fetched !is SourceFetchResult.Fetched) {
            val reason = when (fetched) {
                is SourceFetchResult.Failed -> fetched.reason
                is SourceFetchResult.Unsupported -> fetched.reason
                is SourceFetchResult.Fetched -> ""
            }
            return SourcePackResult.Refused(reason)
        }

        val rawAllowed = request.allowedPaths.map { it.trim().replace('\\', '/') }
        if (rawAllowed.any { it.startsWith("/") || it.split('/').contains("..") }) {
            return SourcePackResult.Refused("source context pack territory must stay within the bound source tree")
        }
        val allowed = rawAllowed.map { it.trim('/').trim() }.filter { it.isNotBlank() }
        val included = fetched.receipt.paths.filter { path ->
            allowed.any { root -> pathWithinAllowed(path, root) }
        }.sorted()
        if (included.isEmpty()) {
            return SourcePackResult.Refused("source context pack matched no files inside declared territory")
        }

        val builder = StringBuilder(request.maxBytes)
        var truncated = false
        var redacted = false
        BoundedUtf8Appender.append(builder, "SOURCE_PACK_ID=$pendingPackId\n", request.maxBytes) { truncated = true }
        BoundedUtf8Appender.append(builder, "FETCH_RECEIPT_ID=${fetched.receipt.id}\n", request.maxBytes) { truncated = true }
        BoundedUtf8Appender.append(builder, "BINDING=${fetched.receipt.bindingKind}\n", request.maxBytes) { truncated = true }
        BoundedUtf8Appender.append(builder, "TREE_HASH=${fetched.receipt.treeHash}\n", request.maxBytes) { truncated = true }
        BoundedUtf8Appender.append(builder, "PACK_CONTENT_HASH=$contentHashPlaceholder\n", request.maxBytes) { truncated = true }
        BoundedUtf8Appender.append(builder, "TERRITORY=${allowed.joinToString("|")}\n\n", request.maxBytes) { truncated = true }

        val packedPaths = mutableListOf<String>()
        for (relative in included) {
            val currentBytes = BoundedUtf8Appender.utf8Size(builder)
            if (currentBytes >= request.maxBytes) {
                truncated = true
                break
            }
            val file = fetched.receipt.contentRoot.resolve(relative).normalize()
            if (!file.startsWith(fetched.receipt.contentRoot) ||
                !Files.isRegularFile(file, LinkOption.NOFOLLOW_LINKS)
            ) continue
            val bytes = Files.readAllBytes(file)
            val body = String(bytes.take(request.maxFileBytes).toByteArray(), StandardCharsets.UTF_8)
            if (bytes.size > request.maxFileBytes) truncated = true
            val report = redactionFilter.report(body)
            redacted = redacted || report.changed
            val section = "FILE $relative\n${report.redacted}\nEND FILE\n\n"
            val minimumSectionBytes = "FILE $relative\n".toByteArray(StandardCharsets.UTF_8).size + 1
            if (request.maxBytes - currentBytes < minimumSectionBytes) {
                truncated = true
                break
            }
            BoundedUtf8Appender.append(builder, section, request.maxBytes) { truncated = true }
            packedPaths += relative
        }

        if (packedPaths.isEmpty()) {
            return SourcePackResult.Refused("source context pack contained no readable files")
        }
        val initial = builder.toString()
        // Hash the exact canonical representation used for verification. The two
        // self-referential fields remain placeholders while all source bytes and
        // receipt provenance are included in the digest.
        val contentHash = sha256(initial)
        val packId = "pack-${contentHash.take(16)}"
        val text = initial
            .replaceFirst("SOURCE_PACK_ID=$pendingPackId", "SOURCE_PACK_ID=$packId")
            .replaceFirst("PACK_CONTENT_HASH=$contentHashPlaceholder", "PACK_CONTENT_HASH=$contentHash")
        val bytes = text.toByteArray(StandardCharsets.UTF_8).size
        return SourcePackResult.Packed(
            CodebaseContextPack(
                id = packId,
                fetchReceipt = fetched.receipt,
                contentHash = contentHash,
                includedPaths = packedPaths,
                byteCount = bytes,
                truncated = truncated,
                redacted = redacted,
                text = text
            )
        )
    }

    private fun sha256(value: String): String =
        MessageDigest.getInstance("SHA-256")
            .digest(value.toByteArray(StandardCharsets.UTF_8))
            .joinToString("") { "%02x".format(it) }

    private fun pathWithinAllowed(path: String, root: String): Boolean {
        val normalizedPath = path.replace('\\', '/').trim('/')
        val normalizedRoot = root.replace('\\', '/').trim('/')
        return normalizedPath == normalizedRoot || normalizedPath.startsWith("$normalizedRoot/")
    }
}
