from typing import List, Dict, Any, Optional, Set, Tuple
from collections import defaultdict, deque
from .compiler_fingerprints import generate_fingerprint


# The obligation strengths a document can state outright.
#
# Kept as a vocabulary, not as an admission test. A node carries the strength
# the document stated so a MUST can still be told from an unstated one
# downstream -- what changed is that an unstated strength no longer removes the
# work from the graph.
DECLARED_FORCES = frozenset({
    "MUST", "MUST_NOT", "SHALL", "SHALL_NOT", "SHOULD", "SHOULD_NOT",
    "MAY", "PROHIBITED", "REQUIRED",
})


class ExecutionNode:
    def __init__(self, node_id: str, node_type: str, label: str,
                 source_atom_id: Optional[str] = None,
                 acceptance_basis: Optional[str] = None,
                 force: str = "UNSPECIFIED"):
        self.node_id = node_id
        self.node_type = node_type
        self.label = label
        self.source_atom_id = source_atom_id
        self.acceptance_basis = acceptance_basis
        self.force = force or "UNSPECIFIED"

    def to_dict(self) -> Dict[str, Any]:
        return {
            "id": self.node_id,
            "node_type": self.node_type,
            "label": self.label,
            "source_atom_id": self.source_atom_id,
            "acceptance_basis": self.acceptance_basis,
            "force": self.force,
        }


class ExecutionEdge:
    def __init__(self, from_id: str, to_id: str, edge_type: str,
                 acceptance_basis: str, provenance: Optional[List[str]] = None):
        self.from_id = from_id
        self.to_id = to_id
        self.edge_type = edge_type
        self.acceptance_basis = acceptance_basis
        self.provenance = provenance or []

    def to_dict(self) -> Dict[str, Any]:
        return {
            "from_node_id": self.from_id,
            "to_node_id": self.to_id,
            "edge_type": self.edge_type,
            "acceptance_basis": self.acceptance_basis,
            "provenance": self.provenance,
        }


def build_execution_dag(
    atoms: List[Dict[str, Any]],
    authority_edges: List[Dict[str, Any]],
    resolved_unresolved_ids: Set[str],
) -> Dict[str, Any]:
    atom_map = {a.get("stable_id") or a.get("id", ""): a for a in atoms}
    execution_nodes: Dict[str, ExecutionNode] = {}
    execution_edges: List[ExecutionEdge] = []

    # Every accepted atom becomes a node.
    #
    # This used to admit only atoms whose text carried a modal verb -- a `force`
    # in the declared set -- and drop the rest. Extraction had already stopped
    # requiring modality by then, deliberately, because obligation documents
    # state their work structurally rather than in "shall" sentences; so the
    # same modal test simply moved one stage downstream and did the same damage
    # where nobody was measuring. Measured on a real 390-atom obligation DAG:
    # 361 atoms recorded UNSPECIFIED, 29 carried a modal, and the execution
    # graph contained exactly those 29. The document's own atoms had been
    # extracted correctly and then thrown away here.
    #
    # `force` is a statement about how strongly the document phrased something.
    # It is not a statement about whether the work exists. An atom that reached
    # this point survived segmentation, discourse classification, candidacy and
    # decomposition; deciding a second time that it is not real, on weaker
    # evidence than any of those stages used, cannot be right.
    #
    # The strength is carried onto the node instead of gating it, so a consumer
    # that genuinely needs to rank a MUST above an unstated obligation still
    # can, and does it with the fact in hand rather than with a hole.
    for atom_id, atom in atom_map.items():
        if not atom_id or atom_id in resolved_unresolved_ids:
            continue
        node = ExecutionNode(
            node_id=f"contract-{atom_id}",
            node_type="CONTRACT",
            label=atom.get("canonical_statement", ""),
            source_atom_id=atom_id,
            acceptance_basis="ROLE_CLASSIFICATION",
            force=atom.get("force") or "UNSPECIFIED",
        )
        execution_nodes[node.node_id] = node

    # Edge types that mean "this has to happen first".
    #
    # The first group are Authority Graph relations. The second are the rules
    # the dependency compiler emits, which were being computed on every run and
    # then never reaching this function -- the caller passed only the authority
    # relations, so a document with 108 compiled dependency edges produced an
    # execution graph with zero. A DAG with no edges is a list, and a list
    # cannot say what to build first.
    #
    # REFINES is deliberately absent: it says one statement narrows another,
    # which is a semantic relationship and not an execution order.
    accepted_edge_types = {
        "REQUIRES", "PRODUCES", "CONSUMES", "ALLOCATES_TO",
        "IMPLEMENTED_BY", "VERIFIED_BY", "TRACED_TO",
        "DECLARED_DEPENDS_ON", "AUTHORITY_REQUIRES", "EXPLICIT_PHRASE",
        "PRODUCER_CONSUMER_CONTRACT", "ARCH_SCHEMA_BEFORE_SERIALIZER",
        "ARCH_MODEL_BEFORE_MIGRATION",
    }

    for edge in authority_edges:
        rel_type = edge.get("relation_type") or edge.get("edge_type", "")
        from_atom = edge.get("from_atom_id") or edge.get("from_node_id", "")
        to_atom = edge.get("to_atom_id") or edge.get("to_node_id", "")

        if rel_type not in accepted_edge_types:
            continue
        if from_atom not in atom_map or to_atom not in atom_map:
            continue

        from_node_id = f"contract-{from_atom}"
        to_node_id = f"contract-{to_atom}"
        if from_node_id not in execution_nodes or to_node_id not in execution_nodes:
            continue

        basis = f"Authority Graph relation '{rel_type}'"
        execution_edges.append(ExecutionEdge(
            from_id=from_node_id,
            to_id=to_node_id,
            edge_type=rel_type,
            acceptance_basis=basis,
            provenance=[f"authority_graph:{rel_type}:{from_atom}->{to_atom}"],
        ))

    # Add producer-consumer edges from artifact port matching
    for a in atoms:
        for b in atoms:
            aid = a.get("stable_id") or a.get("id", "")
            bid = b.get("stable_id") or b.get("id", "")
            if aid >= bid:
                continue
            a_produces = set(a.get("produced_artifacts", []))
            b_consumes = set(b.get("consumed_artifacts", []))
            overlap = a_produces.intersection(b_consumes)
            if overlap:
                from_id = f"contract-{aid}"
                to_id = f"contract-{bid}"
                if from_id in execution_nodes and to_id in execution_nodes:
                    execution_edges.append(ExecutionEdge(
                        from_id=from_id, to_id=to_id,
                        edge_type="PRODUCER_CONSUMER",
                        acceptance_basis="Artifact port matching",
                        provenance=[f"artifact:{aid}_produces_{list(overlap)}_consumed_by_{bid}"],
                    ))

    # Order the graph, and if it will not order, break the cycles and order
    # the result. The order used to be computed once, before cycle breaking,
    # and kept whatever Kahn's algorithm had managed before it stalled -- so a
    # cyclic input returned repaired edges alongside a truncated or empty
    # execution order that no longer described them.
    node_ids = list(execution_nodes.keys())
    order = _topological_order(node_ids, execution_edges)
    if len(order) != len(node_ids):
        execution_edges = _break_cycles(execution_nodes, execution_edges)
        order = _topological_order(node_ids, execution_edges)

    # Compute READY vs BLOCKED
    ready_states = _compute_readiness(execution_nodes, execution_edges)

    nodes_payload = [n.to_dict() for n in execution_nodes.values()]
    edges_payload = [e.to_dict() for e in execution_edges]
    fingerprint = generate_fingerprint({
        "schema": "execution-dag-v1",
        "nodes": nodes_payload,
        "edges": edges_payload,
        "execution_order": order,
        "ready_states": ready_states,
    })[:16]

    return {
        "nodes": nodes_payload,
        "edges": edges_payload,
        "execution_order": order,
        "ready_states": ready_states,
        "fingerprint": fingerprint,
    }


def _topological_order(
    node_ids: List[str],
    edges: List[ExecutionEdge],
) -> List[str]:
    """Kahn's algorithm. A result shorter than [node_ids] means a cycle."""
    adj = defaultdict(list)
    in_degree = {nid: 0 for nid in node_ids}
    for edge in edges:
        if edge.from_id in in_degree and edge.to_id in in_degree:
            adj[edge.from_id].append(edge.to_id)
            in_degree[edge.to_id] += 1

    queue = deque(nid for nid in node_ids if in_degree[nid] == 0)
    order: List[str] = []
    while queue:
        current = queue.popleft()
        order.append(current)
        for successor in adj[current]:
            in_degree[successor] -= 1
            if in_degree[successor] == 0:
                queue.append(successor)
    return order


def _break_cycles(
    nodes: Dict[str, ExecutionNode],
    edges: List[ExecutionEdge],
) -> List[ExecutionEdge]:
    """Keep every edge that does not close a cycle, in the order given.

    An edge `u -> v` closes a cycle exactly when `v` already reaches `u` over
    the edges kept so far, so the test is one reachability walk rather than a
    full topological sort of the whole graph per candidate edge. The previous
    implementation rebuilt the adjacency map and ran Kahn's algorithm once for
    every edge considered, which is O(E^2 * (V + E)) -- tolerable on the
    handful of nodes the tests use and quadratic-with-a-multiplier on a real
    document's several hundred. Same edges kept, same order, same result.
    """
    kept_adjacency: Dict[str, List[str]] = defaultdict(list)
    seen: Set[Tuple[str, str]] = set()
    acyclic: List[ExecutionEdge] = []

    def reaches(start: str, target: str) -> bool:
        stack = [start]
        visited: Set[str] = set()
        while stack:
            current = stack.pop()
            if current == target:
                return True
            if current in visited:
                continue
            visited.add(current)
            stack.extend(kept_adjacency.get(current, ()))
        return False

    for edge in edges:
        key = (edge.from_id, edge.to_id)
        if key in seen:
            continue
        if edge.from_id == edge.to_id:
            continue
        if reaches(edge.to_id, edge.from_id):
            continue
        kept_adjacency[edge.from_id].append(edge.to_id)
        seen.add(key)
        acyclic.append(edge)

    return acyclic


def _compute_readiness(
    nodes: Dict[str, ExecutionNode],
    edges: List[ExecutionEdge],
) -> Dict[str, str]:
    """READY when nothing has to be built first; BLOCKED when something does.

    This function used to write READY into every slot, then walk the nodes a
    second time and write READY again into the ones with no incoming edge. It
    could not return BLOCKED for any input. That was harmless while the graph
    had no edges at all -- everything genuinely was a root -- and became wrong
    the moment dependency edges started arriving, because a consumer asking
    "what can I start now?" was handed the whole graph.
    """
    blocked: Set[str] = set()
    for edge in edges:
        if edge.to_id in nodes and edge.from_id in nodes:
            blocked.add(edge.to_id)

    return {nid: ("BLOCKED" if nid in blocked else "READY") for nid in nodes}
