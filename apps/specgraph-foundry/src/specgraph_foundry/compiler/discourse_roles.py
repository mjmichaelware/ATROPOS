import re
from typing import List, Dict, Any, Optional
from .statement_segmentation import StatementIR

DISCOURSE_ROLES = {
    "NORMATIVE_REQUIREMENT", "CONSTRAINT", "PROHIBITION", "PERMISSION",
    "ACCEPTANCE_CRITERION", "QUALITY_ATTRIBUTE", "INTERFACE_CONTRACT",
    "DATA_CONTRACT", "DESIGN_DECISION", "ARCHITECTURAL_PRINCIPLE", "DEFINITION",
    "ASSUMPTION", "GOAL", "RATIONALE", "BACKGROUND", "OBSERVATION",
    "DEFECT_FINDING", "IMPLEMENTATION_STATUS", "EXAMPLE", "NOTE", "WARNING",
    "OPEN_QUESTION", "SOURCE_REFERENCE", "DOCUMENT_METADATA", "TITLE",
    "HEADING", "SECTION_LABEL", "CAPTION", "SEPARATOR", "CODE_SAMPLE",
    "TABLE_HEADER", "OUT_OF_SCOPE", "INCOMPLETE_FRAGMENT", "UNRESOLVED"
}

# Regex rules for discourse roles
RATIONALE_RE = re.compile(r"^\s*(?:rationale|reason|why|background|explanation|context)[:\-]?\s", re.IGNORECASE)
EXAMPLE_RE = re.compile(r"^\s*(?:example|e\.g\.|illustration)[:\-]?\s", re.IGNORECASE)
NOTE_RE = re.compile(r"^\s*(?:note|nb|attention|remark)[:\-]?\s", re.IGNORECASE)

# `Key: value` lines whose key names a property of the document rather than
# work to do. Admitting a delimited item is right; admitting `Status: DRAFT`
# as an obligation is not, and the difference is which key it is.
DOCUMENT_FIELD_RE = re.compile(
    r"^\s*(?:status|version|revision|author|authors|owner|date|last\s+updated|"
    r"document|doc|id|identifier|classification|confidentiality|copyright|"
    r"license|applies\s+to|supersedes|prepared\s+by|reviewed\s+by|approved\s+by)"
    r"\s*:",
    re.IGNORECASE,
)

# `Key: value` lines whose key marks commentary about the system rather than a
# statement of what it must do.
COMMENTARY_FIELD_RE = re.compile(
    r"^\s*(?:observation|observations|finding|findings|caveat|caveats|"
    r"assumption|assumptions|conclusion|summary|abstract|disclaimer|"
    r"glossary|terminology|scope\s+note)\s*:",
    re.IGNORECASE,
)
WARNING_RE = re.compile(r"^\s*(?:warning|caution|danger|alert)[:\-]?\s", re.IGNORECASE)
# `Todo:` and `Fixme:` join the defect family rather than becoming
# requirements. Once a `Key: value` line is a delimited item in its own
# right, an outstanding-work marker would otherwise be admitted as an
# obligation the document never made; the compiler already has a channel
# for acknowledged gaps, and this is one.
DEFECT_FINDING_RE = re.compile(
    r"^\s*(?:defect|bug|issue|failure|broken|defect_finding|todo|to-do|fixme|action\s+item)[:\-]?\s",
    re.IGNORECASE,
)
PROHIBITION_RE = re.compile(r"\b(?:must\s+not|shall\s+not|should\s+not|never|prohibited|forbidden|cannot)\b", re.IGNORECASE)
NORMATIVE_RE = re.compile(r"\b(?:shall|must|should|required\s+to|shall\s+be|must\s+be|needs\s+to|mandatory|shall be|must be)\b", re.IGNORECASE)
PERMISSION_RE = re.compile(r"\b(?:may|can|optionally|permitted|allowed)\b", re.IGNORECASE)
MANDATORY_RE = re.compile(r"\b(?:mandatory|required|obligatory|compulsory)\b", re.IGNORECASE)
ACCEPTANCE_RE = re.compile(r"\b(?:acceptance\s+criterion|acceptance\s+criteria|test\s+case|verification\s+condition)\b", re.IGNORECASE)
DESIGN_DECISION_RE = re.compile(r"\b(?:design\s+decision|design\s+choice|we\s+decide|architectural\s+decision)\b", re.IGNORECASE)
DEFINITION_RE = re.compile(r"\b(?:is\s+defined\s+as|means\b|refers\s+to\b|definition)\b", re.IGNORECASE)
PROSE_RE = re.compile(
    r"\b(?:explaining|explains|illustrates|describes|contains|discusses|shows|refers|meaning|goal|purpose|rationale)\b|"
    r"^\s*(?:this\s+(?:section|document|chapter|paragraph|text|spec|specification))\b",
    re.IGNORECASE
)

HEADING_RE = re.compile(r"^#{1,6}\s+")

# ---------------------------------------------------------------------------
# Pre-structured obligation documents
#
# A source document is not always prose to be mined. An obligation DAG arrives
# with its atoms already declared -- an id, a title, its dependencies, and a
# typed obligation triple -- and mining it for modal verbs finds nothing,
# because "S-001 - Six-answers status contract" contains no "shall". A 227-atom
# document classified 188 of its statements UNRESOLVED and yielded one
# requirement.
#
# These rules read that grammar directly. They are hard structural rules like
# the heading and separator rules above, and they run before the modal lattice
# for the same reason those do: the form of the line already says what it is,
# and guessing from its words can only be worse.
#
# The triple maps by what each line obliges, not by keyword:
#   RESEARCH -- what must be found out first. An open question, not buildable.
#   IMPL     -- what must be built. Normative.
#   WIRE     -- what must call it. The document's own acceptance condition:
#               "Acceptance = callers >= 1 outside tests".
# The final segment may be digits or a bare letter.
#
# `B-MCP-GH-a` and `B-MCP-CORE-k` end in a letter with no number after the
# family, and a digits-only pattern missed fifty-nine ids in one document --
# 228 seen where 286 were declared. The letter form is how the source marks a
# micro-atom split off an integration, which is exactly the content this rule
# exists to catch.
ATOM_DECLARATION_RE = re.compile(
    r"^\s*(?P<id>[A-Z]{1,6}(?:-[A-Z]{1,6})*-(?:\d+(?:\.\d+)*[a-z]?|[a-z]))"
    r"\s*[\u00b7\u2022:\-]\s*(?P<title>\S.*)$"
)
OBLIGATION_RESEARCH_RE = re.compile(r"^\s*RESEARCH\s*:", re.IGNORECASE)
OBLIGATION_IMPL_RE = re.compile(r"^\s*IMPL\s*:", re.IGNORECASE)
OBLIGATION_WIRE_RE = re.compile(r"^\s*WIRE\s*:", re.IGNORECASE)
DEPENDS_ON_RE = re.compile(r"^\s*dependsOn\s*:", re.IGNORECASE)


# Roles whose nodes are delimited units of content.
#
# TABLE_CELL and TABLE_ROW are included although no parser emits them yet: the
# vocabulary and the downstream handling already exist, so the table work
# becomes a parser change only.
# A path in a directory listing and a `Key: value` line are delimited items in
# exactly the sense the fallthrough below means: someone wrote them as
# separate things. `app/routes/generate.py` is the most literal statement of
# work a document can make, and it contains no verb at all.
STRUCTURAL_ITEM_ROLES = frozenset({
    "LIST_ITEM", "TABLE_CELL", "TABLE_ROW", "FILE_PATH", "KEY_VALUE",
})


def is_structural_item(parent_role):
    """Whether this statement's node is a delimited item rather than prose."""
    return parent_role in STRUCTURAL_ITEM_ROLES


def declared_atom_id(text):
    """The atom id this line declares, or None when it declares none."""
    match = ATOM_DECLARATION_RE.match(text.strip())
    return match.group("id") if match else None

def classify_discourse_role(
    stmt: StatementIR,
    parent_role: str,
    proposal_role: Optional[str] = None
) -> str:
    """
    Classify discourse role of a statement using the hard rule lattice.
    Lattice overrides statistical/provider proposals.
    """
    text = stmt.canonical_text.strip()

    # 0. Structural exclusion patterns check
    text_lower = text.lower()
    structural_forbidden = [
        "source document #3",
        "purpose:",
        "layout:",
        "motion:",
        "states:",
        "data-source mapping:",
        "end of specification",
    ]
    for pattern in structural_forbidden:
        if pattern in text_lower:
            return "OUT_OF_SCOPE"

    # 0. Declared obligations, before any prose rule.
    #
    # Ordered inside the block by specificity: the triple lines are checked
    # before the declaration pattern, because "WIRE: X - Y" would otherwise
    # match the id-and-title shape.
    if OBLIGATION_IMPL_RE.match(text):
        return "NORMATIVE_REQUIREMENT"
    if OBLIGATION_WIRE_RE.match(text):
        return "ACCEPTANCE_CRITERION"
    if OBLIGATION_RESEARCH_RE.match(text):
        return "OPEN_QUESTION"
    if DEPENDS_ON_RE.match(text):
        # The edge, not the work. Carried as metadata so the dependency is
        # available downstream without becoming an atom of its own.
        return "DOCUMENT_METADATA"
    if declared_atom_id(text):
        # The atom's own line: an id and what it is. Normative because the
        # document declares it as work to be done, not as commentary about it.
        return "NORMATIVE_REQUIREMENT"

    # 0. Heading/Markdown Title detection
    if HEADING_RE.match(text) or text.startswith("#"):
        return "HEADING"

    # 1. Hard Structural Exclusions
    if parent_role == "TITLE":
        return "TITLE"
    if parent_role == "HEADING":
        return "HEADING"
    if parent_role == "SEPARATOR" or text.startswith("____") or text.startswith("END OF"):
        return "SEPARATOR"
    if parent_role == "METADATA":
        return "DOCUMENT_METADATA"
    if parent_role == "CAPTION":
        return "CAPTION"
    if parent_role == "CODE_BLOCK":
        return "CODE_SAMPLE"
    if parent_role == "TABLE_HEADER":
        return "TABLE_HEADER"

    # 2. Incompleteness Check
    # A three-word minimum is a reasonable guard against fragments of prose
    # and completely wrong for a delimited item: `app/routes/generate.py` is
    # one token and is a complete statement of a file that must exist. Every
    # path in a directory listing failed here.
    if not is_structural_item(parent_role) and (
        stmt.completeness_state == "INCOMPLETE_FRAGMENT" or len(text.split()) < 3
    ):
        return "INCOMPLETE_FRAGMENT"

    # 3. Specific markers inside statement text
    if RATIONALE_RE.match(text):
        return "RATIONALE"
    if EXAMPLE_RE.match(text):
        return "EXAMPLE"
    if NOTE_RE.match(text):
        return "NOTE"
    if WARNING_RE.match(text):
        return "WARNING"

    if DEFECT_FINDING_RE.search(text):
        return "DEFECT_FINDING"

    if PROSE_RE.search(text):
        return "BACKGROUND"

    # 4. Modality/Force classification
    if PROHIBITION_RE.search(text):
        return "PROHIBITION"

    if ACCEPTANCE_RE.search(text):
        return "ACCEPTANCE_CRITERION"

    if DESIGN_DECISION_RE.search(text):
        return "DESIGN_DECISION"

    if DEFINITION_RE.search(text):
        return "DEFINITION"

    if NORMATIVE_RE.search(text):
        return "NORMATIVE_REQUIREMENT"

    if PERMISSION_RE.search(text):
        return "PERMISSION"

    # 5. Allow provider proposal to resolve remaining cases only if it fits the roles
    if proposal_role and proposal_role in DISCOURSE_ROLES:
        return proposal_role

    # 6. Structure, when language did not decide.
    #
    # Everything above still runs first, so a rationale, an example, a heading
    # or a prose paragraph is excluded exactly as before. What changes is the
    # fallthrough: a statement reaching here carries no modal verb, and
    # returning UNRESOLVED made it unreachable -- UNRESOLVED is not in
    # EXECUTABLE_ROLES, so a plain bullet list produced zero atoms however
    # clearly it stated the work.
    #
    # A delimited item is a deliberate unit. Someone wrote a list, a table row
    # or a declared line because those are separate things, and that is a more
    # reliable signal than whether the sentence happens to contain "shall".
    # Documents do not always speak in modal verbs, and a compiler that hears
    # only those cannot read most real specifications.
    #
    # Admitting the statement is not the same as claiming an obligation
    # strength the document never stated: the modality ladder downstream
    # records UNSPECIFIED, which is the honest answer.
    # A key naming a property of the document, or commentary about the system,
    # is not work however it is delimited. Checked here rather than earlier so
    # nothing that already classified as an obligation is downgraded.
    if DOCUMENT_FIELD_RE.match(text):
        return "DOCUMENT_METADATA"
    if COMMENTARY_FIELD_RE.match(text):
        return "OBSERVATION"

    if is_structural_item(parent_role) or declared_atom_id(text):
        return "NORMATIVE_REQUIREMENT"

    # Prose that named no obligation and sits in no structure. Abstaining is
    # right here -- there is nothing to point at.
    return "UNRESOLVED"
