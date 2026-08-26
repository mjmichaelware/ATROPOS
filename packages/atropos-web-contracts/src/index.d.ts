/* SPDX-License-Identifier: AGPL-3.0-only */

/**
 * TypeScript type definitions for @atropos/web-contracts
 *
 * These types mirror the runtime validation functions in index.mjs.
 * They exist for compile-time type checking only; the runtime validation
 * in index.mjs remains the source of truth.
 */

/** Source Doc 4 §A status terms, in doc order. What the work is doing. */
export const STATUS_TERMS = [
  'idle',
  'planning',
  'waiting',
  'working',
  'review-required',
  'blocked',
  'completed',
  'failed',
  'cancelled',
] as const;

export type StatusTerm = typeof STATUS_TERMS[number];

/** P20-G09 completion terms, weakest claim first. */
export const COMPLETION_TERMS = [
  'implemented',
  'compiled',
  'tested',
  'verified',
  'blocked',
] as const;

export type CompletionTerm = typeof COMPLETION_TERMS[number];

export const POSITIVE_COMPLETION_TERM = 'verified';

/** The six continuous answers of Source Doc 4 §0.1, in order. */
export const SIX_ANSWER_KEYS = [
  'objective',
  'doing',
  'why',
  'progress',
  'next',
  'evidence',
] as const;

export type SixAnswerKey = typeof SIX_ANSWER_KEYS[number];

/** Health values an answer may carry. */
export type HealthValue = 'verified' | 'pending' | 'error' | 'unknown';

/** HOE-A02 primary navigation spine. */
export interface NavSpineEntry {
  id: string;
  label: string;
  path: string;
}

export const NAV_SPINE: readonly NavSpineEntry[];

export interface DeveloperTools {
  id: 'developer';
  label: 'Developer Tools';
  path: '/developer';
  hiddenByDefault: true;
  tenants: readonly { id: string; label: string; path: string }[];
}

export const DEVELOPER_TOOLS: DeveloperTools;

/** Engine routes this contract version knows how to read. */
export interface EngineRoutes {
  health: '/v1/health';
  routes: '/v1/routes';
  answers: '/v1/answers';
  answersStream: '/v1/answers/stream';
  projects: '/v1/projects';
  commands: '/v1/commands';
  commandRun: '/v1/command';
  commandAllowed: '/v1/command/allowed';
  vocabulary: '/v1/vocabulary';
  checkpoint: '/v1/checkpoint';
  approvals: '/v1/approvals';
  approvalsDecide: '/v1/approvals/decide';
  activity: '/v1/activity';
  events: '/v1/events';
  eventsStream: '/v1/events/stream';
  sessions: '/v1/sessions';
  files: '/v1/files';
  cascade: '/v1/cascade';
  quarantine: '/v1/quarantine';
  evidenceList: '/v1/evidence/list';
  deltaRegister: '/v1/delta-register';
  quota: '/v1/quota';
  queueCancel: '/v1/queue/cancel';
  queueHardInterrupt: '/v1/queue/hard-interrupt';
  queueFreeze: '/v1/queue/freeze';
  queueResume: '/v1/queue/resume';
  queueFreezeStatus: '/v1/queue/freeze';
}

export const ENGINE_ROUTES: EngineRoutes;

/** Routes the web surface has a client seam for but no bridge build serves yet. */
export interface MissingEngineRoute {
  path: string;
  servesTo: string;
}

export const MISSING_ENGINE_ROUTES: readonly MissingEngineRoute[];

/** S-005: one evidence reference, the shape every completion claim carries. */
export interface EvidenceRef {
  casHash: string;
  claimId: string;
  gateIds: string[];
}

export function isEvidenceRef(value: unknown): value is EvidenceRef;

/** S-008 mirror of the engine's resume-checkpoint payload. */
export interface CheckpointAction {
  id: string;
  label: string;
  primary: boolean;
}

export type CheckpointPayload =
  | {
      present: true;
      goalId: string;
      recordedAt: string;
      ageMinutes: number;
      resumable: boolean;
      evidenceCount: number;
      actions: CheckpointAction[];
    }
  | {
      present: false;
      detail: string;
      remedy: string;
    };

export function isCheckpointPayload(value: unknown): value is CheckpointPayload;

/** S-008 mirror of the bridge approval card (`ApprovalProjection`). */
export const APPROVAL_EVENT_KIND = 'approval_raised';

export interface ApprovalDecision {
  approved: boolean;
  approver: string;
  decidedAt: string;
  surface: string;
}

export interface ApprovalCard {
  id: string;
  proposalId: string;
  actor: string;
  operation: string;
  territory: string[];
  reason: string;
  requestedAt: string;
  pending: boolean;
  proposer?: string;
  approver?: string;
  decision?: ApprovalDecision | null;
}

export function isApprovalCard(value: unknown): value is ApprovalCard;

export interface ApprovalEvent {
  type: typeof APPROVAL_EVENT_KIND;
  data: ApprovalCard;
  timestamp: number;
}

/** S-008 mirror of the bridge cascade snapshot (`CascadeProjection`). */
export interface CascadeKey {
  key: string;
  value: string;
  heldBy: string;
  final: boolean;
  state: 'resolved' | 'violation' | 'undefined';
}

export interface CascadePayload {
  ok: true;
  keys: readonly CascadeKey[];
}

export function isCascadePayload(value: unknown): value is CascadePayload;

/** S-008 mirror of the bridge quarantine projection. */
export interface QuarantineItem {
  id: string;
  title: string;
  summary: string;
  state: string;
  createdAt: string;
}

export interface QuarantineObservation {
  subsystem: string;
  startedAt: string;
  durationSeconds: number;
}

export interface QuarantinePayload {
  ok: true;
  count: number;
  observationCount: number;
  items: readonly QuarantineItem[];
  observation: readonly QuarantineObservation[];
}

export function isQuarantinePayload(value: unknown): value is QuarantinePayload;

/** S-008 mirror of the bridge evidence list projection. */
export interface EvidenceItem {
  id: string;
  kind: string;
  path: string;
  sha256: string;
  bytes: number;
  createdAt: string;
}

export interface EvidenceListPayload {
  ok: true;
  items: readonly EvidenceItem[];
}

/** S-008 mirror of the bridge delta register projection. */
export interface DeltaRegisterEntry {
  id: string;
  path: string;
  kind: string;
  sha256: string;
  bytes: number;
  createdAt: string;
}

export interface DeltaRegisterPayload {
  ok: true;
  entries: readonly DeltaRegisterEntry[];
}

/** S-008 mirror of the bridge quota projection. */
export interface QuotaPayload {
  ok: true;
  used: number;
  limit: number;
  remaining: number;
  fractionUsed: number;
  resetAt: string | null;
}

/** Health values an answer may carry. */
export const HEALTH_VALUES = ['verified', 'pending', 'error', 'unknown'] as const;

/** Six answer keys. */
export const SIX_ANSWER_KEYS = [
  'objective',
  'doing',
  'why',
  'progress',
  'next',
  'evidence',
] as const;

export function isSixAnswersPayload(value: unknown): boolean;

/** Throws when the engine's served vocabulary differs from this package's copy. */
export function assertVocabularyMatches(served: unknown): true;

/**
 * ADD-W-027: SurfaceContract — the contract a web surface must satisfy.
 */
export type SurfaceContractKind =
  | 'home'
  | 'project-work'
  | 'project-files'
  | 'project-activity'
  | 'project-agents'
  | 'models'
  | 'automation'
  | 'history'
  | 'settings'
  | 'developer-specgraph';

export const SURFACE_CONTRACT_KINDS: readonly SurfaceContractKind[];

export interface SurfaceComponentContract {
  componentId: string;
  requiredData: Record<string, unknown>;
}

export interface SurfaceContract {
  surfaceId: SurfaceContractKind;
  requiredRoutes: string[];
  components: SurfaceComponentContract[];
  requiredState?: Record<string, unknown>;
}

export function isSurfaceContract(value: unknown): value is SurfaceContract;

export interface SurfaceContractValidationResultSuccess {
  ok: true;
}

export interface SurfaceContractValidationResultFailure {
  ok: false;
  reason: string;
  detail: string;
  remedy: string;
  [key: string]: unknown;
}

export type SurfaceContractValidationResult = SurfaceContractValidationResultSuccess | SurfaceContractValidationResultFailure;

export function validateSurfaceContract(contract: SurfaceContract, instance: unknown): SurfaceContractValidationResult;

export interface SurfaceContractFixture {
  surfaceId: SurfaceContractKind;
  requiredRoutes: string[];
  components: SurfaceComponentContract[];
  requiredState?: Record<string, unknown>;
}

export const SURFACE_CONTRACT_FIXTURES: Record<SurfaceContractKind, SurfaceContractFixture>;

export function validateSurfaceFixture(surfaceId: SurfaceContractKind, instance: unknown): SurfaceContractValidationResult;