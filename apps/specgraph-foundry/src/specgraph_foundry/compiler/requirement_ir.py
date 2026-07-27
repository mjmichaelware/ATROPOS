from typing import List, Dict, Any, Optional
from .source_coordinates import SourceCoordinates

class CanonicalRequirementIR:
    def __init__(
        self,
        stable_id: str,
        coordinates: SourceCoordinates,
        original_statement: str,
        canonical_statement: str,
        actor: str,
        force: str,
        action_predicate: Optional[str] = None,
        obj: Optional[str] = None,
        trigger: Optional[str] = None,
        preconditions: Optional[List[str]] = None,
        postconditions: Optional[List[str]] = None,
        constraints: Optional[List[str]] = None,
        exceptions: Optional[List[str]] = None,
        quantitative_bounds: Optional[List[Dict[str, str]]] = None,
        scope: Optional[str] = None,
        domains: Optional[List[str]] = None,
        target_artifacts: Optional[List[str]] = None,
        produced_artifacts: Optional[List[str]] = None,
        consumed_artifacts: Optional[List[str]] = None,
        verification_methods: Optional[List[str]] = None,
        acceptance_criteria: Optional[List[str]] = None,
        authority_state: str = "PROPOSED",
        ambiguity_state: str = "RESOLVED",
        quality_findings: Optional[List[Dict[str, str]]] = None,
        provenance_chain: Optional[List[Dict[str, str]]] = None
    ):
        self.stable_id = stable_id
        self.coordinates = coordinates
        self.original_statement = original_statement
        self.canonical_statement = canonical_statement
        self.actor = actor
        self.force = force
        self.action_predicate = action_predicate
        self.obj = obj
        self.trigger = trigger
        self.preconditions = preconditions or []
        self.postconditions = postconditions or []
        self.constraints = constraints or []
        self.exceptions = exceptions or []
        self.quantitative_bounds = quantitative_bounds or []
        self.scope = scope
        self.domains = domains or []
        self.target_artifacts = target_artifacts or []
        self.produced_artifacts = produced_artifacts or []
        self.consumed_artifacts = consumed_artifacts or []
        self.verification_methods = verification_methods or []
        self.acceptance_criteria = acceptance_criteria or []
        self.authority_state = authority_state
        self.ambiguity_state = ambiguity_state
        self.quality_findings = quality_findings or []
        self.provenance_chain = provenance_chain or []

    def to_dict(self) -> Dict[str, Any]:
        return {
            "stable_id": self.stable_id,
            "coordinates": self.coordinates.to_dict(),
            "original_statement": self.original_statement,
            "canonical_statement": self.canonical_statement,
            "actor": self.actor,
            "force": self.force,
            "action_predicate": self.action_predicate,
            "obj": self.obj,
            "trigger": self.trigger,
            "preconditions": self.preconditions,
            "postconditions": self.postconditions,
            "constraints": self.constraints,
            "exceptions": self.exceptions,
            "quantitative_bounds": self.quantitative_bounds,
            "scope": self.scope,
            "domains": self.domains,
            "target_artifacts": self.target_artifacts,
            "produced_artifacts": self.produced_artifacts,
            "consumed_artifacts": self.consumed_artifacts,
            "verification_methods": self.verification_methods,
            "acceptance_criteria": self.acceptance_criteria,
            "authority_state": self.authority_state,
            "ambiguity_state": self.ambiguity_state,
            "quality_findings": self.quality_findings,
            "provenance_chain": self.provenance_chain,
        }
