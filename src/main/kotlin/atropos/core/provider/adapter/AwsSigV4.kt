/* SPDX-License-Identifier: AGPL-3.0-only */
package atropos.core.provider.adapter

import java.net.URI
import java.nio.charset.StandardCharsets
import java.security.MessageDigest
import java.time.Instant
import java.time.ZoneOffset
import java.time.format.DateTimeFormatter
import javax.crypto.Mac
import javax.crypto.spec.SecretKeySpec

data class AwsSigV4Credentials(
    val accessKeyId: String,
    val secretAccessKey: String,
    val sessionToken: String? = null
)

/** Pure AWS Signature Version 4 request signer; no credentials are persisted. */
object AwsSigV4 {
    private val timestampFormat = DateTimeFormatter.ofPattern("yyyyMMdd'T'HHmmss'Z'")
        .withZone(ZoneOffset.UTC)

    fun sign(
        method: String,
        uri: URI,
        body: String,
        credentials: AwsSigV4Credentials,
        region: String,
        service: String = "bedrock",
        now: Instant = Instant.now()
    ): Map<String, String> {
        require(method.uppercase() == "POST") { "Bedrock signer supports POST only" }
        require(region.matches(Regex("[a-z0-9-]{3,32}"))) { "AWS region is invalid" }
        require(service.matches(Regex("[a-z0-9-]{2,32}"))) { "AWS service is invalid" }
        require(credentials.accessKeyId.isNotBlank() && credentials.secretAccessKey.isNotBlank()) {
            "AWS credentials are required"
        }
        val amzDate = timestampFormat.format(now)
        val date = amzDate.substring(0, 8)
        val payloadHash = sha256(body)
        val host = uri.authority ?: error("AWS request URI has no authority")
        val canonicalHeaders = buildString {
            append("host:").append(host).append('\n')
            append("x-amz-content-sha256:").append(payloadHash).append('\n')
            append("x-amz-date:").append(amzDate).append('\n')
            if (!credentials.sessionToken.isNullOrBlank()) {
                append("x-amz-security-token:").append(credentials.sessionToken).append('\n')
            }
        }
        val signedHeaders = buildString {
            append("host;x-amz-content-sha256;x-amz-date")
            if (!credentials.sessionToken.isNullOrBlank()) append(";x-amz-security-token")
        }
        val canonicalRequest = listOf(
            method.uppercase(),
            uri.rawPath.ifBlank { "/" },
            uri.rawQuery.orEmpty(),
            canonicalHeaders,
            signedHeaders,
            payloadHash
        ).joinToString("\n")
        val scope = "$date/$region/$service/aws4_request"
        val stringToSign = listOf(
            "AWS4-HMAC-SHA256", amzDate, scope, sha256(canonicalRequest)
        ).joinToString("\n")
        val signingKey = hmac(
            hmac(hmac(hmac("AWS4${credentials.secretAccessKey}".toByteArray(StandardCharsets.UTF_8), date), region), service),
            "aws4_request"
        )
        val signature = hmacHex(signingKey, stringToSign)
        val headers = linkedMapOf(
            "Host" to host,
            "x-amz-date" to amzDate,
            "x-amz-content-sha256" to payloadHash,
            "Authorization" to "AWS4-HMAC-SHA256 Credential=${credentials.accessKeyId}/$scope, SignedHeaders=$signedHeaders, Signature=$signature"
        )
        credentials.sessionToken?.takeIf(String::isNotBlank)?.let { headers["x-amz-security-token"] = it }
        return headers
    }

    private fun sha256(value: String): String = MessageDigest.getInstance("SHA-256")
        .digest(value.toByteArray(StandardCharsets.UTF_8))
        .joinToString("") { "%02x".format(it) }

    private fun hmac(key: ByteArray, value: String): ByteArray = Mac.getInstance("HmacSHA256")
        .apply { init(SecretKeySpec(key, "HmacSHA256")) }
        .doFinal(value.toByteArray(StandardCharsets.UTF_8))

    private fun hmacHex(key: ByteArray, value: String): String = hmac(key, value)
        .joinToString("") { "%02x".format(it) }
}
