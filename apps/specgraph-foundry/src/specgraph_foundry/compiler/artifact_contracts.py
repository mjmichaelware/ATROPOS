import re
from typing import List, Dict, Any, Optional

PORT_TYPES = {
    "PRODUCES", "CONSUMES", "READS", "WRITES", "REGISTERS", "EXPOSES",
    "PERSISTS", "RESTORES", "MIGRATES", "VERIFIES"
}

class ArtifactPort:
    def __init__(self, port_type: str, artifact_name: str, schema_version: Optional[str] = None):
        if port_type not in PORT_TYPES:
            raise ValueError(f"Invalid port type: {port_type}")
        self.port_type = port_type
        self.artifact_name = artifact_name
        self.schema_version = schema_version or "1.0.0"

    def to_dict(self) -> Dict[str, Any]:
        return {
            "port_type": self.port_type,
            "artifact_name": self.artifact_name,
            "schema_version": self.schema_version
        }

def extract_artifact_ports(text: str) -> List[ArtifactPort]:
    """
    Scan requirements text for explicit artifact producers, consumers, writers.
    """
    ports = []
    text_lower = text.lower()

    # 1. PRODUCES (generates, creates, produces, outputs)
    produces_match = re.search(r"\b(?:produce|produces|generate|generates|create|creates|output|outputs)\s+(?:a|the)?\s*([a-zA-Z0-9_\-]+)\b", text_lower)
    if produces_match:
        ports.append(ArtifactPort("PRODUCES", produces_match.group(1)))

    # 2. CONSUMES (consumes, reads, uses, reads from)
    consumes_match = re.search(r"\b(?:consume|consumes|read|reads|use|uses|read\s+from|reads\s+from)\s+(?:a|the)?\s*([a-zA-Z0-9_\-]+)\b", text_lower)
    if consumes_match:
        ports.append(ArtifactPort("CONSUMES", consumes_match.group(1)))

    # 3. WRITES (writes, persists, stores, saves)
    writes_match = re.search(r"\b(?:write|writes|persist|persists|store|stores|save|saves)\s+(?:a|the)?\s*([a-zA-Z0-9_\-]+)\b", text_lower)
    if writes_match:
        ports.append(ArtifactPort("WRITES", writes_match.group(1)))

    # 4. EXPOSES (exposes, registers, serves)
    exposes_match = re.search(r"\b(?:expose|exposes|register|registers|serve|serves)\s+(?:an?|the)?\s*([a-zA-Z0-9_\-]+)\b", text_lower)
    if exposes_match:
        ports.append(ArtifactPort("EXPOSES", exposes_match.group(1)))

    return ports
