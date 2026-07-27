from typing import List, Dict, Any, Optional
from hashlib import sha256
from .source_coordinates import SourceCoordinates


class UnresolvedRecord:
    def __init__(
        self,
        record_id: str,
        source_sha256: str,
        coordinates: SourceCoordinates,
        original_text: str,
        role_candidates: List[str],
        failed_rules: List[str],
        confidence: float,
        missing_axes: List[str],
        conflicts: List[str],
        pass_fingerprint: str,
        next_action: str = "REVIEW",
    ):
        self.record_id = record_id
        self.source_sha256 = source_sha256
        self.coordinates = coordinates
        self.original_text = original_text
        self.role_candidates = role_candidates
        self.failed_rules = failed_rules
        self.confidence = confidence
        self.missing_axes = missing_axes
        self.conflicts = conflicts
        self.pass_fingerprint = pass_fingerprint
        self.next_action = next_action

    def to_dict(self) -> Dict[str, Any]:
        return {
            "id": self.record_id,
            "source_sha256": self.source_sha256,
            "coordinates": self.coordinates.to_dict(),
            "original_text": self.original_text,
            "role_candidates": self.role_candidates,
            "failed_rules": self.failed_rules,
            "confidence": self.confidence,
            "missing_axes": self.missing_axes,
            "conflicts": self.conflicts,
            "pass_fingerprint": self.pass_fingerprint,
            "next_action": self.next_action,
        }


class UnresolvedTracker:
    def __init__(self, project_id: str, source_sha256: str, pass_fingerprint: str):
        self.project_id = project_id
        self.source_sha256 = source_sha256
        self.pass_fingerprint = pass_fingerprint
        self.records: List[UnresolvedRecord] = []
        self._counter = 0

    def register_unresolved(
        self,
        original_text: str,
        coordinates: SourceCoordinates,
        role_candidates: Optional[List[str]] = None,
        failed_rules: Optional[List[str]] = None,
        confidence: float = 0.0,
        missing_axes: Optional[List[str]] = None,
        conflicts: Optional[List[str]] = None,
    ) -> UnresolvedRecord:
        self._counter += 1
        payload = f"{self.project_id}:{self.source_sha256}:unresolved:{self._counter}:{coordinates.byte_start}:{coordinates.byte_end}"
        record_id = f"unresolved-{sha256(payload.encode('utf-8')).hexdigest()[:16]}"

        record = UnresolvedRecord(
            record_id=record_id,
            source_sha256=self.source_sha256,
            coordinates=coordinates,
            original_text=original_text,
            role_candidates=role_candidates or [],
            failed_rules=failed_rules or [],
            confidence=confidence,
            missing_axes=missing_axes or [],
            conflicts=conflicts or [],
            pass_fingerprint=self.pass_fingerprint,
        )
        self.records.append(record)
        return record

    def all_records(self) -> List[UnresolvedRecord]:
        return list(self.records)

    def to_list(self) -> List[Dict[str, Any]]:
        return [r.to_dict() for r in self.records]


def detect_unresolved_candidacy(
    stmt_text: str,
    role: str,
    coordinates: SourceCoordinates,
    tracker: UnresolvedTracker,
    modal_present: bool = False,
    actor_present: bool = False,
) -> Optional[UnresolvedRecord]:
    if role == "UNRESOLVED":
        failed_rules = []
        missing_axes = []

        if not modal_present:
            failed_rules.append("NO_MODAL_KEYWORD")
            missing_axes.append("modality")
        if not actor_present:
            failed_rules.append("NO_SYSTEM_ACTOR")
            missing_axes.append("actor")

        return tracker.register_unresolved(
            original_text=stmt_text,
            coordinates=coordinates,
            role_candidates=["NORMATIVE_REQUIREMENT", "BACKGROUND", "OBSERVATION"],
            failed_rules=failed_rules,
            confidence=0.3,
            missing_axes=missing_axes,
            conflicts=[],
        )
    return None
