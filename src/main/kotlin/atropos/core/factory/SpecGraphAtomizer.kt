package atropos.core.factory

import atropos.core.policy.BoundedProcessRunner
import java.nio.charset.StandardCharsets
import java.nio.file.Files
import java.nio.file.LinkOption
import java.nio.file.Path
import java.security.MessageDigest

/**
 * Thin boundary to the canonical SpecGraph atomizer. It produces research
 * evidence only; execution planning remains owned by ATROPOS's internal DAG.
 */
class SpecGraphAtomizer(
    private val processRunner: BoundedProcessRunner = BoundedProcessRunner(),
    private val pythonExecutable: String = System.getenv("ATROPOS_SPECGRAPH_PYTHON")
        ?.trim()
        ?.takeIf(String::isNotBlank)
        ?: "python3"
) {
    fun atomize(
        repoRoot: Path,
        projectId: String,
        source: String,
        promptFingerprint: String,
        promptSpans: String
    ): String {
        val specGraphRoot = System.getenv("SPECGRAPH_ROOT")?.trim()
            ?.takeIf(String::isNotBlank)
            ?: return skipped("SPECGRAPH_ROOT_unset")
        val canonicalRoot = runCatching {
            val candidate = Path.of(specGraphRoot).toAbsolutePath().normalize()
            require(Files.isDirectory(candidate)) { "root_missing" }
            require(Files.isRegularFile(candidate.resolve("src/specgraph_foundry/atoms.py"))) {
                "atomizer_missing"
            }
            candidate.toRealPath()
        }.getOrElse { return skipped(safeReason(it)) }

        val normalizedRepo = repoRoot.toAbsolutePath().normalize()
        if (!Files.isDirectory(normalizedRepo, LinkOption.NOFOLLOW_LINKS) || hasSymbolicComponent(normalizedRepo)) {
            return skipped("repository_root_redirected")
        }
        val realRepo = runCatching { normalizedRepo.toRealPath() }
            .getOrElse { return skipped("repository_root_unavailable") }
        val runRoot = normalizedRepo.resolve(".atropos/research/factory").resolve(projectId).normalize()
        if (!runRoot.startsWith(normalizedRepo)) return skipped("research_path_escape")
        if (hasSymbolicComponent(runRoot)) return skipped("research_root_redirected")

        return runCatching {
            Files.createDirectories(runRoot)
            require(
                Files.isDirectory(runRoot, LinkOption.NOFOLLOW_LINKS) &&
                    !hasSymbolicComponent(runRoot) &&
                    runRoot.toRealPath().startsWith(realRepo)
            ) { "research_root_redirected" }
            val sourceFile = Files.createTempFile(runRoot, ".specgraph-source-", ".md")
            val databaseFile = Files.createTempFile(runRoot, ".specgraph-", ".sqlite3")
            val lineageSource = buildString {
                appendLine("prompt_fingerprint=$promptFingerprint")
                appendLine("prompt_spans=$promptSpans")
                append(source)
            }
            Files.writeString(sourceFile, lineageSource, StandardCharsets.UTF_8)
            try {
                val result = processRunner.run(
                    command = listOf(
                        pythonExecutable,
                        "-c",
                        PYTHON_ATOMIZE_SCRIPT,
                        canonicalRoot.toString(),
                        sourceFile.toString(),
                        databaseFile.toString()
                    ),
                    directory = normalizedRepo,
                    timeoutMillis = 120_000,
                    maxOutputBytes = 128 * 1024,
                    maxOutputLines = 2_000,
                    removeEnvironmentKeys = System.getenv().keys.filter(::isSecretEnvironmentKey).toSet()
                )
                require(!result.timedOut) { "timeout" }
                require(result.launchError == null) { "launch_failed" }
                require(result.exitCode == 0) { "exit_${result.exitCode ?: "unknown"}" }
                require(!result.outputTruncated) { "output_truncated" }
                val atomCount = JSON_ATOM_COUNT.find(result.stdout)?.groupValues?.get(1)?.toIntOrNull()
                    ?: error("invalid_output")
                val sourceSha = JSON_SOURCE_SHA.find(result.stdout)?.groupValues?.get(1)
                    ?: error("invalid_output")
                require(sourceSha == sha256(lineageSource)) { "source_hash_mismatch" }
                "PASS:canonical_specgraph_atomizer atom_count=$atomCount source_sha256=$sourceSha output_sha256=${result.outputSha256.orEmpty()} prompt_fingerprint=$promptFingerprint"
            } finally {
                Files.deleteIfExists(sourceFile)
                Files.deleteIfExists(databaseFile)
                Files.deleteIfExists(Path.of("${databaseFile}-wal"))
                Files.deleteIfExists(Path.of("${databaseFile}-shm"))
            }
        }.getOrElse { skipped(safeReason(it)) }
    }

    private fun skipped(reason: String): String =
        "SKIPPED_SOFT_FAIL:$reason; internal DAG fallback required"

    private fun safeReason(failure: Throwable): String =
        failure.message.orEmpty().lowercase()
            .replace(Regex("[^a-z0-9]+"), "_")
            .trim('_')
            .ifBlank { failure.javaClass.simpleName.lowercase() }
            .take(80)

    private fun isSecretEnvironmentKey(key: String): Boolean =
        key.contains("KEY", ignoreCase = true) ||
            key.contains("TOKEN", ignoreCase = true) ||
            key.contains("SECRET", ignoreCase = true) ||
            key.contains("PASSWORD", ignoreCase = true)

    private fun hasSymbolicComponent(path: Path): Boolean {
        val root = path.root ?: return true
        var cursor = root
        for (part in root.relativize(path)) {
            cursor = cursor.resolve(part)
            if (Files.isSymbolicLink(cursor)) return true
        }
        return false
    }

    private fun sha256(value: String): String = MessageDigest.getInstance("SHA-256")
        .digest(value.toByteArray(StandardCharsets.UTF_8))
        .joinToString("") { "%02x".format(it) }

    private companion object {
        val JSON_ATOM_COUNT = Regex("\\\"atom_count\\\"\\s*:\\s*(\\d+)")
        val JSON_SOURCE_SHA = Regex("\\\"source_sha256\\\"\\s*:\\s*\\\"([0-9a-f]{64})\\\"")
        val PYTHON_ATOMIZE_SCRIPT = """
import hashlib, json, sys
from pathlib import Path
root = Path(sys.argv[1]).resolve()
sys.path.insert(0, str(root / "src"))
from specgraph_foundry.atoms import AtomService
from specgraph_foundry.database import Database
from specgraph_foundry.ingestion import IngestionService
from specgraph_foundry.services import ProjectService
source = Path(sys.argv[2]).read_text(encoding="utf-8")
database = Database(Path(sys.argv[3]))
database.initialize()
slug = "atropos-factory-" + hashlib.sha256((source + sys.argv[3]).encode("utf-8")).hexdigest()[:16]
project = ProjectService(database).create(slug, "ATROPOS factory atomization")
document = IngestionService(database).ingest_text(str(project["id"]), "factory-requirements.md", source)
extraction = AtomService(database).extract_document(str(document["id"]))
print(json.dumps({"atom_count": len(extraction.get("atoms", [])), "source_sha256": str(document["sha256"])}, sort_keys=True))
""".trimIndent()
    }
}
