from typing import List, Dict, Any, Optional
from .statement_segmentation import StatementIR
from .discourse_roles import declared_atom_id

EXECUTABLE_ROLES = {
    "NORMATIVE_REQUIREMENT",
    "CONSTRAINT",
    "PROHIBITION",
    "DESIGN_DECISION",
    "ARCHITECTURAL_PRINCIPLE",
    "REMEDIATION_REQUIREMENT",
    # An acceptance criterion is executable: it states a condition the built
    # thing must satisfy, which is exactly what a verification atom is for.
    # Excluding it made TESTS_ACCEPTANCE an unreachable dimension -- the
    # vocabulary declared it, determine_applicable_dimensions could assign it,
    # and no sentence could ever produce an atom in it, because every sentence
    # that would have been classified here was dropped before candidacy.
    "ACCEPTANCE_CRITERION",
}

class RequirementCandidacy:
    def __init__(
        self,
        statement: StatementIR,
        role: str,
        is_candidate: bool,
        actor: Optional[str] = None,
        trigger: Optional[str] = None,
        behavior: Optional[str] = None,
        ears_pattern: Optional[str] = None,
        rejection_reason: Optional[str] = None
    ):
        self.statement = statement
        self.role = role
        self.is_candidate = is_candidate
        self.actor = actor or "system"  # Inherited default actor if not specified
        self.trigger = trigger
        self.behavior = behavior
        self.ears_pattern = ears_pattern
        self.rejection_reason = rejection_reason

    def to_dict(self) -> Dict[str, Any]:
        return {
            "statement": self.statement.to_dict(),
            "role": self.role,
            "is_candidate": self.is_candidate,
            "actor": self.actor,
            "trigger": self.trigger,
            "behavior": self.behavior,
            "ears_pattern": self.ears_pattern,
            "rejection_reason": self.rejection_reason
        }

def evaluate_candidacy(
    stmt: StatementIR,
    role: str,
    is_inherited: bool = False
) -> RequirementCandidacy:
    """
    Check if the discourse role is executable.
    Parse simple EARS structure (Ubiquitous, Event-driven, etc.).
    EARS templates:
    - Ubiquitous: The <system> shall <do something>
    - Event-driven: When <trigger>, the <system> shall <do something>
    - State-driven: While <state>, the <system> shall <do something>
    """
    if role not in EXECUTABLE_ROLES:
        return RequirementCandidacy(
            stmt, role, is_candidate=False,
            rejection_reason=f"Discourse role '{role}' is not executable."
        )

    text = stmt.canonical_text

    # Very simple deterministic parser for EARS-like elements
    trigger = None
    actor = None
    behavior = text
    ears_pattern = "Ubiquitous"

    # Event-driven
    if text.lower().startswith("when ") or " when " in text.lower():
        ears_pattern = "Event-driven"
        parts = re_split_when(text)
        if parts:
            trigger, behavior = parts

    # State-driven
    elif text.lower().startswith("while ") or " while " in text.lower():
        ears_pattern = "State-driven"
        parts = re_split_while(text)
        if parts:
            trigger, behavior = parts

    # Extract candidate actor (supporting multi-word system/component nouns)
    actor = None
    actor_matches = []

    # Pattern 1: 'the <actor> shall|must|should|will'
    actor_match = re.search(r"\b(?:the|a|an)\s+([a-zA-Z0-9_\-\s]{1,30}?)\s+(?:shall|must|should|will)\b", text, re.IGNORECASE)
    if actor_match:
        actor_matches.append(actor_match.group(1).strip())

    # Pattern 2: bare system actor keyword directly before modal verb ('System must', 'API shall')
    bare_match = re.search(r"\b([a-zA-Z0-9_\-]{2,30}?)\s+(?:shall|must|should|will|may|can)\b", text, re.IGNORECASE)
    if bare_match:
        actor_matches.append(bare_match.group(1).strip())

    SYSTEM_ACTORS = {
        "system", "service", "module", "component", "worker", "api", "screen", "ui", "ux",
        "database", "application", "app", "server", "client", "process", "driver", "handler",
        "builder", "validator", "compiler", "executor", "verifier", "registry", "provider",
        "tool", "auth", "manager", "kernel", "daemon", "agent", "parser", "schema", "config",
        "configuration", "migration", "user", "artifact", "resource", "policy", "file",
        "encryption", "framework", "middleware", "pipeline", "template", "encoder",
        "decoder", "adapter", "connector", "gateway", "proxy", "cache",
        # Architectural nouns this list was missing. Each one was rejecting
        # real requirements as "lacks a valid system actor" -- "The runtime
        # MUST recover after a restart" is unambiguously a requirement about
        # the system, and it was silently discarded because `runtime` was not
        # spelled here.
        #
        # A closed word list will always be incomplete; that is why the
        # rejection is now *reported* rather than only recorded. Extending the
        # list narrows the gap, surfacing the rejection is what stops the next
        # gap being invisible.
        "runtime", "engine", "bridge", "router", "store", "gate", "queue",
        "scheduler", "planner", "orchestrator", "runner", "session", "node",
        "task", "job", "loop", "monitor", "index", "ledger", "repository",
        "controller", "listener", "endpoint", "transport", "serializer",
        "collector", "exporter", "importer", "watcher", "supervisor",
        "installer", "packager", "renderer", "extractor", "atomizer",
        # Test artifacts are system actors too. Without these, "The generated
        # tests MUST verify the acceptance criteria" was rejected for lacking an
        # actor -- so ACCEPTANCE_CRITERION became executable and still produced
        # nothing, and TESTS_ACCEPTANCE stayed an unreachable dimension for a
        # second, entirely different reason than the first.
        "test", "tests", "suite", "harness", "fixture", "assertion", "check",
    }

    for candidate_actor in actor_matches:
        if any(keyword in candidate_actor.lower() for keyword in SYSTEM_ACTORS):
            actor = candidate_actor
            break

    # Fallback: detect any system actor keyword in the statement text
    if not actor:
        text_lower = text.lower()
        for keyword in SYSTEM_ACTORS:
            if re.search(r"\b" + re.escape(keyword) + r"\b", text_lower):
                actor = keyword
                break

    # A declared atom names its own actor.
    #
    # `S-001 - Six-answers status contract` carries no system noun, so the
    # keyword scan above finds nothing and the statement is rejected for
    # lacking actor context. But the declaration *is* the context: the document
    # already states that this is a unit of work in a named track, which is
    # strictly more than "the system shall" would have told us. Rejecting it
    # dropped seventy-five atoms from a document whose whole purpose was to
    # declare them.
    #
    # The track prefix becomes the actor, so downstream sees `S`, `B-PROV`,
    # `F-CLI` rather than an undifferentiated "system".
    if not actor:
        declared = declared_atom_id(text)
        if declared:
            actor = declared.rsplit("-", 1)[0].lower()

    # Standalone requirements without system context get rejected
    if not is_inherited and not actor:
        return RequirementCandidacy(
            stmt, role, is_candidate=False,
            rejection_reason="Standalone statement lacks a valid system/architectural actor context."
        )

    if not actor:
        actor = "system"

    return RequirementCandidacy(
        statement=stmt,
        role=role,
        is_candidate=True,
        actor=actor,
        trigger=trigger,
        behavior=behavior,
        ears_pattern=ears_pattern
    )

import re

def re_split_when(text: str) -> Optional[tuple[str, str]]:
    match = re.search(r"^(?:when)\s+(.+?),\s*(?:the|a)\s+(.+)$", text, re.IGNORECASE)
    if match:
        return match.group(1), match.group(2)
    return None

def re_split_while(text: str) -> Optional[tuple[str, str]]:
    match = re.search(r"^(?:while)\s+(.+?),\s*(?:the|a)\s+(.+)$", text, re.IGNORECASE)
    if match:
        return match.group(1), match.group(2)
    return None
