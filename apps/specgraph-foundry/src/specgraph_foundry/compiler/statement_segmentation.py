import re
import hashlib
from typing import List, Dict, Any, Optional
from .source_coordinates import SourceCoordinates
from .document_ir import DocumentNode

# Abbreviations to protect
ABBREVIATIONS = {
    "e.g.", "i.e.", "cf.", "al.", "vs.", "etc.", "ca.", "ed.", "eds.",
    "vol.", "vols.", "sec.", "secs.", "fig.", "figs.", "app.", "apps.",
    "v.", "ver.", "rev.", "approx.", "max.", "min.", "dept.", "univ.",
    "std.", "spec.", "doc.", "docs."
}

ABBREVIATION_PATTERN = re.compile(
    r"\b(" + "|".join(re.escape(abbr.rstrip('.')) for abbr in ABBREVIATIONS) + r")\.\s",
    flags=re.IGNORECASE
)

DECIMAL_PATTERN = re.compile(r"\b\d+\.\d+\b")
VERSION_PATTERN = re.compile(r"\bv\d+\.\d+(?:\.\d+)?\b", flags=re.IGNORECASE)
FILE_EXTENSION_PATTERN = re.compile(r"\b[a-zA-Z0-9_\-]+\.[a-zA-Z0-9]{1,4}\b")

class StatementIR:
    def __init__(
        self,
        statement_id: str,
        exact_quote: str,
        canonical_text: str,
        coordinates: SourceCoordinates,
        parent_node_id: str,
        governing_heading_id: Optional[str] = None,
        governing_list_item_id: Optional[str] = None,
        structural_ancestry: Optional[List[str]] = None,
        neighbors: Optional[List[str]] = None,
        completeness_state: str = "COMPLETE"
    ):
        self.statement_id = statement_id
        self.exact_quote = exact_quote
        self.canonical_text = canonical_text
        self.coordinates = coordinates
        self.parent_node_id = parent_node_id
        self.governing_heading_id = governing_heading_id
        self.governing_list_item_id = governing_list_item_id
        self.structural_ancestry = structural_ancestry or []
        self.neighbors = neighbors or []
        self.completeness_state = completeness_state

    def to_dict(self) -> Dict[str, Any]:
        return {
            "statement_id": self.statement_id,
            "exact_quote": self.exact_quote,
            "canonical_text": self.canonical_text,
            "coordinates": self.coordinates.to_dict(),
            "parent_node_id": self.parent_node_id,
            "governing_heading_id": self.governing_heading_id,
            "governing_list_item_id": self.governing_list_item_id,
            "structural_ancestry": self.structural_ancestry,
            "neighbors": self.neighbors,
            "completeness_state": self.completeness_state
        }

def clean_statement_text(text: str) -> str:
    return " ".join(text.split()).strip()

def split_sentences_custom(text: str) -> List[tuple[int, int, str]]:
    """
    Split text into sentences while protecting decimals, abbreviations, versions, file names.
    Returns list of (start_char, end_char, sentence_text)
    """
    sentences = []
    # Find positions where a split is likely to occur
    # Periods, Exclamation, Question followed by space or end of string
    cursor = 0
    length = len(text)

    # We will scan through the string and find boundaries
    last_boundary = 0

    while cursor < length:
        # Check if cursor is at a sentence terminator
        char = text[cursor]
        if char in {'.', '!', '?'}:
            # Peek ahead to see if it is followed by space or end of text
            is_end = (cursor + 1 == length) or (cursor + 1 < length and text[cursor + 1].isspace())

            if is_end:
                # Validate that this is NOT an abbreviation, version, decimal, etc.
                # Check for decimals (e.g., digit . digit)
                is_decimal = False
                if cursor > 0 and cursor + 1 < length:
                    if text[cursor - 1].isdigit() and text[cursor + 1].isdigit():
                        is_decimal = True

                # Check for abbreviations
                is_abbr = False
                # Grab a window before the period
                left_window = text[max(0, cursor - 10):cursor + 1]
                for abbr in ABBREVIATIONS:
                    if left_window.lower().endswith(abbr):
                        abbr_len = len(abbr)
                        start_idx = len(left_window) - abbr_len
                        if start_idx == 0 or left_window[start_idx - 1].isspace() or left_window[start_idx - 1] in {"(", "[", "{"}:
                            is_abbr = True
                            break

                # Check for version numbers (e.g. v1.)
                is_version = False
                left_words = left_window.split()
                if left_words:
                    last_word = left_words[-1]
                    if re.match(r'^v\d+$', last_word, re.IGNORECASE):
                        is_version = True

                # If it is none of these, it's a true boundary
                if not (is_decimal or is_abbr or is_version):
                    # End of sentence
                    end_pos = cursor + 1
                    # include trailing whitespace if any (will strip later but need coordinates)
                    sent_text = text[last_boundary:end_pos]
                    # strip leading/trailing spaces for character start/end
                    stripped = sent_text.strip()
                    if stripped:
                        start_offset = sent_text.find(stripped)
                        sentences.append((last_boundary + start_offset, last_boundary + start_offset + len(stripped), stripped))

                    last_boundary = end_pos
                    cursor = end_pos
                    continue
        cursor += 1

    if last_boundary < length:
        sent_text = text[last_boundary:]
        stripped = sent_text.strip()
        if stripped:
            start_offset = sent_text.find(stripped)
            sentences.append((last_boundary + start_offset, last_boundary + start_offset + len(stripped), stripped))

    return sentences

# An atom declaration starts a new statement.
#
# split_sentences_custom breaks on '.', '!' and '?' only, which is right for
# prose and wrong for a declared obligation list: `B-INST-001a - Detect linux -
# estLOC 5` carries no terminator, so a run of forty microatoms arrived as one
# statement and yielded one requirement. The id at the start of a declaration is
# this document grammar's terminator, and it is treated as one here rather than
# in the sentence splitter so ordinary prose keeps the punctuation rule.
# Kept in step with ATOM_DECLARATION_RE in discourse_roles: the final segment
# may be digits or a bare letter. These two patterns describe the same grammar
# from two sides -- where a declaration *starts* and what a declaration *is* --
# and widening only one of them is how eleven `B-MCP-CORE-*` atoms stayed
# invisible after the declaration side already understood them.
ATOM_BOUNDARY_RE = re.compile(
    r"(?<!\S)(?P<id>[A-Z]{1,6}(?:-[A-Z]{1,6})*-(?:\d+(?:\.\d+)*[a-z]?|[a-z]))\s*[\u00b7\u2022]"
)


def split_on_atom_declarations(spans, text):
    """Re-split spans that carry more than one atom declaration."""
    out = []
    for start, end, span_text in spans:
        cuts = [m.start() for m in ATOM_BOUNDARY_RE.finditer(span_text)]
        # Nothing declared here: leave the span exactly as the sentence
        # splitter produced it.
        if not cuts:
            out.append((start, end, span_text))
            continue
        # One declaration that does not start the span still needs cutting.
        #
        # A section header and the first atom under it often share a line --
        # `VISUAL BLUEPRINT ATOMS (what you see) F-VIS-001 - CLI open frame`.
        # Requiring two declarations left that whole line as one statement, and
        # the declaration test is anchored at the start, so the atom was
        # invisible: the header prose classified the line and the atom went
        # down with it. Twenty-one atoms were lost this way on clean input,
        # which is why this is not a wrap-damage problem.
        if len(cuts) == 1 and cuts[0] == 0:
            out.append((start, end, span_text))
            continue
        if cuts[0] != 0:
            cuts.insert(0, 0)
        for index, cut in enumerate(cuts):
            stop = cuts[index + 1] if index + 1 < len(cuts) else len(span_text)
            piece = span_text[cut:stop]
            if piece.strip():
                out.append((start + cut, start + stop, piece))
    return out


def segment_document_node(
    project_id: str,
    source_sha256: str,
    root_node: DocumentNode
) -> List[StatementIR]:
    statements: List[StatementIR] = []

    # Track context during traversal
    current_heading_id: Optional[str] = None
    current_list_item_id: Optional[str] = None
    heading_ancestry: List[str] = []

    # Flatten the tree nodes
    flat_nodes: List[DocumentNode] = []
    def traverse(node: DocumentNode):
        flat_nodes.append(node)
        for child in node.children:
            traverse(child)

    traverse(root_node)

    for node in flat_nodes:
        if node.role == "HEADING":
            current_heading_id = node.node_id
            heading_ancestry.append(node.node_id)
        if node.role == "LIST_ITEM":
            current_list_item_id = node.node_id

        # Only segment PARAGRAPH, LIST_ITEM, and TABLE_CELL
        # LABEL is segmented too.
        #
        # format_adapters turns a line ending in ':' with few words into a
        # LABEL node, and this set used to exclude it -- so those lines produced
        # no statement, never reached rejected_candidates, and disappeared with
        # no record anywhere. They are exactly the declared-item lines this
        # compiler is meant to catch. A loss that leaves no trace is worse than
        # a rejection, because nothing can tell you it happened.
        if node.role in {"PARAGRAPH", "LIST_ITEM", "TABLE_CELL", "TABLE_ROW", "TABLE_HEADER",
                         "FILE_PATH", "KEY_VALUE", "UNKNOWN", "HEADING", "LABEL"}:
            node_text = node.content
            sents = split_on_atom_declarations(split_sentences_custom(node_text), node_text)

            for idx, (s_start, s_end, sent_text) in enumerate(sents):
                # Calculate byte offsets for each sentence relative to original file bytes
                # Since node is decodable, we can find byte start and end
                local_prefix = node_text[:s_start]
                local_sentence = node_text[:s_end]

                byte_start = node.coordinates.byte_start + len(local_prefix.encode("utf-8"))
                byte_end = node.coordinates.byte_start + len(local_sentence.encode("utf-8"))

                # Estimate line numbers based on newline count in local prefix
                newlines_before = local_prefix.count("\n")
                newlines_in_sent = sent_text.count("\n")

                line_start = node.coordinates.line_start + newlines_before
                line_end = line_start + newlines_in_sent

                coords = SourceCoordinates(
                    byte_start=byte_start,
                    byte_end=byte_end,
                    line_start=line_start,
                    line_end=line_end,
                    char_start=node.coordinates.char_start + s_start if node.coordinates.char_start is not None else s_start,
                    char_end=node.coordinates.char_start + s_end if node.coordinates.char_start is not None else s_end
                )

                canonical = clean_statement_text(sent_text)

                # Check completeness (unbalanced delimiters, empty, etc.)
                completeness = "COMPLETE"
                open_brackets = canonical.count("(") - canonical.count(")")
                open_squares = canonical.count("[") - canonical.count("]")
                if open_brackets != 0 or open_squares != 0:
                    completeness = "INCOMPLETE_FRAGMENT"

                # Create stable statement ID
                payload = f"{project_id}:{source_sha256}:statement:{byte_start}:{byte_end}"
                statement_hash = hashlib.sha256(payload.encode("utf-8")).hexdigest()
                statement_id = f"statement-{statement_hash[:16]}"

                statements.append(StatementIR(
                    statement_id=statement_id,
                    exact_quote=sent_text,
                    canonical_text=canonical,
                    coordinates=coords,
                    parent_node_id=node.node_id,
                    governing_heading_id=current_heading_id,
                    governing_list_item_id=current_list_item_id,
                    structural_ancestry=list(heading_ancestry),
                    completeness_state=completeness
                ))

    # Fill in neighbors context
    for i, stmt in enumerate(statements):
        prev_stmt = statements[i-1].canonical_text if i > 0 else ""
        next_stmt = statements[i+1].canonical_text if i + 1 < len(statements) else ""
        stmt.neighbors = [prev_stmt, next_stmt]

    return statements
