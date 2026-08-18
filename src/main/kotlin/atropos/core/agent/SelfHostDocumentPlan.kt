/* SPDX-License-Identifier: AGPL-3.0-only */
package atropos.core.agent

import atropos.core.factory.CanonicalAtomRecord
import atropos.core.factory.CanonicalAtomization
import atropos.core.factory.SpecGraphAtomizer
import atropos.core.ingest.AttachmentReader
import atropos.core.ingest.IngestTerritory
import atropos.core.ingest.MentionResolution
import atropos.core.thinking.Narrate
import java.nio.file.Files
import java.nio.file.Path

/**
 * The document a self-host goal was pointed at, turned into atoms.
 *
 * A self-host run used to get the same three-node cradle DAG whatever the
 * operator asked for. Attaching a four-hundred-atom specification and asking
 * ATROPOS to build it produced: probe the tree, write one marker file, write
 * its test. The document reached the run as `record.task.take(80)` -- a label
 * on the DAG. It was never read.
 *
 * That is the whole of "ATROPOS cannot build itself from a document". Not a
 * missing extractor: the extractor works and produces 390 atoms from the
 * operator's document. Nothing asked it.
 *
 * This asks it. Resolution is deliberately narrow -- a path that exists, inside
 * territory, with an extension something can read -- and every way of failing
 * returns null so the caller falls back to the cradle DAG rather than starting
 * a run against a document it only half understood.
 */
class SelfHostDocumentPlan(
    private val repoRoot: Path,
    /**
     * Atomization, as a function.
     *
     * A seam rather than the class itself so a test can state the atoms and
     * assert the graph without a python interpreter, a SpecGraph checkout, or
     * the ninety seconds a real atomization of a large document takes.
     */
    private val atomize: (Path, String, String, String, String) -> CanonicalAtomization =
        { root, projectId, source, fingerprint, spans ->
            SpecGraphAtomizer().atomizeToRecords(root, projectId, source, fingerprint, spans)
        },
    /**
     * The reader, with a far higher ceiling than the prompt path uses.
     *
     * [AttachmentReader]'s default clips at 200,000 characters because that is
     * how much of a file belongs in one model request without crowding out the
     * operator's own words. Nothing here goes to a model: the text goes to the
     * atomizer, and clipping it silently drops the atoms below the cut from a
     * plan that would then finish and be wrong. The ingest ceiling still bounds
     * what may enter at all.
     */
    private val attachmentReader: AttachmentReader = AttachmentReader(maxPromptChars = MAXIMUM_DOCUMENT_CHARACTERS),
    private val maximumAtoms: Int = DEFAULT_MAXIMUM_ATOMS,
    private val territory: IngestTerritory = IngestTerritory(repoRoot)
) {

    data class Atomized(
        val source: Path,
        val text: String,
        val atoms: List<CanonicalAtomRecord>,
        val evidenceLine: String
    )

    /**
     * The atoms of the document [task] names, or null when it names none this
     * run can read.
     */
    fun atomize(goalId: String, task: String): Atomized? {
        val source = locate(task) ?: return null
        Narrate.ingest.stage("self-host goal $goalId points at $source")

        val attachment = attachmentReader.read(
            MentionResolution.Resolved(source, source.fileName.toString().substringAfterLast('.', ""))
        )
        val text = attachment?.text
        if (text.isNullOrBlank()) {
            Narrate.ingest.skipped(
                source.fileName.toString(),
                "nothing readable came out of it — self-host falls back to the cradle graph"
            )
            return null
        }
        if (attachment.truncated) {
            // Said, not swallowed. A plan built from the first fifth of a
            // specification is a plan that will finish and be wrong.
            Narrate.ingest.trouble(
                "${source.fileName} was truncated at ${text.length} characters",
                "atoms below that point are not in this graph"
            )
        }
        Narrate.ingest.counted("characters read", text.length)

        val atomization = atomize(
            repoRoot,
            goalId,
            text,
            attachment.sha256.take(16),
            "document:${source.fileName}"
        )
        if (!atomization.usable) {
            Narrate.atomize.skipped(
                "atomization of ${source.fileName}",
                atomization.evidenceLine
            )
            return null
        }

        val atoms = atomization.atoms.take(maximumAtoms)
        if (atoms.size < atomization.atoms.size) {
            Narrate.atomize.trouble(
                "${atomization.atoms.size} atoms is over the per-run ceiling of $maximumAtoms",
                "planning the first $maximumAtoms; the rest need a second run"
            )
        }
        Narrate.atomize.counted("atoms to plan", atoms.size, of = atomization.atoms.size)
        return Atomized(source, text, atoms, atomization.evidenceLine)
    }

    /**
     * The document [task] names.
     *
     * Both spellings, because the operator writes one and the CLI may have
     * rewritten it into the other: `@docs/spec.md` as typed, and the bare path
     * once the mention machinery has stripped the sigil.
     *
     * Bounded by [IngestTerritory], which is already the one owner of "where
     * may this install read from" -- the launch directory, `ATROPOS_INGEST_ROOTS`
     * and the Termux shared-storage links when they exist (AGENTS.md 0.7).
     * Restricting this to the repository instead would have been simpler and
     * would have refused the actual case: an operator who attaches a
     * specification out of their phone's Downloads folder. Traversal out of
     * every granted root is refused rather than normalised, because a `..` in
     * a goal prompt is not a typo worth being helpful about.
     */
    private fun locate(task: String): Path? {
        val roots = (listOf(repoRoot) + territory.paths())
            .map { it.toAbsolutePath().normalize() }
            .distinct()

        return CANDIDATE_PATTERN.findAll(task)
            .map { it.value.removePrefix("@").trim(',', ';', ')', '"', '\'') }
            .filter { it.isNotBlank() }
            .flatMap { candidate ->
                roots.asSequence().mapNotNull { root ->
                    runCatching { root.resolve(candidate).normalize() }
                        .getOrNull()
                        ?.takeIf { it.startsWith(root) }
                }
            }
            .firstOrNull { Files.isRegularFile(it) }
    }

    private companion object {
        /**
         * A ceiling on one run's graph.
         *
         * Not a judgement about the document: a node is a directory entry, a
         * meta file and a share of an advance budget, and several thousand of
         * them on a phone is a run that cannot finish. Over the ceiling the
         * operator is told the number and told a second run is needed, which is
         * true and actionable, rather than being handed a graph that stalls.
         */
        const val DEFAULT_MAXIMUM_ATOMS = 600

        /** Well above any specification, well below the 8 MiB ingest ceiling. */
        const val MAXIMUM_DOCUMENT_CHARACTERS = 4_000_000

        /** `@path/to/file.ext` or the bare path, with a readable extension. */
        val CANDIDATE_PATTERN = Regex("""@?[\w./\-]+\.(?:md|txt|docx|pdf)\b""", RegexOption.IGNORE_CASE)
    }
}
