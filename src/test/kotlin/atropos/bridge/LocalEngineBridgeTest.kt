package atropos.bridge

import kotlin.test.Test
import kotlin.test.assertNull

class LocalEngineBridgeTest {
    @Test
    fun LocalEngineBridge_does_not_open_without_explicit_port_configuration() {
        assertNull(LocalEngineBridge.fromEnvironment({ null }) { "test-provider" })
    }
}
