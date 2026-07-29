package atropos.core.provider

import atropos.core.AtroposRepoRootLocator
import java.net.URI
import java.net.http.HttpClient
import java.net.http.HttpRequest
import java.net.http.HttpResponse
import java.nio.charset.StandardCharsets
import java.nio.file.Files
import java.nio.file.Path
import java.security.MessageDigest
import java.time.Instant
import java.util.UUID
import java.util.zip.ZipInputStream

class SourceBindingFetcher(
    private val repoRoot: Path = AtroposRepoRootLocator.resolve(),
    private val storeRoot: Path = repoRoot.resolve(".atropos/source-bindings/trees"),
    private val treeWriter: ContentAddressedTreeWriter = ContentAddressedTreeWriter(storeRoot)
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
        val root = Path.of(binding.uri).toAbsolutePath().normalize()
        if (!Files.isDirectory(root)) return SourceFetchResult.Failed("local_path is not a directory: ${binding.uri}")
        val tree = treeWriter.materialize(root)
        return SourceFetchResult.Fetched(receipt(binding, root.toString(), "local", tree))
    }

    private fun fetchGit(binding: SourceBinding): SourceFetchResult {
        val ref = binding.ref ?: "HEAD"
        val source = Path.of(binding.uri).toAbsolutePath().normalize()
        return if (Files.isDirectory(source.resolve(".git"))) {
            val checkout = temporaryDir("git-local-")
            val copy = runCommand(listOf("git", "clone", "--no-hardlinks", source.toString(), checkout.toString()), repoRoot)
            if (copy.exitCode != 0) return SourceFetchResult.Failed("git clone failed: ${copy.output.take(300)}")
            val checkoutRef = runCommand(listOf("git", "checkout", ref), checkout)
            if (checkoutRef.exitCode != 0) return SourceFetchResult.Failed("git checkout $ref failed: ${checkoutRef.output.take(300)}")
            val commit = runCommand(listOf("git", "rev-parse", "HEAD"), checkout).output.trim().ifBlank { ref }
            val tree = treeWriter.materialize(checkout)
            SourceFetchResult.Fetched(receipt(binding, binding.uri, commit, tree))
        } else {
            val checkout = temporaryDir("git-remote-")
            val clone = runCommand(listOf("git", "clone", "--depth", "1", "--branch", ref, binding.uri, checkout.toString()), repoRoot)
            if (clone.exitCode != 0) return SourceFetchResult.Failed("git fetch failed: ${clone.output.take(300)}")
            val commit = runCommand(listOf("git", "rev-parse", "HEAD"), checkout).output.trim().ifBlank { ref }
            val tree = treeWriter.materialize(checkout)
            SourceFetchResult.Fetched(receipt(binding, binding.uri, commit, tree))
        }
    }

    private fun fetchArchive(binding: SourceBinding): SourceFetchResult {
        val archive = Path.of(binding.uri).toAbsolutePath().normalize()
        if (!Files.isRegularFile(archive)) return SourceFetchResult.Failed("archive is not a file: ${binding.uri}")
        val actual = sha256(archive)
        val expected = binding.expectedSha256
        if (expected != null && !actual.equals(expected, ignoreCase = true)) {
            return SourceFetchResult.Failed("archive hash mismatch: expected=$expected observed=$actual")
        }
        val unpacked = temporaryDir("archive-")
        val name = archive.fileName.toString().lowercase()
        when {
            name.endsWith(".zip") -> unzip(archive, unpacked)
            name.endsWith(".tar") || name.endsWith(".tar.gz") || name.endsWith(".tgz") -> {
                val tar = runCommand(listOf("tar", "-xf", archive.toString(), "-C", unpacked.toString()), repoRoot)
                if (tar.exitCode != 0) return SourceFetchResult.Failed("tar extraction failed: ${tar.output.take(300)}")
            }
            else -> return SourceFetchResult.Unsupported("unsupported archive format: ${archive.fileName}")
        }
        val tree = treeWriter.materialize(unpacked)
        return SourceFetchResult.Fetched(receipt(binding, archive.toString(), actual, tree))
    }

    private fun fetchHttpBundle(binding: SourceBinding): SourceFetchResult {
        val expected = binding.expectedSha256
            ?: return SourceFetchResult.Unsupported("http_bundle requires expectedSha256")
        val targetName = runCatching {
            Path.of(URI.create(binding.uri).path).fileName?.toString()?.takeIf { it.isNotBlank() }
        }.getOrNull() ?: "bundle"
        val target = temporaryDir("http-bundle-").resolve(targetName)
        return try {
            val request = HttpRequest.newBuilder(URI.create(binding.uri)).GET().build()
            val response = HttpClient.newHttpClient().send(request, HttpResponse.BodyHandlers.ofByteArray())
            if (response.statusCode() !in 200..299) {
                return SourceFetchResult.Failed("http_bundle fetch failed: status=${response.statusCode()}")
            }
            Files.write(target, response.body())
            val actual = sha256(target)
            if (!actual.equals(expected, ignoreCase = true)) {
                return SourceFetchResult.Failed("http_bundle hash mismatch: expected=$expected observed=$actual")
            }
            fetchArchive(binding.copy(kind = SourceBindingKind.ARCHIVE, uri = target.toString(), expectedSha256 = expected))
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

    private fun receipt(binding: SourceBinding, repo: String, ref: String, tree: FetchTree): FetchReceipt =
        FetchReceipt(
            id = "fetch-${UUID.randomUUID().toString().take(12)}",
            bindingKind = binding.kind,
            repository = repo,
            ref = ref,
            treeHash = tree.treeHash,
            contentRoot = tree.root,
            paths = tree.paths,
            fetchedAt = Instant.now()
        )

    private fun temporaryDir(prefix: String): Path =
        Files.createTempDirectory(repoRoot.resolve(".atropos/source-bindings").also { Files.createDirectories(it) }, prefix)

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
        return try {
            val process = ProcessBuilder(command)
                .directory(cwd.toFile())
                .redirectErrorStream(true)
                .start()
            val finished = process.waitFor(30, java.util.concurrent.TimeUnit.SECONDS)
            if (!finished) {
                process.destroyForcibly()
                return CommandResult(124, "${command.first()} timed out")
            }
            CommandResult(process.exitValue(), process.inputStream.readAllBytes().toString(StandardCharsets.UTF_8).trim())
        } catch (e: Exception) {
            CommandResult(1, e.message ?: e.javaClass.simpleName)
        }
    }

    private data class CommandResult(val exitCode: Int, val output: String)
}
