package atropos.core.provider

import atropos.core.ProviderCascadeRouter
import atropos.core.ProviderFactory
import kotlin.test.Test
import kotlin.test.assertEquals

class ProviderCascadeRouterTest {
    @Test
    fun exposes_the_documented_chain_through_the_canonical_router() {
        val router = ProviderCascadeRouter(ProviderFactory())
        assertEquals(FallbackChain.CHAT, router.declaredFallbackChain(ApiCapability.CHAT))
    }
}
