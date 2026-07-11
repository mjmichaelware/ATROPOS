# ATROPOS Canonical Phases 1-11 Closure Ledger

Status date: 2026-07-11
Repository: `/data/data/com.termux/files/home/ATROPOS`
Starting head: `30027e1`

## Live reconciliation update - 2026-07-11T18:49Z

This section supersedes stale observations below only where it records current
live evidence. Historical notes remain for audit continuity.

### Phase 1 - PROVEN

- Canonical gate: Provider Activation Doctor.
- Source authority: `97cff09c0f362337` `[S0003]` lines 16-18.
- Classification: `PROVEN`.
- Implementation evidence:
  - `src/main/kotlin/atropos/cli/input/CommandRegistry.kt`
    `da7f3745f460c683e126ba8c2fb68319320bc755bf61d869dfabe526099766c9`
  - `src/main/kotlin/atropos/cli/CommandRouter.kt`
    `9d67e57beae6b9f12c25c2d9672cea53e817678d7b630a5243bb756d4aea52fe`
  - `src/main/kotlin/atropos/core/provider/ProviderActivationModels.kt`
    `833bad043a0363c0e04c5a0b4d35172c6d8ada37c413cfe3f4048b62f5d73ccf`
  - `src/main/kotlin/atropos/core/provider/ProviderActivationService.kt`
    `c04d2c7affea43be55235cf1ee393bce5ddbd17295f9190cb76cad8743df7879`
  - `src/main/kotlin/atropos/core/provider/ProviderActivationStore.kt`
    `5bdbc0cb4d4c0bdd44a829e54259806f08089c625c7a51a498d55556ed332c38`
  - `src/main/kotlin/atropos/core/security/KeyDoctorService.kt`
    `612890a7189204915229526a5301d5e779e01256143616ccb89edf6b99736fd6`
- Command surface evidence:
  - `/keys doctor`
  - `/providers verify`
  - `/providers live-test`
- Focused gate:
  - Command:
    `JAVA_HOME=/data/data/com.termux/files/usr/lib/jvm/java-21-openjdk bash scripts/codex/fast-gate.sh focused -- ./gradlew test --tests atropos.core.provider.ProviderActivationServiceTest --tests atropos.core.security.KeyDoctorServiceTest`
  - Gate cache key:
    `158dad3c23055d68c36cc57bc09cb24a3f0d76880bb07fd10c820822204a1aab`
  - Result: `BUILD SUCCESSFUL`, 4 actionable tasks, 2 executed, 2 up-to-date.
  - Result files:
    - `build/test-results/test/TEST-atropos.core.provider.ProviderActivationServiceTest.xml`
      `a3f7c4637a0b1fcc78a9de3646054cf17b62f82e2ee3cfa3b40600244cd1e0b2`
    - `build/test-results/test/TEST-atropos.core.security.KeyDoctorServiceTest.xml`
      `3362c6ebf7869089e01847c2f66262d9c4beb5b322fa7a38531f8cf913c53ab8`
  - Test assertions proved:
    - configured free transport verifies offline and writes `VERIFIED`
    - paid locked live test refuses without a network call
    - key doctor obeys explicit > environment > local file precedence
    - key setup writes templates without raw secrets
- Relevant input hashes at proof time:
  - `src/test/kotlin/atropos/core/provider/ProviderActivationServiceTest.kt`
    `e27e60d7d085d86784088523161baecf1046b4ee2a1e1e77abc2154170ca2afb`
  - `src/test/kotlin/atropos/core/security/KeyDoctorServiceTest.kt`
    `010429bed13a9da3245e43d3256a1565238c8641ed34847f4bf6fe0dcc080b9c`
- Notes:
  - Earlier Gradle attempt without `JAVA_HOME` failed before tests because
    `/usr/lib/jvm/java-17-openjdk-arm64` lacks `javac`; this is environment
    evidence only and is not a Phase 1 implementation failure.
  - Evidence tightening on 2026-07-11:
    - Existing tests prove key precedence, key setup redaction, one verified
      free-provider path, and one paid-locked live-test refusal.
    - Missing acceptance evidence: a focused assertion that every required
      normalized activation state is reportable with truthful key source,
      provider impact, adapter state, verification state, and remediation.
    - Do not rerun the passing Phase 1 assertions above; the narrow missing
      verification is the state/remediation coverage assertion only.
  - Missing Phase 1 verification completed after tightening:
    - Command:
      `JAVA_HOME=/data/data/com.termux/files/usr/lib/jvm/java-21-openjdk bash scripts/codex/fast-gate.sh focused -- ./gradlew test --tests atropos.core.provider.ProviderActivationServiceTest.activation_records_render_every_canonical_state_with_truth_fields`
    - Gate cache key:
      `17c855b89b6a5eda66db39d0568ce796e8cf195c6651d7e753783522086d98fd`
    - Result: `BUILD SUCCESSFUL`, 4 actionable tasks, 3 executed, 1 up-to-date.
    - Result file:
      - `build/test-results/test/TEST-atropos.core.provider.ProviderActivationServiceTest.xml`
        `5d23be4088a150145b3b110eee502ee301e1039e277eb9447ea8b57c03efde00`
    - Test assertion proved:
      - every `ProviderActivationState` renders state, key source, provider
        impact, adapter state, verification summary, and remediation.
  - Phase 2 is the earliest unproven gate after this record.

### Phase 2 - PROVEN

- Canonical gate: Provider Transport Completion.
- Source authority: `97cff09c0f362337` `[S0004]` lines 19-21.
- Classification: `PROVEN`.
- Implementation evidence:
  - `src/main/kotlin/atropos/core/provider/adapter/ScaffoldAdapters.kt`
    `b34781d44d772dce1ef90015a3573e6766f25bc8e1b901f50201c75b208318a0`
  - `src/main/kotlin/atropos/core/provider/adapter/AdapterRegistry.kt`
    `a08c3ce3e9f6c37a673b54bc247d3211336b2410e7c70b147aec9a45a532ed94`
  - `src/main/kotlin/atropos/core/provider/adapter/AdapterRequest.kt`
    `a1a90057be9b65cf0d2f5076c751e9489aa4cefc98578f12c0757337175a758d`
  - `src/main/kotlin/atropos/core/provider/adapter/AdapterRouteFacade.kt`
    `658ab3c4e7e3a2bdb53b1e9a1fbbdb758746efb38adbf34be49727440ab976a9`
  - `src/main/kotlin/atropos/core/provider/ProviderFixtureMatrixService.kt`
    `0107c26bdc1baa0ed96d0edbe2258d7015ef7721961e027f8527581e3ca0d86a`
- Focused gate:
  - Command:
    `JAVA_HOME=/data/data/com.termux/files/usr/lib/jvm/java-21-openjdk bash scripts/codex/fast-gate.sh focused -- ./gradlew test --tests atropos.core.provider.ProviderFixtureMatrixServiceTest`
  - Gate cache key:
    `e1dd4be77d19f05e6f4eebe2c95a48f4a1947d4844bcff0eac8bbf95944136ae`
  - Result: `BUILD SUCCESSFUL`, 4 actionable tasks, 2 executed, 2 up-to-date.
  - Result file:
    - `build/test-results/test/TEST-atropos.core.provider.ProviderFixtureMatrixServiceTest.xml`
      `d6bc35ccb93a85a8dfe0741eab5c999782ee1c2f5c08da0d7300ca8c2779d798`
  - Test assertions proved:
    - offline fixture matrix is non-empty
    - every registered provider fixture passes without network credentials
    - fixture matrix covers dry-run, auth/rate/quota/billing/unavailable,
      malformed/empty/timeout/cancellation, and redaction paths through the
      transport kernels and normalizer.
- Relevant input hash at proof time:
  - `src/test/kotlin/atropos/core/provider/ProviderFixtureMatrixServiceTest.kt`
    `25469dd2f8880ec2c7e61bef98707799f41df7f4b404334c185457ce478520b7`
- Notes:
  - Live provider calls remain explicitly opt-in and are not part of this
    offline deterministic proof.
  - Evidence tightening on 2026-07-11:
    - The recorded fixture-matrix gate proves fixture execution, parser/error
      normalization, dry-run behavior, and redaction for provider kernels.
    - Real-transport evidence now present:
      - Command:
        `JAVA_HOME=/data/data/com.termux/files/usr/lib/jvm/java-21-openjdk bash scripts/codex/fast-gate.sh no-cache -- bash -lc 'printf "/providers live-test groq\n/exit\n" | kotlin -classpath build/classes/kotlin/main atropos.MainKt'`
      - Gate identity:
        `a2eacdcaeb2c44e71d29b4f49cb23774746ce36fdab8b9dbc247571c5b48765f`
      - Result: `provider: groq`, `mode: live_test`, `state: verified`,
        `adapter health: live_ready`, `verification: live success
        model=llama-3.1-8b-instant`.
    - Paid-lock evidence now present:
      - Command:
        `JAVA_HOME=/data/data/com.termux/files/usr/lib/jvm/java-21-openjdk bash scripts/codex/fast-gate.sh no-cache -- bash -lc 'printf "/providers live-test openai\n/providers verify google_drive\n/providers verify pinecone\n/exit\n" | kotlin -classpath build/classes/kotlin/main atropos.MainKt'`
      - Gate identity:
        `7a1b4bbe6f38ce23b2a822af1b361fdc815580e045c442dbf853dd0250e76576`
      - Result: `provider: openai`, `mode: live_test`, `state: locked`,
        `verification: paid provider live test refused`.
    - Fallback evidence now present outside the offline fixture matrix:
      - Direct `kotlinc` proof compiled the explicit provider/security/paid/core
        source set plus a temporary `AdapterRouteFacade` assertion harness.
      - Result:
        `TRANSPORT_FALLBACK_OK selected=openrouter
        skipped=groq:cooldown,cerebras:blocked_by_cost_policy,
        deepinfra:blocked_by_cost_policy,nvidia:blocked_by_cost_policy,
        sambanova:blocked_by_cost_policy,siliconflow:blocked_by_cost_policy,
        deepseek_direct:blocked_by_cost_policy,mistral:blocked_by_cost_policy,
        anthropic:blocked_by_cost_policy,openai:blocked_by_cost_policy
        adapter=openrouter result=Success`.
    - Contradicting acceptance evidence: `google_drive` reports `state:
      verified` and `remediation: none` while `adapter configured: no` and
      `GOOGLE_APPLICATION_CREDENTIALS:missing`; this does not satisfy the
      unsupported/service-provider requirement for precise missing execution
      requirements.
    - Missing repair target: service providers with missing required execution
      inputs must not be marked verified, and must report the precise missing
      execution requirement.
    - Service-provider missing-requirement repair proven:
      - `src/main/kotlin/atropos/core/provider/ProviderActivationService.kt`
        `9bcc809ada1d671b22edec5d1f897548b465dcb6ac9a96670867b7edd62c2482`
      - `src/test/kotlin/atropos/core/provider/ProviderActivationServiceTest.kt`
        `269d50cdec590290409fe288d8a48a70be43f44ce47e93f4b6ffd84749b14c02`
      - Change: non-local `VERIFY` state now requires required execution
        secrets before returning `VERIFIED`.
      - New narrow regression:
        `verify_reports_missing_service_provider_execution_requirement`.
      - Attempted gate:
        `JAVA_HOME=/data/data/com.termux/files/usr/lib/jvm/java-21-openjdk bash scripts/codex/fast-gate.sh focused -- ./gradlew test --tests atropos.core.provider.ProviderActivationServiceTest.verify_reports_missing_service_provider_execution_requirement`
      - Gate cache key:
        `655c302af35f8ee7a4d8de3c0dd93e9d638b86aa0d82dfaf732aaba0de186b7f`
      - Result: interrupted after Kotlin compiler daemon hang; no updated
        JUnit result file was produced.
      - Compile boundary attempted:
        `JAVA_HOME=/data/data/com.termux/files/usr/lib/jvm/java-21-openjdk ./gradlew --no-daemon -Dkotlin.compiler.execution.strategy=in-process compileKotlin`
      - Compile result: interrupted after Kotlin compiler daemon hang.
      - Direct compiler proof:
        `kotlinc` compiled the explicit provider/security/paid/core source set
        plus a temporary assertion harness outside Gradle.
      - Direct proof result:
        `SERVICE_PROVIDER_MISSING_REQUIREMENT_OK state=fixture_backed
        remediation=configure GOOGLE_APPLICATION_CREDENTIALS for live
        verification`.
    - Offline fixture evidence alone must not be used to close the real
      transport clause.
    - Phase 2 acceptance clauses are now covered:
      - eligible verified provider answered through real transport: Groq live
        test
      - fallback works: Groq cooldown -> OpenRouter dry-run adapter result
      - paid providers remain locked: OpenAI live-test refusal
      - service providers report missing execution requirements: Google Drive
        missing `GOOGLE_APPLICATION_CREDENTIALS`
      - fixture/normalization behavior: provider fixture matrix
  - Phase 3 is the earliest unproven gate after this record.

### Phase 3 - IMPLEMENTED_UNPROVEN

- Canonical gate: Quota Ledger + Route Truth.
- Source authority: `97cff09c0f362337` `[S0005]` lines 22-24.
- Classification: `IMPLEMENTED_UNPROVEN`.
- Implementation evidence:
  - `src/main/kotlin/atropos/core/provider/QuotaLedger.kt`
    `d0ea66efacdf1bfa1778719d9b4f7ce809ae6fcdb05427920161edede7a33bd1`
  - `src/main/kotlin/atropos/core/provider/RoutePolicy.kt`
    `bf2290703f2f8c913f5661831abc00ad728ed6a758a723e5f4eb4c242790c70c`
  - `src/main/kotlin/atropos/core/provider/adapter/AdapterRouteFacade.kt`
    `658ab3c4e7e3a2bdb53b1e9a1fbbdb758746efb38adbf34be49727440ab976a9`
  - `src/main/kotlin/atropos/cli/CommandRouter.kt`
    `9d67e57beae6b9f12c25c2d9672cea53e817678d7b630a5243bb756d4aea52fe`
- Command surface evidence:
  - `/status quota`
  - `/status route <task>`
  - route decisions expose selected provider, skipped providers, and reasons.
- Focused gate:
  - Command:
    `JAVA_HOME=/data/data/com.termux/files/usr/lib/jvm/java-21-openjdk bash scripts/codex/fast-gate.sh focused -- ./gradlew test --tests atropos.core.provider.QuotaLedgerRouteTruthTest`
  - Gate cache key:
    `fc712359ccb70bc4feac05c706e004ab785ca31c22af5a6de79b6724b236d1ef`
  - Result: `BUILD SUCCESSFUL`, 4 actionable tasks, 2 executed, 2 up-to-date.
  - Result file:
    - `build/test-results/test/TEST-atropos.core.provider.QuotaLedgerRouteTruthTest.xml`
      `df6f0ddd2ba8e970c536bd9918e2b7d4272b037c408690c9ffe36282883b5c81`
  - Test assertions proved:
    - rate-limit failure persists to the quota ledger
    - reopening the ledger preserves `COOLDOWN`
    - route selection falls back away from cooled-down `groq`
    - skipped-provider reasons include `groq:cooldown`
- Relevant input hash at proof time:
  - `src/test/kotlin/atropos/core/provider/QuotaLedgerRouteTruthTest.kt`
    `4296030ce3dca6905601de36382d325d190c0eb0e893e29376e8c992819fead4`
- Notes:
  - Evidence tightening on 2026-07-11:
    - The recorded route gate proves quota persistence, cooldown persistence,
      fallback away from cooled-down `groq`, and one skipped-provider reason.
    - Missing acceptance evidence: selected provider is explained in the same
      accepted route proof.
    - Missing acceptance evidence: every skipped provider is explained, not
      only `groq`.
    - Missing acceptance evidence: fallback reason is present in the accepted
      route output.
    - Missing acceptance evidence: cooldown and reset are both present.
    - Missing acceptance evidence: paid-lock state is present.
    - Missing acceptance evidence: final route outcome is persisted or
      reported.
    - The narrow missing verification is the route explanation assertion over
      one deterministic route fixture; do not repeat the passing quota
      persistence/cooldown test.
    - Implementation now present but intentionally unproven until the single
      milestone verification:
      - `src/main/kotlin/atropos/cli/ui/StatusQuotaRenderer.kt` reports
        selected provider, full skipped-provider reasons, fallback reason,
        cooldown, reset, paid-lock state, and final outcome.
      - `src/test/kotlin/atropos/core/provider/QuotaLedgerRouteTruthTest.kt`
        contains the deterministic route explanation assertion.
      - Direct harness proof was not accepted and must not be retried in this
        phase campaign.
  - Phase 3 remains unproven after this correction.

### Phase 4 - IMPLEMENTED_UNPROVEN

- Canonical gate: Secret and Security Hardening.
- Source authority: `97cff09c0f362337` `[S0006]` lines 25-27.
- Classification: `IMPLEMENTED_UNPROVEN`.
- Implementation evidence:
  - `src/main/kotlin/atropos/core/security/RedactionFilter.kt`
    `cf741d2b1ab9169576df731d350bb82c79dbfa72dcfaa5f0055fbeb90f1ab45d`
  - `src/main/kotlin/atropos/core/security/SecretSource.kt`
    `1ca9a8ba9dae9534790663a378f07063250d1575a595ff61675ee84c08a70b06`
  - `src/main/kotlin/atropos/core/security/KeyDoctorService.kt`
    `612890a7189204915229526a5301d5e779e01256143616ccb89edf6b99736fd6`
  - `src/main/kotlin/atropos/cli/shell/ShellCommandRunner.kt`
    `95bc62fe96f7b69efa4206eb15d8097c660516bb576fded247fad8880130f079`
  - `src/main/kotlin/atropos/core/provider/ProviderFailure.kt`
    `f190408b2060ae56d1982970fb4f8d9524e4227143e5ec422fb15c883e7f7555`
  - `src/main/kotlin/atropos/core/agent/AgentDaemonStore.kt`
    `c52b51e5a010ef363fbfe060c4295bdffcdeda0999402a57c40380702c5f0bf3`
- Command surface evidence:
  - `/security status`
  - `/security redact <text>`
  - `/keys setup`
  - `/keys status`
  - `/keys doctor`
- Focused gate:
  - Command:
    `JAVA_HOME=/data/data/com.termux/files/usr/lib/jvm/java-21-openjdk bash scripts/codex/fast-gate.sh focused -- ./gradlew test --tests atropos.core.security.RedactionFilterTest --tests atropos.core.security.KeyDoctorServiceTest`
  - Gate cache key:
    `673df259958853bb4cd107c1f724f46f20b03747bcb70546eacd9240fa85e9d4`
  - Result: `BUILD SUCCESSFUL`, 4 actionable tasks, 2 executed, 2 up-to-date.
  - Result files:
    - `build/test-results/test/TEST-atropos.core.security.RedactionFilterTest.xml`
      `e6b9303ecfb88e831b0630afe15676650479cb98ddb05f6683195c25e85eaf22`
    - `build/test-results/test/TEST-atropos.core.security.KeyDoctorServiceTest.xml`
      `a2d14ab7ca0b1960cf045fb2b5aa3cc2f6bbc5a5ef8925aceaa2d5fe2d9c939d`
  - Test assertions proved:
    - bearer tokens, OpenAI-style keys, private-key blocks, signed URLs, and
      credential paths are redacted
    - secret-source precedence is explicit > environment > local file
    - key doctor output does not include raw secret values
    - setup templates do not write raw keys
- Relevant input hashes at proof time:
  - `src/test/kotlin/atropos/core/security/RedactionFilterTest.kt`
    `8c2a48fccc79594147379492e0c5b7bfeef29365979cb1ac22f525e529c566f9`
  - `src/test/kotlin/atropos/core/security/KeyDoctorServiceTest.kt`
    `010429bed13a9da3245e43d3256a1565238c8641ed34847f4bf6fe0dcc080b9c`
- Notes:
  - Evidence tightening on 2026-07-11:
    - The recorded redaction/key-doctor gate proves core redaction patterns,
      secret-source precedence, redacted key-doctor output, and safe setup
      templates.
    - Missing acceptance evidence: fixtures prove no raw secret reaches each
      required surface: UI, logs, history, memory, queue, prompts, diffs, and
      status.
    - Do not repeat the passing redaction/key-doctor assertions; the narrow
      missing verification is the surface-coverage fixture only.
    - Implementation now present but intentionally unproven until the single
      milestone verification:
      - `src/test/kotlin/atropos/core/security/RedactionFilterTest.kt`
        contains `redacts_every_canonical_surface_without_raw_secret_output`
        covering UI, logs, history, memory, queue, prompts, diffs, and status.
  - Phase 4 remains unproven after this correction.

### Phase 5 - PROVEN

- Canonical gate: Provider Fixture Matrix.
- Source authority: `97cff09c0f362337` `[S0007]` lines 28-30.
- Classification: `PROVEN`.
- Implementation evidence:
  - `src/main/kotlin/atropos/core/provider/ProviderFixtureMatrixService.kt`
    `0107c26bdc1baa0ed96d0edbe2258d7015ef7721961e027f8527581e3ca0d86a`
  - `src/main/kotlin/atropos/core/provider/adapter/ScaffoldAdapters.kt`
    `b34781d44d772dce1ef90015a3573e6766f25bc8e1b901f50201c75b208318a0`
  - `src/main/kotlin/atropos/core/provider/adapter/AdapterRegistry.kt`
    `a08c3ce3e9f6c37a673b54bc247d3211336b2410e7c70b147aec9a45a532ed94`
  - `src/main/kotlin/atropos/core/provider/adapter/AdapterRequest.kt`
    `a1a90057be9b65cf0d2f5076c751e9489aa4cefc98578f12c0757337175a758d`
- Focused gate:
  - Command:
    `JAVA_HOME=/data/data/com.termux/files/usr/lib/jvm/java-21-openjdk bash scripts/codex/fast-gate.sh focused -- ./gradlew test --tests atropos.core.provider.ProviderFixtureMatrixServiceTest`
  - Gate cache key:
    `e1dd4be77d19f05e6f4eebe2c95a48f4a1947d4844bcff0eac8bbf95944136ae`
  - Result: `BUILD SUCCESSFUL`, 4 actionable tasks, 2 executed, 2 up-to-date.
  - Result file:
    - `build/test-results/test/TEST-atropos.core.provider.ProviderFixtureMatrixServiceTest.xml`
      `d6bc35ccb93a85a8dfe0741eab5c999782ee1c2f5c08da0d7300ca8c2779d798`
  - Test assertions proved:
    - all registered provider fixtures pass without network or real keys
    - live testing is outside the fixture path and remains explicitly opt-in
    - success, auth failure, rate limit, quota exhaustion, billing required,
      malformed response, empty response, timeout, cancellation, dry-run, and
      redaction classes are covered by the matrix.
- Relevant input hash at proof time:
  - `src/test/kotlin/atropos/core/provider/ProviderFixtureMatrixServiceTest.kt`
    `25469dd2f8880ec2c7e61bef98707799f41df7f4b404334c185457ce478520b7`
- Notes:
  - This reuses the exact Phase 2 fixture-matrix command because Phase 5's
    acceptance target is the provider fixture matrix itself and relevant input
    hashes are unchanged.
  - Phase 6 is the earliest unproven gate after this record.

### Phase 6 - IMPLEMENTED_UNPROVEN

- Canonical gate: DLOI Source Router.
- Source authority: `97cff09c0f362337` `[S0008]` lines 31-33.
- Classification: `IMPLEMENTED_UNPROVEN`.
- Implementation evidence:
  - `src/main/kotlin/atropos/dloi/DloiService.kt`
  - `src/test/kotlin/atropos/dloi/DloiServiceTest.kt`
- Implemented assertions awaiting milestone verification:
  - exact document and section identity resolution
  - line-address parsing and bounded extraction
  - provenance path plus exact line span
  - refusal on unproven section rather than blind ingestion
- Missing evidence:
  - the new DLOI exact-coordinate assertions have not yet been run under the
    required single milestone Gradle verification.

### Phase 7 - IMPLEMENTED_UNPROVEN

- Canonical gate: AST Symbol Graph.
- Source authority: `97cff09c0f362337` `[S0009]` lines 34-36.
- Classification: `IMPLEMENTED_UNPROVEN`.
- Implementation evidence:
  - `src/main/kotlin/atropos/ast/AstSymbolGraph.kt`
  - `src/test/kotlin/atropos/ast/AstSymbolGraphTest.kt`
- Implemented assertions awaiting milestone verification:
  - exact impacted-file symbol lookup
  - package, file path, import dependency, line, column, and offset metadata
  - function-symbol discovery for the impacted file
- Missing evidence:
  - the new AST impact assertions have not yet been run under the required
    single milestone Gradle verification.

### Phase 8 - IMPLEMENTED_UNPROVEN

- Canonical gate: Deterministic Verifier.
- Source authority: `97cff09c0f362337` `[S0010]` lines 37-39.
- Classification: `IMPLEMENTED_UNPROVEN`.
- Implementation evidence:
  - `src/main/kotlin/atropos/core/verification/DeterministicVerifier.kt`
  - `src/test/kotlin/atropos/core/verification/DeterministicVerifierTest.kt`
- Implemented assertions awaiting milestone verification:
  - broken package path, duplicate import, shell safety, invalid DLOI address,
    forbidden paths, and malformed patch structure are caught deterministically
  - every finding carries evidence, remediation, and deterministic
    classification
  - out-of-repository source paths are refused before model review
- Missing evidence:
  - the expanded deterministic-verifier assertions have not yet been run under
    the required single milestone Gradle verification.

### Phase 9 - IMPLEMENTED_UNPROVEN

- Canonical gate: Persistent Memory.
- Source authority: `97cff09c0f362337` `[S0011]` lines 40-42.
- Classification: `IMPLEMENTED_UNPROVEN`.
- Implementation evidence:
  - `src/main/kotlin/atropos/core/memory/LocalMemoryStore.kt`
  - `src/test/kotlin/atropos/core/memory/LocalMemoryStoreTest.kt`
- Implemented assertions awaiting milestone verification:
  - restart persistence and redaction
  - corrupt-line reporting and compaction
  - route, failure, repair, verification, and tool records queryable after
    restart
- Missing evidence:
  - the expanded memory restart assertions have not yet been run under the
    required single milestone Gradle verification.

### Phase 10 - IMPLEMENTED_UNPROVEN

- Canonical gate: Execution Policy Engine.
- Source authority: `97cff09c0f362337` `[S0012]` lines 43-45.
- Classification: `IMPLEMENTED_UNPROVEN`.
- Implementation evidence:
  - `src/main/kotlin/atropos/core/policy/ExecutionPolicyEngine.kt`
  - `src/test/kotlin/atropos/core/policy/ExecutionPolicyEngineTest.kt`
- Implemented assertions awaiting milestone verification:
  - destructive shell and forbidden mutation denial
  - paid-provider lock enforcement
  - all policy action classes produce audited decisions
  - network action requires approval
  - audit output redacts secret-bearing metadata
- Missing evidence:
  - the expanded policy assertions have not yet been run under the required
    single milestone Gradle verification.

### Phase 11 - IMPLEMENTED_UNPROVEN

- Canonical gate: Self-Build Loop.
- Source authority: `97cff09c0f362337` `[S0013]` lines 46-48.
- Classification: `IMPLEMENTED_UNPROVEN`.
- Implementation evidence:
  - `src/main/kotlin/atropos/core/agent/AgentRunService.kt`
  - `src/main/kotlin/atropos/core/agent/AgentJobStore.kt`
  - `src/main/kotlin/atropos/core/agent/AgentContextExportStore.kt`
  - `src/main/kotlin/atropos/core/agent/AgentVerificationStore.kt`
  - `src/test/kotlin/atropos/core/agent/AgentSelfBuildLoopTest.kt`
- Implemented assertions awaiting milestone verification:
  - bounded unsafe-smoke refusal before provider or compiler work
  - durable job record, final report, next safe action, and context export
  - restart-safe latest-job resolution
  - final outcome persisted to memory with source and status evidence
- Missing evidence:
  - the bounded Phase 11 self-build smoke and new unit assertion have not yet
    been run under the required single milestone verification sequence.

### Milestone verification attempt - 2026-07-11T20:21Z

- Required Gradle test command:
  `timeout 12m ./gradlew --no-daemon --max-workers=1 --no-configuration-cache -Pkotlin.compiler.execution.strategy=in-process test`
- Result:
  - `BUILD FAILED in 46s`
  - Failure occurred during Gradle project configuration before tests ran.
  - Root cause reported by Gradle:
    `/usr/lib/jvm/java-17-openjdk-arm64` does not provide `JAVA_COMPILER`.
  - No Phase 3-11 test assertions were accepted as proven by this command.
- Single compile/jar lane:
  - Command:
    `timeout 12m env JAVA_HOME=/data/data/com.termux/files/usr/lib/jvm/java-21-openjdk ./gradlew --no-daemon --max-workers=1 --no-configuration-cache -Pkotlin.compiler.execution.strategy=in-process jar`
  - Result: `BUILD SUCCESSFUL in 5m 29s`, `compileKotlin`, `compileJava
    NO-SOURCE`, and `jar` executed.
  - Scope: proves main Kotlin source and jar production for the current source
    slice, but does not prove the Phase 3-11 test assertions because the test
    task did not run.
- Bounded Phase 11 self-build smoke:
  - Command:
    `timeout 2m bash -lc 'printf "/agent run --smoke \"git push origin main\" Phase 11 bounded refusal smoke\n/exit\n" | java -jar build/libs/ATROPOS.jar'`
  - Result: failed before ATROPOS startup because default `java` is class-file
    version 61-capable while the jar is class-file version 65.
  - No retry was run; Phase 11 remains `IMPLEMENTED_UNPROVEN`.
- Deterministic verifier:
  - Command:
    `timeout 2m bash -lc 'printf "/verify narrow\n/exit\n" | /data/data/com.termux/files/usr/lib/jvm/java-21-openjdk/bin/java -jar build/libs/ATROPOS.jar'`
  - Result: exit code `0`; narrow verifier invocation completed.
- Secret scan:
  - Scoped scan across `AGENTS.md`, `.agents`, `docs`, `scripts/codex`,
    `src/main`, and `src/test` with explicit fixture-placeholder allowlist.
  - Result: `SECRET_SCAN_OK files=82`.
- Diff-scope and rollback audit:
  - `git diff --check`: exit code `0`.
  - No reset, restore, clean, stash, discard, commit, push, or staging action
    was performed.
  - Build and Gradle generated artifacts remain dirty and preserved.
- Post-milestone classification:
  - Phase 1: `PROVEN`.
  - Phase 2: `PROVEN`.
  - Phase 3: `IMPLEMENTED_UNPROVEN`.
  - Phase 4: `IMPLEMENTED_UNPROVEN`.
  - Phase 5: `PROVEN`.
  - Phase 6: `IMPLEMENTED_UNPROVEN`.
  - Phase 7: `IMPLEMENTED_UNPROVEN`.
  - Phase 8: `IMPLEMENTED_UNPROVEN`.
  - Phase 9: `IMPLEMENTED_UNPROVEN`.
  - Phase 10: `IMPLEMENTED_UNPROVEN`.
  - Phase 11: `IMPLEMENTED_UNPROVEN`.

## Startup audit

- `pwd`: `/data/data/com.termux/files/home/ATROPOS`
- `git branch --show-current`: `main`
- `git fetch origin main`: success
- `git pull --ff-only origin main`: already up to date
- `git status --short --branch`: only generated dirt under `.gradle/`, `build/`, and `atropos.jar`
- `git log --oneline --decorate -10`: HEAD `30027e1`

## Source authority audit

Observed source documents:

- `docs/ATROPOS_PASS10_JOB_UI_SPEC.md`
- `docs/ATROPOS_PASS11_SELF_BUILD_LOOP.md`
- `docs/ATROPOS_PASS12_DURABLE_QUEUE.md`
- `docs/ATROPOS_PASS13_PROVIDER_DAEMON.md`
- `docs/ATROPOS_TIER_H_ADDENDUM.md`

Authority update:

- `docs/ATROPOS_CANONICAL_PHASES_1_11_AUTHORITY.md` now records the Director-supplied canonical extract from `ATROPOS CODEX-CLI BUILD BLUEPRINT OVER TIME`.
- Existing documents still cover Pass 10 UI, Pass 11 self-build loop, Pass 12 queue, Pass 13 daemon, and Tier H phases 14-16.
- This closure ledger now treats missing implementation, commands, tests, and packages as active work rather than blockers.

## Command and test surface audit

Observed command evidence:

- Present in `src/main/kotlin/atropos/cli/input/CommandRegistry.kt`:
  - `/status quota`
  - `/status adapters`
  - `/status endpoints`
  - `/status failures`
  - `/providers descriptors`
  - `/providers validate`
  - `/keys setup`
  - `/keys status`
  - `/memory`
  - `/agent queue doctor`
  - `/agent daemon once|foreground|start|stop|status|doctor`
  - `/verify narrow`
  - `/verify wide`
- Missing from `src/main/kotlin/atropos/cli/input/CommandRegistry.kt`:
  - `/keys doctor`
  - `/providers verify <id>`
  - `/providers verify all`
  - `/providers live-test <id>`

Observed package evidence:

- Present:
  - `src/main/kotlin/atropos/core/provider`
  - `src/main/kotlin/atropos/core/security`
  - `src/main/kotlin/atropos/core/verification`
  - `src/main/kotlin/atropos/core/memory`
  - `src/main/kotlin/atropos/core/agent`
  - `src/main/kotlin/atropos/data`
- Not present as canonical package roots:
  - `src/main/kotlin/atropos/core/routing`
  - `src/main/kotlin/atropos/core/data`
  - `src/main/kotlin/atropos/core/policy`
  - `src/main/kotlin/atropos/dloi`
  - `src/main/kotlin/atropos/ast`

Observed test evidence:

- No files present under `src/test/kotlin`

## Phase ledger

### Phase 1

- Canonical goal: Provider activation doctor
- Required components: secret source precedence, `/keys status`, `/keys setup`, `/keys doctor`, provider verification states, `/providers verify`, `/providers live-test`, focused doctor tests
- Current evidence:
  - `src/main/kotlin/atropos/cli/input/CommandRegistry.kt`
  - `src/main/kotlin/atropos/core/provider/ProviderTruthService.kt`
  - `src/main/kotlin/atropos/core/provider/ProviderTruthModels.kt`
  - `src/main/kotlin/atropos/core/provider/ProviderAdapterIntrospection.kt`
- Missing gaps:
  - No canonical Phase 1 source document in repository/exported context
  - `/keys doctor` missing from command registry
  - `/providers verify*` and `/providers live-test` missing from command registry
  - No focused test suite under `src/test/kotlin`
- Exact files implementing the gap:
  - `src/main/kotlin/atropos/cli/input/CommandRegistry.kt`
  - `src/main/kotlin/atropos/cli/CommandRouter.kt`
  - `src/main/kotlin/atropos/cli/commands/`
  - `src/test/kotlin/`
- Focused verification:
  - `rg -n 'keys doctor|providers verify|providers live-test' src/main/kotlin docs`
  - `find src/test/kotlin -maxdepth 3 -type f`
- Final status: WORKING
- Blocking reason, if any: none yet; implementation in progress

### Phase 2

- Canonical goal: Provider transport completion
- Required components: real capability-specific transports, normalized errors, dry-run, fixture execution, live-test gate, precise unsupported-provider reporting
- Current evidence:
  - `src/main/kotlin/atropos/core/provider/adapter/ScaffoldAdapters.kt`
  - `src/main/kotlin/atropos/core/provider/adapter/AdapterRegistry.kt`
  - `src/main/kotlin/atropos/core/provider/StaticProviderDescriptorRegistry.kt`
- Missing gaps:
  - No canonical Phase 2 source document in repository/exported context
  - No authoritative fixture matrix or transport acceptance evidence for all required providers
  - No focused `src/test/kotlin` transport suite
- Exact files implementing the gap:
  - `src/main/kotlin/atropos/core/provider/adapter/`
  - `src/main/kotlin/atropos/core/provider/`
  - `src/test/kotlin/`
- Focused verification:
  - repository authority audit
  - command/test surface audit
- Final status: WORKING
- Blocking reason, if any: none yet; implementation in progress

### Phase 3

- Canonical goal: Quota ledger and route truth
- Required components: persistent quota ledger, cooldown tracking, fallback explanation, `/status route`, `/status quota`, `/route explanation`
- Current evidence:
  - `src/main/kotlin/atropos/core/provider/QuotaLedger.kt`
  - `src/main/kotlin/atropos/cli/input/CommandRegistry.kt`
  - `src/main/kotlin/atropos/core/provider/RoutePolicy.kt`
- Missing gaps:
  - No canonical Phase 3 source document in repository/exported context
  - No authoritative route-law closure evidence for every skipped-provider reason and cooldown queue outcome
  - No focused tests under `src/test/kotlin`
- Exact files implementing the gap:
  - `src/main/kotlin/atropos/core/provider/QuotaLedger.kt`
  - `src/main/kotlin/atropos/core/provider/RoutePolicy.kt`
  - `src/main/kotlin/atropos/cli/CommandRouter.kt`
  - `src/test/kotlin/`
- Focused verification:
  - `rg -n 'status quota|status route|route' src/main/kotlin`
- Final status: WORKING
- Blocking reason, if any: none yet; implementation in progress

### Phase 4

- Canonical goal: Secret and security hardening
- Required components: centralized redaction before output/logs/persistence/prompts/history/audit plus secret scanning
- Current evidence:
  - `src/main/kotlin/atropos/core/security`
  - `docs/ATROPOS_TIER_H_ADDENDUM.md`
- Missing gaps:
  - No canonical Phase 4 source document in repository/exported context
  - Tier H addendum is for phases 14-16, not canonical Phase 4 authority
  - No focused tests under `src/test/kotlin`
- Exact files implementing the gap:
  - `src/main/kotlin/atropos/core/security/`
  - `src/test/kotlin/`
- Focused verification:
  - repository authority audit
- Final status: WORKING
- Blocking reason, if any: none yet; implementation in progress

### Phase 5

- Canonical goal: Provider fixture matrix
- Required components: network-free fixtures for every executable provider across success and failure classes, plus opt-in live tests
- Current evidence:
  - `src/main/kotlin/atropos/core/testing/AtroposTestMatrix.kt`
  - adapter/provider packages present
- Missing gaps:
  - No canonical Phase 5 source document in repository/exported context
  - No fixture matrix test suite under `src/test/kotlin`
  - No authoritative provider-by-provider offline acceptance evidence
- Exact files implementing the gap:
  - `src/main/kotlin/atropos/core/testing/AtroposTestMatrix.kt`
  - `src/main/kotlin/atropos/core/provider/adapter/`
  - `src/test/kotlin/`
- Focused verification:
  - `find src/test/kotlin -maxdepth 3 -type f`
- Final status: WORKING
- Blocking reason, if any: none yet; implementation in progress

### Phase 6

- Canonical goal: DLOI source router
- Required components: coordinate model, document identity, address parsing, exact extraction, provenance, failure on unprovable address
- Current evidence:
  - no canonical `src/main/kotlin/atropos/dloi` package present
  - `src/main/kotlin/atropos/data` exists but is not a DLOI authority package
- Missing gaps:
  - No canonical Phase 6 source document in repository/exported context
  - No DLOI package root present
  - No focused tests under `src/test/kotlin`
- Exact files implementing the gap:
  - `src/main/kotlin/atropos/dloi/` (missing)
  - `src/test/kotlin/`
- Focused verification:
  - package surface audit
- Final status: WORKING
- Blocking reason, if any: none yet; implementation in progress

### Phase 7

- Canonical goal: AST symbol graph
- Required components: deterministic source scanner, symbol nodes, references, impacted-symbol resolution, graph persistence/rebuild
- Current evidence:
  - no canonical `src/main/kotlin/atropos/ast` package present
- Missing gaps:
  - No canonical Phase 7 source document in repository/exported context
  - No AST package root present
  - No focused tests under `src/test/kotlin`
- Exact files implementing the gap:
  - `src/main/kotlin/atropos/ast/` (missing)
  - `src/test/kotlin/`
- Focused verification:
  - package surface audit
- Final status: WORKING
- Blocking reason, if any: none yet; implementation in progress

### Phase 8

- Canonical goal: Deterministic verifier
- Required components: structural invariant checks with deterministic/undecidable classification and pre-provider refusal
- Current evidence:
  - `src/main/kotlin/atropos/core/verification`
  - `src/main/kotlin/atropos/cli/input/CommandRegistry.kt` includes `/verify narrow` and `/verify wide`
  - `docs/pass9-verifier-smoke.md`
- Missing gaps:
  - No canonical Phase 8 source document in repository/exported context
  - No focused verifier tests under `src/test/kotlin`
  - Existing smoke artifact is not canonical authority
- Exact files implementing the gap:
  - `src/main/kotlin/atropos/core/verification/`
  - `src/main/kotlin/atropos/cli/commands/`
  - `src/test/kotlin/`
- Focused verification:
  - command/test surface audit
- Final status: WORKING
- Blocking reason, if any: none yet; implementation in progress

### Phase 9

- Canonical goal: Persistent memory
- Required components: durable sessions/threads/jobs/routes/failures/repairs/tool results/compaction/schema handling/corruption handling
- Current evidence:
  - `src/main/kotlin/atropos/core/memory/LocalMemoryStore.kt`
  - `/memory` command in `src/main/kotlin/atropos/cli/input/CommandRegistry.kt`
- Missing gaps:
  - No canonical Phase 9 source document in repository/exported context
  - No focused restart persistence tests under `src/test/kotlin`
  - No authoritative evidence that all required record classes are queryable after restart
- Exact files implementing the gap:
  - `src/main/kotlin/atropos/core/memory/LocalMemoryStore.kt`
  - `src/main/kotlin/atropos/cli/CommandRouter.kt`
  - `src/test/kotlin/`
- Focused verification:
  - `rg -n '/memory|LocalMemoryStore' src/main/kotlin`
- Final status: WORKING
- Blocking reason, if any: none yet; implementation in progress

### Phase 10

- Canonical goal: Execution policy engine
- Required components: policy model, allow/deny/approval rules, destructive classifications, action audit, provider/tool/network policy
- Current evidence:
  - `docs/ATROPOS_PASS10_JOB_UI_SPEC.md` exists but is explicitly UI-only
  - `src/main/kotlin/atropos/cli/input/CommandRegistry.kt`
- Missing gaps:
  - No canonical non-UI Phase 10 source document in repository/exported context
  - Existing Pass 10 document explicitly does not implement backend behavior
  - No focused tests under `src/test/kotlin`
- Exact files implementing the gap:
  - `src/main/kotlin/atropos/core/policy/` (missing)
  - `src/main/kotlin/atropos/cli/commands/`
  - `src/test/kotlin/`
- Focused verification:
  - `sed -n '1,220p' docs/ATROPOS_PASS10_JOB_UI_SPEC.md`
  - package surface audit
- Final status: WORKING
- Blocking reason, if any: none yet; implementation in progress

### Phase 11

- Canonical goal: Self-build loop
- Required components: bounded plan->context->patch->verify->repair->smoke->final verification->commit proposal->durable record
- Current evidence:
  - `docs/ATROPOS_PASS11_SELF_BUILD_LOOP.md`
  - `src/main/kotlin/atropos/core/agent/`
  - queue and daemon command surface present
- Missing gaps:
  - Existing Pass 11 document is concise and not a full canonical Phases 1-11 authority set
  - No focused `src/test/kotlin` suite for full bounded self-build acceptance
  - This closure pass cannot mark Phase 11 CLOSED while Phases 1-10 remain non-closed
- Exact files implementing the gap:
  - `src/main/kotlin/atropos/core/agent/`
  - `src/main/kotlin/atropos/cli/commands/AgentCommand.kt`
  - `src/test/kotlin/`
- Focused verification:
  - `sed -n '1,260p' docs/ATROPOS_PASS11_SELF_BUILD_LOOP.md`
  - command/test surface audit
- Final status: WORKING
- Blocking reason, if any: none yet; implementation in progress

## Summary

Closure result: active implementation campaign in progress.

Current baseline:

- canonical authority is now recorded locally from the Director extract
- required command surface is still incomplete for Phase 1
- no focused `src/test/kotlin` suite exists yet for the canonical offline acceptance gates
- phases remain `WORKING` until implementation, focused tests, compile gates, smokes, and final safety checks are complete
