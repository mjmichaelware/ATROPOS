from typing import List, Dict, Any, Optional
from .requirement_ir import CanonicalRequirementIR

class SemanticRelation:
    def __init__(
        self,
        from_req_id: str,
        to_req_id: str,
        relation_type: str,
        rationale: str,
        confidence: float = 1.0,
        inferred: bool = True
    ):
        self.from_req_id = from_req_id
        self.to_req_id = to_req_id
        self.relation_type = relation_type
        self.rationale = rationale
        self.confidence = confidence
        self.inferred = inferred

    def to_dict(self) -> Dict[str, Any]:
        return {
            "from_atom_id": self.from_req_id,
            "to_atom_id": self.to_req_id,
            "relation_type": self.relation_type,
            "rationale": self.rationale,
            "confidence": self.confidence,
            "inferred": self.inferred
        }

def evaluate_semantic_relation(
    req_a: CanonicalRequirementIR,
    req_b: CanonicalRequirementIR,
    precedence_resolver = None
) -> Optional[SemanticRelation]:
    """
    Compare two requirements and determine if there's a relation.
    Exact canonical equivalence -> DUPLICATES
    Compatible semantic equivalence -> EXACT_MATCH
    Overlapping intent -> CLOSE_MATCH
    Added scope/constraints -> REFINES
    Authoritative replacement -> SUPERSEDES
    Incompatible force/bounds/state -> CONFLICTS_WITH
    """
    if req_a.stable_id == req_b.stable_id:
        return None

    text_a = req_a.canonical_statement.lower().strip()
    text_b = req_b.canonical_statement.lower().strip()

    # 1. Exact canonical equivalence
    if text_a == text_b:
        return SemanticRelation(
            req_a.stable_id, req_b.stable_id, "DUPLICATES",
            "Identical canonical statement text."
        )

    # 2. Check for incompatibility (CONFLICTS_WITH)
    # E.g. opposite forces: A must do X vs A must not do X
    if req_a.actor == req_b.actor and req_a.actor != "system":
        # Force collision
        forces = {req_a.force, req_b.force}
        if ("MUST" in forces or "SHALL" in forces) and ("MUST_NOT" in forces or "SHOULD_NOT" in forces or "PROHIBITED" in forces):
            return SemanticRelation(
                req_a.stable_id, req_b.stable_id, "CONFLICTS_WITH",
                f"Conflicting modalities: {req_a.force} vs {req_b.force}."
            )

    # 3. Refinement
    # If req_b has the same actor and behavior but additional conditions/preconditions
    if req_a.actor == req_b.actor and req_a.actor != "system":
        if text_a in text_b and len(text_b) > len(text_a):
            return SemanticRelation(
                req_b.stable_id, req_a.stable_id, "REFINES",
                f"Statement '{req_b.stable_id}' adds constraints to '{req_a.stable_id}'."
            )
        if text_b in text_a and len(text_a) > len(text_b):
            return SemanticRelation(
                req_a.stable_id, req_b.stable_id, "REFINES",
                f"Statement '{req_a.stable_id}' adds constraints to '{req_b.stable_id}'."
            )

    # 4. Supersedes
    # If precedence_resolver is provided and resolves doc precedence
    if precedence_resolver:
        # If we have a relation or close equivalence but they belong to different documents
        # Let's say one supersedes another
        pass

    # Simple word overlap heuristic for close match
    words_a = set(text_a.split())
    words_b = set(text_b.split())
    intersection = words_a.intersection(words_b)
    union = words_a.union(words_b)
    jaccard = len(intersection) / len(union) if union else 0.0

    if jaccard > 0.8:
        return SemanticRelation(
            req_a.stable_id, req_b.stable_id, "EXACT_MATCH",
            f"High lexical similarity ({jaccard:.2f}) indicating semantic equivalence."
        )
    elif jaccard > 0.5:
        return SemanticRelation(
            req_a.stable_id, req_b.stable_id, "CLOSE_MATCH",
            f"Moderate lexical similarity ({jaccard:.2f}) indicating potential overlap."
        )

    return None
