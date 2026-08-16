/* SPDX-License-Identifier: AGPL-3.0-only */
package atropos.cli.ui

import atropos.core.provider.ProviderTruthRecord
import atropos.core.provider.ProviderTruthService
import atropos.data.cache.CodebaseDeltaTreeTracker
import atropos.data.indexer.LatentOntologicalIndexer
import java.io.File

data class ProviderUiTruth(
    val name: String,
    val implemented: Boolean,
    val configured: Boolean,
    val role: String,
    val latency: String,
    val cost: String,
    val privacy: String
)

data class WorkbenchTruth(
    val providers: List<ProviderUiTruth>,
    val ontologicalRouter: Boolean,
    val latentIndexer: Boolean,
    val cloudLakehouseSync: Boolean,
    val deltaTracker: Boolean,
    val selfImprovingLoop: Boolean,
    val constraintSolver: Boolean,
    val immunityEngine: Boolean,
    val astDb: Boolean,
    val successWeightsDb: Boolean,
    val masterMap: Boolean,
    val lakehouseMounted: Boolean,
    val corpusFiles: Int,
    val sourceFiles: Int,
    val testsPresent: Boolean
)

class WorkbenchTruthProbe {
    fun probe(workspace: String): WorkbenchTruth {
        val root = File(workspace)

        // Provider identity, configuration, adapter presence, and cost are
        // owned by ProviderTruthService. Keep this probe disk-only by making
        // the local health check non-blocking; health is still reported as
        // unavailable rather than guessed online.
        val providerTruth = runCatching {
            ProviderTruthService(ollamaProbe = { false }).snapshot().records
        }.getOrDefault(emptyList())

        fun exists(path: String): Boolean = File(root, path).exists()

        fun countSourceFiles(): Int {
            val src = File(root, "src/main/kotlin")
            if (!src.exists()) return 0
            return src.walkTopDown().count { it.isFile && it.extension == "kt" }
        }

        fun countCorpusFiles(): Int {
            if (!root.exists()) return 0
            return root.walkTopDown()
                .maxDepth(5)
                .filter { it.isFile }
                .count {
                    it.name.startsWith("ATROPOS_") &&
                        it.extension.lowercase() in setOf("txt", "md", "json", "tsv")
                }
        }

        val lakehouseMounted =
            File(root, "lakehouse").isDirectory ||
                File(root, "lakehouse_build").isDirectory ||
                File(root, ".atropos/lakehouse").isDirectory

        val masterMap =
            exists("ATROPOS_00_MASTER_ADDRESS_MAP.md") ||
                exists("ATROPOS_00_MASTER_ADDRESS_MAP.txt") ||
                exists(".atropos/ATROPOS_00_MASTER_ADDRESS_MAP.md") ||
                exists(".atropos/ATROPOS_00_MASTER_ADDRESS_MAP.txt")

        val astDb =
            exists(".atropos/ast_symbol_graph.db") ||
                exists(".atropos/ast_symbol_graph.sqlite") ||
                exists("ast_symbol_graph.db") ||
                exists("ast_symbol_graph.sqlite")

        val weightsDb =
            exists(".atropos/success_weights.db") ||
                exists(".atropos/success_weights.sqlite") ||
                exists("success_weights.db") ||
                exists("success_weights.sqlite")

        val providers = providerTruth.map(::toUiTruth)

        val deltaTrackerAvailable = runCatching {
            CodebaseDeltaTreeTracker(root.path).getActiveWorkspaceDeltas()
            true
        }.getOrDefault(false)

        val latentIndexerAvailable = runCatching {
            // Diagnostic capability only. This never participates in DLOI
            // resolution or supplies a nearest-match authority result.
            LatentOntologicalIndexer(File(root, ".atropos/latent-index.diagnostic").path)
                .computeCosineSimilarity(listOf(1.0), listOf(1.0)) == 1.0
        }.getOrDefault(false)

        return WorkbenchTruth(
            providers = providers,
            ontologicalRouter = exists("src/main/kotlin/atropos/data/lakehouse/OntologicalAddressRouter.kt"),
            latentIndexer = latentIndexerAvailable && exists("src/main/kotlin/atropos/data/indexer/LatentOntologicalIndexer.kt"),
            cloudLakehouseSync = exists("src/main/kotlin/atropos/data/storage/CloudLakehouseSyncEngine.kt"),
            deltaTracker = deltaTrackerAvailable && exists("src/main/kotlin/atropos/data/cache/CodebaseDeltaTreeTracker.kt"),
            selfImprovingLoop = exists("src/main/kotlin/atropos/core/knowledge/SelfImprovingCompilationLoop.kt"),
            constraintSolver = exists("src/main/kotlin/atropos/core/verifier/ConstraintSolverEvaluator.kt"),
            immunityEngine = exists("src/main/kotlin/atropos/core/verifier/ProbabilisticImmunityEngine.kt"),
            astDb = astDb,
            successWeightsDb = weightsDb,
            masterMap = masterMap,
            lakehouseMounted = lakehouseMounted,
            corpusFiles = countCorpusFiles(),
            sourceFiles = countSourceFiles(),
            testsPresent = exists("src/main/kotlin/atropos/tests/cli/CommandRouterTest.kt") ||
                exists("src/main/kotlin/atropos/tests/data/OntologicalIndexTest.kt")
        )
    }

    private fun toUiTruth(record: ProviderTruthRecord): ProviderUiTruth {
        val local = record.category == "local"
        val cost = when (record.costMode) {
            atropos.core.provider.CostMode.LOCAL -> "free"
            atropos.core.provider.CostMode.FREE -> "low"
            atropos.core.provider.CostMode.PAID_LOCKED -> "paid-locked"
            else -> record.costMode.name.lowercase().replace('_', '-')
        }
        return ProviderUiTruth(
            name = record.id,
            implemented = record.adapterPresent,
            configured = record.keyPresent,
            role = "${record.category} route",
            latency = if (local) "local" else "provider-managed",
            cost = cost,
            privacy = if (local) "local" else "cloud"
        )
    }
}
