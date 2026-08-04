/* SPDX-License-Identifier: AGPL-3.0-only */
package atropos.bridge.http

import java.io.BufferedReader
import java.io.StringReader
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull
import kotlin.test.assertTrue

class HttpRequestParserTest {

    private fun parse(raw: String, parser: HttpRequestParser = HttpRequestParser()) =
        parser.parse(BufferedReader(StringReader(raw)))

    @Test
    fun `parses method path query and lower-cased headers`() {
        val request = parse(
            "GET /v1/answers?provider=groq&depth=2 HTTP/1.1\r\n" +
                "Host: 127.0.0.1\r\n" +
                "ACCEPT: application/json\r\n" +
                "\r\n"
        )

        requireNotNull(request)
        assertEquals("GET", request.method)
        assertEquals("/v1/answers", request.path)
        assertEquals(mapOf("provider" to "groq", "depth" to "2"), request.query)
        assertEquals("application/json", request.header("Accept"))
        assertEquals("application/json", request.header("accept"))
    }

    @Test
    fun `reads a body bounded by content length`() {
        val request = parse(
            "POST /v1/answers HTTP/1.1\r\n" +
                "Content-Length: 7\r\n" +
                "\r\n" +
                "abcdefg"
        )

        assertEquals("abcdefg", requireNotNull(request).body)
    }

    @Test
    fun `refuses a body larger than the bound rather than truncating it`() {
        val parser = HttpRequestParser(maxBodyBytes = 4)

        val request = parse(
            "POST /v1/answers HTTP/1.1\r\nContent-Length: 64\r\n\r\n",
            parser
        )

        assertNull(request, "an over-long body must be refused, not silently cut")
    }

    @Test
    fun `refuses an unbounded header sequence`() {
        val parser = HttpRequestParser(maxHeaderCount = 2)
        val headers = (1..40).joinToString("") { "X-Pad-$it: v\r\n" }

        assertNull(parse("GET /v1/health HTTP/1.1\r\n$headers\r\n", parser))
    }

    @Test
    fun `refuses a truncated request instead of guessing`() {
        assertNull(parse("GET /v1/health HTTP/1.1\r\n"))
        assertNull(parse("GARBAGE\r\n\r\n"))
    }

    @Test
    fun `percent-decodes the path and query`() {
        val request = parse("GET /v1/a%20b?q=one%20two HTTP/1.1\r\n\r\n")

        requireNotNull(request)
        assertEquals("/v1/a b", request.path)
        assertEquals("one two", request.query["q"])
    }

    @Test
    fun `detects an event-stream request`() {
        val request = parse(
            "GET /v1/answers HTTP/1.1\r\nAccept: text/event-stream\r\n\r\n"
        )

        assertTrue(requireNotNull(request).wantsEventStream())
    }
}
