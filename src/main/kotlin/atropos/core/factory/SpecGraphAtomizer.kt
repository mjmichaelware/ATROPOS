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
        ?: "python3",
    /** Test seam for a deliberately wrong root. Production reads the env var. */
    private val specGraphRootOverride: String? = null
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
        // SpecGraph lives in this repository at apps/specgraph-foundry. It used
        // to be reachable only through SPECGRAPH_ROOT, so an unset variable
        // skipped the canonical atomizer on every run while the atomizer sat
        // in the tree -- the plan fell back to the internal extractor and the
        // evidence said SPECGRAPH_ROOT_unset, which reads like configuration
        // rather than the defect it was.
        //
        // The environment variable still wins, for a checkout kept elsewhere.
        // Located from the ATROPOS installation, not from [repoRoot]. Callers
        // pass the *project being planned* as repoRoot -- for a factory run
        // that is a generated project under .atropos/generated-projects, and
        // resolving the atomizer against it looked for SpecGraph inside the
        // thing SpecGraph was supposed to plan. That reported root_missing on
        // every factory run, which reads like an absent install rather than a
        // path bug. repoRoot still bounds where the run's files are written.
        val specGraphRoot = specGraphRootOverride
            ?: System.getenv("SPECGRAPH_ROOT")?.trim()
            ?.takeIf(String::isNotBlank)
            // Resolved from the *installation*, not the working directory. This
            // used to call AtroposRepoRootLocator, which walks up from cwd -- so
            // despite the comment above, an operator running ATROPOS from their
            // own project directory (the ordinary case) got root_missing on
            // every run, and the canonical atomizer was reachable only from
            // inside the source tree.
            ?: atropos.core.AtroposInstallationLocator.resource(IN_REPO_SPECGRAPH)?.toString()
            ?: atropos.core.AtroposRepoRootLocator.resolve().resolve(IN_REPO_SPECGRAPH).toString()
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
                } else if (atoms.isEmpty()) {
                    // SpecGraph ran, understood the document, and found nothing
                    // in it to atomize. That used to fall through to the PASS
                    // branch below and record
                    // `PASS:canonical_specgraph_atomizer atom_count=0`, which is
                    // a success line for a stage that produced nothing and was
                    // then discarded -- `usable` is `atoms.isNotEmpty()`, so the
                    // internal extractor silently planned every one of these
                    // runs while the evidence said the canonical atomizer had
                    // worked.
                    //
                    // The cause is upstream and worth naming here rather than
                    // leaving to be rediscovered: `AtomService.extract_document`
                    // keys on modal requirement sentences (MUST/SHALL), and a
                    // `key=value` document contains none, so it yields zero
                    // atoms from a document that is not empty.
                    CanonicalAtomization(
                        atoms = emptyList(),
                        sourceSha256 = sourceSha,
                        documentId = documentId,
                        evidenceLine = "SKIPPED_SOFT_FAIL:no_atoms_extracted source_sha256=$sourceSha " +
                            "document=$documentId; source states no modal requirements; " +
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
        /** Where SpecGraph lives inside ATROPOS. */
        const val IN_REPO_SPECGRAPH = "apps/specgraph-foundry"

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
from specgraph_foundry.planning import PlanningService
from specgraph_foundry.services import ProjectService

def esc(value):
    return str(value).replace("\\", "\\\\").replace("\t", "\\t").replace("\n", "\\n")

def pick(atom, names, default=""):
    for name in names:
        if isinstance(atom, dict) and atom.get(name) not in (None, ""):
            return atom[name]
    return default

def coordinates(atom):
    # The atoms table carries byte and line spans, not a formatted coordinate.
    # Built here so the exact span survives into the DAG node's lineage, which
    # is what makes an atom traceable back to the sentence that produced it.
    line_start = pick(atom, ["line_start"], "")
    line_end = pick(atom, ["line_end"], "")
    byte_start = pick(atom, ["byte_start"], "")
    byte_end = pick(atom, ["byte_end"], "")
    document = pick(atom, ["document_id"], "document")
    span = ""
    if line_start != "" and line_end != "":
        span = "L%s-%s" % (line_start, line_end)
    if byte_start != "" and byte_end != "":
        span = (span + " " if span else "") + "B%s-%s" % (byte_start, byte_end)
    return "%s:%s" % (document, span or "unspanned")

def listed(value):
    if value in (None, ""):
        return ""
    if isinstance(value, (list, tuple)):
        return ",".join(str(item).replace(",", " ").strip() for item in value if str(item).strip())
    return str(value).replace("\n", " ").strip()

# Bytes, not read_text: Path.read_text applies universal-newline translation,
# so a CRLF source arrived here as LF and hashed differently from the bytes
# ATROPOS wrote. That read as source_hash_mismatch and discarded a valid
# atomization on any document with Windows line endings -- which the source
# documents have.
source_bytes = Path(sys.argv[2]).read_bytes()
source = source_bytes.decode("utf-8")
database = Database(Path(sys.argv[3]))
database.initialize()
# Database.initialize() applies the core schema only. extract_document reads
# authority_relations, which PlanningService creates on construction -- without
# this the atomizer exits 1 on "no such table: authority_relations" and the
# whole canonical path silently falls back to the internal extractor.
PlanningService(database)
slug = "atropos-factory-" + hashlib.sha256((source + sys.argv[3]).encode("utf-8")).hexdigest()[:16]
project = ProjectService(database).create(slug, "ATROPOS factory atomization")
document = IngestionService(database).ingest_text(str(project["id"]), "factory-requirements.md", source)
extraction = AtomService(database).extract_document(str(document["id"]))
atoms = extraction.get("atoms", []) or []

# The atom row carries SpecGraph's orthogonal *kind* -- SECURITY, DATA, API --
# which is an eight-value vocabulary, not the sixteen dimensions. ATROPOS reads
# a single `dimension` field, found none that matched, and defaulted every atom
# to FUNCTIONAL_CONTRACT. The result was a plan of nothing but contracts: no
# implementation or verification stage depended on anything, so a canonical
# atomization produced roots and no edges, and every node became a PROVIDER_CALL.
#
# The dimensions are real and are stored per atom in atom_dimensions, one row
# each, applicable or not. Read here rather than re-derived on the Kotlin side:
# determine_applicable_dimensions is the owner of that mapping and lives in this
# process, and a second copy across the boundary would drift the first time
# SpecGraph added a kind.
def applicable_dimensions(connection, atom_id):
    rows = connection.execute(
        "SELECT dimension FROM atom_dimensions "
        "WHERE atom_id = ? AND applicability != 'NOT_APPLICABLE'",
        (atom_id,),
    ).fetchall()
    return [str(row[0]) for row in rows]

# The most specific applicable dimension, or the contract when none is.
#
# FUNCTIONAL_CONTRACT is in every atom's applicable set as the baseline, so
# picking it whenever it is present would reproduce exactly the collapse this
# replaces. It is therefore the answer only when nothing more specific applies.
#
# Comments rather than a docstring: this script is embedded in a Kotlin raw
# string, and a Python triple-quote would end the literal.
def specific_dimension(connection, atom_id):
    names = applicable_dimensions(connection, atom_id)
    specific = sorted(n for n in names if n != "FUNCTIONAL_CONTRACT")
    return specific[0] if specific else "FUNCTIONAL_CONTRACT"

reported_schema = False
with database.connect() as connection:
    for atom in atoms:
        identifier = pick(atom, ["id", "atom_id", "uuid", "key"])
        statement = pick(atom, ["canonical_statement", "statement", "exact_quote", "text", "body"])
        if not identifier or not statement:
            if not reported_schema and isinstance(atom, dict):
                print("SCHEMA\t" + ",".join(sorted(str(k) for k in atom.keys())))
                reported_schema = True
            continue
        print("\t".join([
            "ATOM",
            esc(identifier),
            esc(specific_dimension(connection, identifier)),
            esc(pick(atom, ["section_id", "sectionId", "section"])),
            esc(coordinates(atom)),
            listed(pick(atom, ["dependencies", "depends_on", "requires"])),
            listed(pick(atom, ["territory", "paths", "files"])),
            esc(statement),
            esc(pick(atom, ["confidence"], "")),
        ]))

# input_sha256 is over the exact bytes this bridge handed across, not over
# SpecGraph's normalized copy of them. The old check compared ATROPOS's hash of
# what it sent against the document hash SpecGraph stored, so any normalization
# on the far side -- a stripped BOM, rewritten line endings -- read as a hash
# mismatch and discarded a perfectly good atomization.
print("\t".join([
    "META",
    str(len(atoms)),
    hashlib.sha256(source_bytes).hexdigest(),
    str(document["id"]),
    str(document["sha256"]),
]))
""".trimIndent()
    }
}
