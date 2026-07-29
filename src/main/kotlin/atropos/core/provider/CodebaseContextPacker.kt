package atropos.core.provider

import atropos.core.AtroposRepoRootLocator
import atropos.core.security.RedactionFilter
import java.nio.charset.StandardCharsets
import java.nio.file.Files
import java.nio.file.Path
import java.security.MessageDigest

class CodebaseContextPacker(
    private val repoRoot: Path = AtroposRepoRootLocator.resolve(),
    private val fetcher: SourceBindingFetcher = SourceBindingFetcher(repoRoot),
    private val redactionFilter: RedactionFilter = RedactionFilter()
) {
    fun pack(request: SourcePackRequest): SourcePackResult {
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

        val allowed = request.allowedPaths.map { it.trim().trim('/').replace('\\', '/') }.filter { it.isNotBlank() }
        val included = fetched.receipt.paths.filter { path ->
            allowed.any { root -> path == root || path.startsWith("$root/") }
        }.sorted()
        if (included.isEmpty()) {
            return SourcePackResult.Refused("source context pack matched no files inside declared territory")
        }

        val builder = StringBuilder(request.maxBytes)
        var truncated = false
        var redacted = false
        appendBounded(builder, "SOURCE_PACK_ID=pending\n", request.maxBytes) { truncated = true }
        appendBounded(builder, "FETCH_RECEIPT_ID=${fetched.receipt.id}\n", request.maxBytes) { truncated = true }
        appendBounded(builder, "BINDING=${fetched.receipt.bindingKind}\n", request.maxBytes) { truncated = true }
        appendBounded(builder, "TREE_HASH=${fetched.receipt.treeHash}\n", request.maxBytes) { truncated = true }
        appendBounded(builder, "TERRITORY=${allowed.joinToString("|")}\n\n", request.maxBytes) { truncated = true }

        val packedPaths = mutableListOf<String>()
        for (relative in included) {
            if (builder.toString().toByteArray(StandardCharsets.UTF_8).size >= request.maxBytes) {
                truncated = true
                break
            }
            val file = fetched.receipt.contentRoot.resolve(relative).normalize()
            if (!file.startsWith(fetched.receipt.contentRoot) || !Files.isRegularFile(file)) continue
            val bytes = Files.readAllBytes(file)
            val body = String(bytes.take(request.maxFileBytes).toByteArray(), StandardCharsets.UTF_8)
            if (bytes.size > request.maxFileBytes) truncated = true
            val report = redactionFilter.report(body)
            redacted = redacted || report.changed
            val section = "FILE $relative\n${report.redacted}\nEND FILE\n\n"
            appendBounded(builder, section, request.maxBytes) { truncated = true }
            packedPaths += relative
        }

        if (packedPaths.isEmpty()) {
            return SourcePackResult.Refused("source context pack contained no readable files")
        }
        val initial = builder.toString()
        val contentHash = sha256(initial)
        val packId = "pack-${contentHash.take(16)}"
        val text = initial.replace("SOURCE_PACK_ID=pending", "SOURCE_PACK_ID=$packId")
        val bytes = text.toByteArray(StandardCharsets.UTF_8).size
        return SourcePackResult.Packed(
            CodebaseContextPack(
                id = packId,
                fetchReceipt = fetched.receipt,
                contentHash = sha256(text),
                includedPaths = packedPaths,
                byteCount = bytes,
                truncated = truncated,
                redacted = redacted,
                text = text
            )
        )
    }

    private fun appendBounded(builder: StringBuilder, text: String, maxBytes: Int, onTruncated: () -> Unit) {
        val current = builder.toString().toByteArray(StandardCharsets.UTF_8).size
        val remaining = maxBytes - current
        if (remaining <= 0) {
            onTruncated()
            return
        }
        val bytes = text.toByteArray(StandardCharsets.UTF_8)
        if (bytes.size <= remaining) {
            builder.append(text)
        } else {
            builder.append(String(bytes, 0, remaining, StandardCharsets.UTF_8))
            builder.appendLine()
            builder.appendLine("[source context truncated]")
            onTruncated()
        }
    }

    private fun sha256(value: String): String =
        MessageDigest.getInstance("SHA-256")
            .digest(value.toByteArray(StandardCharsets.UTF_8))
            .joinToString("") { "%02x".format(it) }
}
