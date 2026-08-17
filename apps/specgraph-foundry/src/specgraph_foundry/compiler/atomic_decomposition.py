from typing import List, Dict, Any, Optional
from .requirement_candidates import RequirementCandidacy
from .verb_lexicon import verb_count
from .source_coordinates import SourceCoordinates

class AtomicRequirement:
    def __init__(
        self,
        requirement_id: str,
        candidacy: RequirementCandidacy,
        canonical_statement: str,
        coordinates: SourceCoordinates,
        lineage: Optional[List[Dict[str, str]]] = None
    ):
        self.requirement_id = requirement_id
        self.candidacy = candidacy
        self.canonical_statement = canonical_statement
        self.coordinates = coordinates
        self.lineage = lineage or []  # List of dicts representing relations like COMPOSED_FROM

    def to_dict(self) -> Dict[str, Any]:
        return {
            "requirement_id": self.requirement_id,
            "candidacy": self.candidacy.to_dict(),
            "canonical_statement": self.canonical_statement,
            "coordinates": self.coordinates.to_dict(),
            "lineage": self.lineage
        }

def decompose_requirement(
    project_id: str,
    source_sha256: str,
    candidate: RequirementCandidacy
) -> List[AtomicRequirement]:
    """
    Decompose compound requirements.
    Detects if there are multiple clauses separated by semicolons or 'and the' with modal verbs.
    """
    text = candidate.statement.canonical_text

    # Check for semicolon splits with modals
    # E.g. "The system must start; it must listen on port 80."
    clauses = []

    # Splitting on semicolons
    raw_clauses = text.split(";")
    if len(raw_clauses) > 1:
        # Check if the subsequent clauses have modal force (contain shall/must/should)
        has_multiple_obligations = True
        for part in raw_clauses[1:]:
            part_lower = part.lower()
            if not ("must" in part_lower or "shall" in part_lower or "should" in part_lower):
                has_multiple_obligations = False
                break

        if has_multiple_obligations:
            clauses = [c.strip() for c in raw_clauses if c.strip()]

    # If no semicolon split, check for "and the ... shall/must/should"
    if not clauses:
        import re
        split_pattern = re.compile(r"\s+and\s+(?:the|a)\s+.+?\s+(?:shall|must|should)\s+", re.IGNORECASE)
        matches = list(split_pattern.finditer(text))
        if matches:
            # Split the text at these match positions
            last_idx = 0
            for match in matches:
                clauses.append(text[last_idx:match.start()].strip())
                last_idx = match.start() + 5 # skip ' and '
            clauses.append(text[last_idx:].strip())

    # The source rule: two verbs means two atoms.
    #
    # The splits above both require a modal verb in every clause, so a
    # statement that states two actions without saying "shall" stayed whole --
    # which is most of a bullet list, and most of a declared obligation.
    if not clauses:
        clauses = split_on_action_verbs(text)

    if not clauses:
        # Single atomic requirement
        req_id = f"req-{candidate.statement.statement_id.split('-')[-1]}"
        return [AtomicRequirement(
            requirement_id=req_id,
            candidacy=candidate,
            canonical_statement=text,
            coordinates=candidate.statement.coordinates
        )]

    # We have multiple clauses
    atomics = []
    parent_stmt_id = candidate.statement.statement_id

    for idx, clause in enumerate(clauses):
        # Calculate sub-spans (approximate based on text search)
        clause_len = len(clause)
        char_start = text.find(clause)
        if char_start == -1:
            char_start = 0

        byte_start = candidate.statement.coordinates.byte_start + len(text[:char_start].encode("utf-8"))
        byte_end = byte_start + len(clause.encode("utf-8"))

        coords = SourceCoordinates(
            byte_start=byte_start,
            byte_end=byte_end,
            line_start=candidate.statement.coordinates.line_start,
            line_end=candidate.statement.coordinates.line_end
        )

        req_id = f"req-{parent_stmt_id.split('-')[-1]}-part{idx+1}"

        lineage = [
            {"type": "COMPOSED_FROM", "target": parent_stmt_id},
            {"type": "DERIVED_FROM_CLAUSE", "target": parent_stmt_id}
        ]
        if idx > 0:
            lineage.append({"type": "SIBLING_CLAUSE", "target": f"req-{parent_stmt_id.split('-')[-1]}-part1"})

        # Create a copy/derived candidate for this atomic obligation
        atomics.append(AtomicRequirement(
            requirement_id=req_id,
            candidacy=candidate,
            canonical_statement=clause,
            coordinates=coords,
            lineage=lineage
        ))

    return atomics


# The conjunctions a compound statement is built from, longest first so " and
# then " is cut before " and " would leave a dangling "then".
_CONJUNCTIONS = (" and then ", " and also ", "; ", " and ", ", then ")

# A guard, not a tuning knob. Splitting is applied until nothing changes; this
# stops a pathological line from looping and states the bound in one place.
MAX_SPLIT_PASSES = 6

# Below this, a fragment is not a requirement -- it is the tail of a phrase the
# splitter cut in the wrong place, and emitting it as an atom would be worse
# than leaving the sentence whole.
MIN_CLAUSE_WORDS = 3


def split_on_action_verbs(text: str) -> List[str]:
    """Split a statement that states more than one action.

    Returns [] when the statement states one action, which the caller reads as
    "leave this alone" -- distinct from returning [text], which would claim a
    decomposition happened.
    """
    parts = [text]
    for _ in range(MAX_SPLIT_PASSES):
        expanded = []
        for part in parts:
            expanded.extend(_split_once(part))
        if expanded == parts:
            break
        parts = expanded

    cleaned = [p.strip(" ,;") for p in parts if p.strip(" ,;")]
    if len(cleaned) < 2:
        return []
    # A split that produced a fragment did not find a real boundary. Keeping
    # the statement whole is the safer half of that mistake: an atom too coarse
    # can still be read, where an atom that is half a clause cannot.
    if any(len(c.split()) < MIN_CLAUSE_WORDS for c in cleaned):
        return []
    return cleaned


def _split_once(text: str) -> List[str]:
    """One cut, at the first conjunction that has a verb on both sides."""
    if verb_count(text) < 2:
        return [text]
    lowered = text.lower()
    for conjunction in _CONJUNCTIONS:
        start = lowered.find(conjunction)
        while start != -1:
            head, tail = text[:start], text[start + len(conjunction):]
            # Both halves must state an action. Cutting at an "and" that joins
            # two nouns -- "reads config and secrets" -- would split one action
            # into two fragments of itself.
            if verb_count(head) >= 1 and verb_count(tail) >= 1:
                return [head, tail]
            start = lowered.find(conjunction, start + 1)
    return [text]
