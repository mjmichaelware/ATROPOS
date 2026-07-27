from typing import List, Dict, Any, Optional, Set, Tuple
from hashlib import sha256


SHACL_VERSION = "specgraph-shacl-v1"


class ShapeViolation:
    def __init__(self, severity: str, shape_id: str, focus_node: str,
                 result_message: str, result_path: Optional[str] = None,
                 value: Optional[Any] = None):
        self.severity = severity
        self.shape_id = shape_id
        self.focus_node = focus_node
        self.result_message = result_message
        self.result_path = result_path
        self.value = value

    def to_dict(self) -> Dict[str, Any]:
        return {
            "severity": self.severity,
            "shape_id": self.shape_id,
            "focus_node": self.focus_node,
            "result_message": self.result_message,
            "result_path": self.result_path,
            "value": self.value,
        }


class Shape:
    def __init__(self, shape_id: str, description: str,
                 node_type: str, version: str = SHACL_VERSION):
        self.shape_id = shape_id
        self.description = description
        self.node_type = node_type
        self.version = version

    def validate(self, node: Dict[str, Any]) -> List[ShapeViolation]:
        raise NotImplementedError


class RequirementShape(Shape):
    def __init__(self):
        super().__init__(
            "RequirementShape",
            "Validates that a requirement atom has all mandatory properties",
            "ATOM",
        )

    def validate(self, node: Dict[str, Any]) -> List[ShapeViolation]:
        violations = []
        node_id = node.get("stable_id") or node.get("id", "?")
        statement = node.get("canonical_statement") or node.get("title", "")
        if not statement.strip():
            violations.append(ShapeViolation(
                "ERROR", self.shape_id, node_id,
                "Missing canonical_statement.",
                result_path="canonical_statement",
            ))
        if not node.get("coordinates"):
            violations.append(ShapeViolation(
                "ERROR", self.shape_id, node_id,
                "Missing source coordinates.",
                result_path="coordinates",
            ))
        coords = node.get("coordinates", {})
        if isinstance(coords, dict):
            byte_start = coords.get("byte_start", -1)
            byte_end = coords.get("byte_end", -1)
            if byte_start < 0 or byte_end < 0 or byte_end < byte_start:
                violations.append(ShapeViolation(
                    "ERROR", self.shape_id, node_id,
                    f"Invalid byte coordinates [{byte_start}, {byte_end}).",
                    result_path="coordinates",
                ))
        force = node.get("force")
        if force and force not in {"MUST", "MUST_NOT", "SHALL", "SHOULD",
                                    "SHOULD_NOT", "MAY", "UNSPECIFIED",
                                    "PROHIBITED", "DECLARATIVE", "BINDING_FACT",
                                    "GOAL", "INFORMATIVE"}:
            violations.append(ShapeViolation(
                "WARNING", self.shape_id, node_id,
                f"Unrecognized modality: '{force}'.",
                result_path="force", value=force,
            ))
        return violations


class UnresolvedShape(Shape):
    def __init__(self):
        super().__init__(
            "UnresolvedShape",
            "Ensures UNRESOLVED records carry required metadata",
            "UNRESOLVED",
        )

    def validate(self, node: Dict[str, Any]) -> List[ShapeViolation]:
        violations = []
        node_id = node.get("id", "?")
        if not node.get("coordinates"):
            violations.append(ShapeViolation(
                "ERROR", self.shape_id, node_id,
                "UNRESOLVED record missing coordinates.",
                result_path="coordinates",
            ))
        if not node.get("confidence"):
            violations.append(ShapeViolation(
                "WARNING", self.shape_id, node_id,
                "UNRESOLVED record missing confidence value.",
                result_path="confidence",
            ))
        return violations


class AuthorityRelationShape(Shape):
    VALID_TYPES = {
        "REFINES", "CLARIFIES", "SUPERSEDES", "CONFLICTS_WITH",
        "DUPLICATES", "EXACT_MATCH", "CLOSE_MATCH", "RATIONALE_FOR",
        "ACCEPTANCE_FOR", "ALLOCATES_TO", "PRODUCES", "CONSUMES",
        "IMPLEMENTED_BY", "VERIFIED_BY", "TRACED_TO", "GENERATED_BY",
        "USED", "PROPOSED_BY", "ACCEPTED_BY", "REJECTED_BY",
        "INVALIDATED_BY", "REQUIRES", "VIEWPOINT_CONFLICT",
        "REFINES", "REALIZES", "WEAKENS", "DEFERRED_BY",
        "ARGUES_DEFECT", "ARGUES_REFINEMENT",
    }

    def __init__(self):
        super().__init__(
            "AuthorityRelationShape",
            "Validates authority graph edge structure",
            "RELATION",
        )

    def validate(self, edge: Dict[str, Any]) -> List[ShapeViolation]:
        violations = []
        edge_id = f"{edge.get('from_node_id', '?')}->{edge.get('to_node_id', '?')}"
        rel_type = edge.get("relation_type") or edge.get("edge_type")
        if not rel_type:
            violations.append(ShapeViolation(
                "ERROR", self.shape_id, edge_id,
                "Authority relation missing relation_type.",
                result_path="relation_type",
            ))
        elif rel_type not in self.VALID_TYPES:
            violations.append(ShapeViolation(
                "ERROR", self.shape_id, edge_id,
                f"Unsupported relation type '{rel_type}'. Must be one of {sorted(self.VALID_TYPES)}.",
                result_path="relation_type", value=rel_type,
            ))
        if not edge.get("from_node_id"):
            violations.append(ShapeViolation(
                "ERROR", self.shape_id, edge_id,
                "Authority relation missing from_node_id.",
                result_path="from_node_id",
            ))
        if not edge.get("to_node_id"):
            violations.append(ShapeViolation(
                "ERROR", self.shape_id, edge_id,
                "Authority relation missing to_node_id.",
                result_path="to_node_id",
            ))
        if edge.get("from_node_id") and edge.get("to_node_id") \
           and edge["from_node_id"] == edge["to_node_id"]:
            violations.append(ShapeViolation(
                "ERROR", self.shape_id, edge_id,
                "Self-loop authority relation.",
                result_path="from_node_id",
            ))
        return violations


class DependencyEdgeShape(Shape):
    def __init__(self):
        super().__init__(
            "DependencyEdgeShape",
            "Validates dependency edge invariants",
            "DEPENDENCY",
        )

    def validate(self, edge: Dict[str, Any]) -> List[ShapeViolation]:
        violations = []
        edge_id = f"{edge.get('from_node_id', '?')}->{edge.get('to_node_id', '?')}"
        if not edge.get("from_node_id") or not edge.get("to_node_id"):
            violations.append(ShapeViolation(
                "ERROR", self.shape_id, edge_id,
                "Dependency edge missing from_node_id or to_node_id.",
            ))
        if edge.get("from_node_id") == edge.get("to_node_id"):
            violations.append(ShapeViolation(
                "ERROR", self.shape_id, edge_id,
                "Self-loop dependency edge.",
            ))
        if not edge.get("evidence"):
            violations.append(ShapeViolation(
                "WARNING", self.shape_id, edge_id,
                "Dependency edge missing acceptance evidence.",
                result_path="evidence",
            ))
        return violations


class ExecutionNodeShape(Shape):
    def __init__(self):
        super().__init__(
            "ExecutionNodeShape",
            "Validates execution DAG node properties",
            "EXECUTION_NODE",
        )

    def validate(self, node: Dict[str, Any]) -> List[ShapeViolation]:
        violations = []
        node_id = node.get("id", "?")
        if not node.get("node_type"):
            violations.append(ShapeViolation(
                "ERROR", self.shape_id, node_id,
                "Execution node missing node_type.",
                result_path="node_type",
            ))
        valid_types = {"CONTRACT", "IMPLEMENTATION", "VERIFICATION",
                       "ACCEPTANCE_CRITERION"}
        if node.get("node_type") not in valid_types:
            violations.append(ShapeViolation(
                "ERROR", self.shape_id, node_id,
                f"Execution node has invalid node_type '{node.get('node_type')}'.",
                result_path="node_type", value=node.get("node_type"),
            ))
        return violations


class ProviderProposalShape(Shape):
    def __init__(self):
        super().__init__(
            "ProviderProposalShape",
            "Ensures provider proposals are never treated as authority",
            "PROVIDER_PROPOSAL",
        )

    def validate(self, proposal: Dict[str, Any]) -> List[ShapeViolation]:
        violations = []
        proposal_id = proposal.get("id", "?")
        status = proposal.get("status", "PROPOSED")
        if status not in {"PROPOSED", "ACCEPTED", "REJECTED",
                           "INVALIDATED", "SUPERSEDED"}:
            violations.append(ShapeViolation(
                "ERROR", self.shape_id, proposal_id,
                f"Invalid proposal status '{status}'.",
                result_path="status", value=status,
            ))
        if status == "ACCEPTED" and not proposal.get("acceptance_basis"):
            violations.append(ShapeViolation(
                "WARNING", self.shape_id, proposal_id,
                "Accepted proposal missing acceptance_basis.",
                result_path="acceptance_basis",
            ))
        return violations


SHAPES: List[Shape] = [
    RequirementShape(),
    UnresolvedShape(),
    AuthorityRelationShape(),
    DependencyEdgeShape(),
    ExecutionNodeShape(),
    ProviderProposalShape(),
]


def validate_graph(graph: Dict[str, Any]) -> Dict[str, Any]:
    violations: List[ShapeViolation] = []
    nodes = graph.get("nodes", [])
    edges = graph.get("edges", [])
    proposals = graph.get("proposals", [])

    for node in nodes:
        node_type = node.get("node_type", "")
        if node_type == "ATOM":
            violations.extend(RequirementShape().validate(node))
        elif node_type == "UNRESOLVED":
            violations.extend(UnresolvedShape().validate(node))
        else:
            violations.extend(ExecutionNodeShape().validate(node))

    for edge in edges:
        violations.extend(AuthorityRelationShape().validate(edge))
        if "dependency" in str(edge.get("edge_type", "")).lower() or \
           edge.get("relation_type") == "DEPENDENCY":
            violations.extend(DependencyEdgeShape().validate(edge))

    for proposal in proposals:
        violations.extend(ProviderProposalShape().validate(proposal))

    has_violations = any(v.severity == "ERROR" for v in violations)
    return {
        "valid": not has_violations,
        "shape_version": SHACL_VERSION,
        "violation_count": len(violations),
        "violations": [v.to_dict() for v in violations],
        "fingerprint": sha256(
            str([v.to_dict() for v in violations]).encode()
        ).hexdigest()[:16],
    }
