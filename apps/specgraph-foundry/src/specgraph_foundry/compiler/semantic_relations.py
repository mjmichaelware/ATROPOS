from typing import List, Dict, Any, Optional, Tuple
from .requirement_ir import CanonicalRequirementIR
from .compiler_fingerprints import generate_fingerprint

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
        self.relation_id = f"rel-{generate_fingerprint(self.identity_payload())[:16]}"

    def identity_payload(self) -> Dict[str, Any]:
        return {
            "from_atom_id": self.from_req_id,
            "to_atom_id": self.to_req_id,
            "relation_type": self.relation_type,
            "rationale": self.rationale,
            "confidence": self.confidence,
            "inferred": self.inferred,
        }

    def to_dict(self) -> Dict[str, Any]:
        return {
            "id": self.relation_id,
            "from_atom_id": self.from_req_id,
            "to_atom_id": self.to_req_id,
            "relation_type": self.relation_type,
            "rationale": self.rationale,
            "rationale_sha256": generate_fingerprint(self.rationale),
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
        supersession = _authority_supersession_relation(
            req_a,
            req_b,
            precedence_resolver,
            1.0,
        )
        if supersession:
            return supersession
        if getattr(req_a, "source_document_id", None) != getattr(req_b, "source_document_id", None):
            return SemanticRelation(
                req_a.stable_id, req_b.stable_id, "EXACT_MATCH",
                "Identical canonical statement text across unresolved source authorities.",
            )
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

    equivalence = _lexical_equivalence(text_a, text_b)

    # 4. Supersedes
    # If precedence_resolver is provided and resolves doc precedence.
    if precedence_resolver and equivalence[0] in {"EXACT_MATCH", "CLOSE_MATCH"}:
        supersession = _authority_supersession_relation(
            req_a,
            req_b,
            precedence_resolver,
            equivalence[1],
        )
        if supersession:
            return supersession

    # Simple word overlap heuristic for close match
    relation_type, confidence = equivalence
    if relation_type == "EXACT_MATCH":
        return SemanticRelation(
            req_a.stable_id, req_b.stable_id, "EXACT_MATCH",
            f"High lexical similarity ({confidence:.2f}) indicating semantic equivalence.",
            confidence=confidence,
        )
    if relation_type == "CLOSE_MATCH":
        return SemanticRelation(
            req_a.stable_id, req_b.stable_id, "CLOSE_MATCH",
            f"Moderate lexical similarity ({confidence:.2f}) indicating potential overlap.",
            confidence=confidence,
        )

    return None


def _lexical_equivalence(text_a: str, text_b: str) -> Tuple[str, float]:
    words_a = set(text_a.split())
    words_b = set(text_b.split())
    intersection = words_a.intersection(words_b)
    union = words_a.union(words_b)
    jaccard = len(intersection) / len(union) if union else 0.0

    if jaccard > 0.8:
        return "EXACT_MATCH", jaccard
    if jaccard > 0.5:
        return "CLOSE_MATCH", jaccard
    return "", jaccard


def _authority_supersession_relation(
    req_a: CanonicalRequirementIR,
    req_b: CanonicalRequirementIR,
    precedence_resolver: Any,
    confidence: float,
) -> Optional[SemanticRelation]:
    if not precedence_resolver:
        return None

    doc_a = getattr(req_a, "source_document_id", None)
    doc_b = getattr(req_b, "source_document_id", None)
    if not doc_a or not doc_b or doc_a == doc_b:
        return None

    winner = precedence_resolver.resolve_precedence(doc_a, doc_b)
    if winner == doc_a:
        return SemanticRelation(
            req_a.stable_id, req_b.stable_id, "SUPERSEDES",
            f"Source authority '{doc_a}' has precedence over '{doc_b}'.",
            confidence=confidence,
            inferred=False,
        )
    if winner == doc_b:
        return SemanticRelation(
            req_b.stable_id, req_a.stable_id, "SUPERSEDES",
            f"Source authority '{doc_b}' has precedence over '{doc_a}'.",
            confidence=confidence,
            inferred=False,
        )
    return None
