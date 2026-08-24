/* SPDX-License-Identifier: AGPL-3.0-only */
package atropos.bridge

import atropos.bridge.http.HttpRequest
import java.nio.file.Files
import java.security.MessageDigest
import java.util.Base64
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue
import kotlin.test.assertFalse

class BridgeFilesHandlerTest {

    @Test
    fun test_file_upload_and_listing_and_security_checks() {
        val tempDir = Files.createTempDirectory("files-test-")
        try {
            val handler = BridgeFilesHandler(repoRoot = tempDir)

            // 1. Invalid session or filename format should be rejected
            val responseBadSession = handler.upload(
                HttpRequest("POST", "/v1/files", mapOf("session" to "bad/session", "filename" to "test.txt"), emptyMap(), "")
            )
            assertEquals(400, responseBadSession.status)

            val responseBadFile = handler.upload(
                HttpRequest("POST", "/v1/files", mapOf("session" to "s1", "filename" to "../test.txt"), emptyMap(), "")
            )
            assertEquals(400, responseBadFile.status)

            val oversized = Base64.getEncoder().encodeToString(ByteArray(512 * 1024 + 1))
            val responseTooLarge = handler.upload(
                HttpRequest("POST", "/v1/files", mapOf("session" to "s1", "filename" to "large.bin"), emptyMap(), oversized)
            )
            assertEquals(413, responseTooLarge.status)
            assertFalse(Files.exists(tempDir.resolve(".atropos/uploads/s1/large.bin")))

            // 2. Successful file upload
            val fileContent = "Hello World Files Handler"
            val base64Content = Base64.getEncoder().encodeToString(fileContent.toByteArray())
            
            val responseOk = handler.upload(
                HttpRequest("POST", "/v1/files", mapOf("session" to "s1", "filename" to "hello.txt"), emptyMap(), base64Content)
            )
            assertEquals(200, responseOk.status)
            assertTrue(responseOk.body.contains("\"ok\":true"))
            assertTrue(responseOk.body.contains("hello.txt"))
            assertTrue(responseOk.body.contains("sha256"))
            assertTrue(responseOk.body.contains("\"attested\":true"))
            assertTrue(responseOk.body.contains("envelopeSha256"))
            val contentSha256 = sha256(fileContent)
            val envelopeSha256 = sha256("s1\nhello.txt\n$contentSha256\n${fileContent.toByteArray().size}")
            assertTrue(responseOk.body.contains("\"sha256\":\"$contentSha256\""))
            assertTrue(responseOk.body.contains("\"envelopeSha256\":\"$envelopeSha256\""))

            val responsePolicyRefused = handler.upload(
                HttpRequest("POST", "/v1/files", mapOf("session" to "s1", "filename" to "blocked.jar"), emptyMap(), base64Content)
            )
            assertEquals(403, responsePolicyRefused.status)
            assertFalse(Files.exists(tempDir.resolve(".atropos/uploads/s1/blocked.jar")))

            // Verify file exists on disk under uploads directory
            val expectedPath = tempDir.resolve(".atropos/uploads/s1/hello.txt")
            assertTrue(Files.isRegularFile(expectedPath))
            assertEquals(fileContent, Files.readString(expectedPath))

            // Externally-created oversized files are not read or exposed by listing.
            val oversizedExisting = tempDir.resolve(".atropos/uploads/s1/oversized.bin")
            Files.write(oversizedExisting, ByteArray(512 * 1024 + 1))

            // 3. List uploads for the session
            val responseList = handler.list(
                HttpRequest("GET", "/v1/files", mapOf("session" to "s1"), emptyMap(), "")
            )
            assertEquals(200, responseList.status)
            assertTrue(responseList.body.contains("\"count\":1"))
            assertTrue(responseList.body.contains("hello.txt"))
            assertFalse(responseList.body.contains("oversized.bin"))

            // 4. List uploads for empty session directory should return count 0
            val responseEmptyList = handler.list(
                HttpRequest("GET", "/v1/files", mapOf("session" to "empty-session"), emptyMap(), "")
            )
            assertEquals(200, responseEmptyList.status)
            assertTrue(responseEmptyList.body.contains("\"count\":0"))
        } finally {
            tempDir.toFile().deleteRecursively()
        }
    }

    private fun sha256(value: String): String = MessageDigest.getInstance("SHA-256")
        .digest(value.toByteArray())
        .joinToString("") { "%02x".format(it) }
}
