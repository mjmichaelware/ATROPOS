#!/usr/bin/env python3
"""Static regression checks for the strict obligation auditor.

These checks intentionally do not compile or execute the product.  They guard
the accounting contract against historical-status leakage and weak evidence.
"""

import importlib.util
import json
from pathlib import Path


ROOT = Path(__file__).resolve().parents[1]
SCRIPT = ROOT / "scripts/audit-residual-obligations.py"
spec = importlib.util.spec_from_file_location("strict_audit", SCRIPT)
assert spec and spec.loader
audit = importlib.util.module_from_spec(spec)
spec.loader.exec_module(audit)


def test_test_file_cannot_prove_implementation():
    paths = ("src/test/kotlin/atropos/cli/CommandRouterHelpTest.kt",)
    assert audit.valid_contract_paths("M006", "impl", paths, ("portableSurfacePlan",)) == []


def test_contract_file_must_name_its_owner():
    paths = ("scripts/audit-residual-obligations.py",)
    assert audit.valid_contract_paths("AUD036", "wire", paths, ("package-installers",)) == []


def test_documentation_cannot_prove_wiring():
    paths = ("docs/architecture/DOCKER_NATIVE_DESKTOP_ANDROID_WEB_PLAN.md",)
    assert audit.valid_contract_paths("M006", "wire", paths, ("M006",)) == []


def test_residual_integration_requires_executable_owner_reference():
    assert audit.valid_residual_integration_evidence(
        "SD5#E03-secret-egress",
        ("src/main/kotlin/atropos/cli/ProviderChatDispatcher.kt",),
        ("SecretEgressGate", "LeakageAccumulator", "Canary"),
    ) == ["src/main/kotlin/atropos/cli/ProviderChatDispatcher.kt"]
    assert audit.valid_residual_integration_evidence(
        "SD5#E03-secret-egress",
        ("src/main/kotlin/atropos/cli/ProviderChatDispatcher.kt",),
        ("NotARealOwner",),
    ) == []


def test_android_composition_evidence_requires_each_boundary_reference():
    paths = (
        "app/src/main/AndroidManifest.xml",
        "app/src/main/java/com/atropos/android/app/MainActivity.kt",
        "app/src/main/java/com/atropos/android/app/ui/ConversationScreen.kt",
        "app/src/main/java/com/atropos/android/app/ui/MobileAppMvi.kt",
    )
    symbols = ("MainActivity", "ChatListScreen", "CheckpointChip", "ThinkingSheet", "SessionTabModel")
    assert audit.valid_residual_integration_evidence("HOE-D-android", paths, symbols) == list(paths)


def test_residual_edge_requires_behavioral_assertion():
    path = "src/test/kotlin/atropos/core/parser/TreeSitterGrammarBridgeExtendedTest.kt"
    assert audit.valid_residual_edge_evidence(
        "CORE#TreeSitterGrammarBridge", (path,), ("TreeSitterGrammarBridge",)
    ) == [path]
    assert audit.valid_residual_edge_evidence(
        "CORE#TreeSitterGrammarBridge", (path,), ("NotARealOwner",)
    ) == []


def test_cli_surface_edge_uses_the_aggregate_contract():
    path = "src/test/kotlin/atropos/cli/ui/HoeAntigravitySurfaceContractTest.kt"
    assert audit.valid_residual_edge_evidence(
        "HOE-B-cli-antigravity", (path,), ("LandingRenderer", "DesignTokens", "AnsiTerminalEngine")
    ) == [path]


def test_factory_edge_uses_general_factory_acceptance_contract():
    path = "src/test/kotlin/atropos/core/factory/AppFactoryAcceptanceContractTest.kt"
    assert audit.valid_residual_edge_evidence(
        "C3-AF-app-factory", (path,), ("AppFactoryRouter", "LivePreviewService")
    ) == [path]


def test_progressive_disclosure_edge_uses_l4_retention_contract():
    path = "src/test/kotlin/atropos/cli/ui/disclosure/ProgressiveDisclosureTest.kt"
    assert audit.valid_residual_edge_evidence(
        "HOE-A08-progressive-disclosure", (path,), ("ProgressiveDisclosure",)
    ) == [path]


def test_current_status_is_the_only_machine_facing_status():
    audit_data = json.loads(
        (ROOT / "docs/completion/ATROPOS_UNIFIED_OBLIGATION_AUDIT.json").read_text()
    )
    registry = json.loads(
        (ROOT / "docs/completion/ATROPOS_CODE_OBLIGATION_REGISTRY.json").read_text()
    )
    by_id = {row["obligationId"]: row for row in audit_data["originalRegistryCurrentRows"]}
    for row in registry["obligations"]:
        current = by_id[row["obligationId"]]
        assert row["status"] == current["currentStatus"]
        assert current["currentStatus"] == "WRITTEN" or not current["predicateSatisfied"]


def test_satisfied_predicates_have_matching_evidence_channels():
    audit_data = json.loads(
        (ROOT / "docs/completion/ATROPOS_UNIFIED_OBLIGATION_AUDIT.json").read_text()
    )
    assert audit_data["evidenceIntegrity"]["passed"]
    assert audit_data["evidenceIntegrity"]["failures"] == []


def test_report_counts_match_machine_audit():
    audit_data = json.loads(
        (ROOT / "docs/completion/ATROPOS_UNIFIED_OBLIGATION_AUDIT.json").read_text()
    )
    report = (ROOT / "docs/completion/ATROPOS_CODE_COMPLETION_REPORT.md").read_text()
    totals = audit_data["combinedPredicateTotals"]
    assert f"**{totals['written']}**" in report
    assert f"**{totals['missing']}**" in report
    assert "No 1,355-row source inventory exists" in report


def test_audit_identity_includes_dirty_worktree_content():
    audit_data = json.loads(
        (ROOT / "docs/completion/ATROPOS_UNIFIED_OBLIGATION_AUDIT.json").read_text()
    )
    fingerprint = audit_data["currentWorktreeFingerprint"]
    assert len(fingerprint) == 64
    assert audit_data["currentHead"].endswith(f"@worktree:{fingerprint}")


def test_requirement_is_not_complete_when_any_predicate_is_open():
    audit_data = json.loads(
        (ROOT / "docs/completion/ATROPOS_UNIFIED_OBLIGATION_AUDIT.json").read_text()
    )
    for requirement in audit_data["originalRequirementCurrentRows"]:
        predicates = requirement["predicates"]
        expected = "WRITTEN" if all(predicates.values()) else "PARTIAL"
        assert requirement["currentStatus"] == expected


def test_phase20_aggregate_rows_resolve_to_canonical_owners():
    assert audit.REQUIREMENT_ALIASES["AUD211"] == ("EvidenceLedger",)
    assert audit.REQUIREMENT_ALIASES["AUD218"] == ("GovernanceDetectorsRegistry",)
    assert audit.REQUIREMENT_ALIASES["AUD233"] == ("ProofCarryingAmendment",)
    assert audit.REQUIREMENT_ALIASES["AUD239"] == ("TerminationRanking",)


if __name__ == "__main__":
    for name, value in sorted(globals().items()):
        if name.startswith("test_"):
            value()
    print("STRICT_AUDIT_REGRESSION_CHECKS_OK")
