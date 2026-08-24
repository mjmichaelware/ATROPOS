package atropos.core.provider.adapter

import atropos.core.provider.StaticProviderDescriptorRegistry
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test

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
            transport = { error("live transport must not run in status test") }
        )

        assertTrue(adapter.implemented())
        assertTrue(adapter.status().configured)
        assertEquals("aws_bedrock", adapter.providerId)
    }
}
