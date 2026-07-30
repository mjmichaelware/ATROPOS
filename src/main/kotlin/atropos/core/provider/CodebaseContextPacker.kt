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
    private val pendingPackId = "pack-0000000000000000"

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
        appendBounded(builder, "SOURCE_PACK_ID=$pendingPackId\n", request.maxBytes) { truncated = true }
        appendBounded(builder, "FETCH_RECEIPT_ID=${fetched.receipt.id}\n", request.maxBytes) { truncated = true }
        appendBounded(builder, "BINDING=${fetched.receipt.bindingKind}\n", request.maxBytes) { truncated = true }
        appendBounded(builder, "TREE_HASH=${fetched.receipt.treeHash}\n", request.maxBytes) { truncated = true }
        appendBounded(builder, "TERRITORY=${allowed.joinToString("|")}\n\n", request.maxBytes) { truncated = true }

        val packedPaths = mutableListOf<String>()
        for (relative in included) {
            val currentBytes = builder.byteCount()
            if (currentBytes >= request.maxBytes) {
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
            val minimumSectionBytes = "FILE $relative\n".toByteArray(StandardCharsets.UTF_8).size + 1
            if (request.maxBytes - currentBytes < minimumSectionBytes) {
                truncated = true
                break
            }
            appendBounded(builder, section, request.maxBytes) { truncated = true }
            packedPaths += relative
        }

        if (packedPaths.isEmpty()) {
            return SourcePackResult.Refused("source context pack contained no readable files")
        }
        val initial = builder.toString()
        val contentHash = sha256(initial)
        val packId = "pack-${contentHash.take(16)}"
        val text = initial.replace("SOURCE_PACK_ID=$pendingPackId", "SOURCE_PACK_ID=$packId")
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
            val marker = "\n[source context truncated]\n"
            val markerBytes = marker.toByteArray(StandardCharsets.UTF_8).size
            val bodyLimit = (remaining - markerBytes).coerceAtLeast(0)
            builder.append(text.utf8Prefix(bodyLimit))
            if (markerBytes <= remaining - builder.lastAppendByteCount(current)) {
                builder.append(marker)
            }
            onTruncated()
        }
    }

    private fun StringBuilder.lastAppendByteCount(previousBytes: Int): Int =
        byteCount() - previousBytes

    private fun StringBuilder.byteCount(): Int =
        toString().toByteArray(StandardCharsets.UTF_8).size

    private fun String.utf8Prefix(maxBytes: Int): String {
        if (maxBytes <= 0) return ""
        val out = StringBuilder()
        var used = 0
        var index = 0
        while (index < length) {
            val codePoint = codePointAt(index)
            val text = String(Character.toChars(codePoint))
            val size = text.toByteArray(StandardCharsets.UTF_8).size
            if (used + size > maxBytes) break
            out.append(text)
            used += size
            index += Character.charCount(codePoint)
        }
        return out.toString()
    }

    private fun sha256(value: String): String =
        MessageDigest.getInstance("SHA-256")
            .digest(value.toByteArray(StandardCharsets.UTF_8))
            .joinToString("") { "%02x".format(it) }
}
