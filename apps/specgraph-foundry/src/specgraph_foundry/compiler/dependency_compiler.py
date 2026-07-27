import re
from typing import List, Dict, Any, Optional
from .requirement_ir import CanonicalRequirementIR

DEPENDENCY_PHRASES = [
    r"\bdepends\s+on\b",
    r"\brequires\b",
    r"\bafter\b",
    r"\bbefore\b",
    r"\bonly\s+after\b",
    r"\bcannot\s+start\s+until\b",
    r"\bblocked\s+by\b",
    r"\bconsumes\b",
    r"\bgenerated\s+by\b",
    r"\bregistered\s+in\b",
    r"\bpersisted\s+by\b",
    r"\brestored\s+from\b",
    r"\bverified\s+by\b"
]

class DependencyEdge:
    def __init__(self, from_id: str, to_id: str, rule: str, evidence: str):
        self.from_id = from_id
        self.to_id = to_id
        self.rule = rule
        self.evidence = evidence

    def to_dict(self) -> Dict[str, Any]:
        return {
            "from_node_id": self.from_id,
            "to_node_id": self.to_id,
            "rule": self.rule,
            "evidence": self.evidence
        }

def compile_dependencies(
    requirements: List[CanonicalRequirementIR],
    relations: List[Dict[str, Any]]
) -> List[DependencyEdge]:
    edges = []

    # Map stable_id to requirement for easy lookup
    req_map = {req.stable_id: req for req in requirements}

    # 1. Edge derivation from explicit authority relations (e.g. REQUIRES relations)
    for rel in relations:
        if rel.get("relation_type") == "REQUIRES":
            from_id = rel["from_atom_id"]
            to_id = rel["to_atom_id"]
            if from_id in req_map and to_id in req_map:
                # In planning, REQUIRES means to_atom must happen before from_atom
                # So edge goes from to_id (prerequisite) to from_id (dependent)
                edges.append(DependencyEdge(
                    from_id=to_id,
                    to_id=from_id,
                    rule="AUTHORITY_REQUIRES",
                    evidence=f"Explicit REQUIRES relation in authority graph: {rel.get('rationale', '')}"
                ))

    # 2. Edge derivation from explicit phrases in statement text
    for req in requirements:
        text_lower = req.canonical_statement.lower()
        for pattern in DEPENDENCY_PHRASES:
            if re.search(pattern, text_lower):
                # Search for target keywords/actors within this statement to find dependency target
                for target_req in requirements:
                    if target_req.stable_id == req.stable_id:
                        continue
                    # If target actor or stable_id or a distinct part is mentioned
                    if target_req.actor != "system" and target_req.actor.lower() in text_lower:
                        # Precedence: target_req must happen before req
                        edges.append(DependencyEdge(
                            from_id=target_req.stable_id,
                            to_id=req.stable_id,
                            rule="EXPLICIT_PHRASE",
                            evidence=f"Phrase match '{pattern}' targeting actor '{target_req.actor}'"
                        ))

    # 3. Edge derivation from Artifact Producer-Consumer Contracts
    # If req A produces artifact X and req B consumes artifact X, then A must precede B.
    for req_a in requirements:
        for req_b in requirements:
            if req_a.stable_id == req_b.stable_id:
                continue

            # Find matching products and consumers
            # Check overlap between req_a's produced_artifacts and req_b's consumed_artifacts
            produced = set(req_a.produced_artifacts)
            consumed = set(req_b.consumed_artifacts)
            overlap = produced.intersection(consumed)

            if overlap:
                edges.append(DependencyEdge(
                    from_id=req_a.stable_id,
                    to_id=req_b.stable_id,
                    rule="PRODUCER_CONSUMER_CONTRACT",
                    evidence=f"Requirement '{req_a.stable_id}' produces {list(overlap)} consumed by '{req_b.stable_id}'"
                ))

    # 4. Architectural Rules
    # E.g. Schema before Serializer: If req A is a SCHEMA and req B targets a SERIALIZER or uses it
    for req_a in requirements:
        for req_b in requirements:
            if req_a.stable_id == req_b.stable_id:
                continue

            # rule: schema before serializer
            is_schema_term = "schema" in req_a.canonical_statement.lower()
            is_serializer = "serializer" in req_b.canonical_statement.lower() or "serialize" in req_b.canonical_statement.lower()
            if is_schema_term and is_serializer:
                edges.append(DependencyEdge(
                    from_id=req_a.stable_id,
                    to_id=req_b.stable_id,
                    rule="ARCH_SCHEMA_BEFORE_SERIALIZER",
                    evidence="Schema must precede serializer."
                ))

            # rule: data model before migration
            is_model = "model" in req_a.canonical_statement.lower() or "data model" in req_a.canonical_statement.lower()
            is_migration = "migration" in req_b.canonical_statement.lower()
            if is_model and is_migration:
                edges.append(DependencyEdge(
                    from_id=req_a.stable_id,
                    to_id=req_b.stable_id,
                    rule="ARCH_MODEL_BEFORE_MIGRATION",
                    evidence="Data model definition must precede migration script."
                ))

    # Deduplicate edges based on from_id/to_id
    seen = set()
    deduped = []
    for edge in edges:
        key = (edge.from_id, edge.to_id)
        if key not in seen:
            seen.add(key)
            deduped.append(edge)

    return deduped
