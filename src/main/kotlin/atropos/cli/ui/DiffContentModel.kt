/* SPDX-License-Identifier: AGPL-3.0-only */
package atropos.cli.ui

/**
 * Typed model for unified diffs, parsed from raw diff text.
 *
 * Pure data: no parsing logic, no rendering logic, no I/O. Parsing lives
 * in [DiffContentParser]; rendering lives in [DiffContentRenderer].
 *
 * The model mirrors the structure of a unified diff:
 *   DiffContent → FileDiff* → DiffHunk* → DiffLine*
 *
 * Every line carries its kind so the renderer can paint it through the
 * design system's DIFF_ADD / DIFF_REMOVE / DIFF_CONTEXT / DIFF_HUNK roles
 * without re-parsing the raw text.
 */

/** One line of a diff, classified by kind. */
data class DiffLine(
    val kind: Kind,
    val text: String,
    /** 1-based line number in the old file, or null for additions. */
    val oldLineNumber: Int? = null,
    /** 1-based line number in the new file, or null for removals. */
    val newLineNumber: Int? = null
) {
    enum class Kind {
        /** A context line, unchanged between old and new. */
        CONTEXT,
        /** A line added in the new file. */
        ADD,
        /** A line removed from the old file. */
        REMOVE,
        /** A hunk header (`@@ -a,b +c,d @@`). */
        HUNK_HEADER,
        /** The `--- a/path` header. */
        OLD_FILE,
        /** The `+++ b/path` header. */
        NEW_FILE,
        /** `diff --git ...` or similar preamble. */
        META,
        /** `\ No newline at end of file`. */
        NO_NEWLINE
    }
}

/** One hunk: a `@@ ... @@` block and the lines it contains. */
data class DiffHunk(
    /** The raw header line, e.g. `@@ -10,7 +10,9 @@ fun example()`. */
    val header: String,
    /** Parsed old-file start line (1-based). */
    val oldStart: Int,
    /** Parsed old-file line count. */
    val oldCount: Int,
    /** Parsed new-file start line (1-based). */
    val newStart: Int,
    /** Parsed new-file line count. */
    val newCount: Int,
    /** Optional section label after the second `@@`. */
    val sectionLabel: String?,
    /** The lines within this hunk, in order. */
    val lines: List<DiffLine>
) {
    val additions: Int get() = lines.count { it.kind == DiffLine.Kind.ADD }
    val deletions: Int get() = lines.count { it.kind == DiffLine.Kind.REMOVE }
}

/** One file's diff: the pair of paths and all its hunks. */
data class FileDiff(
    /** Path in the old tree (`a/...`), or `/dev/null` for new files. */
    val oldPath: String,
    /** Path in the new tree (`b/...`), or `/dev/null` for deleted files. */
    val newPath: String,
    /** The hunks, in order. */
    val hunks: List<DiffHunk>,
    /** Any preamble lines before the first hunk (e.g. `diff --git`, index). */
    val preamble: List<String> = emptyList()
) {
    /** Display path: the new path unless it is `/dev/null`, in which case the old. */
    val displayPath: String
        get() = newPath.removePrefix("b/").takeIf { it != "/dev/null" }
            ?: oldPath.removePrefix("a/")

    val isNewFile: Boolean get() = oldPath == "/dev/null"
    val isDeletedFile: Boolean get() = newPath == "/dev/null"
    val isRename: Boolean
        get() = oldPath.removePrefix("a/") != newPath.removePrefix("b/") &&
            !isNewFile && !isDeletedFile

    val totalAdditions: Int get() = hunks.sumOf { it.additions }
    val totalDeletions: Int get() = hunks.sumOf { it.deletions }
}

/** A complete diff, possibly spanning multiple files. */
data class DiffContent(
    val files: List<FileDiff>
) {
    val totalFiles: Int get() = files.size
    val totalAdditions: Int get() = files.sumOf { it.totalAdditions }
    val totalDeletions: Int get() = files.sumOf { it.totalDeletions }

    companion object {
        val EMPTY = DiffContent(emptyList())
    }
}
