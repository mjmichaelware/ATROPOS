/* SPDX-License-Identifier: AGPL-3.0-only */

/**
 * Types for the shared presentation contracts.
 *
 * Hand-written against `index.mjs` rather than generated, because the runtime
 * module is the contract — a build step between the two would let the types
 * and the values they describe drift while both looked current.
 */

export type StatusTerm =
  | 'idle'
  | 'planning'
  | 'waiting'
  | 'working'
  | 'review-required'
  | 'blocked'
  | 'completed'
  | 'failed'
  | 'cancelled';

export type CompletionTerm = 'implemented' | 'compiled' | 'tested' | 'verified' | 'blocked';

export type SixAnswerKey = 'objective' | 'doing' | 'why' | 'progress' | 'next' | 'evidence';

export type HealthValue = 'verified' | 'pending' | 'error' | 'unknown';

export interface NavItem {
  readonly id: string;
  readonly label: string;
  readonly path: string;
}

export interface DeveloperTools {
  readonly id: string;
  readonly label: string;
  readonly path: string;
  readonly hiddenByDefault: boolean;
  readonly tenants: readonly NavItem[];
}

export const STATUS_TERMS: readonly StatusTerm[];
export const COMPLETION_TERMS: readonly CompletionTerm[];
export const POSITIVE_COMPLETION_TERM: 'verified';
export const SIX_ANSWER_KEYS: readonly SixAnswerKey[];
export const HEALTH_VALUES: readonly HealthValue[];
export const NAV_SPINE: readonly NavItem[];
export const DEVELOPER_TOOLS: DeveloperTools;
export const ENGINE_ROUTES: Readonly<Record<string, string>>;

/** ADD-W-001: client seams whose bridge routes do not exist yet. */
export interface MissingEngineRoute {
  readonly path: string;
  readonly servesTo: string;
}
export const MISSING_ENGINE_ROUTES: readonly MissingEngineRoute[];

export function isSixAnswersPayload(value: unknown): boolean;

/** S-005: what backs a completion claim — CAS bytes, a claim, and its gates. */
export interface EvidenceRef {
  readonly casHash: string;
  readonly claimId: string;
  readonly gateIds: readonly string[];
}

export function isEvidenceRef(value: unknown): value is EvidenceRef;

/** S-008 mirror of the engine's resume-checkpoint payload. */
export interface CheckpointAction {
  readonly id: string;
  readonly label: string;
  readonly primary: boolean;
}

export type CheckpointPayloadMirror =
  | {
      readonly present: false;
      readonly detail: string;
      readonly remedy: string;
    }
  | {
      readonly present: true;
      readonly goalId: string;
      readonly nodeId?: string | null;
      readonly phase?: string | null;
      readonly recordedAt: string;
      readonly ageMinutes: number;
      readonly resumable: boolean;
      readonly evidenceCount: number;
      readonly nextAction?: string | null;
      readonly primaryAction?: CheckpointAction;
      readonly actions: readonly CheckpointAction[];
    };

export function isCheckpointPayload(value: unknown): value is CheckpointPayloadMirror;

/** S-008 mirror of the bridge approval card (`ApprovalProjection`). */
export const APPROVAL_EVENT_KIND: 'approval_raised';

export interface ApprovalCard {
  readonly id: string;
  readonly proposalId: string;
  readonly actor: string;
  readonly operation: string;
  /** Empty means the action declared no territory — never "all paths". */
  readonly territory: readonly string[];
  readonly reason: string;
  readonly requestedAt: string;
  readonly pending: boolean;
}

export function isApprovalCard(value: unknown): value is ApprovalCard;

/** S-008 mirror of the bridge cascade snapshot (`CascadeProjection`). */
export interface CascadeKey {
  readonly key: string;
  readonly value: string;
  readonly heldBy: string;
  readonly final: boolean;
  readonly state: 'resolved' | 'violation' | 'undefined';
}

export interface CascadePayload {
  readonly ok: true;
  readonly count: number;
  readonly resolvedCount: number;
  readonly violationCount: number;
  readonly undefinedCount: number;
  readonly keys: readonly {
    readonly key: string;
    readonly value: string;
    readonly heldBy: string;
    readonly final: boolean;
    readonly state: 'resolved' | 'violation' | 'undefined';
  }[];
  readonly violations: readonly {
    readonly key: string;
    readonly heldBy: string;
    readonly attemptedBy: readonly string[];
    readonly reason: string;
  }[];
  readonly undefined: readonly {
    readonly key: string;
  }[];
}

export function isCascadePayload(value: unknown): value is CascadePayload;

/** S-008 mirror of the bridge quarantine projection (`QuarantineProjection`). */
export interface QuarantineItem {
  readonly id: string;
  readonly title: string;
  readonly summary: string;
  readonly state: string;
  readonly createdAt: string;
}

export interface QuarantinePayload {
  readonly ok: true;
  readonly count: number;
  readonly observationCount: number;
  readonly items: readonly QuarantineItem[];
  readonly observation: readonly {
    readonly subsystem: string;
    readonly startedAt: string;
    readonly durationSeconds: number;
  }[];
}

export function isQuarantinePayload(value: unknown): value is QuarantinePayload;

/** Throws when the engine's served vocabulary differs from this package's copy. */
export function assertVocabularyMatches(served: unknown): true;