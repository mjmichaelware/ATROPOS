# ATROPOS Canonical Phases 1-11 Authority

Provenance: Director-supplied canonical extract from
`ATROPOS CODEX-CLI BUILD BLUEPRINT OVER TIME`.

This file records the exact Phase 1-11 goals, invariants, and done criteria
provided in the authoritative closure prompt for the Canonical Foundation
Closure pass.

## Operating law

- Source documents are authority.
- Current code is implementation evidence.
- Compile and smoke gates are truth.
- Existing working code must be reused rather than rewritten.
- Configured does not mean verified.
- Descriptor presence does not mean transport exists.
- Transport presence does not mean route eligibility.
- Provider output is a proposal, never final truth.

Preserve:

- `LOCAL_FIRST=true`
- `PAID_AUTO=false`
- `RAW_SECRET_OUTPUT=false`
- `ROUTE_EXPLAINABLE=true`
- `PROVIDER_REPLACEABLE=true`
- `VERIFY_BEFORE_COMMIT=true`
- `ADDRESS_NEVER_BLINDLY_INGEST=true`
- `E(DELTA)=0`

## Phase 1

Name: Provider Activation Doctor

Goal:
Configured providers become diagnosable operational truth.

Complete:

- canonical SecretSource precedence
- `/keys status`
- `/keys setup`
- `/keys doctor`
- provider verification model
- `/providers verify <id>`
- `/providers verify all`
- `/providers live-test <id>`
- explicit live-test opt-in
- normalized provider activation states
- focused doctor tests

Required truthful states:

- missing
- configured
- fixture-backed
- dry-run-capable
- verified
- invalid-key
- auth-failed
- rate-limited
- quota-exhausted
- billing-required
- offline
- degraded
- locked
- disabled
- ready

Done only when key source, provider impact, adapter state, verification
state, and remediation are reported truthfully.

## Phase 2

Name: Provider Transport Completion

Goal:
Eligible providers return actual normalized responses through real
capability-specific transports.

Required transport audit/completion:

OpenAI-compatible family where schemas genuinely match:

- groq
- openrouter
- deepinfra
- siliconflow

Separate schema implementations:

- gemini
- github_models
- cloudflare_ai
- jina
- serpapi
- huggingface
- ollama

Preserve and validate currently implemented free transports such as:

- sambanova
- cerebras
- nvidia

Paid providers remain locked unless explicitly unlocked:

- openai
- anthropic
- xai
- mistral
- cohere
- deepseek_direct

Service providers must use their actual capabilities and must not be
pretended to be chat providers:

- google_drive
- pinecone
- supabase
- github_actions
- cloudflare_workers
- google_cloud_free

Every executable transport must provide:

- request construction
- authentication injection
- timeout
- response parsing
- normalized errors
- redaction
- model selection
- fallback models where supported
- dry-run
- fixture execution
- explicit live-test gate

Do not force incompatible providers through one schema.

Done only when an eligible verified provider can answer a simple request,
fallback works, paid providers remain locked, and unsupported providers
report the precise missing execution requirement.

## Phase 3

Name: Quota Ledger and Route Truth

Goal:
Free-first routing survives provider failures and explains every choice.

Complete:

- persistent quota ledger
- provider state
- request/token windows
- cooldown tracking
- reset timestamps
- success/failure timestamps
- normalized failure classes
- latency
- success score
- fallback chains
- skipped-provider reasons
- paid-lock state
- last-route persistence
- cooldown queue integration
- `/status route`
- `/status quota`
- `/route explanation`

The canonical route law is:

`LOCAL_TOOLCHAIN`
`-> FREE_READY_PROVIDER`
`-> FREE_FALLBACK_PROVIDER`
`-> COOLDOWN_QUEUE`
`-> OFFLINE_DEGRADED_MODE`
`-> PAID_EMERGENCY_ONLY_BY_EXPLICIT_UNLOCK`

Done only when ATROPOS explains:

- selected provider
- every skipped provider
- fallback reason
- cooldown
- reset
- paid lock
- final route outcome

## Phase 4

Name: Secret and Security Hardening

Goal:
Secrets never leak into any output or durable state.

Complete and centralize:

- redaction before UI output
- redaction before logs
- redaction before persistence
- redaction before provider prompts
- redaction before queue records
- redaction before command history
- redaction before error rendering
- redaction before audit records
- auth payload redaction
- secret-source precedence
- chmod-safe local setup
- secret scanning utility
- signed URL and bearer-token detection

Test:

- API keys
- OAuth tokens
- bearer tokens
- private keys
- signed URLs
- authorization headers
- credential paths
- secrets embedded in provider errors
- secrets embedded in patches
- secrets embedded in shell output

Done only when fixtures prove no raw secret reaches UI, logs, history,
memory, queue, prompts, diffs, or status.

## Phase 5

Name: Provider Fixture Matrix

Goal:
Every executable provider can be tested without a network or real key.

For every executable adapter implement isolated fixtures for:

- success
- authentication failure
- rate limit
- quota exhaustion where distinguishable
- billing required where distinguishable
- malformed response
- empty response
- timeout
- unavailable
- cancellation where supported
- dry-run
- redaction

Live tests must be opt-in and excluded from normal tests.

Fixtures may not call the network.

Done only when the full fixture matrix passes without keys.

## Phase 6

Name: DLOI Source Router

Goal:
Source documents become exact machine-addressable truth.

Complete:

- DLOI coordinate model
- source-document map loader
- document identity
- section identity
- line/band or paragraph coordinates
- address parser
- section resolver
- document lookup
- exact-source extraction
- source provenance
- address validation
- blind-ingestion prohibition
- task-to-source resolution
- failure when an address cannot be proven

Do not replace DLOI truth with cosine similarity.

Semantic search may assist discovery but may not become authoritative
source resolution.

Done only when a task resolves to exact source coordinates and ATROPOS
can prove which document sections were supplied.

## Phase 7

Name: AST Symbol Graph

Goal:
ATROPOS identifies impacted code symbols without guessing.

Complete:

- Kotlin source scanner
- package/path invariant
- file nodes
- class/object/interface nodes
- function nodes
- property nodes
- imports
- symbol references
- dependency references
- byte or character offsets
- impacted-file resolution
- impacted-symbol resolution
- import reconciliation
- deterministic graph persistence or reproducible rebuilding

Use existing parsers and scanners when present.

Done only when a task and changed diff can resolve to exact files,
symbols, imports, and dependency slices.

## Phase 8

Name: Deterministic Verifier

Goal:
Structural failures are caught locally before provider review.

Complete deterministic checks for:

- changed files
- package/path invariant
- imports
- unresolved or duplicate symbols where locally detectable
- public contracts
- provider descriptors
- provider routes
- command registry integrity
- redaction
- terminal snapshots
- shell safety
- forbidden paths
- patch structure
- build compatibility
- DLOI addresses
- AST impact
- source scope

The verifier must produce:

- invariant id
- severity
- file
- symbol or location
- evidence
- remediation
- deterministic/undecidable classification

Only undecidable failures may escalate to an LLM.

Done only when intentionally broken fixtures are caught before a
provider is called.

## Phase 9

Name: Persistent Memory

Goal:
Restart does not erase project state or learned failures.

Complete persistent storage for:

- sessions
- threads
- batches
- jobs
- queue records
- route outcomes
- provider failures
- compile failures
- failure signatures
- successful repairs
- source decisions
- DLOI links
- tool results
- verification outcomes
- reward/penalty records
- summaries
- compaction
- schema/version handling
- corruption handling
- atomic writes or transactions

Use SQLite where already required and available.
JSONL may be retained for appropriate append-only records.
Do not create duplicate competing memory systems.

Done only when a restart smoke proves state, failures, route history,
and successful repairs remain queryable.

## Phase 10

Name: Execution Policy Engine

Goal:
Every tool action is governed and logged.

Complete:

- policy model
- action classes
- allow rules
- deny rules
- approval-required rules
- destructive classifications
- mutation guards
- cwd/repository scope
- command timeout
- bounded output
- environment filtering
- network policy
- provider policy
- paid-provider policy
- dry-run
- audit log
- approval modes
- cancellation
- rollback requirement where applicable

Apply policy to:

- shell actions
- git actions
- file mutation
- patch application
- build/test commands
- provider calls
- network access
- daemon actions
- queue actions
- future tool entry points

No provider may supply arbitrary executable shell authority.

Done only when every supported action produces a policy decision and
audit record, and forbidden destructive fixtures are refused without
side effects.

## Phase 11

Name: Self-Build Loop

Goal:
ATROPOS can land a bounded software batch with `E(DELTA)=0`.

Unify and complete the existing:

- plan record
- source coordinates
- impacted-symbol graph
- territory/file scope
- provider route
- bounded context
- patch proposal
- patch extraction
- patch validation
- patch apply check
- patch application
- deterministic verification
- narrow compile
- stderr slicing
- focused repair
- wider compile
- smoke execution
- final verification
- context export
- commit proposal
- rollback evidence
- job persistence
- queue persistence
- daemon execution

Do not auto-commit or auto-push in this closure pass.

The commit planner must produce an exact proposed file list and message.

Done only when a bounded fixture task passes:

`plan`
`-> exact source context`
`-> exact impacted symbols`
`-> bounded patch`
`-> safety gate`
`-> apply`
`-> deterministic verification`
`-> narrow compile`
`-> focused repair if needed`
`-> wide build`
`-> smoke`
`-> final clean verification`
`-> commit proposal`
`-> durable restart-safe record`

`E(DELTA)=0` requires:

- requested invariant achieved
- no unrelated source changes
- no secret leaks
- no failed verification
- no undocumented generated artifacts
- rollback information available
