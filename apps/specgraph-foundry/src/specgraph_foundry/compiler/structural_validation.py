from typing import List, Dict, Any, Optional, Tuple
from .source_coordinates import SourceCoordinates
from .document_ir import DocumentNode, STRUCTURAL_ROLES


class ValidationFinding:
    def __init__(
        self,
        severity: str,
        code: str,
        message: str,
        node_id: Optional[str] = None,
        coordinates: Optional[SourceCoordinates] = None,
        provenance: Optional[Dict[str, Any]] = None,
    ):
        self.severity = severity
        self.code = code
        self.message = message
        self.node_id = node_id
        self.coordinates = coordinates
        self.provenance = provenance or {}

    def to_dict(self) -> Dict[str, Any]:
        return {
            "severity": self.severity,
            "code": self.code,
            "message": self.message,
            "node_id": self.node_id,
            "coordinates": self.coordinates.to_dict() if self.coordinates else None,
            "provenance": self.provenance,
        }


class QuarantineResult:
    def __init__(
        self,
        accepted: List[DocumentNode],
        quarantined: List[Tuple[DocumentNode, List[ValidationFinding]]],
        findings: List[ValidationFinding],
        fingerprint: str,
    ):
        self.accepted = accepted
        self.quarantined = quarantined
        self.findings = findings
        self.fingerprint = fingerprint

    @property
    def accepted_ids(self) -> List[str]:
        return [n.node_id for n in self.accepted]

    @property
    def quarantined_ids(self) -> List[str]:
        return [n.node_id for n, _ in self.quarantined]

    @property
    def accepted_count(self) -> int:
        return len(self.accepted)

    @property
    def quarantined_count(self) -> int:
        return len(self.quarantined)

    def to_dict(self) -> Dict[str, Any]:
        return {
            "accepted_count": self.accepted_count,
            "quarantined_count": self.quarantined_count,
            "total_findings": len(self.findings),
            "findings": [f.to_dict() for f in self.findings],
            "fingerprint": self.fingerprint,
            "accepted_ids": self.accepted_ids,
            "quarantined_ids": self.quarantined_ids,
        }


def _flatten_tree(node: DocumentNode) -> List[DocumentNode]:
    result = []
    def traverse(n: DocumentNode):
        result.append(n)
        for child in n.children:
            traverse(child)
    traverse(node)
    return result


class StructuralValidator:
    def __init__(self, source_sha256: str, raw_content: Optional[bytes] = None):
        self.source_sha256 = source_sha256
        self.raw_content = raw_content

    def validate(self, root_node: DocumentNode) -> QuarantineResult:
        findings: List[ValidationFinding] = []
        accepted: List[DocumentNode] = []
        quarantined: List[Tuple[DocumentNode, List[ValidationFinding]]] = []

        all_nodes = _flatten_tree(root_node)

        for node in all_nodes:
            node_findings: List[ValidationFinding] = []

            node_findings.extend(self._check_coordinate_bounds(node))
            node_findings.extend(self._check_role_validity(node))
            if self.raw_content is not None:
                node_findings.extend(self._check_text_fidelity(node))

            if node_findings:
                quarantined.append((node, node_findings))
                findings.extend(node_findings)
            else:
                accepted.append(node)

        findings.extend(self._check_sibling_overlaps(all_nodes))

        findings.extend(self._check_parent_references(all_nodes))

        from hashlib import sha256
        payload = f"StructuralValidator:v1:{self.source_sha256}:{len(all_nodes)}:{len(findings)}"
        fingerprint = sha256(payload.encode("utf-8")).hexdigest()[:16]

        return QuarantineResult(
            accepted=accepted,
            quarantined=quarantined,
            findings=findings,
            fingerprint=fingerprint,
        )

    def _check_coordinate_bounds(self, node: DocumentNode) -> List[ValidationFinding]:
        findings: List[ValidationFinding] = []
        c = node.coordinates

        if c.byte_start < 0:
            findings.append(ValidationFinding(
                severity="ERROR", code="NEGATIVE_BYTE_START",
                message=f"Node '{node.node_id}' has negative byte_start ({c.byte_start}).",
                node_id=node.node_id, coordinates=c,
            ))
        if c.byte_end < c.byte_start:
            findings.append(ValidationFinding(
                severity="ERROR", code="INVALID_BYTE_RANGE",
                message=f"Node '{node.node_id}' has byte_end ({c.byte_end}) < byte_start ({c.byte_start}).",
                node_id=node.node_id, coordinates=c,
            ))
        if c.line_start < 1:
            findings.append(ValidationFinding(
                severity="ERROR", code="NEGATIVE_LINE_START",
                message=f"Node '{node.node_id}' has line_start ({c.line_start}) < 1.",
                node_id=node.node_id, coordinates=c,
            ))
        if c.line_end < c.line_start:
            findings.append(ValidationFinding(
                severity="ERROR", code="INVALID_LINE_RANGE",
                message=f"Node '{node.node_id}' has line_end ({c.line_end}) < line_start ({c.line_start}).",
                node_id=node.node_id, coordinates=c,
            ))

        return findings

    def _check_role_validity(self, node: DocumentNode) -> List[ValidationFinding]:
        findings: List[ValidationFinding] = []
        if node.role not in STRUCTURAL_ROLES:
            findings.append(ValidationFinding(
                severity="ERROR", code="UNKNOWN_ROLE",
                message=f"Node '{node.node_id}' has unrecognized structural role '{node.role}'. Must be one of {sorted(STRUCTURAL_ROLES)}.",
                node_id=node.node_id, coordinates=node.coordinates,
            ))
        return findings

    def _check_text_fidelity(self, node: DocumentNode) -> List[ValidationFinding]:
        findings: List[ValidationFinding] = []
        if self.raw_content is None:
            return findings
        c = node.coordinates
        if 0 <= c.byte_start < c.byte_end <= len(self.raw_content):
            raw_slice = self.raw_content[c.byte_start:c.byte_end]
            try:
                raw_slice.decode("utf-8")
            except UnicodeDecodeError:
                findings.append(ValidationFinding(
                    severity="ERROR", code="INVALID_UTF8",
                    message=f"Node '{node.node_id}' byte range [{c.byte_start}, {c.byte_end}) contains invalid UTF-8.",
                    node_id=node.node_id, coordinates=c,
                ))
        return findings

    def _check_sibling_overlaps(self, flat_nodes: List[DocumentNode]) -> List[ValidationFinding]:
        findings: List[ValidationFinding] = []
        parent_children: Dict[str, List[DocumentNode]] = {}
        for node in flat_nodes:
            pid = node.parent_id or "ROOT"
            if pid not in parent_children:
                parent_children[pid] = []
            parent_children[pid].append(node)

        for pid, children in parent_children.items():
            sorted_children = sorted(children, key=lambda n: n.coordinates.byte_start)
            for i in range(1, len(sorted_children)):
                prev = sorted_children[i - 1]
                curr = sorted_children[i]
                if prev.coordinates.byte_end > curr.coordinates.byte_start:
                    findings.append(ValidationFinding(
                        severity="ERROR", code="OVERLAPPING_REGION",
                        message=f"Siblings '{prev.node_id}' (byte_end={prev.coordinates.byte_end}) and '{curr.node_id}' (byte_start={curr.coordinates.byte_start}) overlap under parent '{pid}'.",
                        node_id=curr.node_id, coordinates=curr.coordinates,
                        provenance={
                            "overlap_with": prev.node_id,
                            "prev_byte_end": prev.coordinates.byte_end,
                            "curr_byte_start": curr.coordinates.byte_start,
                        },
                    ))
        return findings

    def _check_parent_references(self, flat_nodes: List[DocumentNode]) -> List[ValidationFinding]:
        findings: List[ValidationFinding] = []
        node_map = {n.node_id: n for n in flat_nodes}
        for node in flat_nodes:
            if node.parent_id and node.parent_id not in node_map:
                findings.append(ValidationFinding(
                    severity="ERROR", code="ORPHAN_NODE",
                    message=f"Node '{node.node_id}' references parent_id '{node.parent_id}' which does not exist.",
                    node_id=node.node_id, coordinates=node.coordinates,
                ))
        return findings
