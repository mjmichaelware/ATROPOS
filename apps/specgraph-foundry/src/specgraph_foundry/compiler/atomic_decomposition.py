from typing import List, Dict, Any, Optional
from .requirement_candidates import RequirementCandidacy
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
