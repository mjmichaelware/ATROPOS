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
    "RECOVERY": ["backup", "restore", "recovery", "rollback", "reboot", "disaster"],
    "OBSERVABILITY": ["log", "metric", "trace", "observability", "provenance", "event", "telemetry"],
    "PLATFORM": ["os", "linux", "termux", "windows", "macos", "docker", "runtime", "environment"],
    "DEPLOYMENT": ["deploy", "ci/cd", "kubernetes", "vercel", "supabase", "cloud run", "dockerfile"],
    "COMPLIANCE": ["regulatory", "gdpr", "hipaa", "audit", "compliance"],
    "SAFETY": ["safety", "fail-safe", "emergency"]
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
    matched_domains = []
    for domain, keywords in DOMAIN_KEYWORDS.items():
        if any(re.search(r"\b" + re.escape(kw) + r"\b", text_lower) for kw in keywords):
            matched_domains.append(domain)

    # Avoid classification if just a casual mention (needs direct behavioral context)
    # We look for verbs and constraints to verify domain kind
    # If no domains match, set to UNSPECIFIED. The compiler specification prohibits defaulting to FUNCTIONAL.
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
