package atropos.core.agent

import atropos.core.factory.CanonicalAtomRecord
import atropos.core.thinking.Narrate
import atropos.core.dag.DagDefinition
import atropos.core.dag.DagExecutionService
import atropos.core.dag.DagNode
import atropos.core.dag.DagNodeAction
import java.nio.charset.StandardCharsets
import java.nio.file.Path
import java.security.MessageDigest
import java.time.Instant

/**
 * The DAG a self-host goal runs against.
 *
 * There are two, and which one a run gets is decided by whether the operator
 * named a document:
 *
 * - **The document graph.** One node per atom the operator's specification
 *   states. This is the one the engine exists for and the one that was missing:
 *   the run used to get the cradle graph below whatever was attached, so
 *   "ATROPOS, build yourself from this" meant "ATROPOS, write one marker file".
 * - **The cradle graph.** Three nodes that prove the run chain itself works --
 *   probe the tree, write a marker, write its test. It is the right answer for
 *   a goal that names no document, and the right fallback when a named document
 *   cannot be read or cannot be atomized.
 *
 * Both are built through [DagExecutionService.createDag]. There is one DAG
 * system (AGENTS.md 0.7); this decides what goes in it.
 */
class SelfHostBootstrapDagFactory(
    private val repoRoot: Path,
    private val dagService: DagExecutionService,
    private val clock: () -> Instant,
    private val documentPlan: SelfHostDocumentPlan = SelfHostDocumentPlan(repoRoot)
) {
    fun fingerprint(text: String): String {
        val digest = MessageDigest.getInstance("SHA-256")
            .digest(text.toByteArray(StandardCharsets.UTF_8))
            .joinToString("") { "%02x".format(it) }
        return digest.take(16)
    }

    fun create(record: GoalRunRecord, phase: String): DagDefinition {
        documentGraph(record, phase)?.let { return it }
        return cradleGraph(record, phase)
    }

    /**
     * One node per atom of the document the goal names.
     *
     * Nodes are [DagNodeAction.PROVIDER_CALL] rather than EDIT_FILE: an atom
     * states work in the document's own words, and there is no deterministic
     * file content to write for it the way there is for the cradle marker.
     * Turning a stated obligation into a diff is a provider's job, and the node
     * carries the statement verbatim so the provider is asked the document's
     * question rather than a paraphrase of it.
     */
    private fun documentGraph(record: GoalRunRecord, phase: String): DagDefinition? {
        val plan = runCatching { documentPlan.atomize(record.id, record.task) }
            .onFailure { failure ->
                Narrate.plan.trouble(
                    "could not plan from the document",
                    failure.message ?: failure::class.simpleName.orEmpty()
                )
            }
            .getOrNull() ?: return null

        val now = clock()
        // One node id per atom, by position.
        //
        // Keyed by position and not by atom id, because two atoms carrying the
        // same id would collapse to one entry in a map and the graph would
        // come back one node short of the document -- silently, and only
        // visible by counting. An end-to-end run produced 389 nodes from 390
        // atoms for exactly that reason.
        val nodeIdAt = plan.atoms.indices.map { index ->
            "${record.id}-atom-${(index + 1).toString().padStart(ATOM_INDEX_CELLS, '0')}"
        }
        // And a separate map for resolving what an atom's stated dependencies
        // point at. First declaration wins, so a repeated id resolves to the
        // atom that earned it rather than to whichever came last.
        val nodeIdByAtom = mutableMapOf<String, String>()
        plan.atoms.forEachIndexed { index, atom ->
            nodeIdByAtom.putIfAbsent(atom.id, nodeIdAt[index])
        }

        val nodes = plan.atoms.mapIndexed { index, atom ->
            val nodeId = nodeIdAt[index]
            Narrate.plan.item(index + 1, plan.atoms.size, atom.id, atom.statement.take(90))
            DagNode(
                id = nodeId,
                label = atom.statement.take(DAG_LABEL_CELLS).ifBlank { "atom ${index + 1}" },
                // Only backward edges are kept.
                //
                // Not a simplification: it is what makes the graph acyclic by
                // construction. An atom's stated dependencies come from a
                // compiler that already breaks cycles, but this graph is
                // executed on a device where a cycle means a run that never
                // terminates, and "trust the upstream" is not a guarantee.
                // Ordering is the document's own, so a backward edge is the
                // dependency as written and a forward one is either a repaired
                // cycle or a reference the document makes ahead of itself.
                dependencies = atom.dependencies
                    .mapNotNull(nodeIdByAtom::get)
                    .filter { it < nodeId }
                    .distinct(),
                territory = atom.territory.ifEmpty { DEFAULT_TERRITORY },
                action = DagNodeAction.PROVIDER_CALL,
                actionPayload = atom.statement,
                createdAt = now,
                updatedAt = now,
                metaFile = repoRoot.resolve(".atropos/dag/$nodeId.meta")
            )
        }

        Narrate.plan.counted("DAG nodes from ${plan.label}", nodes.size)
        Narrate.plan.counted("edges between them", nodes.sumOf { it.dependencies.size })

        return dagService.createDag(
            label = "self-host phase $phase from ${plan.label}: ${nodes.size} atoms",
            projectId = "atropos-self-host",
            nodes = nodes
        )
    }

    private fun cradleGraph(record: GoalRunRecord, phase: String): DagDefinition {
        val now = clock()
        val probeId = "${record.id}-identity-probe"
        val markerId = "${record.id}-source-marker"
        val testId = "${record.id}-source-marker-test"
        val markerPath = "src/main/kotlin/atropos/core/agent/SelfHostCradleRuntimeState.kt"
        val testPath = "src/test/kotlin/atropos/core/agent/SelfHostCradleRuntimeStateTest.kt"
        val goalLiteral = kotlinString(record.id)
        val phaseLiteral = kotlinString(phase)
        val markerContent = SelfHostCradleRuntimeState.sourceFor(record.id, phase)
        val testContent = """
            package atropos.core.agent

            import kotlin.test.Test
            import kotlin.test.assertEquals

            class SelfHostCradleRuntimeStateTest {
                @Test
                fun records_latest_self_host_goal_and_phase() {
                    assertEquals("$goalLiteral", SelfHostCradleRuntimeState.LAST_SELF_HOST_GOAL)
                    assertEquals("$phaseLiteral", SelfHostCradleRuntimeState.LAST_SELF_HOST_PHASE)
                }
            }
        """.trimIndent()
        return dagService.createDag(
            label = "self-host bootstrap phase $phase: ${record.task.take(80)}",
            projectId = "atropos-self-host",
            nodes = listOf(
                DagNode(
                    id = probeId,
                    label = "ATROPOS cradle verification probe",
                    territory = listOf("src/main/kotlin/atropos", "src/test/kotlin/atropos"),
                    action = DagNodeAction.VERIFY,
                    actionPayload = "git status --short -- src/main/kotlin/atropos src/test/kotlin/atropos",
                    expectedOutputs = listOf("src/main/kotlin/atropos/Main.kt"),
                    optionalChecks = setOf("Focused Tests"),
                    createdAt = now,
                    updatedAt = now,
                    metaFile = repoRoot.resolve(".atropos/dag/$probeId.meta")
                ),
                DagNode(
                    id = markerId,
                    label = "ATROPOS deterministic self-host source marker",
                    dependencies = listOf(probeId),
                    territory = listOf("src/main/kotlin/atropos/core/agent"),
                    action = DagNodeAction.EDIT_FILE,
                    actionPayload = "$markerPath::$markerContent",
                    expectedOutputs = listOf(markerPath),
                    optionalChecks = setOf("Focused Tests"),
                    createdAt = now,
                    updatedAt = now,
                    metaFile = repoRoot.resolve(".atropos/dag/$markerId.meta")
                ),
                DagNode(
                    id = testId,
                    label = "SelfHostCradleRuntimeStateTest",
                    dependencies = listOf(markerId),
                    territory = listOf(
                        "src/main/kotlin/atropos/core/agent",
                        "src/test/kotlin/atropos/core/agent"
                    ),
                    action = DagNodeAction.CREATE_FILE,
                    actionPayload = "$testPath::$testContent",
                    expectedOutputs = listOf(testPath),
                    createdAt = now,
                    updatedAt = now,
                    metaFile = repoRoot.resolve(".atropos/dag/$testId.meta")
                )
            )
        )
    }

    private companion object {
        /** Zero-padded so node ids sort in document order as strings. */
        const val ATOM_INDEX_CELLS = 4

        /** A DAG label is a line in a list, not the atom's whole statement. */
        const val DAG_LABEL_CELLS = 120

        /**
         * Where a node may write when its atom named no files.
         *
         * The engine's own source, matching the cradle graph's probe. Territory
         * is a bound and not a hint, so an atom that states no paths gets the
         * narrowest useful one rather than the repository.
         */
        val DEFAULT_TERRITORY = listOf(
            "src/main/kotlin/atropos",
            "src/test/kotlin/atropos"
        )
    }

    private fun kotlinString(value: String): String =
        value
            .replace("\\", "\\\\")
            .replace("\"", "\\\"")
            .replace("$", "\\\$")
            .replace("\n", "\\n")
            .replace("\r", "\\r")
}
