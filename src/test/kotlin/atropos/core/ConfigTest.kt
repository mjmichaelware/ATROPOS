package atropos.core

import kotlin.io.path.Path
import kotlin.test.Test
import kotlin.test.assertEquals

class ConfigTest {
    @Test
    fun configured_root_is_used_by_all_local_state_owners() {
        assertEquals(
            Path("/tmp/atropos-config").toAbsolutePath().normalize(),
            AtroposConfig.configRoot(
                environment = mapOf("ATROPOS_CONFIG_DIR" to "/tmp/atropos-config"),
                userHome = "/ignored-home"
            )
        )
    }

    @Test
    fun user_home_fallback_keeps_the_default_dot_atropos_root() {
        assertEquals(
            Path("/tmp/operator").resolve(".atropos").toAbsolutePath().normalize(),
            AtroposConfig.configRoot(environment = emptyMap(), userHome = "/tmp/operator")
        )
    }
}
