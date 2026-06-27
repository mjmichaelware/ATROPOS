/* SPDX-License-Identifier: AGPL-3.0-only */
package atropos.cli.ui

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

        fun exists(path: String): Boolean = File(root, path).exists()

        fun keyPresent(name: String): Boolean {
            val cfg = File(System.getProperty("user.home"), ".atropos/config.json")
            if (!cfg.exists()) return false
            val text = runCatching { cfg.readText() }.getOrDefault("")
            val match = Regex("\"$name\"\\s*:\\s*\"([^\"]*)\"").find(text)
            return !match?.groupValues?.getOrNull(1).isNullOrBlank()
        }

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

        val providers = listOf(
            ProviderUiTruth("groq", true, keyPresent("groq"), "fast chat / cheap route", "fast", "low", "cloud"),
            ProviderUiTruth("openai", true, keyPresent("openai"), "general reasoning / vision route", "med", "med", "cloud"),
            ProviderUiTruth("anthropic", true, keyPresent("anthropic"), "coding / deep reasoning", "med", "med", "cloud"),
            ProviderUiTruth("xai", true, keyPresent("xai"), "alternate cloud route", "med", "med", "cloud"),
            ProviderUiTruth("ollama", true, true, "local privacy fallback", "slow", "free", "local"),
            ProviderUiTruth("gemini", false, false, "not installed", "--", "--", "--")
        )

        return WorkbenchTruth(
            providers = providers,
            ontologicalRouter = exists("src/main/kotlin/atropos/data/lakehouse/OntologicalAddressRouter.kt"),
            latentIndexer = exists("src/main/kotlin/atropos/data/indexer/LatentOntologicalIndexer.kt"),
            cloudLakehouseSync = exists("src/main/kotlin/atropos/data/storage/CloudLakehouseSyncEngine.kt"),
            deltaTracker = exists("src/main/kotlin/atropos/data/cache/CodebaseDeltaTreeTracker.kt"),
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
}
