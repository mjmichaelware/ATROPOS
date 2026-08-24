package atropos.core.provider.adapter

import java.net.URI
import java.time.Instant
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test

class AwsSigV4Test {
    @Test
    fun `signer emits deterministic bedrock authorization without exposing secret`() {
        val headers = AwsSigV4.sign(
            method = "POST",
            uri = URI("https://bedrock-runtime.us-east-1.amazonaws.com/model/test/converse"),
            body = "{\"messages\":[]}",
            credentials = AwsSigV4Credentials("AKID", "SECRET"),
            region = "us-east-1",
            now = Instant.parse("2024-01-02T03:04:05Z")
        )

        assertTrue(headers.getValue("Authorization").contains("us-east-1/bedrock/aws4_request"))
        assertTrue(headers.getValue("Authorization").contains("SignedHeaders=host;x-amz-content-sha256;x-amz-date"))
        assertEquals(null, headers.values.firstOrNull { it.contains("SECRET") })
        assertEquals("20240102T030405Z", headers.getValue("x-amz-date"))
    }
}
