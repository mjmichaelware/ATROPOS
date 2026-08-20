/* SPDX-License-Identifier: AGPL-3.0-only */
package atropos.core.factory

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

/**
 * The tree a document declares, built as the document declares it.
 *
 * A build specification states its layout as an indented listing -- the most
 * literal statement of work a document can make -- and the factory laid out
 * whatever its own scaffold decided instead. A specification naming two
 * hundred files produced eleven of someone else's, and every file a provider
 * wrote afterwards landed in a tree the document never described.
 */
class DeclaredProjectTreeTest {

    private val blueprint = """
        MUSIC MAKERLM — BUILD BLUEPRINT

        - Engine language: Python 3.11+
        - Web framework: FastAPI + Uvicorn

        STARTING TREE

        musicmakerlm/
          README.md
          requirements.txt
          app/
            __init__.py
            main.py
            routes/
              generate.py
              analyze.py
            core/
              analysis.py
          tests/
            test_generation.py
    """.trimIndent()

    @Test
    fun every_declared_path_is_read() {
        val entries = DeclaredProjectTree.read(blueprint)

        // Eight files and the four directories holding them: a declared
        // directory with nothing in it still has to be created.
        assertEquals(8, entries.count { !it.isDirectory }, entries.map { it.path }.toString())
        assertEquals(4, entries.count { it.isDirectory }, entries.map { it.path }.toString())
    }

    @Test
    fun indentation_is_the_parent_link() {
        // A bare `generate.py` cannot tell a build where to put itself.
        val paths = DeclaredProjectTree.read(blueprint).map { it.path }

        assertTrue("app/routes/generate.py" in paths, paths.toString())
        assertTrue("tests/test_generation.py" in paths, paths.toString())
    }

    @Test
    fun the_wrapper_directory_the_listing_is_rooted_at_is_dropped() {
        // The generated repository *is* the project; keeping its own name would
        // put every file inside a folder named after the folder it is in.
        val paths = DeclaredProjectTree.read(blueprint).map { it.path }

        assertFalse(paths.any { it.startsWith("musicmakerlm/") }, paths.toString())
        assertTrue("README.md" in paths, paths.toString())
    }

    @Test
    fun a_module_a_later_tree_turned_into_a_package_is_dropped() {
        // A document that states a starting layout and a finished one grows
        // some modules into packages. In Python the two shadow each other.
        val document = blueprint + "\n\nDEPLOYED TREE\n\n" + """
            musicmakerlm/
              app/
                core/
                  analysis/
                    __init__.py
                    key_mode.py
        """.trimIndent()
        val paths = DeclaredProjectTree.read(document).map { it.path }

        assertFalse("app/core/analysis.py" in paths, paths.toString())
        assertTrue("app/core/analysis/key_mode.py" in paths, paths.toString())
    }

    @Test
    fun prose_is_not_mistaken_for_a_tree() {
        assertEquals(emptyList(), DeclaredProjectTree.read("Build me a tool that tracks expenses."))
    }

    @Test
    fun a_flat_column_of_words_is_not_a_tree() {
        // No directory and no indentation: a list of something else.
        assertEquals(emptyList(), DeclaredProjectTree.read("alpha\nbeta\ngamma\ndelta\n"))
    }

    @Test
    fun box_drawing_indentation_reads_the_same_as_spaces() {
        val paths = DeclaredProjectTree.read(
            "project/\n├── src/\n│   ├── main.py\n│   └── util.py\n└── README.md\n"
        ).map { it.path }

        assertTrue("src/main.py" in paths, paths.toString())
        assertTrue("README.md" in paths, paths.toString())
    }

    @Test
    fun a_trailing_comment_is_kept_without_becoming_part_of_the_path() {
        val entry = DeclaredProjectTree.read(
            "project/\n  app/\n    config.py    # reads LLM_PROVIDER\n    main.py\n"
        ).single { it.path == "app/config.py" }

        assertEquals("reads LLM_PROVIDER", entry.comment)
    }

    @Test
    fun the_generated_repository_is_the_declared_tree() {
        val spec = AppProjectSpec(
            prompt = blueprint,
            intent = AppIntent(name = "musicmakerlm", kind = "cli", features = listOf("generate"))
        )
        val files = RepoScaffold().files(spec)

        assertTrue("app/routes/generate.py" in files, files.keys.toString())
        assertTrue("app/routes/analyze.py" in files, files.keys.toString())
        assertTrue("requirements.txt" in files, files.keys.toString())
    }

    @Test
    fun the_seed_program_goes_to_the_entry_point_the_document_named() {
        // Ranked rather than first-matched: the document lists its entry point
        // beside forty other modules.
        val spec = AppProjectSpec(
            prompt = blueprint,
            intent = AppIntent(name = "musicmakerlm", kind = "cli", features = listOf("generate"))
        )
        val layout = ProjectLayout.resolve(spec)

        assertEquals("app/main.py", layout.sourcePath)
        assertEquals("tests/test_generation.py", layout.testPath)
        assertEquals("app.main", layout.importReference)
    }

    @Test
    fun verification_runs_the_files_the_project_actually_has() {
        // A verify.sh naming the scaffold's paths in a document's tree fails
        // the repository for a reason unrelated to its code.
        val spec = AppProjectSpec(
            prompt = blueprint,
            intent = AppIntent(name = "musicmakerlm", kind = "cli", features = listOf("generate"))
        )
        val verify = RepoScaffold().files(spec).getValue("verify.sh")

        assertTrue("tests" in verify, verify)
        assertFalse("test_musicmakerlm.py" in verify, verify)
    }

    @Test
    fun a_document_is_named_and_described_by_its_own_structure() {
        // The first twelve meaningful words of a ten-kilobyte specification
        // became the generated application's commands, so the CLI offered
        // `built`, `during` and `monetization` as things to run, and the
        // application was named after whichever word came first.
        val intent = IntentParser().parse(blueprint)

        assertEquals("musicmakerlm", intent.name)
        assertTrue("routes" in intent.features, intent.features.toString())
        assertTrue("core" in intent.features, intent.features.toString())
        assertFalse("engine" in intent.features, intent.features.toString())
        assertFalse("tests" in intent.features, intent.features.toString())
    }

    @Test
    fun a_short_request_is_still_read_as_a_request() {
        val intent = IntentParser().parse("build me a todo list app")

        assertEquals("todo", intent.name)
        assertTrue("todo" in intent.features, intent.features.toString())
    }

    @Test
    fun a_document_declaring_no_tree_still_gets_the_language_layout() {
        val spec = AppProjectSpec(
            prompt = "a Python 3.11 tool built with pytest and pyproject.toml",
            intent = AppIntent(name = "trackr", kind = "cli", features = listOf("report"))
        )
        val layout = ProjectLayout.resolve(spec)

        assertFalse(layout.declaresItsOwnTree)
        assertEquals("trackr/__init__.py", layout.sourcePath)
    }
}
