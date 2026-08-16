import re
from typing import List, Dict, Any, Optional

DOMAINS = {
    "FUNCTIONAL_BEHAVIOR", "UI_UX", "ACCESSIBILITY", "DATA", "API",
    "INTEGRATION", "SECURITY", "PRIVACY", "PERFORMANCE", "RELIABILITY",
    "RECOVERY", "OBSERVABILITY", "PLATFORM", "DEPLOYMENT", "GOVERNANCE",
    "ARCHITECTURE", "TESTABILITY", "DOCUMENTATION", "COMPLIANCE", "SAFETY"
}

MODALITIES = {
    "MUST", "MUST_NOT", "SHALL", "SHOULD", "SHOULD_NOT", "MAY", "BINDING_FACT",
    "GOAL", "INFORMATIVE", "UNSPECIFIED"
}

TARGETS = {
    "CAPABILITY", "FEATURE", "SERVICE", "COMPONENT", "MODULE", "CLASS",
    "FUNCTION", "COMMAND", "VIEW", "WIDGET", "ENDPOINT", "SCHEMA",
    "DATABASE_OBJECT", "POLICY", "PROVIDER", "FILE", "TEST", "BUILD",
    "DEPLOYMENT", "DOCUMENT", "RUNTIME"
}

VERIFICATION_METHODS = {
    "UNIT_TEST", "INTEGRATION_TEST", "CONTRACT_TEST", "PROPERTY_TEST",
    "STATIC_ANALYSIS", "INSPECTION", "DEMONSTRATION", "BUILD_PROOF",
    "RUNTIME_PROOF", "VISUAL_COMPARISON", "ACCESSIBILITY_SCAN",
    "SECURITY_SCAN", "PERFORMANCE_MEASUREMENT", "RECOVERY_TEST",
    "MIGRATION_TEST", "SOURCE_TRACE"
}

# Rule tables
DOMAIN_KEYWORDS = {
    "SECURITY": ["auth", "security", "encryption", "cipher", "ssl", "tls", "permissions", "secrets", "restrict", "token"],
    "PERFORMANCE": ["latency", "throughput", "concurrency", "response time", "ms", "scale", "performance", "limit", "capacity"],
    "DATA": ["database", "schema", "record", "sqlite", "postgres", "sql", "migration", "data", "storage", "table", "field"],
    "API": ["endpoint", "http", "api", "request", "response", "rest", "graphql", "grpc", "port", "routes"],
    "UI_UX": ["ui", "ux", "screen", "button", "widget", "layout", "font", "css", "color", "visual"],
    "RELIABILITY": ["failover", "retry", "reliability", "redundancy", "heartbeat", "health check"],
    "RECOVERY": ["backup", "restore", "recovery", "recover", "rollback", "roll back", "reboot", "disaster", "restart", "resume"],
    "OBSERVABILITY": ["log", "metric", "trace", "observability", "provenance", "event", "telemetry"],
    "PLATFORM": ["os", "linux", "termux", "windows", "macos", "docker", "runtime", "environment"],
    "DEPLOYMENT": ["deploy", "ci/cd", "kubernetes", "vercel", "supabase", "cloud run", "dockerfile"],
    "COMPLIANCE": ["regulatory", "gdpr", "hipaa", "audit", "compliance"],
    "SAFETY": ["safety", "fail-safe", "emergency"],
    # Eight of the twenty declared DOMAINS had no keywords at all, so no
    # sentence could ever be classified into them. TESTABILITY is the clearest
    # loss: "The generated tests MUST verify the acceptance criteria" matched
    # nothing, fell through to UNSPECIFIED, and the atom lost its dimension.
    #
    # `test` deliberately excludes bare "verify"/"validate", which appear in
    # requirements about the system verifying its own inputs and are not about
    # testability at all.
    "TESTABILITY": ["test", "tests", "unit test", "acceptance", "assertion", "test suite", "coverage"],
    "ACCESSIBILITY": ["accessibility", "screen reader", "contrast", "keyboard navigation", "aria", "a11y"],
    "INTEGRATION": ["integration", "adapter", "connector", "webhook", "third-party", "external service", "sdk"],
    "PRIVACY": ["privacy", "personal data", "pii", "anonymize", "anonymise", "retention", "consent"],
    "GOVERNANCE": ["governance", "approval", "authority", "territory", "permission boundary", "attestation"],
    "ARCHITECTURE": ["architecture", "dependency", "coupling", "boundary", "layering", "interface contract"],
    "DOCUMENTATION": ["documentation", "docstring", "readme", "changelog", "comment"],
    "FUNCTIONAL_BEHAVIOR": ["behavior", "behaviour", "workflow", "business rule", "state machine"],
}

TARGET_KEYWORDS = {
    "SCHEMA": ["schema", "table schema", "json schema"],
    "DATABASE_OBJECT": ["database", "table", "index", "trigger"],
    "ENDPOINT": ["endpoint", "route", "url", "uri", "api/"],
    "SERVICE": ["service", "daemon", "server"],
    "FEATURE": ["feature", "capability"],
    "COMPONENT": ["component", "module"],
    "TEST": ["test", "verification suite", "pytest", "unittest"],
    "POLICY": ["policy", "rls policy", "access control policy"],
    "FILE": ["file", "filepath", "filename", ".json", ".py", ".toml"]
}

VERIFICATION_KEYWORDS = {
    "UNIT_TEST": ["unit test", "unit-test", "pytest"],
    "INTEGRATION_TEST": ["integration test", "integration-test", "system test"],
    "CONTRACT_TEST": ["contract test", "openapi test", "api contract"],
    "PROPERTY_TEST": ["property test", "hypothesis", "property-based"],
    "STATIC_ANALYSIS": ["static analysis", "lint", "type check", "mypy"],
    "INSPECTION": ["manual review", "inspect", "audit", "code review"],
    "DEMONSTRATION": ["demo", "demonstration", "user manual"],
    "PERFORMANCE_MEASUREMENT": ["benchmark", "performance measurement", "load test"]
}

def classify_orthogonal_types(text: str, inherited_modality: Optional[str] = None) -> Dict[str, object]:
    """
    Deterministically derive kind, modality, target, and verification axes.
    No default assumptions! If we cannot resolve with high confidence, set to UNSPECIFIED.
    """
    text_lower = text.lower()

    # 1. Modality
    modality = "UNSPECIFIED"
    if "must not" in text_lower or "shall not" in text_lower or "should not" in text_lower or "never" in text_lower:
        modality = "MUST_NOT"
    elif "shall" in text_lower:
        modality = "SHALL"
    elif "must" in text_lower or "required" in text_lower or "mandatory" in text_lower or "obligatory" in text_lower:
        modality = "MUST"
    elif "should" in text_lower:
        modality = "SHOULD"
    elif "may" in text_lower or "optional" in text_lower:
        modality = "MAY"
    elif inherited_modality:
        modality = inherited_modality

    # 2. Domain Kind (Multi-label but returns primary or a list of matching domains)
    # Scored, not first-past-the-post.
    #
    # This used to take the first domain in *dictionary order* that matched a
    # single keyword, which made the classification an artifact of how the table
    # happened to be written. "The runtime MUST recover after a restart" matched
    # PLATFORM on `runtime` and RECOVERY on `recover`/`restart`, and PLATFORM won
    # for no better reason than being declared earlier. Same for "emit a trace
    # event for every provenance record": DATA matched `record`, OBSERVABILITY
    # matched `trace`, `event` and `provenance`, and DATA won.
    #
    # Ranking by how many distinct keywords hit makes the strongest signal win.
    # Ties keep declaration order, so the table stays a tiebreak rather than the
    # decision, and a single-keyword match is still a match -- this narrows what
    # is misclassified, it does not raise the bar for being classified at all.
    domain_scores = []
    for domain, keywords in DOMAIN_KEYWORDS.items():
        hits = sum(
            1 for kw in keywords
            if re.search(r"\b" + re.escape(kw) + r"\b", text_lower)
        )
        if hits:
            domain_scores.append((hits, domain))

    matched_domains = [
        domain for _, domain in sorted(domain_scores, key=lambda pair: -pair[0])
    ]

    # If no domains match, set to UNSPECIFIED. The compiler specification
    # prohibits defaulting to FUNCTIONAL.
    kind = matched_domains[0] if matched_domains else "UNSPECIFIED"

    # 3. Target Artifact
    matched_targets = []
    for target, keywords in TARGET_KEYWORDS.items():
        if any(re.search(r"\b" + re.escape(kw) + r"\b", text_lower) for kw in keywords):
            matched_targets.append(target)
    target_artifact = matched_targets[0] if matched_targets else "UNSPECIFIED"

    # 4. Verification Method
    matched_verifications = []
    for method, keywords in VERIFICATION_KEYWORDS.items():
        if any(re.search(r"\b" + re.escape(kw) + r"\b", text_lower) for kw in keywords):
            matched_verifications.append(method)
    verification_method = matched_verifications[0] if matched_verifications else "UNSPECIFIED"

    return {
        "modality": modality,
        "domain_kind": kind,
        "artifact_target": target_artifact,
        "verification_method": verification_method,
        "all_domains": matched_domains,
        "all_targets": matched_targets,
        "all_verifications": matched_verifications
    }
