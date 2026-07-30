package atropos.core.provider

import com.sun.net.httpserver.HttpServer
import atropos.core.policy.BoundedProcessRunner
import java.net.InetSocketAddress
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertIs
import kotlin.test.assertTrue
import java.nio.file.Files
import java.util.zip.ZipEntry
import java.util.zip.ZipOutputStream

class SourceBindingContextPackerTest {
    @Test
    fun git_fetch_uses_literal_bounded_argv_and_refuses_nonzero_result() {
        val root = Files.createTempDirectory("atropos-source-git-command-")
        Files.createDirectories(root.resolve(".git"))
        val commands = mutableListOf<List<String>>()
        val bounded = BoundedProcessRunner { command, _, _, _ ->
            commands += command
            ProcessBuilder("false").start()
        }

        val result = SourceBindingFetcher(repoRoot = root, processRunner = bounded).fetch(
            SourceBinding.git("https://example.invalid/repo; touch SHOULD_NOT_RUN", "main")
        )

        val failure = assertIs<SourceFetchResult.Failed>(result)
        assertTrue(commands.isNotEmpty())
        assertEquals("git", commands.first().first())
        assertEquals("https://example.invalid/repo; touch SHOULD_NOT_RUN", commands.first().last { it.contains("example.invalid") })
        assertTrue(failure.reason.contains("git clone failed"))
    }

    @Test
    fun localPathBindingYieldsContentAddressedRedactedPack() {
        val root = Files.createTempDirectory("atropos-source-local-")
        Files.createDirectories(root.resolve("src/main/kotlin/atropos/core/agent"))
        Files.writeString(
            root.resolve("src/main/kotlin/atropos/core/agent/Sample.kt"),
            "package sample\nval api_key=\"secret-value\"\n"
        )

        val packer = CodebaseContextPacker(repoRoot = root)
        val result = packer.pack(
            SourcePackRequest(
                binding = SourceBinding.localPath(root),
                allowedPaths = listOf("src/main/kotlin/atropos/core/agent")
            )
        )

        val pack = assertIs<SourcePackResult.Packed>(result).pack
        assertEquals(SourceBindingKind.LOCAL_PATH, pack.fetchReceipt.bindingKind)
        assertTrue(pack.id.startsWith("pack-"))
        assertTrue(pack.fetchReceipt.treeHash.isNotBlank())
        assertTrue(pack.text.contains("SOURCE_PACK_ID=${pack.id}"))
        assertTrue(pack.text.contains("FETCH_RECEIPT_ID=${pack.fetchReceipt.id}"))
        assertTrue(pack.text.contains("FILE src/main/kotlin/atropos/core/agent/Sample.kt"))
        assertTrue(pack.text.contains("<redacted:secret>"), pack.text)
        assertTrue(!pack.text.contains("secret-value"), pack.text)
        assertTrue(pack.hasValidContentHash(), pack.text)
    }

    @Test
    fun content_hash_placeholder_keeps_final_pack_within_requested_byte_bound() {
        val root = Files.createTempDirectory("atropos-source-pack-bound-")
        Files.createDirectories(root.resolve("src/main/kotlin/atropos/core"))
        Files.writeString(root.resolve("src/main/kotlin/atropos/core/Bound.kt"), "package bound\nobject Bound\n")

        val result = CodebaseContextPacker(repoRoot = root).pack(
            SourcePackRequest(
                binding = SourceBinding.localPath(root),
                allowedPaths = listOf("src/main/kotlin/atropos/core"),
                maxBytes = 2_048
            )
        )

        val pack = assertIs<SourcePackResult.Packed>(result).pack
        assertTrue(pack.byteCount <= 2_048, "pack exceeded bound: ${pack.byteCount}")
        assertTrue(pack.text.contains("PACK_CONTENT_HASH=${pack.contentHash}"), pack.text)
        assertTrue(!pack.text.contains("PACK_CONTENT_HASH=${"0".repeat(64)}"), pack.text)
        assertTrue(pack.hasValidContentHash(), pack.text)
    }

    @Test
    fun contextPackIntegrityRejectsTamperedBytes() {
        val root = Files.createTempDirectory("atropos-source-pack-integrity-")
        Files.createDirectories(root.resolve("src"))
        Files.writeString(root.resolve("src/Valid.kt"), "package valid\nobject Valid\n")
        val packed = assertIs<SourcePackResult.Packed>(
            CodebaseContextPacker(repoRoot = root).pack(
                SourcePackRequest(SourceBinding.localPath(root), listOf("src"))
            )
        ).pack

        assertTrue(packed.hasValidContentHash())
        val tampered = packed.copy(text = packed.text.replace("object Valid", "object Tampered"))
        assertTrue(!tampered.hasValidContentHash())
    }

    @Test
    fun contextPackRefusesInvalidBudgetsAndTraversalTerritory() {
        val root = Files.createTempDirectory("atropos-source-pack-invalid-request-")
        Files.createDirectories(root.resolve("src"))
        Files.writeString(root.resolve("src/Valid.kt"), "package valid\nobject Valid\n")
        val packer = CodebaseContextPacker(repoRoot = root)
        val binding = SourceBinding.localPath(root)

        val zeroBytes = packer.pack(SourcePackRequest(binding, listOf("src"), maxBytes = 0))
        assertTrue(zeroBytes is SourcePackResult.Refused)
        assertTrue((zeroBytes as SourcePackResult.Refused).reason.contains("maxBytes"))

        val zeroFileBytes = packer.pack(SourcePackRequest(binding, listOf("src"), maxFileBytes = 0))
        assertTrue(zeroFileBytes is SourcePackResult.Refused)
        assertTrue((zeroFileBytes as SourcePackResult.Refused).reason.contains("maxFileBytes"))

        val traversal = packer.pack(SourcePackRequest(binding, listOf("../src")))
        assertTrue(traversal is SourcePackResult.Refused)
        assertTrue((traversal as SourcePackResult.Refused).reason.contains("bound source tree"))

        val absolute = packer.pack(SourcePackRequest(binding, listOf("/src")))
        assertTrue(absolute is SourcePackResult.Refused)
        assertTrue((absolute as SourcePackResult.Refused).reason.contains("bound source tree"))
    }

    @Test
    fun localPathBindingDoesNotPackSymlinkTargetsOutsideSourceRoot() {
        val root = Files.createTempDirectory("atropos-source-symlink-")
        val outside = Files.createTempDirectory("atropos-source-outside-")
        Files.createDirectories(root.resolve("src/main/kotlin/atropos/core/provider"))
        Files.writeString(
            outside.resolve("ExternalSecret.kt"),
            "package leaked\nval token = \"sk-outside-secret-value\"\n"
        )
        val link = root.resolve("src/main/kotlin/atropos/core/provider/LinkedSecret.kt")
        runCatching { Files.createSymbolicLink(link, outside.resolve("ExternalSecret.kt")) }
            .getOrElse { return }
        Files.writeString(
            root.resolve("src/main/kotlin/atropos/core/provider/RealPack.kt"),
            "package pack\nobject RealPack\n"
        )

        val packer = CodebaseContextPacker(repoRoot = root)
        val result = packer.pack(
            SourcePackRequest(
                binding = SourceBinding.localPath(root),
                allowedPaths = listOf("src/main/kotlin/atropos/core/provider")
            )
        )

        val pack = assertIs<SourcePackResult.Packed>(result).pack
        assertTrue(pack.text.contains("FILE src/main/kotlin/atropos/core/provider/RealPack.kt"), pack.text)
        assertTrue(!pack.fetchReceipt.paths.contains("src/main/kotlin/atropos/core/provider/LinkedSecret.kt"))
        assertTrue(!pack.text.contains("LinkedSecret.kt"), pack.text)
        assertTrue(!pack.text.contains("outside-secret-value"), pack.text)
    }

    @Test
    fun archiveBindingIsHashPinnedAndOriginAgnostic() {
        val root = Files.createTempDirectory("atropos-source-archive-")
        val archive = root.resolve("bundle.zip")
        ZipOutputStream(Files.newOutputStream(archive)).use { zip ->
            zip.putNextEntry(ZipEntry("src/main/kotlin/atropos/core/provider/Pack.kt"))
            zip.write("package pack\nobject Pack\n".toByteArray())
            zip.closeEntry()
        }
        val hash = sha256(archive)
        val packer = CodebaseContextPacker(repoRoot = root)

        val packed = packer.pack(
            SourcePackRequest(
                binding = SourceBinding.archive(archive, expectedSha256 = hash),
                allowedPaths = listOf("src/main/kotlin/atropos/core/provider")
            )
        )

        val pack = assertIs<SourcePackResult.Packed>(packed).pack
        assertEquals(SourceBindingKind.ARCHIVE, pack.fetchReceipt.bindingKind)
        assertEquals(hash, pack.fetchReceipt.ref)
        assertTrue(pack.text.contains("FILE src/main/kotlin/atropos/core/provider/Pack.kt"))

        val refused = packer.pack(
            SourcePackRequest(
                binding = SourceBinding.archive(archive, expectedSha256 = "0".repeat(64)),
                allowedPaths = listOf("src/main/kotlin/atropos/core/provider")
            )
        )
        assertIs<SourcePackResult.Refused>(refused)
    }

    @Test
    fun gitBindingUsesAnyGitRemoteAndYieldsAContentAddressedPack() {
        val root = Files.createTempDirectory("atropos-source-git-")
        val source = root.resolve("source-repo")
        Files.createDirectories(source.resolve("src/main/kotlin/atropos/core/provider"))
        Files.writeString(
            source.resolve("src/main/kotlin/atropos/core/provider/GitPack.kt"),
            "package pack\nobject GitPack\n"
        )
        git(source, "init")
        git(source, "config", "user.email", "atropos@example.invalid")
        git(source, "config", "user.name", "ATROPOS Test")
        git(source, "add", ".")
        git(source, "commit", "-m", "seed")
        val commit = git(source, "rev-parse", "HEAD").trim()
        val packer = CodebaseContextPacker(repoRoot = root)

        val result = packer.pack(
            SourcePackRequest(
                binding = SourceBinding.git(source.toString(), ref = "HEAD"),
                allowedPaths = listOf("src/main/kotlin/atropos/core/provider")
            )
        )

        val pack = assertIs<SourcePackResult.Packed>(result).pack
        assertEquals(SourceBindingKind.GIT, pack.fetchReceipt.bindingKind)
        assertEquals(commit, pack.fetchReceipt.ref)
        assertTrue(pack.fetchReceipt.repository.endsWith("source-repo"), pack.fetchReceipt.repository)
        assertTrue(pack.text.contains("FILE src/main/kotlin/atropos/core/provider/GitPack.kt"), pack.text)
    }

    @Test
    fun gitBindingWithoutRefUsesRemoteDefaultBranch() {
        val root = Files.createTempDirectory("atropos-source-git-default-")
        val source = root.resolve("source-repo")
        val bare = root.resolve("source.git")
        Files.createDirectories(source.resolve("src/main/kotlin/atropos/core/provider"))
        Files.writeString(
            source.resolve("src/main/kotlin/atropos/core/provider/DefaultBranchPack.kt"),
            "package pack\nobject DefaultBranchPack\n"
        )
        git(source, "init")
        git(source, "config", "user.email", "atropos@example.invalid")
        git(source, "config", "user.name", "ATROPOS Test")
        git(source, "add", ".")
        git(source, "commit", "-m", "seed")
        git(root, "clone", "--bare", source.toString(), bare.toString())
        val commit = git(source, "rev-parse", "HEAD").trim()
        val packer = CodebaseContextPacker(repoRoot = root)

        val result = packer.pack(
            SourcePackRequest(
                binding = SourceBinding.git(bare.toString()),
                allowedPaths = listOf("src/main/kotlin/atropos/core/provider")
            )
        )

        val pack = assertIs<SourcePackResult.Packed>(result).pack
        assertEquals(SourceBindingKind.GIT, pack.fetchReceipt.bindingKind)
        assertEquals(commit, pack.fetchReceipt.ref)
        assertTrue(pack.text.contains("FILE src/main/kotlin/atropos/core/provider/DefaultBranchPack.kt"), pack.text)
    }

    @Test
    fun httpBundleRequiresHashPin() {
        val binding = SourceBinding(SourceBindingKind.HTTP_BUNDLE, "https://example.invalid/bundle.zip")
        val fetcher = SourceBindingFetcher(Files.createTempDirectory("atropos-source-http-"))

        val result = fetcher.fetch(binding)

        val unsupported = assertIs<SourceFetchResult.Unsupported>(result)
        assertTrue(unsupported.reason.contains("expectedSha256"))
    }

    @Test
    fun httpBundleBindingIsHashPinnedAndFeedsTheSameContextPacker() {
        val root = Files.createTempDirectory("atropos-source-http-bundle-")
        val archive = root.resolve("bundle.zip")
        ZipOutputStream(Files.newOutputStream(archive)).use { zip ->
            zip.putNextEntry(ZipEntry("src/main/kotlin/atropos/core/agent/HttpPack.kt"))
            zip.write("package pack\nobject HttpPack\n".toByteArray())
            zip.closeEntry()
        }
        val hash = sha256(archive)
        val server = HttpServer.create(InetSocketAddress("127.0.0.1", 0), 0)
        server.createContext("/bundle.zip") { exchange ->
            val bytes = Files.readAllBytes(archive)
            exchange.sendResponseHeaders(200, bytes.size.toLong())
            exchange.responseBody.use { it.write(bytes) }
        }
        server.start()
        try {
            val uri = "http://127.0.0.1:${server.address.port}/bundle.zip"
            val packer = CodebaseContextPacker(repoRoot = root)

            val result = packer.pack(
                SourcePackRequest(
                    binding = SourceBinding.httpBundle(uri, expectedSha256 = hash),
                    allowedPaths = listOf("src/main/kotlin/atropos/core/agent")
                )
            )

            val pack = assertIs<SourcePackResult.Packed>(result).pack
            assertEquals(SourceBindingKind.HTTP_BUNDLE, pack.fetchReceipt.bindingKind)
            assertEquals(uri, pack.fetchReceipt.repository)
            assertEquals(hash, pack.fetchReceipt.ref)
            assertTrue(pack.text.contains("FILE src/main/kotlin/atropos/core/agent/HttpPack.kt"))
        } finally {
            server.stop(0)
        }
    }

    @Test
    fun httpBundleReceiptRedactsCredentialBearingOrigin() {
        val root = Files.createTempDirectory("atropos-source-http-credentials-")
        val archive = root.resolve("bundle.zip")
        ZipOutputStream(Files.newOutputStream(archive)).use { zip ->
            zip.putNextEntry(ZipEntry("src/Provider.kt"))
            zip.write("package pack\nobject Provider\n".toByteArray())
            zip.closeEntry()
        }
        val hash = sha256(archive)
        val server = HttpServer.create(InetSocketAddress("127.0.0.1", 0), 0)
        server.createContext("/bundle.zip") { exchange ->
            val bytes = Files.readAllBytes(archive)
            exchange.sendResponseHeaders(200, bytes.size.toLong())
            exchange.responseBody.use { it.write(bytes) }
        }
        server.start()
        try {
            val publicUri = "http://127.0.0.1:${server.address.port}/bundle.zip"
            val credentialUri = "http://pack-user:super-secret@127.0.0.1:${server.address.port}/bundle.zip"
            val result = CodebaseContextPacker(repoRoot = root).pack(
                SourcePackRequest(
                    binding = SourceBinding.httpBundle(credentialUri, expectedSha256 = hash),
                    allowedPaths = listOf("src")
                )
            )

            val pack = assertIs<SourcePackResult.Packed>(result).pack
            assertEquals(publicUri, pack.fetchReceipt.repository)
            assertTrue(!pack.fetchReceipt.repository.contains("pack-user"))
            assertTrue(!pack.fetchReceipt.repository.contains("super-secret"))
            assertTrue(pack.text.contains("FILE src/Provider.kt"))
        } finally {
            server.stop(0)
        }
    }

    @Test
    fun archiveBindingReturnsTypedFailureWhenZipEntryEscapesTree() {
        val root = Files.createTempDirectory("atropos-source-zip-slip-")
        val archive = root.resolve("evil.zip")
        ZipOutputStream(Files.newOutputStream(archive)).use { zip ->
            zip.putNextEntry(ZipEntry("../escape.kt"))
            zip.write("package escape\nobject Escape\n".toByteArray())
            zip.closeEntry()
        }
        val hash = sha256(archive)
        val fetcher = SourceBindingFetcher(root)

        val result = fetcher.fetch(SourceBinding.archive(archive, expectedSha256 = hash))

        val failed = assertIs<SourceFetchResult.Failed>(result)
        assertTrue(failed.reason.contains("zip extraction failed"), failed.reason)
    }

    @Test
    fun archiveBindingReturnsTypedFailureWhenTarEntryEscapesTree() {
        val root = Files.createTempDirectory("atropos-source-tar-slip-")
        val archive = root.resolve("evil.tar")
        writeSingleFileTar(archive, "../escape.kt", "package escape\n")
        val hash = sha256(archive)
        val fetcher = SourceBindingFetcher(root)

        val result = fetcher.fetch(SourceBinding.archive(archive, expectedSha256 = hash))

        val failed = assertIs<SourceFetchResult.Failed>(result)
        assertTrue(failed.reason.contains("tar extraction refused"), failed.reason)
    }

    @Test
    fun contextPackTruncationPreservesUtf8AndByteLimit() {
        val root = Files.createTempDirectory("atropos-source-utf8-")
        Files.createDirectories(root.resolve("src/main/kotlin/atropos/core/provider"))
        Files.writeString(
            root.resolve("src/main/kotlin/atropos/core/provider/Utf8Pack.kt"),
            "package pack\nval text = \"phase11-self-host-🚀-context-".repeat(200)
        )
        val packer = CodebaseContextPacker(repoRoot = root)

        val packed = packer.pack(
            SourcePackRequest(
                binding = SourceBinding.localPath(root),
                allowedPaths = listOf("src/main/kotlin/atropos/core/provider"),
                maxBytes = 420,
                maxFileBytes = 4_096
            )
        )

        val pack = assertIs<SourcePackResult.Packed>(packed).pack
        assertTrue(pack.truncated)
        assertTrue(pack.byteCount <= 420, "pack byte count exceeded limit: ${pack.byteCount}")
        assertTrue(!pack.text.contains('\uFFFD'), pack.text)
    }

    @Test
    fun contextPackRefusesWhenBudgetCannotFitAnyFileSection() {
        val root = Files.createTempDirectory("atropos-source-too-small-")
        Files.createDirectories(root.resolve("src/main/kotlin/atropos/core/provider"))
        Files.writeString(root.resolve("src/main/kotlin/atropos/core/provider/Tiny.kt"), "package tiny\n")
        val packer = CodebaseContextPacker(repoRoot = root)

        val result = packer.pack(
            SourcePackRequest(
                binding = SourceBinding.localPath(root),
                allowedPaths = listOf("src/main/kotlin/atropos/core/provider"),
                maxBytes = 96
            )
        )

        val refused = assertIs<SourcePackResult.Refused>(result)
        assertTrue(refused.reason.contains("no readable files"), refused.reason)
    }

    private fun sha256(path: java.nio.file.Path): String {
        val digest = java.security.MessageDigest.getInstance("SHA-256")
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

    private fun writeSingleFileTar(path: java.nio.file.Path, entryName: String, content: String) {
        val bytes = content.toByteArray()
        val header = ByteArray(512) { 0 }
        fun writeAscii(offset: Int, length: Int, value: String) {
            val raw = value.toByteArray()
            raw.copyInto(header, offset, endIndex = raw.size.coerceAtMost(length))
        }
        writeAscii(0, 100, entryName)
        writeAscii(100, 8, "0000644")
        writeAscii(108, 8, "0000000")
        writeAscii(116, 8, "0000000")
        writeAscii(124, 12, bytes.size.toString(8).padStart(11, '0'))
        writeAscii(136, 12, "00000000000")
        for (i in 148 until 156) header[i] = ' '.code.toByte()
        header[156] = '0'.code.toByte()
        writeAscii(257, 6, "ustar")
        writeAscii(263, 2, "00")
        val checksum = header.sumOf { it.toInt() and 0xff }
        writeAscii(148, 8, checksum.toString(8).padStart(6, '0') + "\u0000 ")
        Files.newOutputStream(path).use { out ->
            out.write(header)
            out.write(bytes)
            val padding = (512 - (bytes.size % 512)) % 512
            if (padding > 0) out.write(ByteArray(padding))
            out.write(ByteArray(1024))
        }
    }

    private fun git(cwd: java.nio.file.Path, vararg args: String): String {
        val process = ProcessBuilder(listOf("git") + args)
            .directory(cwd.toFile())
            .redirectErrorStream(true)
            .start()
        val output = process.inputStream.bufferedReader().readText()
        val exit = process.waitFor()
        check(exit == 0) { "git ${args.joinToString(" ")} failed: $output" }
        return output
    }
}
