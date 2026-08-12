/* SPDX-License-Identifier: AGPL-3.0-only */
package atropos.core.auth

import java.nio.file.Files
import java.nio.file.Path
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

/**
 * `SUP.AUTH.SWARM-MD`: topology is a verified input, and an unusable
 * declaration is refused with a recovery hint rather than half-applied.
 */
class SwarmMdLoaderTest {

    private fun workspace(): Path = Files.createTempDirectory("atropos-swarm-test")

    private fun loaderIn(root: Path): SwarmMdLoader =
        SwarmMdLoader(AuthorityAttestor(FingerprintStore(root.resolve(".atropos/fp.tsv")), root))

    @Test
    fun `nodes, depth and escalation are read from the declaration`() {
        val root = workspace()
        Files.writeString(
            root.resolve("SWARM.md"),
            """
            # Swarm

            maxDepth: 2
            escalationPath: reviewer, director
            coordinationCostBound: 500

            ## Nodes
            - reviewer | auditor | src/main/kotlin/atropos/core/verifier
            - builder | worker | src/main/kotlin/atropos/core/factory
            """.trimIndent()
        )
        val loader = loaderIn(root)

        val spec = loader.spec(loader.load())!!

        assertEquals(2, spec.maxDepth)
        assertEquals(listOf("reviewer", "director"), spec.escalationPath)
        assertEquals(500L, spec.coordinationCostBound)
        assertEquals(listOf("reviewer", "builder"), spec.nodes.map { it.name })
        assertEquals(
            listOf("src/main/kotlin/atropos/core/verifier"),
            spec.nodes.first().territoryGrants
        )
        assertTrue(spec.usable)
    }

    @Test
    fun `a missing depth means no delegation rather than unlimited`() {
        val root = workspace()
        Files.writeString(
            root.resolve("SWARM.md"),
            "## Nodes\n- solo | worker | src\n"
        )
        val loader = loaderIn(root)

        assertEquals(0, loader.spec(loader.load())!!.maxDepth)
    }

    @Test
    fun `a node without territory makes the spec unusable`() {
        val spec = SwarmSpec(
            nodes = listOf(SwarmNode("drifter", "worker", emptyList())),
            maxDepth = 1,
            escalationPath = emptyList(),
            coordinationCostBound = null
        )

        assertFalse(spec.usable)
        assertTrue(spec.defects().any { it.contains("no territory grant") })
    }

    @Test
    fun `an unusable topology refuses the boot with a remedy`() {
        val root = workspace()
        Files.writeString(root.resolve("SWARM.md"), "maxDepth: 1\n\n## Nodes\n- drifter | worker\n")
        val store = FingerprintStore(root.resolve(".atropos/fp.tsv"))

        val boot = AuthBootstrap(root, store, AuthorityAttestor(store, root)).boot()

        assertFalse(boot.permitted)
        assertTrue((boot as AuthBootResult.Refused).cause.reason.contains("unusable"))
        assertTrue(boot.cause.remedy.contains("territory grant"))
    }

    @Test
    fun `swarm never outranks agents in the cascade`() {
        assertTrue(SwarmMdLoader.RANK > AgentsMdLoader.RANK)
    }
}
