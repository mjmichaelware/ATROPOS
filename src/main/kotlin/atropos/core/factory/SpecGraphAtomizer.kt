package atropos.core.factory

import atropos.core.policy.BoundedProcessRunner
import java.nio.charset.StandardCharsets
import java.nio.file.Files
import java.nio.file.LinkOption
import java.nio.file.Path
import java.security.MessageDigest

/**
 * The boundary to the canonical SpecGraph atomizer.
 *
 * This used to run `specgraph_foundry.atoms.AtomService.extract_document`, read
 * back the atom count and the source hash, and discard the atoms — then plan
 * execution from [atropos.core.planning.InternalAtomExtractor] instead. The
 * canonical atomizer was being used as a checksum, and every plan that reached
 * execution was second-hand.
 *
 * [atomizeToRecords] returns the atoms. [atomize] keeps the evidence string its
 * existing caller expects, so research reporting is unchanged.
 *
 * Availability is a configuration, not a failure. A repository with no
 * `SPECGRAPH_ROOT` set is a normal state; the caller falls back to the internal
 * extractor and the evidence line says which planner actually produced the DAG,
 * because "which atomizer planned this" is exactly the thing that must not be
 * ambiguous after the fact.
 */
class SpecGraphAtomizer(
    private val processRunner: BoundedProcessRunner = BoundedProcessRunner(),
    private val pythonExecutable: String = System.getenv("ATROPOS_SPECGRAPH_PYTHON")
        ?.trim()
        ?.takeIf(String::isNotBlank)
        ?: "python3"
) {
    /** The evidence line only. Unchanged contract for research reporting. */
    fun atomize(
        repoRoot: Path,
        projectId: String,
        source: String,
        promptFingerprint: String,
        promptSpans: String
    ): String = atomizeToRecords(repoRoot, projectId, source, promptFingerprint, promptSpans).evidenceLine

    /**
     * The atoms the canonical atomizer produced, ready for the authority graph.
     *
     * @return an atomization whose [CanonicalAtomization.usable] is false when
     *   SpecGraph is absent, refused, or returned nothing this bridge could
     *   read. The caller must fall back rather than plan from an empty set.
     */
    fun atomizeToRecords(
        repoRoot: Path,
        projectId: String,
        source: String,
        promptFingerprint: String,
        promptSpans: String
    ): CanonicalAtomization {
        val specGraphRoot = System.getenv("SPECGRAPH_ROOT")?.trim()
            ?.takeIf(String::isNotBlank)
            ?: return unavailable("SPECGRAPH_ROOT_unset")
        val canonicalRoot = runCatching {
            val candidate = Path.of(specGraphRoot).toAbsolutePath().normalize()
            require(Files.isDirectory(candidate)) { "root_missing" }
            require(Files.isRegularFile(candidate.resolve("src/specgraph_foundry/atoms.py"))) {
                "atomizer_missing"
            }
            candidate.toRealPath()
        }.getOrElse { return unavailable(safeReason(it)) }

        val normalizedRepo = repoRoot.toAbsolutePath().normalize()
        if (!Files.isDirectory(normalizedRepo, LinkOption.NOFOLLOW_LINKS) || hasSymbolicComponent(normalizedRepo)) {
            return unavailable("repository_root_redirected")
        }
        val realRepo = runCatching { normalizedRepo.toRealPath() }
            .getOrElse { return unavailable("repository_root_unavailable") }
        val runRoot = normalizedRepo.resolve(".atropos/research/factory").resolve(projectId).normalize()
        if (!runRoot.startsWith(normalizedRepo)) return unavailable("research_path_escape")
        if (hasSymbolicComponent(runRoot)) return unavailable("research_root_redirected")

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
                val lines = result.stdout.lineSequence().map { it.trimEnd() }.toList()

                val meta = lines.lastOrNull { it.startsWith(CanonicalAtomRecord.META_PREFIX + "\t") }
                    ?.split('\t')
                    ?: error("invalid_output")
                require(meta.size >= 4) { "invalid_meta" }
                val declaredCount = meta[1].toIntOrNull() ?: error("invalid_meta")
                val sourceSha = meta[2]
                val documentId = meta[3]

                // The hash check is what makes this atomization about *this*
                // prompt. Without it a stale database or a crossed run would
                // plan the wrong work and every downstream artifact would carry
                // a lineage that looked correct.
                require(sourceSha == sha256(lineageSource)) { "source_hash_mismatch" }

                val atoms = lines.mapNotNull(CanonicalAtomRecord::decode)
                val observedSchema = lines
                    .firstOrNull { it.startsWith(CanonicalAtomRecord.SCHEMA_PREFIX + "\t") }
                    ?.substringAfter('\t')
                    ?.split(',')
                    ?.map { it.trim() }
                    ?.filter { it.isNotEmpty() }
                    .orEmpty()

                // A count the bridge cannot turn into atoms is the interesting
                // failure: SpecGraph worked and this side could not read it.
                // Saying so, with the field names actually present, turns a
                // schema drift into one diagnostic run.
                if (atoms.isEmpty() && declaredCount > 0) {
                    CanonicalAtomization(
                        atoms = emptyList(),
                        sourceSha256 = sourceSha,
                        documentId = documentId,
                        evidenceLine = "SKIPPED_SOFT_FAIL:atom_schema_unreadable declared=$declaredCount " +
                            "observed_fields=${observedSchema.joinToString("|").ifBlank { "none" }}; " +
                            "internal DAG fallback required",
                        observedSchema = observedSchema
                    )
                } else {
                    CanonicalAtomization(
                        atoms = atoms,
                        sourceSha256 = sourceSha,
                        documentId = documentId,
                        evidenceLine = "PASS:canonical_specgraph_atomizer atom_count=${atoms.size} " +
                            "declared=$declaredCount source_sha256=$sourceSha " +
                            "output_sha256=${result.outputSha256.orEmpty()} " +
                            "prompt_fingerprint=$promptFingerprint",
                        observedSchema = observedSchema
                    )
                }
            } finally {
                Files.deleteIfExists(sourceFile)
                Files.deleteIfExists(databaseFile)
                Files.deleteIfExists(Path.of("${databaseFile}-wal"))
                Files.deleteIfExists(Path.of("${databaseFile}-shm"))
            }
        }.getOrElse { unavailable(safeReason(it)) }
    }

    /**
     * SpecGraph could not plan this. The evidence names why, and the caller
     * falls back to the internal extractor.
     */
    private fun unavailable(reason: String): CanonicalAtomization = CanonicalAtomization(
        atoms = emptyList(),
        sourceSha256 = "",
        documentId = "",
        evidenceLine = "SKIPPED_SOFT_FAIL:$reason; internal DAG fallback required"
    )

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
        /**
         * Emits the atoms themselves, not a count of them.
         *
         * Tab-separated lines rather than JSON: the Kotlin side has no JSON
         * reader, and adding one to parse a subprocess's output would be a new
         * parser on a trust boundary. One field per column is also readable by
         * eye, which matters the first time this goes wrong on a phone.
         *
         * `SCHEMA` is emitted whenever an atom carries none of the field names
         * this bridge looks for. Those names are a guess about somebody else's
         * API; a guess that fails should say what it actually saw, so one run
         * establishes the real schema instead of producing an empty plan and no
         * explanation.
         */
        val PYTHON_ATOMIZE_SCRIPT = """
import hashlib, sys
from pathlib import Path
root = Path(sys.argv[1]).resolve()
sys.path.insert(0, str(root / "src"))
from specgraph_foundry.atoms import AtomService
from specgraph_foundry.database import Database
from specgraph_foundry.ingestion import IngestionService
from specgraph_foundry.services import ProjectService

def esc(value):
    return str(value).replace("\\", "\\\\").replace("\t", "\\t").replace("\n", "\\n")

def pick(atom, names, default=""):
    for name in names:
        if isinstance(atom, dict) and atom.get(name) not in (None, ""):
            return atom[name]
    return default

def listed(value):
    if value in (None, ""):
        return ""
    if isinstance(value, (list, tuple)):
        return ",".join(str(item).replace(",", " ").strip() for item in value if str(item).strip())
    return str(value).replace("\n", " ").strip()

source = Path(sys.argv[2]).read_text(encoding="utf-8")
database = Database(Path(sys.argv[3]))
database.initialize()
slug = "atropos-factory-" + hashlib.sha256((source + sys.argv[3]).encode("utf-8")).hexdigest()[:16]
project = ProjectService(database).create(slug, "ATROPOS factory atomization")
document = IngestionService(database).ingest_text(str(project["id"]), "factory-requirements.md", source)
extraction = AtomService(database).extract_document(str(document["id"]))
atoms = extraction.get("atoms", []) or []

reported_schema = False
for atom in atoms:
    identifier = pick(atom, ["id", "atom_id", "uuid", "key"])
    statement = pick(atom, ["statement", "text", "body", "content", "description"])
    if not identifier or not statement:
        if not reported_schema and isinstance(atom, dict):
            print("SCHEMA\t" + ",".join(sorted(str(k) for k in atom.keys())))
            reported_schema = True
        continue
    print("\t".join([
        "ATOM",
        esc(identifier),
        esc(pick(atom, ["dimension", "atom_dimension", "category", "kind"])),
        esc(pick(atom, ["section_id", "sectionId", "section"])),
        esc(pick(atom, ["source_coordinates", "sourceCoordinates", "coordinates", "location"])),
        listed(pick(atom, ["dependencies", "depends_on", "requires"])),
        listed(pick(atom, ["territory", "paths", "files"])),
        esc(statement),
    ]))

print("\t".join([
    "META",
    str(len(atoms)),
    str(document["sha256"]),
    str(document["id"]),
]))
""".trimIndent()
    }
}
