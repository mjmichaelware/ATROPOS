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
  projects: '/v1/projects',
  commands: '/v1/commands',
  vocabulary: '/v1/vocabulary',
});

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
