import re
from typing import List, Dict, Any, Optional
from .requirement_ir import CanonicalRequirementIR
from .compiler_fingerprints import generate_fingerprint

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
        self.edge_id = f"dep-{generate_fingerprint(self.identity_payload())[:16]}"

    def identity_payload(self) -> Dict[str, str]:
        return {
            "from_node_id": self.from_id,
            "to_node_id": self.to_id,
            "rule": self.rule,
            "evidence": self.evidence,
        }

    def to_dict(self) -> Dict[str, Any]:
        return {
            "id": self.edge_id,
            "from_node_id": self.from_id,
            "to_node_id": self.to_id,
            "rule": self.rule,
            "evidence": self.evidence,
            "evidence_sha256": generate_fingerprint(self.evidence),
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

    def has_path(adj: dict[str, list[str]], start: str, end: str, visited: set[str] | None = None) -> bool:
        if visited is None:
            visited = set()
        if start == end:
            return True
        if start in visited:
            return False
        visited.add(start)
        for neighbor in adj.get(start, []):
            if has_path(adj, neighbor, end, visited):
                return True
        return False

    # Deduplicate edges and prevent cycles
    seen = set()
    deduped = []
    adj: dict[str, list[str]] = {}
    for edge in edges:
        key = (edge.from_id, edge.to_id)
        if key not in seen:
            if has_path(adj, edge.to_id, edge.from_id):
                # Cycle detected! Skip this edge to maintain DAG invariant
                continue
            seen.add(key)
            deduped.append(edge)
            if edge.from_id not in adj:
                adj[edge.from_id] = []
            adj[edge.from_id].append(edge.to_id)

    return deduped


# `dependsOn: [S-001, S-004]`, as an obligation document writes it.
#
# Unanchored, and matched with finditer rather than match. The markdown
# paragraph accumulator glues an atom's title, its `dependsOn:` line and its
# RESEARCH/IMPL/WIRE triple into one node, and segmentation joins those lines
# into one statement with the newlines collapsed to spaces -- so a
# `dependsOn:` never begins the text it lives in, and an anchored pattern
# found none of the eighty-one in the document this was written against.
DECLARED_DEPENDS_ON_RE = re.compile(r"dependsOn\s*:\s*\[([^\]]*)\]", re.IGNORECASE)


def parse_declared_dependencies(text: str) -> List[List[str]]:
    """Every `dependsOn:` claim in [text], each as its list of ids.

    A list of lists, because one statement can carry several: the accumulator
    that glues an atom to its triple sometimes glues two atoms together too.
    An empty inner list is a real answer -- `dependsOn: []` says this atom is a
    root -- and is kept so the count of declarations stays honest.
    """
    return [
        [ref.strip() for ref in match.group(1).split(",") if ref.strip()]
        for match in DECLARED_DEPENDS_ON_RE.finditer(text)
    ]


def compile_declared_dependencies(
    ordered_statements: List[Dict[str, str]],
    statement_to_requirement: Dict[str, str],
    declared_to_requirement: Dict[str, str],
) -> tuple[List[DependencyEdge], Dict[str, Any]]:
    """The edges the document states outright, rather than the ones inferred.

    An obligation DAG writes its own graph down. Every atom is followed by a
    `dependsOn: [...]` line naming the atoms that must be green first -- 81 of
    them in the document this was written against. Those lines classify as
    DOCUMENT_METADATA, which is correct (an edge is not work), and then nothing
    read them: every execution edge in the graph was inferred by matching
    phrases like "requires" in prose, while the author's own explicit answer
    sat one line below in the source.

    Resolution never guesses. An id becomes an edge only when some requirement
    in the same document declared that id in its own statement. A reference to
    an atom nobody declared is counted as dangling and reported, because "this
    document names 44 atoms and declares 25 of them" is a fact about the source
    that its author should see -- and inferring the missing 19 from position in
    the file would be inventing a graph rather than reading one.

    @param ordered_statements dicts with `statement_id` and `text`, in document
        order.
    @param statement_to_requirement statement id to the requirement stable id
        it produced. A statement that produced none is absent.
    @param declared_to_requirement declared atom id (`S-001`) to the stable id
        of the requirement that declared it.
    @return the edges, and a report of what resolved.
    """
    edges: List[DependencyEdge] = []
    dangling: List[str] = []
    unowned = 0
    declaration_count = 0
    owner: Optional[str] = None

    for entry in ordered_statements:
        statement_id = entry["statement_id"]
        claims = parse_declared_dependencies(entry.get("text", ""))

        # A statement that produced a requirement can own the edges stated in
        # it, and becomes the owner for any that follow. One that produced none
        # -- a heading, a note -- must not take ownership, or an edge would be
        # attributed to a line that is not work and then dropped.
        produced = statement_to_requirement.get(statement_id)
        if produced is not None:
            owner = produced

        for references in claims:
            declaration_count += 1
            if owner is None:
                unowned += 1
                continue
            for reference in references:
                prerequisite = declared_to_requirement.get(reference)
                if prerequisite is None:
                    dangling.append(reference)
                    continue
                if prerequisite == owner:
                    # A self-edge is not an ordering. Keeping it would make the
                    # graph cyclic on a line the document meant as a no-op.
                    continue
                edges.append(DependencyEdge(
                    from_id=prerequisite,
                    to_id=owner,
                    rule="DECLARED_DEPENDS_ON",
                    evidence=f"Source declares dependsOn: {reference}",
                ))

    # Deduplicated, keeping document order. One statement can restate an edge
    # its neighbour already stated, and the graph should carry it once.
    unique: List[DependencyEdge] = []
    seen = set()
    for edge in edges:
        key = (edge.from_id, edge.to_id)
        if key in seen:
            continue
        seen.add(key)
        unique.append(edge)

    report = {
        "declaration_count": declaration_count,
        "edge_count": len(unique),
        "duplicate_edge_count": len(edges) - len(unique),
        "dangling_reference_count": len(dangling),
        "dangling_references": sorted(set(dangling)),
        "unowned_declaration_count": unowned,
        "declared_id_count": len(declared_to_requirement),
    }
    return unique, report
