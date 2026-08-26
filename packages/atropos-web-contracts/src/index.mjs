/* SPDX-License-Identifier: AGPL-3.0-only */

/**
 * Framework-neutral presentation contracts shared by ATROPOS surfaces.
 *
 * HOE-C09 requires one contracts package that every surface consumes. The
 * package boundary existed before this file, but it declared allowed shapes in
 * prose only — which is a README, not a contract. Two surfaces reading the same
 * paragraph still diverge; two surfaces importing the same constant cannot.
 *
 * These terms are mirrors of engine-owned vocabularies, not second definitions.
 * The engine serves them at `GET /v1/vocabulary`, and `assertVocabularyMatches`
 * exists so a surface can prove at runtime that the copy it compiled against
 * still equals the copy the engine is serving. A mirror that cannot detect
 * drift is how the drift becomes invisible.
 */

/** Source Doc 4 §A status terms, in doc order. What the work is doing. */
export const STATUS_TERMS = Object.freeze([
  'idle',
  'planning',
  'waiting',
  'working',
  'review-required',
  'blocked',
  'completed',
  'failed',
  'cancelled',
]);

/**
 * P20-G09 completion terms, weakest claim first.
 *
 * Kept separate from STATUS_TERMS on purpose: merging them is the vocabulary
 * collapse P20-G09 names as a governance deficiency.
 */
export const COMPLETION_TERMS = Object.freeze([
  'implemented',
  'compiled',
  'tested',
  'verified',
  'blocked',
]);

/** Only this term may render as a positive completion claim. */
export const POSITIVE_COMPLETION_TERM = 'verified';

/** The six continuous answers of Source Doc 4 §0.1, in order. */
export const SIX_ANSWER_KEYS = Object.freeze([
  'objective',
  'doing',
  'why',
  'progress',
  'next',
  'evidence',
]);

/** Health values an answer may carry. */
export const HEALTH_VALUES = Object.freeze(['verified', 'pending', 'error', 'unknown']);

/**
 * HOE-A02 primary navigation spine.
 *
 * `developerTools` is hidden by default and carries SpecGraph. HOE-C07 keeps
 * SpecGraph out of the primary spine entirely, so it is expressed here as a
 * separate field rather than an eleventh entry a renderer might list inline.
 */
export const NAV_SPINE = Object.freeze([
  { id: 'home', label: 'Home', path: '/' },
  { id: 'projects', label: 'Projects', path: '/projects' },
  { id: 'work', label: 'Work', path: '/work' },
  { id: 'conversations', label: 'Conversations', path: '/conversations' },
  { id: 'files', label: 'Files', path: '/files' },
  { id: 'agents', label: 'Agents', path: '/agents' },
  { id: 'models', label: 'Models', path: '/models' },
  { id: 'automation', label: 'Automation', path: '/automation' },
  { id: 'history', label: 'History', path: '/history' },
  { id: 'settings', label: 'Settings', path: '/settings' },
]);

export const DEVELOPER_TOOLS = Object.freeze({
  id: 'developer',
  label: 'Developer Tools',
  path: '/developer',
  hiddenByDefault: true,
  tenants: Object.freeze([{ id: 'specgraph', label: 'SpecGraph', path: '/developer/specgraph' }]),
});

/** Engine routes this contract version knows how to read. */
export const ENGINE_ROUTES = Object.freeze({
  health: '/v1/health',
  routes: '/v1/routes',
  answers: '/v1/answers',
  answersStream: '/v1/answers/stream',
  projects: '/v1/projects',
  commands: '/v1/commands',
  commandRun: '/v1/command',
  commandAllowed: '/v1/command/allowed',
  vocabulary: '/v1/vocabulary',
  checkpoint: '/v1/checkpoint',
  approvals: '/v1/approvals',
  approvalsDecide: '/v1/approvals/decide',
  activity: '/v1/activity',
  events: '/v1/events',
  sessions: '/v1/sessions',
  files: '/v1/files',
});

/**
 * Routes the web surface has a client seam for but no bridge build serves yet.
 *
 * ADD-W-001: this list is the honest gap, kept in the contract so a surface
 * rendering one of these states can name what is missing instead of inventing
 * data. Every entry must disappear from here (and appear above) on the commit
 * the B-track lands it — a stale "missing" entry is as false as a fabricated
 * stream.
 */
export const MISSING_ENGINE_ROUTES = Object.freeze([
  { path: '/v1/workspace/file', servesTo: 'F-WEB-005 editor buffer contents' },
  { path: '/v1/workspace/tree', servesTo: 'F-WEB-004 project file explorer' },
]);

/**
 * S-005: one evidence reference, the shape every completion claim carries.
 *
 * `casHash` is the content address of the evidence bytes — proof, not prose.
 * `claimId` names what is being evidenced so a surface can link claim to
 * support without guessing. `gateIds` lists which verification gates stood
 * behind the claim, because "verified" means nothing without naming who did
 * the verifying.
 *
 * This is deliberately narrower than SpecGraph's research-citation evidence:
 * that describes where a *statement* came from, this describes what *backed a
 * completion claim*. Merging them would let research citations masquerade as
 * gate evidence.
 */
export function isEvidenceRef(value) {
  if (!value || typeof value !== 'object') return false;
  return (
    typeof value.casHash === 'string' &&
    /^[0-9a-f]{64}$/.test(value.casHash) &&
    typeof value.claimId === 'string' &&
    value.claimId.length > 0 &&
    Array.isArray(value.gateIds) &&
    value.gateIds.every((id) => typeof id === 'string' && id.length > 0)
  );
}

/**
 * S-008 mirror of the engine's resume-checkpoint payload.
 *
 * Mirrors `CheckpointProjection`, not the web client's convenience types: the
 * contract describes the wire, and each surface derives its own view from it.
 * `present: false` is first-class here for the same reason it is on the
 * engine — no checkpoint is not a checkpoint at age zero.
 */
export function isCheckpointPayload(value) {
  if (!value || typeof value !== 'object') return false;
  if (value.present === false) {
    return typeof value.detail === 'string' && typeof value.remedy === 'string';
  }
  if (value.present !== true) return false;
  const core =
    typeof value.goalId === 'string' &&
    typeof value.recordedAt === 'string' &&
    Number.isFinite(value.ageMinutes) &&
    typeof value.resumable === 'boolean' &&
    Number.isInteger(value.evidenceCount) &&
    Array.isArray(value.actions);
  if (!core) return false;
  return value.actions.every(
    (action) =>
      action &&
      typeof action.id === 'string' &&
      typeof action.label === 'string' &&
      typeof action.primary === 'boolean',
  );
}

/**
 * S-008 mirror of the bridge approval card (`ApprovalProjection`).
 *
 * The event kind matches what BridgeEventHub emits, so a surface can filter
 * `/v1/events` for cards and validate `/v1/approvals` rows with one guard.
 * Empty territory means the action declared none — never "all paths" — which
 * is why absence and emptiness both pass rather than being rejected.
 */
export const APPROVAL_EVENT_KIND = 'approval_raised';

export function isApprovalCard(value) {
  if (!value || typeof value !== 'object') return false;
  return (
    typeof value.id === 'string' &&
    value.id.length > 0 &&
    typeof value.proposalId === 'string' &&
    typeof value.actor === 'string' &&
    typeof value.operation === 'string' &&
    Array.isArray(value.territory) &&
    value.territory.every((path) => typeof path === 'string') &&
    typeof value.reason === 'string' &&
    typeof value.requestedAt === 'string' &&
    typeof value.pending === 'boolean' &&
    // W1-01: proposer identity from the original request
    (value.proposer === undefined || typeof value.proposer === 'string') &&
    // W1-01: approver identity (set when decision is made)
    (value.approver === undefined || typeof value.approver === 'string') &&
    // W1-02: decision object with approver identity
    (value.decision === undefined || value.decision === null || (
      typeof value.decision === 'object' &&
      typeof value.decision.approved === 'boolean' &&
      typeof value.decision.approver === 'string' &&
      typeof value.decision.decidedAt === 'string' &&
      typeof value.decision.surface === 'string'
    ))
  );
}

/**
 * S-008 mirror of the bridge cascade snapshot (`CascadeProjection`).
 */
export function isCascadePayload(value) {
  if (!value || typeof value !== 'object') return false;
  if (!Array.isArray(value.keys)) return false;
  return value.keys.every((k) =>
    typeof k.key === 'string' &&
    typeof k.value === 'string' &&
    typeof k.heldBy === 'string' &&
    typeof k.final === 'boolean' &&
    typeof k.state === 'string' &&
    ['resolved', 'violation', 'undefined'].includes(k.state)
  );
}

/**
 * S-008 mirror of the bridge quarantine projection.
 */
export function isQuarantinePayload(value) {
  if (!value || typeof value !== 'object') return false;
  return (
    typeof value.ok === 'boolean' &&
    value.ok === true &&
    typeof value.count === 'number' &&
    typeof value.observationCount === 'number' &&
    Array.isArray(value.items) &&
    value.items.every((item) =>
      typeof item.id === 'string' &&
      typeof item.title === 'string' &&
      typeof item.summary === 'string' &&
      typeof item.state === 'string' &&
      typeof item.createdAt === 'string'
    ) &&
    Array.isArray(value.observation) &&
    value.observation.every((obs) =>
      typeof obs.subsystem === 'string' &&
      typeof obs.startedAt === 'string' &&
      typeof obs.durationSeconds === 'number'
    )
  );
}

/**
 * True when a payload is a well-formed six-answers response.
 *
 * Deliberately strict about `readable`: a queue payload that omits it cannot
 * distinguish "unreadable" from "empty", and §4.1 forbids that collapse. A
 * consumer that accepted the shorter form would render a fault as an idle
 * state.
 */
export function isSixAnswersPayload(value) {
  if (!value || typeof value !== 'object') return false;
  const answers = value.answers;
  if (!answers || typeof answers !== 'object') return false;
  const everyAnswerWellFormed = SIX_ANSWER_KEYS.every((key) => {
    const answer = answers[key];
    return (
      answer &&
      typeof answer.value === 'string' &&
      HEALTH_VALUES.includes(answer.health) &&
      typeof answer.signal === 'string'
    );
  });
  return everyAnswerWellFormed && typeof value.queue?.readable === 'boolean';
}

/**
 * Throws when the engine's served vocabulary differs from this package's copy.
 *
 * HOE-F01 requires identical status vocabulary across surfaces. This is the
 * check that makes the requirement falsifiable at runtime rather than assumed
 * at review time.
 */
export function assertVocabularyMatches(served) {
  const servedStatus = (served?.status?.terms ?? []).map((t) => t.term);
  const servedCompletion = (served?.completion?.terms ?? []).map((t) => t.term);
  const mismatches = [];
  if (servedStatus.join(',') !== STATUS_TERMS.join(',')) {
    mismatches.push(`status: engine=[${servedStatus}] contracts=[${STATUS_TERMS}]`);
  }
  if (servedCompletion.join(',') !== COMPLETION_TERMS.join(',')) {
    mismatches.push(`completion: engine=[${servedCompletion}] contracts=[${COMPLETION_TERMS}]`);
  }
  if (mismatches.length > 0) {
    throw new Error(`vocabulary drift between engine and contracts:\n  ${mismatches.join('\n  ')}`);
  }
  return true;
}

/**
 * ADD-W-027: SurfaceContract — the contract a web surface must satisfy.
 *
 * A SurfaceContract defines the shape of a surface's data requirements and
 * the components it renders. It is a pure data contract that can be validated
 * at runtime and tested against shared fixtures.
 *
 * A surface contract defines:
 * - surfaceId: unique identifier for the surface
 * - requiredRoutes: engine routes the surface reads
 * - components: list of component contracts the surface renders
 * - requiredState: state shape the surface requires
 * - validation: function to validate a surface instance against this contract
 */
export const SURFACE_CONTRACT_KINDS = Object.freeze([
  'home',
  'project-work',
  'project-files',
  'project-activity',
  'project-agents',
  'models',
  'automation',
  'history',
  'settings',
  'developer-specgraph',
]);

export function isSurfaceContract(value) {
  if (!value || typeof value !== 'object') return false;
  return (
    typeof value.surfaceId === 'string' &&
    value.surfaceId.length > 0 &&
    SURFACE_CONTRACT_KINDS.includes(value.surfaceId) &&
    Array.isArray(value.requiredRoutes) &&
    value.requiredRoutes.every((r) => typeof r === 'string' && r.startsWith('/v1/')) &&
    Array.isArray(value.components) &&
    value.components.every(
      (c) =>
        typeof c === 'object' &&
        c !== null &&
        typeof c.componentId === 'string' &&
        c.componentId.length > 0 &&
        typeof c.requiredData === 'object' &&
        c.requiredData !== null
    ) &&
    (value.requiredState === undefined ||
      (typeof value.requiredState === 'object' && value.requiredState !== null))
  );
}

/**
 * Validates a surface instance against its contract.
 * Returns { ok: true } or { ok: false, detail, remedy }.
 */
export function validateSurfaceContract(contract, instance) {
  if (!isSurfaceContract(contract)) {
    return { ok: false, reason: 'invalid-contract', detail: 'Contract failed isSurfaceContract check', remedy: 'Fix the contract definition' };
  }
  if (!instance || typeof instance !== 'object') {
    return { ok: false, reason: 'invalid-instance', detail: 'Instance must be an object', remedy: 'Provide a valid surface instance' };
  }
  if (instance.surfaceId !== contract.surfaceId) {
    return { ok: false, reason: 'surface-id-mismatch', detail: `Instance surfaceId ${instance.surfaceId} does not match contract ${contract.surfaceId}`, remedy: 'Ensure instance matches contract surfaceId' };
  }
  for (const route of contract.requiredRoutes) {
    if (!(route in instance)) {
      return { ok: false, reason: 'missing-route-data', detail: `Instance missing required route data: ${route}`, remedy: `Provide data for ${route}` };
    }
  }
  for (const component of contract.components) {
    if (!(component.componentId in instance)) {
      return { ok: false, reason: 'missing-component', detail: `Instance missing required component: ${component.componentId}`, remedy: `Provide data for component ${component.componentId}` };
    }
  }
  return { ok: true };
}

/**
 * Surface contract fixtures for testing.
 * These are shared between surfaces and tests.
 */
export const SURFACE_CONTRACT_FIXTURES = Object.freeze({
  home: {
    surfaceId: 'home',
    requiredRoutes: ['/v1/answers', '/v1/projects', '/v1/approvals', '/v1/quota'],
    components: [
      { componentId: 'EngineSixAnswers', requiredData: { answers: 'object', queue: 'object' } },
      { componentId: 'SessionList', requiredData: { sessions: 'array' } },
      { componentId: 'QuotaChips', requiredData: { used: 'number', limit: 'number' } },
      { componentId: 'ProjectCard', requiredData: { name: 'string', status: 'string' } },
    ],
    requiredState: { projects: 'array', approvals: 'array' },
  },
  'project-work': {
    surfaceId: 'project-work',
    requiredRoutes: ['/v1/answers', '/v1/events/stream', '/v1/checkpoint', '/v1/approvals'],
    components: [
      { componentId: 'WorkbenchShell', requiredData: {} },
      { componentId: 'FileExplorer', requiredData: { files: 'array' } },
      { componentId: 'EditorTabs', requiredData: { tabs: 'array' } },
      { componentId: 'LogPanel', requiredData: { events: 'array' } },
      { componentId: 'CheckpointRail', requiredData: { goalId: 'string', resumable: 'boolean' } },
      { componentId: 'BridgeApprovalList', requiredData: { pending: 'array' } },
    ],
    requiredState: { layout: 'string', activeProjectId: 'string' },
  },
  'project-files': {
    surfaceId: 'project-files',
    requiredRoutes: ['/v1/files', '/v1/evidence/list'],
    components: [
      { componentId: 'FileExplorer', requiredData: { files: 'array' } },
      { componentId: 'EvidenceList', requiredData: { items: 'array' } },
    ],
    requiredState: { activeProjectId: 'string' },
  },
  'project-activity': {
    surfaceId: 'project-activity',
    requiredRoutes: ['/v1/events/stream', '/v1/activity'],
    components: [
      { componentId: 'ActivityMonitor', requiredData: { stages: 'array', events: 'array' } },
    ],
    requiredState: { activeProjectId: 'string' },
  },
  'project-agents': {
    surfaceId: 'project-agents',
    requiredRoutes: ['/v1/agents', '/v1/approvals'],
    components: [
      { componentId: 'AgentList', requiredData: { agents: 'array' } },
      { componentId: 'BridgeApprovalList', requiredData: { pending: 'array' } },
    ],
    requiredState: { activeProjectId: 'string' },
  },
  models: {
    surfaceId: 'models',
    requiredRoutes: ['/v1/models', '/v1/providers'],
    components: [
      { componentId: 'ModelSelector', requiredData: { models: 'array' } },
      { componentId: 'ProviderStatus', requiredData: { providers: 'array' } },
    ],
  },
  automation: {
    surfaceId: 'automation',
    requiredRoutes: ['/v1/automation', '/v1/queue'],
    components: [
      { componentId: 'AutomationList', requiredData: { rules: 'array' } },
      { componentId: 'QueueMonitor', requiredData: { queue: 'object' } },
    ],
  },
  history: {
    surfaceId: 'history',
    requiredRoutes: ['/v1/history', '/v1/evidence/list'],
    components: [
      { componentId: 'HistoryTimeline', requiredData: { entries: 'array' } },
      { componentId: 'EvidenceBrowser', requiredData: { items: 'array' } },
    ],
  },
  settings: {
    surfaceId: 'settings',
    requiredRoutes: ['/v1/storage', '/v1/authority', '/v1/cascade', '/v1/quarantine', '/v1/quota', '/v1/delta-register'],
    components: [
      { componentId: 'SystemPanel', requiredData: {} },
      { componentId: 'ThemeCustomizer', requiredData: {} },
    ],
  },
  'developer-specgraph': {
    surfaceId: 'developer-specgraph',
    requiredRoutes: ['/v1/specgraph', '/v1/vocabulary'],
    components: [
      { componentId: 'SpecGraphProjectList', requiredData: { projects: 'array' } },
      { componentId: 'SpecGraphProjectView', requiredData: { atoms: 'array', edges: 'array' } },
    ],
  },
});

/**
 * Validates a surface instance against a known fixture by surfaceId.
 * Returns { ok: true } or { ok: false, detail, remedy }.
 */
export function validateSurfaceFixture(surfaceId, instance) {
  const fixture = SURFACE_CONTRACT_FIXTURES[surfaceId];
  if (!fixture) {
    return { ok: false, reason: 'unknown-fixture', detail: `No fixture for surfaceId: ${surfaceId}`, remedy: 'Add fixture or fix surfaceId' };
  }
  return validateSurfaceContract(fixture, instance);
}
