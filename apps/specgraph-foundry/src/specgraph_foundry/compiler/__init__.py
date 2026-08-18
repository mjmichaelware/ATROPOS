from typing import List, Dict, Any, Optional
from .proof import build_proof_bundle, compute_frontier_metrics
from .source_coordinates import SourceCoordinates, compute_sha256
from .document_ir import DocumentNode, generate_stable_id, STRUCTURAL_ROLES
from .format_adapters import parse_markdown_to_ir, repair_wrapped_text, wrap_damage_ratio, WRAP_DAMAGE_RATIO
from .structural_validation import StructuralValidator, ValidationFinding, QuarantineResult
from .statement_segmentation import segment_document_node, StatementIR
from .discourse_roles import classify_discourse_role
from .requirement_candidates import evaluate_candidacy, RequirementCandidacy
from .discourse_roles import is_structural_item
from .atomic_decomposition import decompose_requirement, AtomicRequirement
from .requirement_ir import CanonicalRequirementIR
from .requirement_quality import analyze_quality, convert_defect_findings
from .semantic_types import classify_orthogonal_types
from .provenance import ProvGraph
from .source_authority import AuthorityRegistry, SourceAuthority
from .semantic_relations import evaluate_semantic_relation, SemanticRelation
from .artifact_contracts import extract_artifact_ports
from .dependency_compiler import compile_dependencies, DependencyEdge
from .graph_validation import validate_graph_invariants, compute_graph_metrics
from .shacl_validation import validate_graph as validate_shacl
from .unresolved_tracker import UnresolvedTracker, detect_unresolved_candidacy
from .execution_dag import build_execution_dag
from .applicability import ApplicabilityTracker, evaluate_research_applicability
from .compiler_replay import (
    CompilerEventLog,
    build_event_log_manifest,
    verify_event_log_manifest,
)
from .compiler_fingerprints import generate_fingerprint
from .proof_bundle import verify_proof_bundle


# Roles that are prose, not requirements. A statement classified into one of
# these is not a dropped requirement and must not be reported as one -- listing
# every heading and note as a rejection would bury the handful that matter.
EXCLUSION_ROLES = {
    "TITLE", "HEADING", "SECTION_HEADER", "SEPARATOR", "METADATA",
    "DOCUMENT_METADATA", "STATUS_WORD", "RATIONALE", "EXAMPLE",
    "NOTE", "WARNING", "BACKGROUND", "OBSERVATION", "DEFECT_FINDING",
    "CODE_SAMPLE", "INCOMPLETE_FRAGMENT", "FRAGMENT", "CAPTION",
    "TABLE_HEADER", "OUT_OF_SCOPE", "OPEN_QUESTION",
}


class SpecGraphCompiler:
    def __init__(self, project_id: str, compiler_namespace: str = "specgraph-v1"):
        self.project_id = project_id
        self.compiler_namespace = compiler_namespace
        self.event_log = CompilerEventLog()
        self.prov_graph = ProvGraph()
        self.authority_registry = AuthorityRegistry()

    def compile(self, filename: str, content: bytes, media_type: str = "text/markdown") -> Dict[str, Any]:
        source_sha256 = compute_sha256(content)
        pass_fp = source_sha256[:16]
        source_document_id = f"doc-{pass_fp}"
        self.event_log.record_event("RawSourceIngestion", content, {"sha256": source_sha256, "filename": filename, "media_type": media_type})

        self.prov_graph.add_entity(f"doc-raw-{source_sha256[:16]}", "RawSource", filename, {"sha256": source_sha256})
        self.prov_graph.add_agent("compiler-agent", "Compiler", "SpecGraph Compiler")

        text_content = content.decode("utf-8", errors="strict")

        # Repaired before parsing, and said out loud. A document whose lines
        # were shredded by a PDF extractor has no structure left for the parser
        # to find, and silently repairing it would make two runs over visibly
        # different text claim the same provenance.
        damage = wrap_damage_ratio(text_content)
        wrap_repaired = damage >= WRAP_DAMAGE_RATIO
        if wrap_repaired:
            text_content = repair_wrapped_text(text_content)
        self.event_log.record_event(
            "WrapDamageRepair",
            {"single_token_line_ratio": round(damage, 4), "threshold": WRAP_DAMAGE_RATIO},
            {"repaired": wrap_repaired, "repaired_sha256": compute_sha256(text_content.encode("utf-8"))}
        )

        root_node = parse_markdown_to_ir(self.project_id, source_sha256, text_content)
        self.event_log.record_event("FormatAdapter", text_content, root_node.to_dict())

        validator = StructuralValidator(source_sha256=source_sha256, raw_content=content)
        quarantine_result = validator.validate(root_node)
        self.event_log.record_event("StructuralValidation", root_node.to_dict(), quarantine_result.to_dict())

        quarantined_ids = set(n.node_id for n, _ in quarantine_result.quarantined)
        def _prune_quarantined(node):
            node.children = [c for c in node.children if c.node_id not in quarantined_ids]
            for child in node.children:
                _prune_quarantined(child)
        _prune_quarantined(root_node)

        statements = segment_document_node(self.project_id, source_sha256, root_node)
        self.event_log.record_event("StatementSegmentation", root_node.to_dict(), [s.to_dict() for s in statements])

        unresolved_tracker = UnresolvedTracker(self.project_id, source_sha256, pass_fp)

        classified_statements = []
        last_introducing_role = None
        last_introducing_modality = None
        statement_modalities = {}
        inherited_statements = set()
        for stmt in statements:
            parent_node = next((n for n in root_node.children if n.node_id == stmt.parent_node_id), None)
            parent_role = parent_node.role if parent_node else "UNKNOWN"

            intrinsic_role = classify_discourse_role(stmt, parent_role)

            if intrinsic_role in EXCLUSION_ROLES:
                role = intrinsic_role
                last_introducing_role = None
                last_introducing_modality = None
            elif parent_role == "LIST_ITEM" and last_introducing_role:
                role = last_introducing_role
                inherited_statements.add(stmt.statement_id)
                if last_introducing_modality:
                    statement_modalities[stmt.statement_id] = last_introducing_modality
            else:
                role = intrinsic_role
                if stmt.canonical_text.strip().endswith(":") and role in {"NORMATIVE_REQUIREMENT", "CONSTRAINT", "PROHIBITION"}:
                    last_introducing_role = role
                    intro_text_lower = stmt.canonical_text.lower()
                    if "must not" in intro_text_lower or "shall not" in intro_text_lower or "should not" in intro_text_lower or "never" in intro_text_lower:
                        last_introducing_modality = "MUST_NOT"
                    elif "shall" in intro_text_lower:
                        last_introducing_modality = "SHALL"
                    elif "must" in intro_text_lower or "required" in intro_text_lower or "mandatory" in intro_text_lower:
                        last_introducing_modality = "MUST"
                    elif "should" in intro_text_lower:
                        last_introducing_modality = "SHOULD"
                    elif "may" in intro_text_lower or "optional" in intro_text_lower:
                        last_introducing_modality = "MAY"
                    else:
                        last_introducing_modality = "UNSPECIFIED"
                else:
                    if parent_role != "LIST_ITEM":
                        last_introducing_role = None
                        last_introducing_modality = None

            if role == "UNRESOLVED":
                has_modal = any(kw in stmt.canonical_text.lower()
                                for kw in ["must", "shall", "should", "may"])
                has_actor = any(kw in stmt.canonical_text.lower()
                                for kw in ["system", "service", "api", "module", "component"])
                detect_unresolved_candidacy(
                    stmt_text=stmt.canonical_text,
                    role=role,
                    coordinates=stmt.coordinates,
                    tracker=unresolved_tracker,
                    modal_present=has_modal,
                    actor_present=has_actor,
                )

            classified_statements.append((stmt, role, parent_role))

        self.event_log.record_event(
            "DiscourseRoleClassification",
            [s.to_dict() for s in statements],
            [{"statement_id": s.statement_id, "role": r} for s, r, _ in classified_statements]
        )

        all_candidacies = []
        candidates = []
        for stmt, role, parent_role in classified_statements:
            is_inh = stmt.statement_id in inherited_statements
            cand = evaluate_candidacy(
                stmt, role, is_inherited=is_inh,
                # Structure travels with the statement. Candidacy has to know
                # whether this was a delimited item, because that is what
                # supplies the context an actor keyword otherwise would.
                is_structural=is_structural_item(parent_role)
            )
            all_candidacies.append(cand)
            if cand.is_candidate:
                candidates.append(cand)

        self.event_log.record_event(
            "RequirementCandidacy",
            [s.to_dict() for s, _, _ in classified_statements],
            {"candidacies": [c.to_dict() for c in all_candidacies], "candidate_count": len(candidates)}
        )

        atomic_requirements = []
        for cand in candidates:
            atomics = decompose_requirement(self.project_id, source_sha256, cand)
            atomic_requirements.extend(atomics)

        self.event_log.record_event("AtomicDecomposition", [c.to_dict() for c in candidates], [a.to_dict() for a in atomic_requirements])

        canonical_reqs: List[CanonicalRequirementIR] = []
        for req in atomic_requirements:
            inherited_mod = statement_modalities.get(req.candidacy.statement.statement_id)
            types = classify_orthogonal_types(req.canonical_statement, inherited_modality=inherited_mod)
            ports = extract_artifact_ports(req.canonical_statement)

            produced_art = [p.artifact_name for p in ports if p.port_type == "PRODUCES"]
            consumed_art = [p.artifact_name for p in ports if p.port_type == "CONSUMES"]
            target_art = [p.artifact_name for p in ports if p.port_type == "EXPOSES"]

            quality_findings = analyze_quality(req.canonical_statement)
            semantic_owner = _semantic_owner_for(types["all_domains"])
            atom_identity_payload = {
                "compiler_namespace": self.compiler_namespace,
                "project_id": self.project_id,
                "source_document_id": source_document_id,
                "source_sha256": source_sha256,
                "coordinates": req.coordinates.to_dict(),
                "canonical_statement": req.canonical_statement,
                "force": types["modality"],
            }
            atom_sha256 = generate_fingerprint(atom_identity_payload)

            canonical_reqs.append(CanonicalRequirementIR(
                stable_id=req.requirement_id,
                coordinates=req.coordinates,
                original_statement=req.candidacy.statement.exact_quote,
                canonical_statement=req.canonical_statement,
                actor=req.candidacy.actor,
                force=types["modality"],
                trigger=req.candidacy.trigger,
                domains=types["all_domains"],
                target_artifacts=target_art,
                produced_artifacts=produced_art,
                consumed_artifacts=consumed_art,
                verification_methods=types["all_verifications"],
                quality_findings=quality_findings,
                source_document_id=source_document_id,
                source_version=source_sha256,
                source_sha256=source_sha256,
                source_artifact_id=f"sha256:{source_sha256}",
                extraction_decision="ACCEPTED",
                extraction_rejection_reason=None,
                authority_classification="SOURCE_AUTHORITY",
                semantic_owner=semantic_owner,
                acceptance_predicate=(
                    "Accepted only when source SHA-256, exact coordinates, "
                    "canonical statement, authority classification, and "
                    "downstream verification evidence agree deterministically."
                ),
                completion_state="NOT_STARTED",
                verifier_identity="specgraph.compiler.v1",
                artifact_hashes={
                    "source_sha256": source_sha256,
                    "atom_identity_sha256": atom_sha256,
                },
            ))

        self.event_log.record_event("CanonicalRequirementIR", [a.to_dict() for a in atomic_requirements], [r.to_dict() for r in canonical_reqs])

        semantic_relations: List[SemanticRelation] = []
        for i, req_a in enumerate(canonical_reqs):
            for req_b in canonical_reqs[i+1:]:
                rel = evaluate_semantic_relation(req_a, req_b, self.authority_registry)
                if rel:
                    semantic_relations.append(rel)

        self.event_log.record_event("DuplicateAndConflictResolution", [r.to_dict() for r in canonical_reqs], [rel.to_dict() for rel in semantic_relations])

        authority_nodes = [{
            "id": r.stable_id, "title": r.canonical_statement,
            "canonical_statement": r.canonical_statement,
            "coordinates": r.coordinates.to_dict() if r.coordinates else {},
            "node_type": "ATOM",
        } for r in canonical_reqs]
        authority_edges = [{"from_node_id": rel.from_req_id, "to_node_id": rel.to_req_id, "edge_type": rel.relation_type} for rel in semantic_relations]

        dependency_edges = compile_dependencies(canonical_reqs, [rel.to_dict() for rel in semantic_relations])
        _attach_dependency_support(canonical_reqs, dependency_edges)
        self.event_log.record_event("DependencyCompilation", [r.to_dict() for r in canonical_reqs], [d.to_dict() for d in dependency_edges])

        all_edges = authority_edges + [{"from_node_id": d.from_id, "to_node_id": d.to_id, "edge_type": d.rule} for d in dependency_edges]
        validation_findings = validate_graph_invariants(nodes=authority_nodes, edges=all_edges, enforce_acyclic=True)
        authority_graph_metrics = compute_graph_metrics(authority_nodes, all_edges)
        self.event_log.record_event("GraphValidation", authority_nodes, validation_findings)

        shacl_result = validate_shacl({
            "nodes": authority_nodes,
            "edges": all_edges,
            "proposals": [],
        })
        self.event_log.record_event("SHACLValidation", {"nodes": len(authority_nodes), "edges": len(all_edges)}, shacl_result)

        # `all_edges`, not `authority_edges`. The dependency compiler's output
        # was assembled two statements above, validated, SHACL-checked -- and
        # then withheld from the one consumer that decides build order, so the
        # execution graph came out as isolated nodes with no edges at all.
        exec_dag = build_execution_dag(
            atoms=[r.to_dict() for r in canonical_reqs],
            authority_edges=all_edges,
            resolved_unresolved_ids=set(),
        )
        _attach_execution_support(canonical_reqs, exec_dag)
        execution_graph_metrics = compute_graph_metrics(
            exec_dag.get("nodes", []),
            exec_dag.get("edges", []),
        )

        applicability = ApplicabilityTracker()
        for req_dict in [r.to_dict() for r in canonical_reqs]:
            app_state = evaluate_research_applicability(req_dict)
            applicability.set_applicability(req_dict["stable_id"], app_state)

        defect_remediations = convert_defect_findings(
            findings=[],
            statements=[{"role": r, "canonical_text": s.canonical_text, "coordinates": s.coordinates.to_dict()}
                        for s, r, _ in classified_statements],
        )
        requirements_payload = [r.to_dict() for r in canonical_reqs]
        relations_payload = [rel.to_dict() for rel in semantic_relations]
        dependencies_payload = [d.to_dict() for d in dependency_edges]
        unresolved_payload = unresolved_tracker.to_list()
        proof_bundle = build_proof_bundle(
            compiler_namespace=self.compiler_namespace,
            source_document_id=source_document_id,
            source_sha256=source_sha256,
            requirements=requirements_payload,
            unresolved_records=unresolved_payload,
            relations=relations_payload,
            dependencies=dependencies_payload,
            execution_dag=exec_dag,
            authority_graph_metrics=authority_graph_metrics,
            execution_graph_metrics=execution_graph_metrics,
            shacl_result=shacl_result,
            validation_findings=validation_findings,
        )
        proof_bundle_verification = verify_proof_bundle(proof_bundle)
        event_log_payload = self.event_log.to_list()
        event_log_manifest = build_event_log_manifest(event_log_payload)
        event_log_verification = verify_event_log_manifest(
            event_log_payload,
            event_log_manifest,
        )

        return {
            "fingerprint": generate_fingerprint(requirements_payload),
            "requirements": requirements_payload,
            # Every statement that looked like a requirement and did not become
            # one, with the reason. Returned rather than left in the event log:
            # a sentence dropped for lacking a recognised actor, or for a role
            # nobody made executable, is indistinguishable from a sentence the
            # author never wrote -- and a caller comparing "five MUSTs in, three
            # atoms out" had no way to find the other two.
            "rejected_candidates": [
                {
                    "statement_id": c.statement.statement_id,
                    "text": c.statement.canonical_text,
                    "role": c.role,
                    "reason": c.rejection_reason,
                    "coordinates": c.statement.coordinates,
                }
                for c in all_candidacies
                if not c.is_candidate and c.role not in EXCLUSION_ROLES
            ],
            "relations": relations_payload,
            "dependencies": dependencies_payload,
            "validation_findings": validation_findings,
            "authority_graph_metrics": authority_graph_metrics,
            "shacl_validation": shacl_result,
            "execution_dag": exec_dag,
            "execution_graph_metrics": execution_graph_metrics,
            "applicability": applicability.to_list(),
            "defect_remediations": defect_remediations,
            "unresolved_records": unresolved_payload,
            "structural_validation": quarantine_result.to_dict(),
            "proof_bundle": proof_bundle,
            "proof_bundle_verification": proof_bundle_verification,
            "event_log": event_log_payload,
            "event_log_manifest": event_log_manifest,
            "event_log_verification": event_log_verification,
        }


def _semantic_owner_for(domains: List[str]) -> str:
    if not domains:
        return "specgraph.compiler.functional_behavior"
    return f"specgraph.compiler.{domains[0].lower()}"


def _attach_dependency_support(
    requirements: List[CanonicalRequirementIR],
    dependency_edges: List[DependencyEdge],
) -> None:
    req_map = {req.stable_id: req for req in requirements}
    predecessors: Dict[str, set[str]] = {req.stable_id: set() for req in requirements}
    successors: Dict[str, set[str]] = {req.stable_id: set() for req in requirements}

    for edge in dependency_edges:
        if edge.from_id not in req_map or edge.to_id not in req_map:
            continue
        successors[edge.from_id].add(edge.to_id)
        predecessors[edge.to_id].add(edge.from_id)

    for req in requirements:
        req.predecessor_ids = sorted(predecessors[req.stable_id])
        req.successor_ids = sorted(successors[req.stable_id])
        req.artifact_hashes["dependency_support_sha256"] = generate_fingerprint({
            "predecessor_ids": req.predecessor_ids,
            "successor_ids": req.successor_ids,
        })


def _attach_execution_support(
    requirements: List[CanonicalRequirementIR],
    execution_dag: Dict[str, Any],
) -> None:
    node_by_atom = {
        node.get("source_atom_id"): node.get("id")
        for node in execution_dag.get("nodes", [])
        if node.get("source_atom_id")
    }
    readiness = execution_dag.get("ready_states", {})

    for req in requirements:
        node_id = node_by_atom.get(req.stable_id)
        req.execution_node_id = node_id
        if node_id and readiness.get(node_id) == "READY":
            req.completion_state = "READY"
        req.artifact_hashes["execution_support_sha256"] = generate_fingerprint({
            "execution_node_id": req.execution_node_id,
            "completion_state": req.completion_state,
        })












