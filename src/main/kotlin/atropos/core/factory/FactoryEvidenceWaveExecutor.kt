package atropos.core.factory

import atropos.core.dag.DagNode
import java.nio.file.Files
import java.nio.file.Path

/**
 * Bridges the generated project's real acceptance manifest into the outer DAG.
 *
 * The obligation loop owns scheduling; this class only answers whether a
 * dependency-ready atom has evidence that the generator/verifier actually ran.
 * Returning a ready id without this check would be a silent soft-success.
 */
class FactoryEvidenceWaveExecutor(
    private val evidencePath: Path,
    private val freeze: FactoryAcceptanceFreeze
) {
    fun execute(ready: List<DagNode>): Set<String> {
        require(ready.isNotEmpty()) { "factory evidence wave received no runnable atoms" }
        val path = evidencePath.toAbsolutePath().normalize()
        require(Files.isRegularFile(path)) { "factory evidence manifest not found: $path" }
        val fields = Files.readAllLines(path).mapNotNull { line ->
            line.substringBefore('=').takeIf { it.isNotBlank() }?.let { it to line.substringAfter('=') }
        }.toMap()
        require(fields["acceptance_freeze_sha256"] == freeze.sha256) {
            "factory evidence freeze hash does not match the active acceptance freeze"
        }
        require(fields["verification"] == "generated-source-and-tests+deterministic") {
            "factory evidence is missing generated source/test verification"
        }
        require(fields["completion_gate"] == "factory completion gate passed") {
            "factory evidence completion gate is not green"
        }
        val declaredAtoms = fields["planning_atoms"].orEmpty()
            .split(',').map(String::trim).filter(String::isNotBlank).toSet()
        val missing = ready.map { it.id }.filterNot(declaredAtoms::contains)
        require(missing.isEmpty()) {
            "factory evidence does not cover runnable atoms: ${missing.joinToString(",")}" 
        }
        return ready.map { it.id }.toSet()
    }
}
