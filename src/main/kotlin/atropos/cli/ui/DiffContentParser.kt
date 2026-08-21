/* SPDX-License-Identifier: AGPL-3.0-only */
package atropos.cli.ui

/**
 * Parses raw unified diff text into a [DiffContent] model.
 *
 * Handles standard `diff --git` format, `--- a/` / `+++ b/` headers,
 * `@@ -old,count +new,count @@` hunk headers, and the five line kinds.
 *
 * Tolerant: malformed input produces a best-effort parse rather than an
 * exception, because provider-generated diffs are not guaranteed to be
 * well-formed.
 */
class DiffContentParser {

    private val hunkPattern = Regex(
        """^@@\s+-(\d+)(?:,(\d+))?\s+\+(\d+)(?:,(\d+))?\s+@@(.*)$"""
    )

    fun parse(diffText: String): DiffContent {
        if (diffText.isBlank()) return DiffContent.EMPTY

        val lines = diffText.lines()
        val files = mutableListOf<FileDiff>()
        var preamble = mutableListOf<String>()
        var oldPath: String? = null
        var newPath: String? = null
        var hunks = mutableListOf<DiffHunk>()
        var currentHunkLines = mutableListOf<DiffLine>()
        var currentHunkHeader: HunkHeader? = null
        var oldLine = 0
        var newLine = 0

        fun flushHunk() {
            val header = currentHunkHeader ?: return
            hunks.add(
                DiffHunk(
                    header = header.raw,
                    oldStart = header.oldStart,
                    oldCount = header.oldCount,
                    newStart = header.newStart,
                    newCount = header.newCount,
                    sectionLabel = header.sectionLabel,
                    lines = currentHunkLines.toList()
                )
            )
            currentHunkLines = mutableListOf()
            currentHunkHeader = null
        }

        fun flushFile() {
            flushHunk()
            if (oldPath != null || newPath != null) {
                files.add(
                    FileDiff(
                        oldPath = oldPath ?: "/dev/null",
                        newPath = newPath ?: "/dev/null",
                        hunks = hunks.toList(),
                        preamble = preamble.toList()
                    )
                )
            }
            preamble = mutableListOf()
            oldPath = null
            newPath = null
            hunks = mutableListOf()
        }

        for (line in lines) {
            when {
                line.startsWith("diff --git ") || line.startsWith("diff --combined ") -> {
                    flushFile()
                    preamble.add(line)
                }

                line.startsWith("--- ") -> {
                    flushHunk()
                    oldPath = line.removePrefix("--- ").trim()
                    if (oldPath!!.isBlank()) oldPath = "/dev/null"
                }

                line.startsWith("+++ ") -> {
                    newPath = line.removePrefix("+++ ").trim()
                    if (newPath!!.isBlank()) newPath = "/dev/null"
                }

                line.startsWith("@@") -> {
                    flushHunk()
                    val match = hunkPattern.matchEntire(line)
                    if (match != null) {
                        val os = match.groupValues[1].toIntOrNull() ?: 1
                        val oc = match.groupValues[2].toIntOrNull() ?: 1
                        val ns = match.groupValues[3].toIntOrNull() ?: 1
                        val nc = match.groupValues[4].toIntOrNull() ?: 1
                        val label = match.groupValues[5].trim().takeIf { it.isNotEmpty() }
                        currentHunkHeader = HunkHeader(line, os, oc, ns, nc, label)
                        oldLine = os
                        newLine = ns
                        currentHunkLines.add(
                            DiffLine(DiffLine.Kind.HUNK_HEADER, line)
                        )
                    } else {
                        // Malformed hunk header — treat as meta
                        currentHunkHeader = HunkHeader(line, 1, 0, 1, 0, null)
                        currentHunkLines.add(
                            DiffLine(DiffLine.Kind.HUNK_HEADER, line)
                        )
                        oldLine = 1
                        newLine = 1
                    }
                }

                line.startsWith("+") && currentHunkHeader != null -> {
                    currentHunkLines.add(
                        DiffLine(
                            DiffLine.Kind.ADD,
                            line.removePrefix("+"),
                            oldLineNumber = null,
                            newLineNumber = newLine
                        )
                    )
                    newLine++
                }

                line.startsWith("-") && currentHunkHeader != null -> {
                    currentHunkLines.add(
                        DiffLine(
                            DiffLine.Kind.REMOVE,
                            line.removePrefix("-"),
                            oldLineNumber = oldLine,
                            newLineNumber = null
                        )
                    )
                    oldLine++
                }

                line.startsWith("\\ ") -> {
                    currentHunkLines.add(
                        DiffLine(DiffLine.Kind.NO_NEWLINE, line)
                    )
                }

                currentHunkHeader != null -> {
                    // Context line (starts with space or is blank within a hunk)
                    val text = if (line.startsWith(" ")) line.removePrefix(" ") else line
                    currentHunkLines.add(
                        DiffLine(
                            DiffLine.Kind.CONTEXT,
                            text,
                            oldLineNumber = oldLine,
                            newLineNumber = newLine
                        )
                    )
                    oldLine++
                    newLine++
                }

                line.startsWith("index ") || line.startsWith("new file ") ||
                    line.startsWith("deleted file ") || line.startsWith("old mode ") ||
                    line.startsWith("new mode ") || line.startsWith("similarity ") ||
                    line.startsWith("rename ") || line.startsWith("copy ") -> {
                    preamble.add(line)
                }

                else -> {
                    // If we're inside a file but before a hunk, it's preamble.
                    // If we're outside any file, ignore it.
                    if (oldPath != null || newPath != null || preamble.isNotEmpty()) {
                        preamble.add(line)
                    }
                }
            }
        }
        flushFile()

        return DiffContent(files)
    }

    private data class HunkHeader(
        val raw: String,
        val oldStart: Int,
        val oldCount: Int,
        val newStart: Int,
        val newCount: Int,
        val sectionLabel: String?
    )
}
