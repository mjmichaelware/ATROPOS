package atropos.core.provider

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertIs
import kotlin.test.assertTrue
import java.nio.file.Files
import java.util.zip.ZipEntry
import java.util.zip.ZipOutputStream

class SourceBindingContextPackerTest {
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
    fun httpBundleRequiresHashPin() {
        val binding = SourceBinding(SourceBindingKind.HTTP_BUNDLE, "https://example.invalid/bundle.zip")
        val fetcher = SourceBindingFetcher(Files.createTempDirectory("atropos-source-http-"))

        val result = fetcher.fetch(binding)

        val unsupported = assertIs<SourceFetchResult.Unsupported>(result)
        assertTrue(unsupported.reason.contains("expectedSha256"))
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
}
