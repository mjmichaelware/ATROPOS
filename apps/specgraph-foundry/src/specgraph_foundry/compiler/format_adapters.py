import re
from typing import List, Dict, Any
from .source_coordinates import SourceCoordinates
from .block_structures import detect_blocks
from .document_ir import DocumentNode, generate_stable_id

# Common Markdown regexes
HEADING_RE = re.compile(r"^\s{0,3}(#{1,6})\s+(.+?)\s*$")
SETEXT_HEADING_1_RE = re.compile(r"^={3,}\s*$")
SETEXT_HEADING_2_RE = re.compile(r"^-{3,}\s*$")
BULLET_LIST_ITEM_RE = re.compile(r"^\s*([-*+])\s+(.+)$")
NUMBERED_LIST_ITEM_RE = re.compile(r"^\s*(\d+)[.)]\s+(.+)$")
SEPARATOR_RE = re.compile(r"^\s*(?:-{3,}|_{3,}|\*{3,}|__PART [A-Z]__|_+PART [A-Z]_+|END OF SPECIFICATION)\s*$", re.IGNORECASE)
LABEL_RE = re.compile(r"^\s*(?:[a-zA-Z0-9_\-\s#]+):\s*$")

# `Symbolic core: music21, pretty_midi, mido` -- a key naming what follows.
# LABEL_RE only matches a line that ends at the colon, so a line that states
# its value on the same line fell into the paragraph accumulator and was glued
# to its neighbours. A whole stack section was lost that way, as one rejection.
KEY_VALUE_RE = re.compile(
    r"^\s*(?P<key>[A-Za-z][A-Za-z0-9 _/&+.,'()\-]{2,60}):\s+(?P<value>\S.*)$"
)
KEY_VALUE_MAX_KEY_WORDS = 6
BLANK_RE = re.compile(r"^\s*$")

# ---------------------------------------------------------------------------
# Wrap damage
#
# Text extracted from a PDF often arrives one word per line: the extractor
# preserves visual line breaks that were never sentence breaks. A document in
# that state has no statements left to find -- every structural boundary this
# parser depends on has been shredded into single tokens. In one real document
# 49% of lines held a single word, and thirty-eight declared atoms were
# unreachable purely because of it.
#
# Repaired rather than refused, because the operator usually cannot get a better
# copy. Detected first and only repaired when detected, because joining lines
# unconditionally would corrupt a document whose short lines are deliberate --
# a list of one-word options, a glossary column.
WRAP_DAMAGE_RATIO = 0.30
_STRUCTURAL_LINE_START = re.compile(r"^\s*(?:[-*+]\s|\d+[.)]\s|#{1,6}\s|\||>)")


def _block_protected_lines(lines: List[str]) -> set:
    """Line indices belonging to a structure, which wrap repair must not touch.

    A directory listing has exactly the shape this repair was written to fix --
    one token per line -- and is not damage. Repairing a build specification
    glued two hundred file paths into paragraphs before the parser ever saw
    them, which is how the most literal statement of work in the document
    became two rejected blobs.
    """
    return {
        line_index
        for block in detect_blocks(lines).values()
        for line_index in range(block.start, block.end)
    }


def wrap_damage_ratio(text: str) -> float:
    """Share of non-blank lines holding exactly one whitespace-delimited token.

    Lines inside a detected structure are excluded from both sides of the
    ratio: they are neither evidence of damage nor candidates for repair.
    """
    lines = text.split("\n")
    protected = _block_protected_lines(lines)
    counted = [
        line for index, line in enumerate(lines)
        if line.strip() and index not in protected
    ]
    if not counted:
        return 0.0
    return sum(1 for line in counted if len(line.split()) == 1) / len(counted)


def repair_wrapped_text(text: str) -> str:
    """Rejoin lines broken mid-sentence by a PDF or copy-paste extraction.

    A single-token line that opens no markdown construct is a continuation of
    the line above it. Structural openers and lines inside a detected structure
    are left alone, so a genuine one-word bullet and a directory listing both
    survive.
    """
    lines = text.split("\n")
    protected = _block_protected_lines(lines)
    out: List[str] = []
    for index, line in enumerate(lines):
        stripped = line.strip()
        is_continuation = (
            bool(out)
            and out[-1].strip()
            and stripped
            and len(stripped.split()) == 1
            and index not in protected
            and not _STRUCTURAL_LINE_START.match(line)
        )
        if is_continuation:
            out[-1] = out[-1].rstrip() + " " + stripped
        else:
            out.append(line)
    return "\n".join(out)


def parse_markdown_to_ir(project_id: str, source_sha256: str, text: str) -> DocumentNode:
    raw_bytes = text.encode("utf-8")
    lines = text.splitlines(keepends=True)

    # Compute line starts
    starts = [0]
    for index, byte in enumerate(raw_bytes):
        if byte == 10 and index + 1 < len(raw_bytes):
            starts.append(index + 1)

    def get_line_byte_offsets(line_idx: int) -> tuple[int, int]:
        start = starts[line_idx]
        if line_idx + 1 < len(starts):
            end = starts[line_idx + 1]
        else:
            end = len(raw_bytes)
        return start, end

    nodes: List[DocumentNode] = []
    inside_code_block = False
    code_block_lines: List[str] = []
    code_block_start_line = 0
    code_block_start_byte = 0

    current_paragraph_lines: List[str] = []
    current_paragraph_start_line = 0
    current_paragraph_start_byte = 0

    def flush_paragraph():
        nonlocal current_paragraph_lines
        if current_paragraph_lines:
            para_text = "".join(current_paragraph_lines).strip()
            if para_text:
                end_byte = current_paragraph_start_byte + len(para_text.encode("utf-8"))
                coords = SourceCoordinates(
                    byte_start=current_paragraph_start_byte,
                    byte_end=end_byte,
                    line_start=current_paragraph_start_line + 1,
                    line_end=current_paragraph_start_line + len(current_paragraph_lines)
                )
                node_id = generate_stable_id(project_id, source_sha256, "PARAGRAPH", coords)
                nodes.append(DocumentNode(node_id, "PARAGRAPH", para_text, coords))
            current_paragraph_lines = []

    # Structures that need more than one line to recognise: a directory tree, a
    # fixed-width table, a pipe table. Found first, so the line loop can emit
    # their rows instead of accumulating them into a paragraph.
    blocks = detect_blocks([raw_line.rstrip("\n\r") for raw_line in lines])
    consumed_by_block = {
        line_index
        for block in blocks.values()
        for line_index in range(block.start, block.end)
    }

    for i, line in enumerate(lines):
        line_bytes_start, line_bytes_end = get_line_byte_offsets(i)
        stripped = line.strip()

        if i in blocks:
            flush_paragraph()
            block = blocks[i]
            for row in block.rows:
                row_start, row_end = get_line_byte_offsets(row.line_index)
                coords = SourceCoordinates(
                    byte_start=row_start,
                    byte_end=row_end,
                    line_start=row.line_index + 1,
                    line_end=row.line_index + 1
                )
                node_id = generate_stable_id(project_id, source_sha256, row.role, coords)
                nodes.append(DocumentNode(node_id, row.role, row.text, coords, metadata=dict(row.metadata)))
            continue
        if i in consumed_by_block:
            continue

        # Check code block fences
        if stripped.startswith("```") or stripped.startswith("~~~"):
            if inside_code_block:
                # End code block
                inside_code_block = False
                code_text = "".join(code_block_lines).strip()
                coords = SourceCoordinates(
                    byte_start=code_block_start_byte,
                    byte_end=line_bytes_end,
                    line_start=code_block_start_line + 1,
                    line_end=i + 1
                )
                node_id = generate_stable_id(project_id, source_sha256, "CODE_BLOCK", coords)
                nodes.append(DocumentNode(node_id, "CODE_BLOCK", code_text, coords))
                code_block_lines = []
            else:
                # Start code block
                flush_paragraph()
                inside_code_block = True
                code_block_start_line = i
                code_block_start_byte = line_bytes_start
            continue

        if inside_code_block:
            code_block_lines.append(line)
            continue

        # Check for blank lines/separators
        if BLANK_RE.match(line):
            flush_paragraph()
            continue

        if SEPARATOR_RE.match(stripped):
            flush_paragraph()
            coords = SourceCoordinates(
                byte_start=line_bytes_start,
                byte_end=line_bytes_end,
                line_start=i + 1,
                line_end=i + 1
            )
            node_id = generate_stable_id(project_id, source_sha256, "SEPARATOR", coords)
            nodes.append(DocumentNode(node_id, "SEPARATOR", stripped, coords))
            continue

        if LABEL_RE.match(stripped):
            lower_stripped = stripped.lower()
            contains_modal = any(m in lower_stripped for m in ["must", "shall", "should", "may", "prohibited", "required"])
            word_count = len(stripped.split())
            if not contains_modal and word_count <= 4:
                flush_paragraph()
                coords = SourceCoordinates(
                    byte_start=line_bytes_start,
                    byte_end=line_bytes_end,
                    line_start=i + 1,
                    line_end=i + 1
                )
                node_id = generate_stable_id(project_id, source_sha256, "LABEL", coords)
                nodes.append(DocumentNode(node_id, "LABEL", stripped, coords))
                continue

        # Check for headings
        heading_match = HEADING_RE.match(line.rstrip())
        if heading_match:
            flush_paragraph()
            level = len(heading_match.group(1))
            heading_text = heading_match.group(2)
            coords = SourceCoordinates(
                byte_start=line_bytes_start,
                byte_end=line_bytes_end,
                line_start=i + 1,
                line_end=i + 1
            )
            node_id = generate_stable_id(project_id, source_sha256, "HEADING", coords)
            nodes.append(DocumentNode(node_id, "HEADING", heading_text, coords, metadata={"level": level}))
            continue

        # Check for bullet list items
        bullet_match = BULLET_LIST_ITEM_RE.match(line)
        if bullet_match:
            flush_paragraph()
            item_text = bullet_match.group(2).strip()
            coords = SourceCoordinates(
                byte_start=line_bytes_start,
                byte_end=line_bytes_end,
                line_start=i + 1,
                line_end=i + 1
            )
            node_id = generate_stable_id(project_id, source_sha256, "LIST_ITEM", coords)
            nodes.append(DocumentNode(node_id, "LIST_ITEM", item_text, coords))
            continue

        # Check for numbered list items
        numbered_match = NUMBERED_LIST_ITEM_RE.match(line)
        if numbered_match:
            flush_paragraph()
            item_text = numbered_match.group(2).strip()
            coords = SourceCoordinates(
                byte_start=line_bytes_start,
                byte_end=line_bytes_end,
                line_start=i + 1,
                line_end=i + 1
            )
            node_id = generate_stable_id(project_id, source_sha256, "LIST_ITEM", coords)
            nodes.append(DocumentNode(node_id, "LIST_ITEM", item_text, coords, metadata={"ordinal": int(numbered_match.group(1))}))
            continue

        key_value_match = KEY_VALUE_RE.match(line.rstrip())
        if key_value_match and len(key_value_match.group("key").split()) <= KEY_VALUE_MAX_KEY_WORDS:
            flush_paragraph()
            coords = SourceCoordinates(
                byte_start=line_bytes_start,
                byte_end=line_bytes_end,
                line_start=i + 1,
                line_end=i + 1
            )
            node_id = generate_stable_id(project_id, source_sha256, "KEY_VALUE", coords)
            nodes.append(DocumentNode(
                node_id, "KEY_VALUE", stripped, coords,
                metadata={"key": key_value_match.group("key").strip(), "value": key_value_match.group("value").strip()}
            ))
            continue

        # Append to current paragraph block
        if not current_paragraph_lines:
            current_paragraph_start_line = i
            current_paragraph_start_byte = line_bytes_start
        current_paragraph_lines.append(line)

    flush_paragraph()

    # Create root document node
    doc_coords = SourceCoordinates(0, len(raw_bytes), 1, len(lines))
    doc_id = generate_stable_id(project_id, source_sha256, "DOCUMENT", doc_coords)
    root = DocumentNode(doc_id, "DOCUMENT", "", doc_coords, children=nodes)

    # Assign parent IDs
    for node in nodes:
        node.parent_id = doc_id

    return root
