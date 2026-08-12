/* SPDX-License-Identifier: AGPL-3.0-only */
package atropos.bridge

import atropos.bridge.http.HttpRequest
import java.nio.file.Files
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

            // Verify file exists on disk under uploads directory
            val expectedPath = tempDir.resolve(".atropos/uploads/s1/hello.txt")
            assertTrue(Files.isRegularFile(expectedPath))
            assertEquals(fileContent, Files.readString(expectedPath))

            // 3. List uploads for the session
            val responseList = handler.list(
                HttpRequest("GET", "/v1/files", mapOf("session" to "s1"), emptyMap(), "")
            )
            assertEquals(200, responseList.status)
            assertTrue(responseList.body.contains("\"count\":1"))
            assertTrue(responseList.body.contains("hello.txt"))

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
}
