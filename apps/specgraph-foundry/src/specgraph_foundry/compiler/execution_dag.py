from typing import List, Dict, Any, Optional, Set, Tuple
from collections import defaultdict, deque
from .compiler_fingerprints import generate_fingerprint


class ExecutionNode:
    def __init__(self, node_id: str, node_type: str, label: str,
                 source_atom_id: Optional[str] = None,
                 acceptance_basis: Optional[str] = None):
        self.node_id = node_id
        self.node_type = node_type
        self.label = label
        self.source_atom_id = source_atom_id
        self.acceptance_basis = acceptance_basis

    def to_dict(self) -> Dict[str, Any]:
        return {
            "id": self.node_id,
            "node_type": self.node_type,
            "label": self.label,
            "source_atom_id": self.source_atom_id,
            "acceptance_basis": self.acceptance_basis,
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

    for atom_id, atom in atom_map.items():
        if atom_id in resolved_unresolved_ids:
            continue
        force = atom.get("force", "")
        if force in {"MUST", "MUST_NOT", "SHALL", "SHOULD", "MAY", "PROHIBITED"}:
            node = ExecutionNode(
                node_id=f"contract-{atom_id}",
                node_type="CONTRACT",
                label=atom.get("canonical_statement", ""),
                source_atom_id=atom_id,
                acceptance_basis="ROLE_CLASSIFICATION",
            )
            execution_nodes[node.node_id] = node

    # Derive edges only from accepted Authority Graph relations
    accepted_edge_types = {
        "REQUIRES", "PRODUCES", "CONSUMES", "ALLOCATES_TO",
        "IMPLEMENTED_BY", "VERIFIED_BY", "TRACED_TO",
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

    # Validate acyclicity via Kahn
    node_ids = list(execution_nodes.keys())
    edge_tuples = [(e.from_id, e.to_id) for e in execution_edges]
    adj = defaultdict(list)
    in_degree = {nid: 0 for nid in node_ids}
    for u, v in edge_tuples:
        if u in in_degree and v in in_degree:
            adj[u].append(v)
            in_degree[v] += 1

    queue = deque([nid for nid in node_ids if in_degree[nid] == 0])
    order = []
    while queue:
        u = queue.popleft()
        order.append(u)
        for v in adj[u]:
            in_degree[v] -= 1
            if in_degree[v] == 0:
                queue.append(v)

    has_cycle = len(order) != len(node_ids)
    if has_cycle:
        execution_edges = _break_cycles(execution_nodes, execution_edges)

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


def _break_cycles(
    nodes: Dict[str, ExecutionNode],
    edges: List[ExecutionEdge],
) -> List[ExecutionEdge]:
    node_ids = list(nodes.keys())
    edge_set = set()
    acyclic: List[ExecutionEdge] = []
    for e in edges:
        key = (e.from_id, e.to_id)
        if key in edge_set:
            continue
        adj = defaultdict(list)
        for existing in acyclic:
            adj[existing.from_id].append(existing.to_id)
        adj[e.from_id].append(e.to_id)
        in_deg = {nid: 0 for nid in node_ids}
        for u in adj:
            for v in adj[u]:
                in_deg[v] += 1
        q = deque([nid for nid in node_ids if in_deg[nid] == 0])
        count = 0
        while q:
            u = q.popleft()
            count += 1
            for v in adj[u]:
                in_deg[v] -= 1
                if in_deg[v] == 0:
                    q.append(v)
        if count == len(node_ids):
            acyclic.append(e)
            edge_set.add(key)
    return acyclic


def _compute_readiness(
    nodes: Dict[str, ExecutionNode],
    edges: List[ExecutionEdge],
) -> Dict[str, str]:
    states: Dict[str, str] = {}
    for nid in nodes:
        states[nid] = "READY"

    # Nodes with no incoming edges are root -> READY
    has_incoming = defaultdict(bool)
    for e in edges:
        has_incoming[e.to_id] = True

    for nid in nodes:
        if nodes[nid].node_type == "CONTRACT" and not has_incoming[nid]:
            states[nid] = "READY"

    return states
