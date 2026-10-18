/* SPDX-License-Identifier: AGPL-3.0-only */
package atropos.core.provider

import org.json.JSONObject
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class UniversalRequestAdapterTest {

    @Test
    fun translates_groq_to_native_openai_compatible_schema() {
        val req = UniversalRequestAdapter.translate(
            providerId = "groq",
            prompt = "fix syntax error",
            context = "Kotlin 1.9 file context",
            apiKey = "gsk-mock-key"
        )

        assertEquals(ProviderNativeSchema.OPENAI_COMPATIBLE, req.schemaType)
        assertTrue(req.endpoint.contains("api.groq.com"))
        assertEquals("Bearer gsk-mock-key", req.headers["Authorization"])

        val json = JSONObject(req.jsonPayload)
        assertTrue(json.has("model"))
        val messages = json.getJSONArray("messages")
        assertEquals(2, messages.length())
        assertEquals("system", messages.getJSONObject(0).getString("role"))
        assertEquals("Kotlin 1.9 file context", messages.getJSONObject(0).getString("content"))
        assertEquals("user", messages.getJSONObject(1).getString("role"))
        assertEquals("fix syntax error", messages.getJSONObject(1).getString("content"))
    }

    @Test
    fun translates_openrouter_with_required_metadata_headers() {
        val req = UniversalRequestAdapter.translate(
            providerId = "openrouter",
            prompt = "summarize AST",
            context = "project root",
            apiKey = "sk-or-mock"
        )

        assertEquals(ProviderNativeSchema.OPENROUTER, req.schemaType)
        assertEquals("https://openrouter.ai/api/v1/chat/completions", req.endpoint)
        assertEquals("https://local.atropos.invalid", req.headers["HTTP-Referer"])
        assertEquals("ATROPOS", req.headers["X-Title"])
        assertEquals("Bearer sk-or-mock", req.headers["Authorization"])
    }

    @Test
    fun translates_gemini_to_native_system_instruction_and_contents_schema() {
        val req = UniversalRequestAdapter.translate(
            providerId = "gemini",
            prompt = "optimize memory",
            context = "MemoryStore active partition",
            apiKey = "AIzaMock"
        )

        assertEquals(ProviderNativeSchema.GEMINI_REST, req.schemaType)
        assertTrue(req.endpoint.contains("generativelanguage.googleapis.com"))
        assertTrue(req.endpoint.contains("AIzaMock"))

        val json = JSONObject(req.jsonPayload)
        assertTrue(json.has("systemInstruction"))
        val sysParts = json.getJSONObject("systemInstruction").getJSONArray("parts")
        assertEquals("MemoryStore active partition", sysParts.getJSONObject(0).getString("text"))

        assertTrue(json.has("contents"))
        val contents = json.getJSONArray("contents")
        assertEquals("user", contents.getJSONObject(0).getString("role"))
        val userParts = contents.getJSONObject(0).getJSONArray("parts")
        assertEquals("optimize memory", userParts.getJSONObject(0).getString("text"))
    }

    @Test
    fun translates_anthropic_with_top_level_system_and_no_system_in_messages() {
        val req = UniversalRequestAdapter.translate(
            providerId = "anthropic",
            prompt = "repair wave 4",
            context = "compiler stack trace",
            apiKey = "sk-ant-mock"
        )

        assertEquals(ProviderNativeSchema.ANTHROPIC_MESSAGES, req.schemaType)
        assertEquals("sk-ant-mock", req.headers["x-api-key"])
        assertEquals("2023-06-01", req.headers["anthropic-version"])

        val json = JSONObject(req.jsonPayload)
        assertEquals("compiler stack trace", json.getString("system"))
        assertEquals(4096, json.getInt("max_tokens"))

        val messages = json.getJSONArray("messages")
        assertEquals(1, messages.length())
        assertEquals("user", messages.getJSONObject(0).getString("role"))
        assertEquals("repair wave 4", messages.getJSONObject(0).getString("content"))

        // Assert no system message inside messages array (strictly forbidden by native Anthropic API)
        for (i in 0 until messages.length()) {
            assertFalse(messages.getJSONObject(i).getString("role") == "system")
        }
    }

    @Test
    fun parses_extracted_content_across_native_response_schemas() {
        val openAiJson = """{"choices":[{"message":{"content":"fun fix() {}"}}]}"""
        assertEquals("fun fix() {}", UniversalRequestAdapter.extractResponseContent("groq", openAiJson, ProviderNativeSchema.OPENAI_COMPATIBLE))

        val geminiJson = """{"candidates":[{"content":{"parts":[{"text":"class Generated"}]}}]}"""
        assertEquals("class Generated", UniversalRequestAdapter.extractResponseContent("gemini", geminiJson, ProviderNativeSchema.GEMINI_REST))

        val anthropicJson = """{"content":[{"text":"val x = 42"}]}"""
        assertEquals("val x = 42", UniversalRequestAdapter.extractResponseContent("anthropic", anthropicJson, ProviderNativeSchema.ANTHROPIC_MESSAGES))

        val ollamaJson = """{"response":"val local = true"}"""
        assertEquals("val local = true", UniversalRequestAdapter.extractResponseContent("ollama", ollamaJson, ProviderNativeSchema.OLLAMA_GENERATE))
    }
}
