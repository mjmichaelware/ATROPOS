from typing import List, Dict, Any, Optional, Tuple
from hashlib import sha256
from datetime import datetime, UTC


def utc_now() -> str:
    return datetime.now(UTC).isoformat()


QUARANTINE_REASONS = {
    "STRUCTURAL_ATOM": "Atom has only structural/presentation content, no normative force.",
    "DEFAULT_KIND": "Atom kind was set to FUNCTIONAL by default without evidence.",
    "DEFAULT_MODALITY": "Atom modality defaulted to MUST/REQUIRED without evidence.",
    "MISSING_PROVENANCE": "Atom has no provenance chain to source bytes.",
    "SYNTHETIC_DEPENDENCY": "Dependency edge was inferred from position/proximity, not authority or port match.",
    "ORPHAN_NODE": "Atom has no valid parent in the authority graph.",
    "FALSE_RESEARCH_RESOLUTION": "Research claim was NOT_APPLICABLE without supporting evidence.",
    "UNSUPPORTED_RELATION": "Atom has a relation type not in the valid set.",
}


class QuarantineItem:
    def __init__(self, item_id: str, reason_code: str, original_row: Dict[str, Any],
                 replacement_atom_id: Optional[str] = None,
                 migration_activity_id: Optional[str] = None):
        self.item_id = item_id
        self.reason_code = reason_code
        self.original_row = original_row
        self.replacement_atom_id = replacement_atom_id
        self.migration_activity_id = migration_activity_id or f"migrate-{sha256(item_id.encode()).hexdigest()[:12]}"
        self.quarantined_at = utc_now()

    def to_dict(self) -> Dict[str, Any]:
        return {
            "quarantined_id": self.item_id,
            "reason_code": self.reason_code,
            "reason_message": QUARANTINE_REASONS.get(self.reason_code, "Unknown"),
            "original_row": self.original_row,
            "replacement_atom_id": self.replacement_atom_id,
            "migration_activity_id": self.migration_activity_id,
            "quarantined_at": self.quarantined_at,
            "before_fingerprint": sha256(str(self.original_row).encode()).hexdigest()[:16],
        }


class QuarantineLedger:
    def __init__(self):
        self.items: List[QuarantineItem] = []

    def quarantine(self, item_id: str, reason_code: str, original_row: Dict[str, Any],
                   replacement_atom_id: Optional[str] = None) -> QuarantineItem:
        assert reason_code in QUARANTINE_REASONS, f"Unknown quarantine reason: {reason_code}"
        item = QuarantineItem(
            item_id=item_id, reason_code=reason_code,
            original_row=original_row,
            replacement_atom_id=replacement_atom_id,
        )
        self.items.append(item)
        return item

    def all_items(self) -> List[QuarantineItem]:
        return list(self.items)

    def to_list(self) -> List[Dict[str, Any]]:
        return [i.to_dict() for i in self.items]


def scan_legacy_atoms(atoms: List[Dict[str, Any]]) -> Tuple[List[Dict[str, Any]], QuarantineLedger]:
    ledger = QuarantineLedger()
    valid_atoms: List[Dict[str, Any]] = []

    for atom in atoms:
        reasons = []

        kind = atom.get("kind", "")
        if kind == "FUNCTIONAL" and not _has_domain_evidence(atom):
            reasons.append("DEFAULT_KIND")

        modality = atom.get("modality", "")
        if modality == "REQUIRED":
            reasons.append("DEFAULT_MODALITY")

        provenance = atom.get("provenance_chain", atom.get("source_sha256"))
        if kind == "FUNCTIONAL" and not provenance:
            reasons.append("MISSING_PROVENANCE")

        canonical = atom.get("canonical_statement", "")
        if not canonical.strip():
            reasons.append("STRUCTURAL_ATOM")

        if reasons:
            primary_reason = reasons[0]
            ledger.quarantine(
                item_id=atom.get("id", "?"),
                reason_code=primary_reason,
                original_row=atom,
            )
        else:
            valid_atoms.append(atom)

    return valid_atoms, ledger


def _has_domain_evidence(atom: Dict[str, Any]) -> bool:
    statement = atom.get("canonical_statement", "").lower()
    domains = atom.get("domains", [])
    if domains and any(d not in ("UNSPECIFIED", "FUNCTIONAL", "FUNCTIONAL_CONTRACT") for d in domains):
        return True
    domain_keywords = ["database", "api", "ui", "security", "performance",
                        "data", "schema", "integration", "test", "deploy"]
    return any(kw in statement for kw in domain_keywords)
