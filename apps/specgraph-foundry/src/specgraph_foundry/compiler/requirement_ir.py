from typing import List, Dict, Any, Optional
from .source_coordinates import SourceCoordinates

TRUTHFUL_COMPLETION_STATES = {
    "NOT_STARTED",
    "READY",
    "IMPLEMENTING",
    "IMPLEMENTED_UNCOMPILED",
    "COMPILED",
    "TESTED",
    "VERIFIED",
    "BLOCKED",
    "REJECTED",
}

EXTRACTION_DECISIONS = {
    "ACCEPTED",
    "REJECTED",
}


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
        provenance_chain: Optional[List[Dict[str, str]]] = None,
        source_document_id: Optional[str] = None,
        source_version: Optional[str] = None,
        source_sha256: Optional[str] = None,
        source_artifact_id: Optional[str] = None,
        extraction_decision: str = "ACCEPTED",
        extraction_rejection_reason: Optional[str] = None,
        authority_classification: str = "SOURCE_AUTHORITY",
        predecessor_ids: Optional[List[str]] = None,
        successor_ids: Optional[List[str]] = None,
        execution_node_id: Optional[str] = None,
        semantic_owner: Optional[str] = None,
        implementation_symbols: Optional[List[Dict[str, str]]] = None,
        behavioral_tests: Optional[List[Dict[str, str]]] = None,
        evidence_refs: Optional[List[Dict[str, str]]] = None,
        acceptance_predicate: Optional[str] = None,
        completion_state: str = "NOT_STARTED",
        verifier_identity: str = "specgraph.compiler.v1",
        artifact_hashes: Optional[Dict[str, str]] = None
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
        self.source_document_id = source_document_id
        self.source_version = source_version
        self.source_sha256 = source_sha256
        self.source_artifact_id = source_artifact_id
        if extraction_decision not in EXTRACTION_DECISIONS:
            raise ValueError(f"invalid extraction_decision: {extraction_decision}")
        if extraction_decision == "REJECTED" and not extraction_rejection_reason:
            raise ValueError("rejected extraction requires extraction_rejection_reason")
        self.extraction_decision = extraction_decision
        self.extraction_rejection_reason = extraction_rejection_reason
        self.authority_classification = authority_classification
        self.predecessor_ids = predecessor_ids or []
        self.successor_ids = successor_ids or []
        self.execution_node_id = execution_node_id
        self.semantic_owner = semantic_owner
        self.implementation_symbols = implementation_symbols or []
        self.behavioral_tests = behavioral_tests or []
        self.evidence_refs = evidence_refs or []
        self.acceptance_predicate = acceptance_predicate
        if completion_state not in TRUTHFUL_COMPLETION_STATES:
            raise ValueError(f"invalid completion_state: {completion_state}")
        if completion_state == "VERIFIED" and (
            not self.behavioral_tests or not self.evidence_refs
        ):
            raise ValueError("VERIFIED atoms require behavioral_tests and evidence_refs")
        self.completion_state = completion_state
        self.verifier_identity = verifier_identity
        self.artifact_hashes = artifact_hashes or {}

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
            "source_document_id": self.source_document_id,
            "source_version": self.source_version,
            "source_sha256": self.source_sha256,
            "source_artifact_id": self.source_artifact_id,
            "extraction_decision": self.extraction_decision,
            "extraction_rejection_reason": self.extraction_rejection_reason,
            "authority_classification": self.authority_classification,
            "predecessor_ids": self.predecessor_ids,
            "successor_ids": self.successor_ids,
            "execution_node_id": self.execution_node_id,
            "semantic_owner": self.semantic_owner,
            "implementation_symbols": self.implementation_symbols,
            "behavioral_tests": self.behavioral_tests,
            "evidence_refs": self.evidence_refs,
            "acceptance_predicate": self.acceptance_predicate,
            "completion_state": self.completion_state,
            "verifier_identity": self.verifier_identity,
            "artifact_hashes": self.artifact_hashes,
        }
