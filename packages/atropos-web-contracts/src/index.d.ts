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

export function isSixAnswersPayload(value: unknown): boolean;

/** Throws when the engine's served vocabulary differs from this package's copy. */
export function assertVocabularyMatches(served: unknown): true;
