/* SPDX-License-Identifier: AGPL-3.0-only */
package atropos.core.ingest

import java.io.File
import java.nio.file.Files
import java.nio.file.Path
import kotlin.io.path.createDirectories
import kotlin.io.path.createTempDirectory
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

/**
 * `@` has to be able to reach the file the operator is looking at.
 *
 * The launch directory was the entire boundary. On a phone that is the wrong
 * shape: the document lives in Downloads and the engine runs in a source tree,
 * so `@spec.docx` was refused as "outside every granted territory" for a file
 * the operator could see on the same screen — and the refusal did not say what
 * territory was.
 */
class IngestTerritoryTest {

    private fun sandbox(): Path = createTempDirectory("ingest-territory").also { it.toFile().deleteOnExit() }

    private fun territory(
        launch: Path,
        environment: Map<String, String> = emptyMap(),
        home: Path? = null
    ) = IngestTerritory(
        launchDirectory = launch,
        env = environment::get,
        homeDirectory = { home },
        realPath = { null }
    )

    @Test
    fun the_launch_directory_is_always_granted() {
        val launch = sandbox()

        assertEquals(
            listOf(IngestTerritory.Source.LAUNCH_DIRECTORY),
            territory(launch).roots().map { it.source }
        )
    }

    @Test
    fun the_environment_grants_a_root_for_this_run() {
        val launch = sandbox()
        val downloads = sandbox()

        val roots = territory(launch, mapOf("ATROPOS_INGEST_ROOTS" to downloads.toString())).roots()

        assertTrue(
            roots.any { it.path == downloads && it.source == IngestTerritory.Source.ENVIRONMENT },
            "the granted root was not honoured: $roots"
        )
    }

    @Test
    fun several_roots_may_be_granted_at_once() {
        val launch = sandbox()
        val first = sandbox()
        val second = sandbox()
        val joined = listOf(first, second).joinToString(File.pathSeparator)

        val paths = territory(launch, mapOf("ATROPOS_INGEST_ROOTS" to joined)).paths()

        assertTrue(first in paths && second in paths, "only one of two roots was granted: $paths")
    }

    @Test
    fun a_workspace_file_grants_a_root_durably() {
        val launch = sandbox()
        val documents = sandbox()
        launch.resolve(".atropos").createDirectories()
        Files.writeString(
            launch.resolve(".atropos/ingest-roots"),
            "# where the specs live\n\n$documents\n"
        )

        val roots = territory(launch).roots()

        assertTrue(
            roots.any { it.path == documents && it.source == IngestTerritory.Source.WORKSPACE_FILE },
            "the configured root was not read: $roots"
        )
    }

    @Test
    fun android_shared_storage_is_granted_when_the_operator_has_already_set_it_up() {
        // These links exist only after `termux-setup-storage` and an OS
        // permission dialog. Honouring a grant the operator already made is
        // not the same as awarding one.
        val launch = sandbox()
        val home = sandbox()
        home.resolve("storage/downloads").createDirectories()

        val roots = territory(launch, home = home).roots()

        assertTrue(
            roots.any {
                it.path == home.resolve("storage/downloads") &&
                    it.source == IngestTerritory.Source.SHARED_STORAGE
            },
            "Downloads was not reachable: $roots"
        )
    }

    @Test
    fun storage_links_that_do_not_exist_are_not_granted() {
        val launch = sandbox()
        val home = sandbox()

        assertEquals(
            listOf(IngestTerritory.Source.LAUNCH_DIRECTORY),
            territory(launch, home = home).roots().map { it.source }
        )
    }

    @Test
    fun a_root_that_is_not_a_directory_is_ignored_rather_than_granted() {
        val launch = sandbox()
        val file = launch.resolve("notes.txt").also { Files.writeString(it, "x") }

        assertEquals(listOf(launch), territory(launch, mapOf("ATROPOS_INGEST_ROOTS" to file.toString())).paths())
    }

    @Test
    fun a_mention_inside_a_granted_root_resolves() {
        val launch = sandbox()
        val downloads = sandbox()
        val spec = downloads.resolve("spec.md").also { Files.writeString(it, "# spec") }

        val resolver = MentionResolver(
            territoryRoots = territory(launch, mapOf("ATROPOS_INGEST_ROOTS" to downloads.toString())).paths()
        )

        assertEquals(
            MentionResolution.Resolved(spec, "md"),
            resolver.resolve("@$spec", Files.size(spec))
        )
    }

    @Test
    fun a_refusal_names_the_boundary_it_hit() {
        val launch = sandbox()
        val outside = sandbox().resolve("elsewhere.md")
        val granted = territory(launch)

        val refusal = MentionResolver(
            territoryRoots = granted.paths(),
            describeTerritory = granted::describe
        ).resolve("@$outside", 10) as MentionResolution.Refused

        assertTrue(
            refusal.remedy.contains(launch.toString()),
            "the refusal did not say where the boundary was: ${refusal.remedy}"
        )
        assertTrue(
            refusal.remedy.contains("ATROPOS_INGEST_ROOTS"),
            "the refusal did not say how to widen it: ${refusal.remedy}"
        )
    }
}
