/* SPDX-License-Identifier: AGPL-3.0-only */
package atropos.core.factory

import java.nio.charset.StandardCharsets
import java.nio.file.Files
import java.nio.file.Path
import java.nio.file.StandardCopyOption

/**
 * The canonical atomizer, carried inside the jar.
 *
 * Installing ATROPOS used to give you the Kotlin engine and nothing else, so
 * `SPECGRAPH_ROOT` was unset on every fresh machine and [SpecGraphAtomizer]
 * soft-failed to the weaker internal extractor. The run still succeeded, still
 * produced a DAG, and produced far fewer atoms than the document contained —
 * the headline capability degraded quietly, and the only sign was a
 * `specgraph_status=SKIPPED_SOFT_FAIL` line in evidence that nobody reads until
 * something is already wrong.
 *
 * Expecting an operator to `pkg install python && pip install -e .` on a phone
 * before the tool works is not an install story. The atomizer is part of the
 * product, so it ships with the product.
 *
 * ## Why source rather than a wheel
 *
 * The atom path imports nothing outside the standard library — `pypdf` and
 * `fpdf2` are pulled in lazily by the PDF *renderer*, which atomization never
 * touches. So the tree needs no `pip`, no network, and no native wheel, which
 * matters most on the aarch64 device this is built for: `cffi` would need a
 * compiled `_cffi_backend`, and that is exactly the kind of dependency that
 * cannot be shipped portably inside a jar.
 *
 * A `python3` interpreter is still required. That is the one thing this cannot
 * carry, and [SpecGraphAtomizer] already reports its absence honestly.
 *
 * ## Why it is extracted rather than read from the jar
 *
 * Python imports from a filesystem path. The bootstrap does
 * `sys.path.insert(0, root/"src")`, so the tree has to exist as files. It is
 * unpacked once into the operator's home, keyed by the build's own content
 * hash, so upgrading the jar re-extracts and downgrading finds its own copy
 * still there.
 */
object BundledSpecGraph {

    /**
     * The directory to hand [SpecGraphAtomizer] as its SpecGraph root, or null
     * when this jar carries no bundle.
     *
     * Null is an ordinary answer, not a failure: a development build running
     * from classes has the real tree on disk and does not need this at all.
     */
    fun root(): Path? = synchronized(this) {
        resolved ?: locate().also { resolved = it }
    }

    private var resolved: Path? = null

    private fun locate(): Path? {
        val index = resource(INDEX_RESOURCE) ?: return null
        val files = index.lineSequence()
            .map(String::trim)
            .filter { it.isNotEmpty() && !it.startsWith("#") }
            .toList()
        if (files.isEmpty()) return null

        // Keyed by the index's own hash. Two jars with different Python carry
        // different indexes and therefore never share an extraction directory,
        // so an upgrade cannot leave a half-old tree behind.
        val fingerprint = sha256(index).take(16)
        val target = home().resolve(".atropos/specgraph/$fingerprint")
        val marker = target.resolve(".complete")

        if (Files.isRegularFile(marker)) return target

        return runCatching { extract(files, target, marker) }.getOrNull()
    }

    /**
     * Unpacks the tree, writing the completion marker last.
     *
     * The marker is what makes a partial extraction — a process killed
     * mid-write, a full disk — detectable on the next run rather than silently
     * trusted, which would surface as a Python ImportError deep inside a
     * factory run.
     */
    private fun extract(files: List<String>, target: Path, marker: Path): Path? {
        val staging = target.resolveSibling(target.fileName.toString() + ".partial")
        runCatching { deleteTree(staging) }
        Files.createDirectories(staging)

        // Under `src/`, because the atomizer's bootstrap does
        // `sys.path.insert(0, root/"src")`. Extracting flat would work only if
        // that contract changed, and the contract is the reason nothing else
        // in the atomizer has to know this bundle exists.
        val packages = staging.resolve("src")

        files.forEach { relative ->
            // Path traversal is not a real threat from a resource this jar
            // built itself, but the check costs nothing and this writes files.
            val destination = packages.resolve(relative).normalize()
            require(destination.startsWith(packages)) { "bundled path escapes: $relative" }

            val bytes = javaClass.classLoader
                .getResourceAsStream(RESOURCE_PREFIX + relative)
                ?.use { it.readBytes() }
                ?: return null

            Files.createDirectories(destination.parent)
            Files.write(destination, bytes)
        }

        Files.writeString(staging.resolve(".complete"), files.size.toString())

        if (Files.exists(target)) runCatching { deleteTree(target) }
        Files.move(staging, target, StandardCopyOption.ATOMIC_MOVE)

        return if (Files.isRegularFile(marker)) target else null
    }

    private fun deleteTree(path: Path) {
        if (!Files.exists(path)) return
        Files.walk(path).use { stream ->
            stream.sorted(Comparator.reverseOrder()).forEach { entry ->
                runCatching { Files.deleteIfExists(entry) }
            }
        }
    }

    private fun resource(name: String): String? =
        javaClass.classLoader.getResourceAsStream(name)
            ?.use { it.readBytes().toString(StandardCharsets.UTF_8) }

    private fun sha256(value: String): String =
        java.security.MessageDigest.getInstance("SHA-256")
            .digest(value.toByteArray(StandardCharsets.UTF_8))
            .joinToString("") { "%02x".format(it) }

    private fun home(): Path =
        Path.of(System.getProperty("user.home") ?: ".").toAbsolutePath().normalize()

    private const val RESOURCE_PREFIX = "specgraph/"
    private const val INDEX_RESOURCE = "specgraph/INDEX"
}
