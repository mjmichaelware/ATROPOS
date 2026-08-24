#!/usr/bin/env bash
set -euo pipefail

echo "=== FAST CLASSES ==="
scripts/atropos-fast-gate.sh classes

echo "=== FULL SMOKE ==="
scripts/atropos-fast-gate.sh smoke

if [[ "${GITHUB_ACTIONS:-false}" == "true" ]]; then
  echo "=== HOSTED ORPHAN GATE ==="
  python3 scripts/find-orphans.py --fail-on-new

  echo "=== HOSTED COMPILE ==="
  ./gradlew --no-daemon compileJava compileTestJava compileKotlin compileTestKotlin

  echo "=== HOSTED FOCUSED BACKEND TESTS ==="
  ./gradlew --no-daemon :test --rerun \
    --tests 'atropos.core.factory.FactoryResumeAndRepairTest' \
    --tests 'atropos.core.factory.FactoryLiveRepairActionTest' \
    --tests 'atropos.core.factory.FactoryProgressGuardTest' \
    --tests 'atropos.core.factory.FactoryRunHandoffTest' \
    --tests 'atropos.core.factory.FactoryObligationLoopTest' \
    --tests 'atropos.core.factory.FactoryAcceptanceFreezeTest' \
    --tests 'atropos.core.factory.FactoryEvidenceWaveExecutorTest' \
    --tests 'atropos.core.factory.FactoryResearchDloiTest' \
    --tests 'atropos.core.factory.AppActionRegistryTest' \
    --tests 'atropos.core.factory.AppAuthPlannerTest' \
    --tests 'atropos.core.factory.AppBackendIntegrationPlannerTest' \
    --tests 'atropos.core.factory.AppDatabaseSecurityPlannerTest' \
    --tests 'atropos.core.factory.AppDeploymentServiceTest' \
    --tests 'atropos.core.factory.AppDurableStoreTest' \
    --tests 'atropos.core.factory.AppFactoryAcceptanceContractTest' \
    --tests 'atropos.core.factory.AppFactoryRouterTest' \
    --tests 'atropos.core.factory.AppGeneratedBehaviorGuardTest' \
    --tests 'atropos.core.factory.AppProjectGeneratorTest' \
    --tests 'atropos.core.factory.BundledSpecGraphTest' \
    --tests 'atropos.core.factory.CanonicalAtomizationTest' \
    --tests 'atropos.core.factory.FactoryLanguageContractTest' \
    --tests 'atropos.core.factory.FactoryLineageTest' \
    --tests 'atropos.core.factory.FactoryRequirementStatementsTest' \
    --tests 'atropos.core.factory.FactoryRunEventRecorderTest' \
    --tests 'atropos.core.factory.FactoryRunRootGuardTest' \
    --tests 'atropos.core.factory.IntentParserTest' \
    --tests 'atropos.core.factory.LanguageAwareScaffoldTest' \
    --tests 'atropos.core.factory.RepositoryVerificationPlannerTest' \
    --tests 'atropos.core.factory.ReservedPackageNameTest' \
    --tests 'atropos.core.provider.ProviderOnboardingTest' \
    --tests 'atropos.core.provider.ProviderActivationServiceTest' \
    --tests 'atropos.core.provider.ProviderCascadeRouterTest' \
    --tests 'atropos.core.provider.ProviderCascadeOrderTest' \
    --tests 'atropos.core.provider.ProviderFailureClassifierTest' \
    --tests 'atropos.core.provider.ProviderErrorNormalizerTest' \
    --tests 'atropos.core.policy.ProviderActionProposalsTest' \
    --tests 'atropos.core.provider.QuotaLedgerRouteTruthTest' \
    --tests 'atropos.bridge.BridgeEventsHandlerTest' \
    --tests 'atropos.bridge.BridgeStreamTest' \
    --tests 'atropos.bridge.BridgeEditorHandlerTest' \
    --tests 'atropos.bridge.BridgeEvidenceHandlerTest' \
    --tests 'atropos.bridge.BridgeApprovalHandlerTest' \
    --tests 'atropos.bridge.BridgeMcpHandlerTest' \
    --tests 'atropos.core.integration.McpHostManagerTest' \
    --tests 'atropos.core.integration.AdversarialValidatorTest' \
    --tests 'atropos.core.integration.AndroidShellBridgeTest' \
    --tests 'atropos.core.integration.BridgeEndpointsTest' \
    --tests 'atropos.core.integration.InboundActionProposalBridgeTest' \
    --tests 'atropos.core.integration.InboundToolBridgeTest' \
    --tests 'atropos.core.integration.MarkItDownIngestServiceTest' \
    --tests 'atropos.bridge.BridgeQuotaRouteTest' \
    --tests 'atropos.bridge.BridgeStatusHandlerTest' \
    --tests 'atropos.bridge.projection.SixAnswersProjectionTest' \
    --tests 'atropos.bridge.QuotaProjectionTest' \
    --tests 'atropos.bridge.BridgeComputerUseHandlerTest' \
    --tests 'atropos.bridge.BridgeConversationHandlerTest' \
    --tests 'atropos.bridge.BridgeQueueHandlerTest' \
    --tests 'atropos.bridge.BridgeSelfHostRoutesTest' \
    --tests 'atropos.bridge.BridgeSessionHandlerTest' \
    --tests 'atropos.bridge.GovernanceRoutesTest' \
    --tests 'atropos.bridge.LocalEngineBridgeTest' \
    --tests 'atropos.bridge.conversation.BridgeConversationStoreTest' \
    --tests 'atropos.bridge.conversation.BridgeSessionStoreTest' \
    --tests 'atropos.bridge.conversation.QueuedWorkConversationResponderTest' \
    --tests 'atropos.bridge.http.HttpRequestAuthenticatorTest' \
    --tests 'atropos.bridge.http.HttpRequestParserTest' \
    --tests 'atropos.bridge.http.HttpRouteTableTest' \
    --tests 'atropos.bridge.menu.BridgeMenuCatalogTest' \
    --tests 'atropos.bridge.projection.ActivityProjectionTest' \
    --tests 'atropos.bridge.projection.AuthorityProjectionTest' \
    --tests 'atropos.bridge.projection.CheckpointProjectionTest' \
    --tests 'atropos.bridge.projection.ExportProjectionTest' \
    --tests 'atropos.bridge.projection.ThinkingProjectionTest' \
    --tests 'atropos.bridge.projection.WelcomeProjectionTest' \
    --tests 'atropos.bridge.RecoveryProjectionTest' \
    --tests 'atropos.bridge.BridgeFilesHandlerTest' \
    --tests 'atropos.bridge.AtroposBridgeTest' \
    --tests 'atropos.bridge.menu.HelpRegistryTest' \
    --tests 'atropos.cli.input.CommandRegistryParityTest' \
    --tests 'atropos.cli.BackendDoctorTest' \
    --tests 'atropos.cli.FirstRunDoctorRendererTest' \
    --tests 'atropos.cli.input.CommandCatalogBackendEntriesTest' \
    --tests 'atropos.core.agent.AgentContextCollectorTest' \
    --tests 'atropos.cli.shell.ShellBoundedAgencyTest' \
    --tests 'atropos.cli.GitMutationCommandTest' \
    --tests 'atropos.core.worktree.BoundedGitWorktreeCommandRunnerTest' \
    --tests 'atropos.core.verification.GateReachabilityCheckerTest' \
    --tests 'atropos.core.verification.AcceptanceVelocityTest' \
    --tests 'atropos.core.verification.GitHubActionsCompileRunnerTest' \
    --tests 'atropos.core.security.ContextPathExclusionsTest' \
    --tests 'atropos.core.github.GitHubApiClientTest' \
    --tests 'atropos.core.github.GitHubBindingTest' \
    --tests 'atropos.core.scavenge.GitHubScavengerTest'
fi

echo "=== DIFF CHECK ==="
git diff --check

echo "=== NPM INSTALLER CONTRACT ==="
bash scripts/npm-installer-contract-test.sh

echo "=== GITHUB ACTION CONTRACT ==="
bash scripts/atropos-verify-action-contract-test.sh

echo "=== MCP EXAMPLE CONTRACT ==="
bash scripts/mcp-example-contract-test.sh

echo "=== GITHUB WRITE CONTRACT ==="
bash scripts/github-write-contract-test.sh

echo "ATROPOS_WORKTREE_VERIFY_OK"
