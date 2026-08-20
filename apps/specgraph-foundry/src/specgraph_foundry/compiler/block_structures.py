"""Block structures a line-at-a-time classifier cannot see.

``parse_markdown_to_ir`` decides what every line is by looking at that line
alone, and accumulates anything it does not recognise into a paragraph. Three
very common ways of stating work survive neither step:

* **A directory tree.** A build specification that lists the project's files and
  folders states them as an indented listing, not as sentences. Every line is
  unrecognised, so all two hundred of them glue into one paragraph and are
  rejected as a single statement. The most literal statement of work a document
  can contain -- these exact files, in these exact folders -- was the part the
  compiler lost most completely.

* **A fixed-width table.** A feature matrix aligns its columns with runs of
  spaces. Same outcome: one paragraph, one rejection, twenty-seven features
  gone.

* **A pipe table.** ``TABLE`` and ``TABLE_ROW`` have been declared in
  ``document_ir`` and handled in ``statement_segmentation`` since the beginning,
  but no parser ever emitted one.

Each detector answers one question -- does a run of lines form this structure --
and returns the rows it found. Detection is by shape, so it holds for any
document rather than for the ones that happen to be in front of us.
"""

from __future__ import annotations

import re
from dataclasses import dataclass, field
from typing import Dict, List, Optional

# A path with no spaces in it. Slashes are allowed inside the name because a
# tree may collapse a single-child directory onto one line
# (`vendor/opensheetmusicdisplay.min.js`).
_TREE_NAME = r"[A-Za-z0-9_.][A-Za-z0-9_.\-/]*"

# Box-drawing prefixes, so both indentation styles read the same.
_TREE_LINE_RE = re.compile(
    r"^(?P<indent>[ \t]*)"
    r"(?P<branches>(?:[|│]\s{0,3}|├──\s?|└──\s?|\+--\s?|`--\s?)*)"
    r"(?P<name>" + _TREE_NAME + r"/?)"
    r"(?P<trailing>\s*(?:#.*)?)$"
)

# Two or more spaces separate columns in a fixed-width table.
_COLUMN_GAP_RE = re.compile(r"\s{2,}")

_PIPE_ROW_RE = re.compile(r"^\s*\|.*\|\s*$")
_PIPE_RULE_RE = re.compile(r"^\s*\|?[\s:|-]+\|[\s:|-]*$")

MIN_TREE_LINES = 3
MIN_TABLE_ROWS = 3
MIN_TABLE_COLUMNS = 3
TREE_INDENT_UNIT = 2


@dataclass
class BlockRow:
    """One row of a detected block, with the source line it came from."""

    line_index: int
    role: str
    text: str
    metadata: Dict[str, object] = field(default_factory=dict)


@dataclass
class Block:
    """A run of lines that form one structure."""

    kind: str
    start: int
    end: int          # exclusive
    rows: List[BlockRow]


def detect_blocks(lines: List[str]) -> Dict[int, Block]:
    """Every block in the document, keyed by its first line index.

    Detectors run in order of how strictly they constrain their lines, so a run
    that could be read two ways is read the stricter way.
    """
    blocks: Dict[int, Block] = {}
    index = 0
    while index < len(lines):
        block = (
            _pipe_table_at(lines, index)
            or _file_tree_at(lines, index)
            or _fixed_width_table_at(lines, index)
        )
        if block is None:
            index += 1
            continue
        blocks[block.start] = block
        index = block.end
    return blocks


# --------------------------------------------------------------- file trees

def _file_tree_at(lines: List[str], start: int) -> Optional[Block]:
    matches: List[re.Match] = []
    index = start
    blank_run = 0
    while index < len(lines):
        line = lines[index].rstrip("\n\r")
        if not line.strip():
            # One blank line inside a tree is a paragraph break in the source,
            # not the end of the listing; two mean the listing is over.
            blank_run += 1
            if blank_run > 1 or not matches:
                break
            index += 1
            continue
        match = _TREE_LINE_RE.match(line)
        if match is None:
            break
        blank_run = 0
        matches.append(match)
        index += 1

    while matches and not matches[-1].group("name"):
        matches.pop()
    if len(matches) < MIN_TREE_LINES:
        return None
    if not _looks_like_a_tree(matches):
        return None

    end = start + len(matches)
    return Block("FILE_TREE", start, end, _tree_rows(matches, start))


def _looks_like_a_tree(matches: List[re.Match]) -> bool:
    """Distinguish a directory listing from a run of one-word lines.

    A tree names at least one directory, and it is indented -- a flat column of
    bare words is a list of something else and belongs in a paragraph.
    """
    names = [m.group("name") for m in matches]
    if not any(name.endswith("/") for name in names):
        return False
    if not any(m.group("indent") or m.group("branches") for m in matches):
        return False
    # A tree is mostly filenames and directories. Requiring it of every line
    # would reject `Makefile` and `LICENSE`, which are neither.
    structured = sum(1 for name in names if name.endswith("/") or "." in name or "/" in name)
    return structured * 2 >= len(names)


def _tree_rows(matches: List[re.Match], start: int) -> List[BlockRow]:
    """Each line as the full path it denotes.

    The indentation is the parent link, so a stack of open directories turns
    `generate.py` at depth three into `musicmakerlm/app/routes/generate.py` --
    which is the thing a build has to create, and the thing a bare filename
    cannot tell it.
    """
    rows: List[BlockRow] = []
    stack: List[tuple[int, str]] = []
    for offset, match in enumerate(matches):
        depth = _depth_of(match)
        name = match.group("name")
        comment = match.group("trailing").strip().lstrip("#").strip()

        while stack and stack[-1][0] >= depth:
            stack.pop()
        prefix = stack[-1][1] if stack else ""
        path = (prefix + name) if prefix else name

        if name.endswith("/"):
            stack.append((depth, path))

        metadata: Dict[str, object] = {
            "path": path.rstrip("/"),
            "is_directory": name.endswith("/"),
            "depth": depth,
        }
        if comment:
            metadata["comment"] = comment
        text = path.rstrip("/") + (f" ({comment})" if comment else "")
        rows.append(BlockRow(start + offset, "FILE_PATH", text, metadata))
    return rows


def _depth_of(match: re.Match) -> int:
    indent = match.group("indent").replace("\t", " " * 4)
    branches = match.group("branches")
    if branches:
        # Box drawing carries the depth itself; each level is one prefix.
        return len(indent) // TREE_INDENT_UNIT + len(re.findall(r"[|│]|├──|└──|\+--|`--", branches))
    return len(indent) // TREE_INDENT_UNIT


# ------------------------------------------------------------- pipe tables

def _pipe_table_at(lines: List[str], start: int) -> Optional[Block]:
    raw: List[tuple[int, List[str]]] = []
    index = start
    while index < len(lines):
        line = lines[index].rstrip("\n\r")
        if not _PIPE_ROW_RE.match(line):
            break
        if not _PIPE_RULE_RE.match(line):
            raw.append((index, _split_pipe_row(line)))
        index += 1
    if len(raw) < 2 or index - start < MIN_TABLE_ROWS:
        return None
    return Block("TABLE", start, index, _table_rows(raw))


def _split_pipe_row(line: str) -> List[str]:
    body = line.strip().strip("|")
    return [cell.strip() for cell in body.split("|")]


# ------------------------------------------------------ fixed-width tables

def _fixed_width_table_at(lines: List[str], start: int) -> Optional[Block]:
    """A table whose columns are aligned with spaces rather than delimiters.

    Requires a stable column count across the run, which is what separates a
    matrix from ordinary prose that happens to contain a double space.
    """
    raw: List[tuple[int, List[str]]] = []
    index = start
    blank_run = 0
    width: Optional[int] = None
    while index < len(lines):
        line = lines[index].rstrip("\n\r")
        if not line.strip():
            blank_run += 1
            if blank_run > 1 or not raw:
                break
            index += 1
            continue
        cells = [cell.strip() for cell in _COLUMN_GAP_RE.split(line.strip()) if cell.strip()]
        if len(cells) < MIN_TABLE_COLUMNS:
            break
        if width is None:
            width = len(cells)
        elif abs(len(cells) - width) > 1:
            break
        blank_run = 0
        raw.append((index, cells))
        index += 1

    if len(raw) < MIN_TABLE_ROWS:
        return None
    end = raw[-1][0] + 1
    return Block("TABLE", start, end, _table_rows(raw))


# ------------------------------------------------------------ shared rows

def _table_rows(raw: List[tuple[int, List[str]]]) -> List[BlockRow]:
    """Header first, then one row each, read against that header.

    A cell means nothing without its column name: `Y` in isolation is not a
    requirement, and `MIDI export -- MusicMakerLM Target: Y (core)` is.
    """
    header_index, header = raw[0]
    rows = [
        BlockRow(
            header_index,
            "TABLE_HEADER",
            " | ".join(header),
            {"cells": header, "is_header": True},
        )
    ]
    for line_index, cells in raw[1:]:
        label = cells[0]
        pairs = [
            f"{header[position]}: {value}"
            for position, value in enumerate(cells)
            if position > 0 and position < len(header)
        ]
        text = f"{label} -- " + "; ".join(pairs) if pairs else label
        rows.append(
            BlockRow(
                line_index,
                "TABLE_ROW",
                text,
                {"cells": cells, "label": label, "header": header},
            )
        )
    return rows
