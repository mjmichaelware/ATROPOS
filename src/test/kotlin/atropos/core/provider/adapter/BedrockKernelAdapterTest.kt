package atropos.core.provider.adapter

import atropos.core.provider.StaticProviderDescriptorRegistry
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class BedrockKernelAdapterTest {
    @Test
    fun `bedrock descriptor builds a live-ready paid adapter when env is complete`() {
        val descriptor = StaticProviderDescriptorRegistry().getById("aws_bedrock")!!
        val adapter = BedrockKernelAdapter(
            descriptor,
            mapOf(
                "AWS_ACCESS_KEY_ID" to "access",
                "AWS_SECRET_ACCESS_KEY" to "secret",
                "AWS_REGION" to "us-east-1"
            ),
            transport = BedrockTransport { error("live transport must not run in status test") }
        )

        assertTrue(adapter.status().implemented)
        assertTrue(adapter.status().configured)
        assertEquals("aws_bedrock", adapter.providerId)
    }
}
