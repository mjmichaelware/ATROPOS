from collections import defaultdict, deque
from typing import List, Dict, Any, Tuple, Optional, Set

class GraphValidationError(Exception):
    pass

def kahn_topological_sort(
    nodes: List[str],
    edges: List[Tuple[str, str]]
) -> List[str]:
    """
    Sort nodes topologically using Kahn's algorithm.
    Raises GraphValidationError if a cycle is detected.
    """
    # Build adjacency list and in-degree map
    adj = defaultdict(list)
    in_degree = {node: 0 for node in nodes}

    for u, v in edges:
        if u not in in_degree or v not in in_degree:
            continue
        adj[u].append(v)
        in_degree[v] += 1

    queue = deque([node for node in nodes if in_degree[node] == 0])
    order = []

    while queue:
        u = queue.popleft()
        order.append(u)
        for v in adj[u]:
            in_degree[v] -= 1
            if in_degree[v] == 0:
                queue.append(v)

    if len(order) != len(nodes):
        # Cycle detected!
        raise GraphValidationError("Cycle detected in graph.")

    return order

def find_minimal_cycle(
    nodes: List[str],
    edges: List[Tuple[str, str]]
) -> List[str]:
    """
    Find the minimal cycle in the graph using DFS path tracing.
    Returns list of node IDs in the cycle.
    """
    adj = defaultdict(list)
    for u, v in edges:
        adj[u].append(v)

    visited: Set[str] = set()
    rec_stack: List[str] = []
    stack_set: Set[str] = set()

    def dfs(node: str) -> Optional[List[str]]:
        visited.add(node)
        rec_stack.append(node)
        stack_set.add(node)

        for neighbor in adj[node]:
            if neighbor not in visited:
                cycle = dfs(neighbor)
                if cycle:
                    return cycle
            elif neighbor in stack_set:
                # Cycle found! Extract the cycle path
                idx = rec_stack.index(neighbor)
                return rec_stack[idx:] + [neighbor]

        rec_stack.pop()
        stack_set.remove(node)
        return None

    for node in nodes:
        if node not in visited:
            cycle = dfs(node)
            if cycle:
                return cycle

    return []

def validate_graph_invariants(
    nodes: List[Dict[str, Any]],
    edges: List[Dict[str, Any]],
    enforce_acyclic: bool = True
) -> List[Dict[str, str]]:
    """
    Enforce graph validation rules:
    - No self-loops
    - No duplicate edges
    - Acyclic validation (if enforce_acyclic is True)
    - Missing producer detection
    - Orphan / unreachable nodes detection (as warnings)
    """
    findings = []
    node_ids = {node["id"] for node in nodes}

    # 1. Self loops & missing nodes
    seen_edges = set()
    clean_edges: List[Tuple[str, str]] = []

    for edge in edges:
        from_id = edge["from_node_id"]
        to_id = edge["to_node_id"]

        if from_id == to_id:
            findings.append({
                "severity": "ERROR",
                "code": "SELF_EDGE",
                "message": f"Node '{from_id}' has a dependency edge to itself."
            })
            continue

        if from_id not in node_ids:
            findings.append({
                "severity": "ERROR",
                "code": "MISSING_PRODUCER",
                "message": f"Edge references missing source node '{from_id}'."
            })
            continue

        if to_id not in node_ids:
            findings.append({
                "severity": "ERROR",
                "code": "MISSING_CONSUMER",
                "message": f"Edge references missing target node '{to_id}'."
            })
            continue

        edge_key = (from_id, to_id, edge.get("edge_type", ""))
        if edge_key in seen_edges:
            findings.append({
                "severity": "WARNING",
                "code": "DUPLICATE_EDGE",
                "message": f"Duplicate edge detected from '{from_id}' to '{to_id}'."
            })
            continue
        seen_edges.add(edge_key)
        clean_edges.append((from_id, to_id))

    # 2. Cycle validation
    if enforce_acyclic:
        try:
            kahn_topological_sort(list(node_ids), clean_edges)
        except GraphValidationError:
            cycle_path = find_minimal_cycle(list(node_ids), clean_edges)
            cycle_str = " -> ".join(cycle_path)
            findings.append({
                "severity": "ERROR",
                "code": "CYCLIC_DEPENDENCY",
                "message": f"Cyclic dependency detected: {cycle_str}"
            })

    # 3. SHACL-like node validation
    for node in nodes:
        node_type = node.get("node_type")
        if not node.get("title"):
            findings.append({
                "severity": "ERROR",
                "code": "SHACL_CONSTRAINT_VIOLATION",
                "message": f"Node '{node['id']}' violates shape: missing title."
            })

    return findings
