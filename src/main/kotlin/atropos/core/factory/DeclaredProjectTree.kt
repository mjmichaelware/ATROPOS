/* SPDX-License-Identifier: AGPL-3.0-only */
package atropos.core.factory

/**
 * The files and folders a document says the project has.
 *
 * A build specification states its layout as an indented listing, not as
 * sentences -- and that listing is the most literal statement of work the
 * document contains. The factory ignored it completely and laid out whatever
 * its own language scaffold decided, so a specification naming two hundred
 * files produced eleven of someone else's.
 *
 * Recognition is by shape, so it holds for any document rather than for the
 * ones in front of us: a run of lines that each name a path, at least one of
 * them a directory, at least one of them indented. Indentation is the parent
 * link, which is what turns `generate.py` at depth three into
 * `app/routes/generate.py` -- a bare filename cannot tell a build where to put
 * itself.
 *
 * `specgraph_foundry.compiler.block_structures` recognises the same shape on
 * the way to atoms. They are deliberately separate readers with the same
 * rules: SpecGraph answers what work the document declares, and this answers
 * what tree to create, and the second must keep working on a machine with no
 * SpecGraph installed.
 */
object DeclaredProjectTree {

    /** One entry of a declared tree. */
    data class Entry(
        val path: String,
        val isDirectory: Boolean,
        val comment: String = ""
    )

    private const val MIN_TREE_LINES = 3
    private const val INDENT_UNIT = 2
    private const val MAX_ENTRIES = 2_000

    private val LINE = Regex(
        "^(?<indent>[ \\t]*)" +
            "(?<branches>(?:[|│]\\s{0,3}|├──\\s?|└──\\s?|\\+--\\s?|`--\\s?)*)" +
            "(?<name>[A-Za-z0-9_.][A-Za-z0-9_./\\-]*/?)" +
            "(?<trailing>\\s*(?:#.*)?)$"
    )

    private val BRANCH = Regex("[|│]|├──|└──|\\+--|`--")

    /**
     * Every path the document declares, in document order, deduplicated.
     *
     * A document may state more than one tree -- a starting layout and the
     * finished one. They are unioned rather than picked between: the finished
     * tree is what the project becomes, and the starting tree is a subset of
     * it in every specification that states both.
     */
    fun read(document: String): List<Entry> {
        val lines = document.replace("\r\n", "\n").replace('\r', '\n').split("\n")
        val entries = LinkedHashMap<String, Entry>()
        var index = 0
        while (index < lines.size) {
            val block = treeAt(lines, index)
            if (block == null) {
                index++
                continue
            }
            block.second.forEach { entry -> entries.putIfAbsent(entry.path, entry) }
            index = block.first
            if (entries.size >= MAX_ENTRIES) break
        }
        return stripSharedRoot(withoutModulePackageCollisions(entries.values.toList()))
    }

    /** The block starting at [start], as (next index, entries), or null. */
    private fun treeAt(lines: List<String>, start: Int): Pair<Int, List<Entry>>? {
        val matched = mutableListOf<MatchResult>()
        var index = start
        var blankRun = 0
        while (index < lines.size) {
            val line = lines[index]
            if (line.isBlank()) {
                // One blank line inside a listing is a break in the source, not
                // the end of it; two mean the listing is over.
                blankRun++
                if (blankRun > 1 || matched.isEmpty()) break
                index++
                continue
            }
            val match = LINE.matchEntire(line) ?: break
            blankRun = 0
            matched += match
            index++
        }
        if (matched.size < MIN_TREE_LINES || !looksLikeATree(matched)) return null
        return (start + matched.size) to entriesOf(matched)
    }

    /**
     * A directory listing rather than a column of one-word lines.
     *
     * Without both tests, any run of short lines -- a list of options, a
     * glossary column -- would be read as a project layout.
     */
    private fun looksLikeATree(matched: List<MatchResult>): Boolean {
        val names = matched.map { it.groups["name"]!!.value }
        if (names.none { it.endsWith("/") }) return false
        if (matched.none { it.groups["indent"]!!.value.isNotEmpty() || it.groups["branches"]!!.value.isNotEmpty() }) {
            return false
        }
        // Mostly filenames and directories. Requiring it of every line would
        // reject `Makefile` and `LICENSE`, which are neither.
        val structured = names.count { it.endsWith("/") || it.contains('.') || it.contains('/') }
        return structured * 2 >= names.size
    }

    private fun entriesOf(matched: List<MatchResult>): List<Entry> {
        val entries = mutableListOf<Entry>()
        val open = ArrayDeque<Pair<Int, String>>()
        matched.forEach { match ->
            val depth = depthOf(match)
            val name = match.groups["name"]!!.value
            val comment = match.groups["trailing"]!!.value.trim().removePrefix("#").trim()

            while (open.isNotEmpty() && open.last().first >= depth) open.removeLast()
            val prefix = open.lastOrNull()?.second.orEmpty()
            val path = prefix + name
            if (name.endsWith("/")) open.addLast(depth to path)

            entries += Entry(path.trimEnd('/'), name.endsWith("/"), comment)
        }
        return entries
    }

    private fun depthOf(match: MatchResult): Int {
        val indent = match.groups["indent"]!!.value.replace("\t", "    ")
        val branches = match.groups["branches"]!!.value
        val levels = indent.length / INDENT_UNIT
        return if (branches.isEmpty()) levels else levels + BRANCH.findAll(branches).count()
    }

    /**
     * Drop a file that a later tree turned into a directory.
     *
     * A document that states a starting layout and a finished one grows some
     * modules into packages on the way: `core/analysis.py` becomes
     * `core/analysis/`. Unioning the two produces both, and in Python a module
     * and a package of the same name at the same level shadow each other --
     * whichever wins, one of them is dead code the document did not ask for.
     * The directory is the later shape, so the directory wins.
     */
    private fun withoutModulePackageCollisions(entries: List<Entry>): List<Entry> {
        val directories = entries.filter { it.isDirectory }.map { it.path }.toSet()
        if (directories.isEmpty()) return entries
        return entries.filterNot { entry ->
            !entry.isDirectory && entry.path.substringBeforeLast('.', "") in directories
        }
    }

    /**
     * Drop the wrapper directory the listing is rooted at.
     *
     * A tree usually opens with the project's own name, and the generated
     * repository *is* that project -- keeping it would put every file one
     * directory too deep, inside a folder named after the folder it is in.
     */
    private fun stripSharedRoot(entries: List<Entry>): List<Entry> {
        if (entries.isEmpty()) return entries
        val roots = entries.map { it.path.substringBefore('/') }.distinct()
        if (roots.size != 1) return entries
        val root = roots.single()
        if (entries.none { it.path.startsWith("$root/") }) return entries
        return entries.mapNotNull { entry ->
            if (entry.path == root) null
            else entry.copy(path = entry.path.removePrefix("$root/"))
        }
    }
}
