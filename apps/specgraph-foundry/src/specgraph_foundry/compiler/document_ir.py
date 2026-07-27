import hashlib
import json
from typing import Optional, List, Dict, Any
from .source_coordinates import SourceCoordinates

STRUCTURAL_ROLES = {
    "DOCUMENT", "FRONT_MATTER", "TITLE", "SUBTITLE", "SECTION", "HEADING",
    "PARAGRAPH", "LIST", "LIST_ITEM", "DEFINITION_LIST", "TABLE",
    "TABLE_HEADER", "TABLE_ROW", "TABLE_CELL", "CODE_BLOCK", "INLINE_CODE",
    "QUOTE", "NOTE", "WARNING", "EXAMPLE_BLOCK", "CAPTION", "FIGURE",
    "FORMULA", "FOOTNOTE", "REFERENCE", "SEPARATOR", "LABEL", "METADATA",
    "PAGE_HEADER", "PAGE_FOOTER", "UNKNOWN"
}

class DocumentNode:
    def __init__(
        self,
        node_id: str,
        role: str,
        content: str,
        coordinates: SourceCoordinates,
        parent_id: Optional[str] = None,
        metadata: Optional[Dict[str, Any]] = None,
        children: Optional[List['DocumentNode']] = None
    ):
        self.node_id = node_id
        self.role = role
        self.content = content
        self.coordinates = coordinates
        self.parent_id = parent_id
        self.metadata = metadata or {}
        self.children = children or []

    def to_dict(self) -> Dict[str, Any]:
        return {
            "node_id": self.node_id,
            "role": self.role,
            "content": self.content,
            "coordinates": self.coordinates.to_dict(),
            "parent_id": self.parent_id,
            "metadata": self.metadata,
            "children": [child.to_dict() for child in self.children]
        }

def generate_stable_id(
    project_id: str,
    source_sha256: str,
    role: str,
    coordinates: SourceCoordinates,
    compiler_namespace: str = "specgraph-v1"
) -> str:
    # Use deterministic hash over coordinates to generate ID
    coord_str = f"{coordinates.byte_start}:{coordinates.byte_end}:{coordinates.line_start}:{coordinates.line_end}"
    payload = f"{project_id}:{source_sha256}:{role}:{coord_str}:{compiler_namespace}"
    h = hashlib.sha256(payload.encode("utf-8")).hexdigest()
    return f"{role.lower()}-{h[:16]}"
