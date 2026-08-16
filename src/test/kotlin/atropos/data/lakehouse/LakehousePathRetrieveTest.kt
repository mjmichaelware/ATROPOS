package atropos.data.lakehouse

import atropos.core.ApiKeys
import atropos.core.AtroposConfig
import atropos.core.LakehouseConfig
import atropos.core.RuntimeConfig
import atropos.data.storage.CloudLakehouseSyncEngine
import java.nio.file.Files
import kotlin.test.Test
import kotlin.test.assertContentEquals
import kotlin.test.assertEquals

class LakehousePathRetrieveTest {
    @Test
    fun `registered atom path retrieves verified CAS content instead of unconditional miss`() {
        val root = Files.createTempDirectory("atropos-lakehouse-retrieve-")
        val mount = root.resolve("mount")
        val cas = root.resolve("cas")
        Files.createDirectories(mount.resolve("index"))
        val content = "bounded authority context\n".toByteArray()
        val store = CloudLakehouseSyncEngine(cas.toFile())
        val hash = store.storeContentAddressed(content)
        Files.writeString(mount.resolve("index/paths.txt"), "P/authority/bounded\n")
        Files.writeString(mount.resolve("index/objects.tsv"), "$hash\tP/authority/bounded\n")

        val config = AtroposConfig(
            ApiKeys("", "", "", ""),
            LakehouseConfig(mount.toString(), mount.resolve("vector.db").toString()),
            RuntimeConfig("local", 0.0)
        )
        val result = LakehousePathRetrieve(store, config).get("P/authority/bounded")

        assertEquals("HIT", result.status)
        assertEquals(hash, result.nodeId)
        assertContentEquals(content, result.bytes)
    }

    @Test
    fun `unregistered atom path is refused without guessing another shelf`() {
        val root = Files.createTempDirectory("atropos-lakehouse-reject-")
        val mount = root.resolve("mount")
        Files.createDirectories(mount.resolve("index"))
        Files.writeString(mount.resolve("index/paths.txt"), "P/authority/bounded\n")
        Files.writeString(mount.resolve("index/objects.tsv"), "${"a".repeat(64)}\tP/authority/bounded\n")
        val config = AtroposConfig(
            ApiKeys("", "", "", ""),
            LakehouseConfig(mount.toString(), mount.resolve("vector.db").toString()),
            RuntimeConfig("local", 0.0)
        )

        val result = LakehousePathRetrieve(
            CloudLakehouseSyncEngine(root.resolve("cas").toFile()),
            config
        ).get("P/other/shelf")

        assertEquals("REJECT", result.status)
        assertEquals("path_not_in_registry", result.reason)
    }
}
