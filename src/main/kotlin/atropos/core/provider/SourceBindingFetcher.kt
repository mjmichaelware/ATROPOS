package atropos.core.provider

import atropos.core.AtroposConfig
import atropos.core.AtroposRepoRootLocator
import atropos.core.policy.BoundedProcessRunner
import atropos.core.security.RedactionFilter
import atropos.core.security.SecretSinkKind
import atropos.core.security.SecretSinkMatrix
import java.io.BufferedInputStream
import java.io.BufferedOutputStream
import java.net.URI
import java.net.http.HttpClient
import java.net.http.HttpRequest
import java.net.http.HttpResponse
import java.nio.file.Files
import java.nio.file.LinkOption
import java.nio.file.Path
import java.security.MessageDigest
import java.time.Instant
import java.util.UUID
import java.util.zip.ZipInputStream

class SourceBindingFetcher(
    private val repoRoot: Path = AtroposRepoRootLocator.resolve(),
    private val storeRoot: Path = repoRoot.resolve(".atropos/source-bindings/trees"),
    private val treeWriter: ContentAddressedTreeWriter = ContentAddressedTreeWriter(storeRoot),
    private val processRunner: BoundedProcessRunner = BoundedProcessRunner(),
    private val redactionFilter: RedactionFilter = RedactionFilter(),
    private val localOnly: Boolean = AtroposConfig.load().runtime.localOnly
) {
    fun fetch(binding: SourceBinding): SourceFetchResult {
        return when (binding.kind) {
            SourceBindingKind.LOCAL_PATH -> fetchLocal(binding)
            SourceBindingKind.GIT -> fetchGit(binding)
            SourceBindingKind.ARCHIVE -> fetchArchive(binding)
            SourceBindingKind.HTTP_BUNDLE -> fetchHttpBundle(binding)
        }
    }

    private fun fetchLocal(binding: SourceBinding): SourceFetchResult {
        val root = resolveFilePath(binding.uri)
            ?: return SourceFetchResult.Failed("local_path contains an invalid path")
        if (hasSymbolicComponent(root)) {
            return SourceFetchResult.Failed("local_path crosses a symbolic path component")
        }
        if (!Files.isDirectory(root)) return SourceFetchResult.Failed("local_path is not a directory: ${binding.uri}")
        val tree = treeWriter.materialize(root)
        return SourceFetchResult.Fetched(receipt(binding, root.toString(), "local", tree))
    }

    private fun fetchGit(binding: SourceBinding): SourceFetchResult {
        val ref = binding.ref ?: "HEAD"
        val source = runCatching { resolveFilePath(binding.uri) }.getOrNull()
        if (source != null && hasSymbolicComponent(source)) {
            return SourceFetchResult.Failed("git source binding crosses a symbolic path component")
        }
        return if (source != null && Files.isDirectory(source.resolve(".git"), LinkOption.NOFOLLOW_LINKS)) {
            val checkout = temporaryDir("git-local-")
            val copy = runCommand(listOf("git", "clone", "--no-hardlinks", source.toString(), checkout.toString()), repoRoot)
            if (copy.exitCode != 0) return SourceFetchResult.Failed("git clone failed: ${copy.safeOutput()}")
            val checkoutRef = runCommand(listOf("git", "checkout", ref), checkout)
            if (checkoutRef.exitCode != 0) return SourceFetchResult.Failed("git checkout $ref failed: ${checkoutRef.safeOutput()}")
            val commit = runCommand(listOf("git", "rev-parse", "HEAD"), checkout).output.trim().ifBlank { ref }
            val tree = treeWriter.materialize(checkout)
            SourceFetchResult.Fetched(receipt(binding, binding.uri, commit, tree))
        } else if (localOnly) {
            SourceFetchResult.Failed("remote git source binding disabled by localOnly")
        } else {
            val checkout = temporaryDir("git-remote-")
            val cloneCommand = if (binding.ref.isNullOrBlank() || binding.ref == "HEAD") {
                listOf("git", "clone", "--depth", "1", binding.uri, checkout.toString())
            } else {
                listOf("git", "clone", "--depth", "1", "--branch", ref, binding.uri, checkout.toString())
            }
            val clone = runCommand(cloneCommand, repoRoot)
            if (clone.exitCode != 0) return SourceFetchResult.Failed("git clone failed: ${clone.safeOutput()}")
            val commit = runCommand(listOf("git", "rev-parse", "HEAD"), checkout).output.trim().ifBlank { ref }
            val tree = treeWriter.materialize(checkout)
            SourceFetchResult.Fetched(receipt(binding, binding.uri, commit, tree))
        }
    }

    private fun fetchArchive(binding: SourceBinding): SourceFetchResult {
        val archive = resolveFilePath(binding.uri)
            ?: return SourceFetchResult.Failed("archive contains an invalid path")
        if (hasSymbolicComponent(archive)) {
            return SourceFetchResult.Failed("archive source binding crosses a symbolic path component")
        }
        if (!Files.isRegularFile(archive)) return SourceFetchResult.Failed("archive is not a file: ${binding.uri}")
        val actual = sha256(archive)
        val expected = binding.expectedSha256
        if (expected != null && !actual.equals(expected, ignoreCase = true)) {
            return SourceFetchResult.Failed("archive hash mismatch: expected=$expected observed=$actual")
        }
        val unpacked = temporaryDir("archive-")
        val name = archive.fileName.toString().lowercase()
        when {
            name.endsWith(".zip") -> {
                val extracted = runCatching { unzip(archive, unpacked) }
                if (extracted.isFailure) {
                    return SourceFetchResult.Failed(
                        "zip extraction failed: ${extracted.exceptionOrNull()?.message ?: "unknown"}"
                    )
                }
            }
            name.endsWith(".tar") || name.endsWith(".tar.gz") || name.endsWith(".tgz") -> {
                val unsafeEntry = firstUnsafeTarEntry(archive)
                if (unsafeEntry != null) {
                    return SourceFetchResult.Failed("tar extraction refused: archive entry escapes target root: $unsafeEntry")
                }
                val tar = runCommand(listOf("tar", "-xf", archive.toString(), "-C", unpacked.toString()), repoRoot)
                if (tar.exitCode != 0) return SourceFetchResult.Failed("tar extraction failed: ${tar.safeOutput()}")
            }
            else -> return SourceFetchResult.Unsupported("unsupported archive format: ${archive.fileName}")
        }
        val unsafeExtractedEntry = firstUnsafeExtractedEntry(unpacked)
        if (unsafeExtractedEntry != null) {
            return SourceFetchResult.Failed(
                "archive extraction refused: unsafe extracted entry: $unsafeExtractedEntry"
            )
        }
        val tree = treeWriter.materialize(unpacked)
        return SourceFetchResult.Fetched(receipt(binding, archive.toString(), actual, tree))
    }

    private fun fetchHttpBundle(binding: SourceBinding): SourceFetchResult {
        if (localOnly) return SourceFetchResult.Failed("http source binding disabled by localOnly")
        if (!SecretSinkMatrix.isEgressPermitted(SecretSinkKind.EGRESS_URL)) {
            return SourceFetchResult.Failed("http source binding refused by SecretSinkMatrix")
        }
        val expected = binding.expectedSha256
            ?: return SourceFetchResult.Unsupported("http_bundle requires expectedSha256")
        val targetName = runCatching {
            Path.of(URI.create(binding.uri).path).fileName?.toString()?.takeIf { it.isNotBlank() }
        }.getOrNull() ?: "bundle"
        val target = temporaryDir("http-bundle-").resolve(targetName)
        return try {
            val uri = URI.create(binding.uri)
            require(uri.scheme.equals("http", ignoreCase = true) || uri.scheme.equals("https", ignoreCase = true)) {
                "http_bundle requires an http or https URI"
            }
            val request = HttpRequest.newBuilder(uri)
                .timeout(java.time.Duration.ofSeconds(30))
                .GET()
                .build()
            val response = HttpClient.newBuilder()
                .connectTimeout(java.time.Duration.ofSeconds(5))
                .build()
                .send(request, HttpResponse.BodyHandlers.ofInputStream())
            if (response.statusCode() !in 200..299) {
                return SourceFetchResult.Failed("http_bundle fetch failed: status=${response.statusCode()}")
            }
            val downloadFailure = writeBoundedBody(response, target)
            if (downloadFailure != null) return SourceFetchResult.Failed(downloadFailure)
            val actual = sha256(target)
            if (!actual.equals(expected, ignoreCase = true)) {
                return SourceFetchResult.Failed("http_bundle hash mismatch: expected=$expected observed=$actual")
            }
            when (val archive = fetchArchive(binding.copy(kind = SourceBindingKind.ARCHIVE, uri = target.toString(), expectedSha256 = expected))) {
                is SourceFetchResult.Fetched -> SourceFetchResult.Fetched(
                    archive.receipt.copy(
                        bindingKind = SourceBindingKind.HTTP_BUNDLE,
                        repository = sanitizeOrigin(binding.uri),
                        message = "hash-pinned http bundle"
                    )
                )
                is SourceFetchResult.Failed -> archive
                is SourceFetchResult.Unsupported -> archive
            }
        } catch (e: Exception) {
            SourceFetchResult.Failed("http_bundle fetch failed: ${e.message ?: e.javaClass.simpleName}")
        }
    }

    private fun unzip(archive: Path, targetRoot: Path) {
        ZipInputStream(Files.newInputStream(archive)).use { zip ->
            while (true) {
                val entry = zip.nextEntry ?: break
                val target = targetRoot.resolve(entry.name).normalize()
                require(target.startsWith(targetRoot)) { "archive entry escapes target root: ${entry.name}" }
                if (entry.isDirectory) {
                    Files.createDirectories(target)
                } else {
                    Files.createDirectories(target.parent)
                    Files.copy(zip, target)
                }
                zip.closeEntry()
            }
        }
    }

    private fun firstUnsafeTarEntry(archive: Path): String? {
        val listing = runCommand(listOf("tar", "-tf", archive.toString()), repoRoot)
        if (listing.exitCode != 0) return "unreadable tar listing: ${listing.safeOutput(120)}"
        return listing.output
            .lineSequence()
            .map { it.trim() }
            .filter { it.isNotBlank() }
            .firstOrNull(::isUnsafeArchiveEntry)
    }

    private fun isUnsafeArchiveEntry(entry: String): Boolean {
        val normalized = Path.of(entry).normalize().toString().replace('\\', '/')
        return entry.startsWith("/") ||
            normalized == ".." ||
            normalized.startsWith("../") ||
            "/../" in normalized
    }

    private fun firstUnsafeExtractedEntry(root: Path): String? = runCatching {
        Files.walk(root).use { entries ->
            val unsafe = entries
                .filter { entry ->
                    entry != root && (
                        Files.isSymbolicLink(entry) ||
                            (!Files.isDirectory(entry, LinkOption.NOFOLLOW_LINKS) &&
                                !Files.isRegularFile(entry, LinkOption.NOFOLLOW_LINKS))
                        )
                }
                .findFirst()
            if (!unsafe.isPresent) {
                null
            } else {
                val entry = unsafe.get()
                val reason = if (Files.isSymbolicLink(entry)) "symbolic-link" else "special-file"
                "$reason:${root.relativize(entry).toString().replace('\\', '/')}"
            }
        }
    }.getOrElse { "scan-failed" }

    private fun receipt(binding: SourceBinding, repo: String, ref: String, tree: FetchTree): FetchReceipt =
        FetchReceipt(
            id = "fetch-${UUID.randomUUID().toString().take(12)}",
            bindingKind = binding.kind,
            repository = sanitizeOrigin(repo),
            ref = ref,
            treeHash = tree.treeHash,
            contentRoot = tree.root,
            paths = tree.paths,
            fetchedAt = Instant.now()
        )

    private fun sanitizeOrigin(origin: String): String {
        val parsed = runCatching { URI.create(origin) }.getOrNull()
        if (parsed?.rawUserInfo == null) return origin
        return runCatching {
            URI(
                parsed.scheme,
                null,
                parsed.host,
                parsed.port,
                parsed.path,
                parsed.query,
                parsed.fragment
            ).toString()
        }.getOrDefault(origin.replace(Regex("//[^/@]+@"), "//"))
    }

    private fun temporaryDir(prefix: String): Path =
        Files.createTempDirectory(repoRoot.resolve(".atropos/source-bindings").also { Files.createDirectories(it) }, prefix)

    private fun resolveFilePath(raw: String): Path? = runCatching {
        Path.of(raw).let { path ->
            if (path.isAbsolute) path.normalize() else repoRoot.resolve(path).normalize()
        }
    }.getOrNull()

    private fun hasSymbolicComponent(path: Path): Boolean {
        var current: Path? = path.toAbsolutePath().normalize()
        while (current != null) {
            if (Files.isSymbolicLink(current)) return true
            current = current.parent
        }
        return false
    }

    private fun writeBoundedBody(
        response: HttpResponse<java.io.InputStream>,
        target: Path
    ): String? {
        val advertised = response.headers().firstValueAsLong("Content-Length").orElse(-1L)
        if (advertised > MAX_HTTP_BUNDLE_BYTES) {
            response.body().close()
            return "http_bundle exceeds bounded download size: $advertised bytes"
        }
        var total = 0L
        var exceeded = false
        return try {
            BufferedInputStream(response.body()).use { input ->
                BufferedOutputStream(Files.newOutputStream(target)).use { output ->
                    val buffer = ByteArray(DEFAULT_BUFFER_SIZE)
                    while (true) {
                        val read = input.read(buffer)
                        if (read < 0) break
                        total += read
                        if (total > MAX_HTTP_BUNDLE_BYTES) {
                            exceeded = true
                            break
                        }
                        output.write(buffer, 0, read)
                    }
                }
            }
            if (exceeded) {
                Files.deleteIfExists(target)
                "http_bundle exceeds bounded download size: $total bytes"
            } else {
                null
            }
        } catch (error: Exception) {
            Files.deleteIfExists(target)
            "http_bundle download failed: ${error.message ?: error.javaClass.simpleName}"
        }
    }

    private fun sha256(path: Path): String {
        val digest = MessageDigest.getInstance("SHA-256")
        Files.newInputStream(path).use { input ->
            val buffer = ByteArray(DEFAULT_BUFFER_SIZE)
            while (true) {
                val read = input.read(buffer)
                if (read < 0) break
                digest.update(buffer, 0, read)
            }
        }
        return digest.digest().joinToString("") { "%02x".format(it) }
    }

    private fun runCommand(command: List<String>, cwd: Path): CommandResult {
        val result = runCatching {
            processRunner.run(
                command = command,
                directory = cwd,
                timeoutMillis = 30_000,
                maxOutputBytes = 64 * 1024,
                maxOutputLines = 1_000
            )
        }.getOrElse { failure ->
            return CommandResult(1, failure.message ?: failure.javaClass.simpleName, false)
        }
        val output = listOf(result.stdout, result.stderr)
            .filter { it.isNotBlank() }
            .joinToString("\n")
        return CommandResult(
            exitCode = result.exitCode ?: if (result.timedOut) 124 else 1,
            output = redactionFilter.compact(output, 600),
            timedOut = result.timedOut
        )
    }

    private data class CommandResult(val exitCode: Int, val output: String, val timedOut: Boolean) {
        fun safeOutput(maxChars: Int = 300): String =
            output.ifBlank { if (timedOut) "command timed out" else "no command output" }.take(maxChars)
    }

    private companion object {
        const val MAX_HTTP_BUNDLE_BYTES = 16L * 1024L * 1024L
    }
}
