package atropos.core.verification

import atropos.core.parser.TreeSitterGrammarBridge
import java.nio.file.Files
import java.nio.file.Path

class CImplScalarEngine(
    private val astBridge: TreeSitterGrammarBridge = TreeSitterGrammarBridge()
) {
    fun evaluate(worktreeRoot: Path, expectedFiles: List<String>): Int {
        for (filePath in expectedFiles) {
            val path = worktreeRoot.resolve(filePath)
            if (!Files.isRegularFile(path)) {
                return 0
            }
            if (Files.size(path) == 0L) {
                return 0
            }

            val sourceCode = Files.readString(path)
            val hollowStubs = astBridge.scanHollowStubs(sourceCode)
            if (hollowStubs.isNotEmpty()) {
                return 0
            }
        }
        return 1
    }
}