from typing import List, Dict, Any, Optional
from hashlib import sha256


APPLICABILITY_STATES = {
    "APPLICABLE_OPEN",
    "APPLICABLE_RESOLVED",
    "NOT_APPLICABLE",
    "UNRESOLVED_APPLICABILITY",
}


class ApplicabilityState:
    def __init__(self, atom_id: str, state: str, dimension: str = "FUNCTIONAL_CONTRACT",
                 evidence_summary: Optional[str] = None,
                 claim_id: Optional[str] = None,
                 fingerprint: Optional[str] = None):
        self.atom_id = atom_id
        assert state in APPLICABILITY_STATES, f"Invalid applicability state: {state}"
        self.state = state
        self.dimension = dimension
        self.evidence_summary = evidence_summary
        self.claim_id = claim_id
        self.fingerprint = fingerprint or self._compute_fingerprint()

    def _compute_fingerprint(self) -> str:
        payload = f"{self.atom_id}:{self.state}:{self.dimension}:{self.evidence_summary or ''}:{self.claim_id or ''}"
        return sha256(payload.encode("utf-8")).hexdigest()[:16]

    def to_dict(self) -> Dict[str, Any]:
        return {
            "atom_id": self.atom_id,
            "state": self.state,
            "dimension": self.dimension,
            "evidence_summary": self.evidence_summary,
            "claim_id": self.claim_id,
            "fingerprint": self.fingerprint,
        }


class ApplicabilityTracker:
    def __init__(self):
        self._states: Dict[str, ApplicabilityState] = {}
        self._legacy_compat: List[Dict[str, Any]] = []

    def set_applicability(self, atom_id: str, state: str, dimension: str = "FUNCTIONAL_CONTRACT",
                          evidence_summary: Optional[str] = None,
                          claim_id: Optional[str] = None) -> ApplicabilityState:
        as_obj = ApplicabilityState(
            atom_id=atom_id, state=state, dimension=dimension,
            evidence_summary=evidence_summary, claim_id=claim_id,
        )
        self._states[atom_id] = as_obj
        return as_obj

    def get_applicability(self, atom_id: str) -> Optional[ApplicabilityState]:
        return self._states.get(atom_id)

    def register_legacy_compat(self, compat_row: Dict[str, Any]):
        compat_row["_legacy"] = True
        self._legacy_compat.append(compat_row)

    def is_research_applicable(self, atom_id: str) -> bool:
        state = self._states.get(atom_id)
        if state is None:
            return False
        return state.state == "APPLICABLE_OPEN"

    def is_resolved(self, atom_id: str) -> bool:
        state = self._states.get(atom_id)
        if state is None:
            return False
        return state.state == "APPLICABLE_RESOLVED"

    def is_not_applicable(self, atom_id: str) -> bool:
        state = self._states.get(atom_id)
        if state is None:
            return False
        return state.state == "NOT_APPLICABLE"

    def is_unresolved(self, atom_id: str) -> bool:
        state = self._states.get(atom_id)
        if state is None:
            return True
        return state.state == "UNRESOLVED_APPLICABILITY"

    def all_states(self) -> List[ApplicabilityState]:
        return list(self._states.values())

    def to_list(self) -> List[Dict[str, Any]]:
        return [s.to_dict() for s in self._states.values()]

    def legacy_compat_rows(self) -> List[Dict[str, Any]]:
        return list(self._legacy_compat)


def evaluate_research_applicability(
    atom: Dict[str, Any],
    existing_claim: Optional[Dict[str, Any]] = None,
) -> str:
    if existing_claim:
        applicability = existing_claim.get("applicability", "")
        if applicability == "APPLICABLE":
            claim_status = existing_claim.get("status", "")
            if claim_status in {"COMPLETE", "RESOLVED"}:
                return "APPLICABLE_RESOLVED"
            return "APPLICABLE_OPEN"
        elif applicability == "NOT_APPLICABLE":
            return "NOT_APPLICABLE"
        return "UNRESOLVED_APPLICABILITY"

    force = atom.get("force", "")
    if force in {"MUST", "MUST_NOT", "SHALL", "SHOULD", "MAY"}:
        return "APPLICABLE_OPEN"
    domains = atom.get("domains", [])
    if domains and any(d not in ("UNSPECIFIED",) for d in domains):
        return "APPLICABLE_OPEN"
    return "UNRESOLVED_APPLICABILITY"
