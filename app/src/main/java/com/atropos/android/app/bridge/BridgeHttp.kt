/* SPDX-License-Identifier: AGPL-3.0-only */
package com.atropos.android.app.bridge

import java.io.BufferedReader
import java.net.HttpURLConnection
import java.net.URL

/** What a bridge call returned, or why it could not be made. */
sealed class BridgeResult {
    data class Ok(val body: String) : BridgeResult()
    data class HttpError(val code: Int, val body: String) : BridgeResult()
    data class Unreachable(val reason: String) : BridgeResult()
}

/**
 * The only place this app performs network I/O.
 *
 * Plain HttpURLConnection rather than a client library: the entire surface is
 * a handful of loopback calls, and an HTTP stack would be a dependency, a
 * proguard surface and an update obligation for no benefit at this size.
 *
 * Timeouts are short on purpose. The engine is on the same device, so a call
 * that has not answered in two seconds is not slow, it is absent, and the UI
 * needs to say "offline" rather than hang.
 */
object BridgeHttp {
    private const val CONNECT_TIMEOUT_MS = 1_500
    private const val READ_TIMEOUT_MS = 20_000

    fun get(url: String): BridgeResult = call(url, "GET", null, READ_TIMEOUT_MS)

    fun post(url: String, body: String, readTimeoutMs: Int = READ_TIMEOUT_MS): BridgeResult =
        call(url, "POST", body, readTimeoutMs)

    private fun call(url: String, method: String, body: String?, readTimeoutMs: Int): BridgeResult {
        var connection: HttpURLConnection? = null
        return try {
            connection = (URL(url).openConnection() as HttpURLConnection).apply {
                requestMethod = method
                connectTimeout = CONNECT_TIMEOUT_MS
                readTimeout = readTimeoutMs
                setRequestProperty("Accept", "application/json")
                if (body != null) {
                    doOutput = true
                    setRequestProperty("Content-Type", "application/json; charset=utf-8")
                }
            }
            if (body != null) {
                connection.outputStream.use { it.write(body.toByteArray(Charsets.UTF_8)) }
            }
            val code = connection.responseCode
            val stream = if (code in 200..299) connection.inputStream else connection.errorStream
            val text = stream?.bufferedReader()?.use(BufferedReader::readText).orEmpty()
            if (code in 200..299) BridgeResult.Ok(text) else BridgeResult.HttpError(code, text)
        } catch (failure: Exception) {
            // Every failure to reach a loopback port is the same fact to the
            // operator: the engine is not running. The exception class is kept
            // for the log, not shown as a diagnosis.
            BridgeResult.Unreachable(failure.javaClass.simpleName)
        } finally {
            connection?.disconnect()
        }
    }
}
