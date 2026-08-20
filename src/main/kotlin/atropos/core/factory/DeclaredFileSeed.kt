/* SPDX-License-Identifier: AGPL-3.0-only */
package atropos.core.factory

/**
 * What to write into a file the document named but did not fill in.
 *
 * A declared tree says a file exists; it rarely says what is in it. An empty
 * file is wrong for most formats -- an empty `pyproject.toml` breaks the build
 * it configures, an empty `package.json` is not JSON -- so each one gets the
 * smallest content that is valid for its kind and says where it came from.
 *
 * This is a skeleton, not the application. The DAG fills these in through
 * provider patches; what the seed has to do is exist at the right path, in a
 * form its own toolchain will parse.
 */
object DeclaredFileSeed {

    fun contentFor(entry: DeclaredProjectTree.Entry): String {
        val name = entry.path.substringAfterLast('/')
        val note = entry.comment.ifBlank { "Declared by the source document." }
        return when {
            name == ".gitkeep" || name.isEmpty() -> ""
            name == "package.json" -> "{\n  \"name\": \"${parentName(entry.path)}\",\n  \"version\": \"0.1.0\"\n}\n"
            name.endsWith(".json") -> "{}\n"
            name == "Makefile" -> "# $note\n\n.PHONY: help\nhelp:\n\t@echo 'no targets yet'\n"
            name == "Dockerfile" -> "# $note\nFROM scratch\n"
            name.startsWith(".env") -> "# $note\n"
            name == ".gitignore" -> "# $note\n"
            else -> when (name.substringAfterLast('.', "")) {
                "py" -> pythonSeed(entry, note)
                "ts", "js", "kt", "java", "go", "rs", "cs", "swift", "cpp", "hpp", "c", "h", "php" ->
                    "// $note\n"
                "rb" -> "# frozen_string_literal: true\n\n# $note\n"
                "sh" -> "#!/usr/bin/env sh\n# $note\nset -eu\n"
                "md" -> "# ${name.removeSuffix(".md")}\n\n$note\n"
                "toml", "cfg", "ini", "yml", "yaml", "txt" -> "# $note\n"
                "html" -> "<!-- $note -->\n"
                "css" -> "/* $note */\n"
                "xml" -> "<!-- $note -->\n"
                "sql" -> "-- $note\n"
                else -> ""
            }
        }
    }

    /**
     * A Python file that is safe to import.
     *
     * `__init__.py` is left empty on purpose: a docstring is harmless, but an
     * empty package initialiser is what every Python project actually has.
     */
    private fun pythonSeed(entry: DeclaredProjectTree.Entry, note: String): String =
        if (entry.path.endsWith("__init__.py")) ""
        else "\"\"\"${entry.path.substringAfterLast('/').removeSuffix(".py")}.\n\n$note\n\"\"\"\n"

    private fun parentName(path: String): String =
        path.substringBeforeLast('/', "app").substringAfterLast('/').ifBlank { "app" }
}
