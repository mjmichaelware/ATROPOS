/* SPDX-License-Identifier: AGPL-3.0-only */
import test from 'node:test';
import assert from 'node:assert/strict';
import {
  STATUS_TERMS,
  COMPLETION_TERMS,
  POSITIVE_COMPLETION_TERM,
  SIX_ANSWER_KEYS,
  NAV_SPINE,
  DEVELOPER_TOOLS,
  isSixAnswersPayload,
  assertVocabularyMatches,
} from '../src/index.mjs';

const answer = (value) => ({ value, health: 'verified', signal: 'verified' });
const sixAnswers = Object.fromEntries(SIX_ANSWER_KEYS.map((k) => [k, answer(k)]));

test('status and completion vocabularies stay separate', () => {
  assert.equal(STATUS_TERMS.length, 9);
  assert.equal(COMPLETION_TERMS.length, 5);
  const overlap = STATUS_TERMS.filter((t) => COMPLETION_TERMS.includes(t));
  // "blocked" is the one term both vocabularies legitimately use.
  assert.deepEqual(overlap, ['blocked']);
});

test('only verified is a positive completion claim', () => {
  assert.equal(POSITIVE_COMPLETION_TERM, 'verified');
  assert.ok(!COMPLETION_TERMS.slice(0, 3).includes(POSITIVE_COMPLETION_TERM));
});

test('nav spine is the HOE-A02 order and excludes SpecGraph', () => {
  assert.deepEqual(
    NAV_SPINE.map((i) => i.id),
    ['home', 'projects', 'work', 'conversations', 'files', 'agents', 'models', 'automation', 'history', 'settings'],
  );
  assert.ok(!NAV_SPINE.some((i) => i.id === 'specgraph'), 'HOE-C07 keeps SpecGraph out of the spine');
  assert.ok(!NAV_SPINE.some((i) => i.id === 'developer'));
});

test('developer tools is hidden by default and owns the SpecGraph mount', () => {
  assert.equal(DEVELOPER_TOOLS.hiddenByDefault, true);
  assert.equal(DEVELOPER_TOOLS.tenants[0].path, '/developer/specgraph');
});

test('a well-formed six-answers payload validates', () => {
  assert.ok(isSixAnswersPayload({ answers: sixAnswers, queue: { readable: true } }));
});

test('a payload missing queue.readable is refused', () => {
  assert.ok(
    !isSixAnswersPayload({ answers: sixAnswers, queue: {} }),
    'without readable, unreadable and empty collapse into one state',
  );
});

test('a payload missing an answer is refused', () => {
  const { evidence, ...partial } = sixAnswers;
  assert.ok(!isSixAnswersPayload({ answers: partial, queue: { readable: true } }));
});

test('an unknown health value is refused rather than coerced', () => {
  const bad = { ...sixAnswers, why: { value: 'x', health: 'green', signal: 'ok' } };
  assert.ok(!isSixAnswersPayload({ answers: bad, queue: { readable: true } }));
});

test('matching engine vocabulary passes', () => {
  assert.ok(
    assertVocabularyMatches({
      status: { terms: STATUS_TERMS.map((term) => ({ term })) },
      completion: { terms: COMPLETION_TERMS.map((term) => ({ term })) },
    }),
  );
});

test('drift between engine and contracts throws rather than degrading', () => {
  assert.throws(
    () =>
      assertVocabularyMatches({
        status: { terms: [{ term: 'idle' }] },
        completion: { terms: COMPLETION_TERMS.map((term) => ({ term })) },
      }),
    /vocabulary drift/,
  );
});

// ─── S-005 EvidenceRef ────────────────────────────────────────────────────
import { isEvidenceRef, isCheckpointPayload, isApprovalCard, APPROVAL_EVENT_KIND } from '../src/index.mjs';

test('isEvidenceRef accepts a well-formed reference', () => {
  const ref = {
    casHash: 'a'.repeat(64),
    claimId: 'claim-1',
    gateIds: ['compile', 'test'],
  };
  assert.equal(isEvidenceRef(ref), true);
});

test('isEvidenceRef rejects malformed hashes, claims and gates', () => {
  const good = { casHash: 'a'.repeat(64), claimId: 'c', gateIds: [] };
  assert.equal(isEvidenceRef({ ...good, casHash: 'nothex' }), false);
  assert.equal(isEvidenceRef({ ...good, casHash: 'b'.repeat(63) }), false);
  assert.equal(isEvidenceRef({ ...good, claimId: '' }), false);
  assert.equal(isEvidenceRef({ ...good, claimId: 7 }), false);
  assert.equal(isEvidenceRef({ ...good, gateIds: ['ok', 3] }), false);
  assert.equal(isEvidenceRef(null), false);
  assert.equal(isEvidenceRef('evidence'), false);
});

// ─── S-008 checkpoint mirror ─────────────────────────────────────────────
test('isCheckpointPayload accepts present and absent forms', () => {
  assert.equal(
    isCheckpointPayload({
      present: true,
      goalId: 'g1',
      recordedAt: '2026-01-01T00:00:00Z',
      ageMinutes: 5,
      resumable: true,
      evidenceCount: 2,
      actions: [{ id: 'resume', label: 'Resume', primary: true }],
    }),
    true,
  );
  assert.equal(
    isCheckpointPayload({ present: false, detail: 'none', remedy: 'run' }),
    true,
  );
});

test('isCheckpointPayload rejects payloads that collapse absence into age zero', () => {
  assert.equal(isCheckpointPayload({ present: true, goalId: 'g1' }), false);
  assert.equal(isCheckpointPayload({ present: true }), false);
  // An action without the primary flag cannot drive HOE-B04's primary rule.
  assert.equal(
    isCheckpointPayload({
      present: true,
      goalId: 'g',
      recordedAt: 't',
      ageMinutes: 0,
      resumable: false,
      evidenceCount: 0,
      actions: [{ id: 'x', label: 'X' }],
    }),
    false,
  );
});

// ─── S-008 approval mirror ───────────────────────────────────────────────
test('isApprovalCard accepts the projection shape including empty territory', () => {
  const card = {
    id: 'ap-1',
    proposalId: 'p-1',
    actor: 'patch:x',
    operation: 'WRITE_FILE',
    territory: [],
    reason: 'outside grant',
    requestedAt: '2026-01-01T00:00:00Z',
    pending: true,
  };
  assert.equal(isApprovalCard(card), true);
  assert.equal(APPROVAL_EVENT_KIND, 'approval_raised');
});

test('isApprovalCard rejects rows missing decision-required fields', () => {
  const card = {
    id: 'ap-1',
    proposalId: 'p',
    actor: 'a',
    operation: 'o',
    territory: [],
    reason: 'r',
    requestedAt: 't',
  };
  assert.equal(isApprovalCard(card), false); // pending missing — cannot render a card for an unknown state.
});

import { MISSING_ENGINE_ROUTES } from '../src/index.mjs';

test('ADD-W-001: documented missing routes stay a small, named honest gap', () => {
  // Every entry must name the surface waiting on it — an unnamed gap is a
  // guess, and a guessed gap cannot be closed on purpose.
  for (const missing of MISSING_ENGINE_ROUTES) {
    assert.match(missing.path, /^\/v1\//);
    assert.ok(missing.servesTo.length > 0);
  }
  assert.ok(MISSING_ENGINE_ROUTES.length <= 4);
});

// ─── S-008 cascade mirror ──────────────────────────────────────────────────
import { isCascadePayload } from '../src/index.mjs';

test('isCascadePayload accepts well-formed cascade', () => {
  const cascade = {
    ok: true,
    count: 2,
    resolvedCount: 1,
    violationCount: 1,
    undefinedCount: 0,
    keys: [
      { key: 'authority.rank', value: '0', heldBy: 'source-authority', final: true, state: 'resolved' },
      { key: 'secret.policy', value: 'hidden', heldBy: 'rank-0', final: false, state: 'violation' },
    ],
    violations: [
      { key: 'secret.policy', heldBy: 'rank-0', attemptedBy: ['env', 'flag'], reason: 'core keys non-overridable' },
    ],
    undefined: [],
  };
  assert.equal(isCascadePayload(cascade), true);
});

test('isCascadePayload rejects malformed keys', () => {
  const bad = {
    ok: true,
    count: 1,
    resolvedCount: 0,
    violationCount: 0,
    undefinedCount: 0,
    keys: [{ key: 'x', value: 1, heldBy: 'x', final: true, state: 'resolved' }], // value not string
    violations: [],
    undefined: [],
  };
  assert.equal(isCascadePayload(bad), false);
});

// ─── S-008 quarantine mirror ───────────────────────────────────────────────
import { isQuarantinePayload } from '../src/index.mjs';

test('isQuarantinePayload accepts well-formed quarantine', () => {
  const q = {
    ok: true,
    count: 1,
    observationCount: 1,
    items: [{ id: 'p-1', title: 't', summary: 's', state: 'quarantined', createdAt: '2026-01-01T00:00:00Z' }],
    observation: [{ subsystem: 's1', startedAt: '2026-01-01T00:00:00Z', durationSeconds: 3600 }],
  };
  assert.equal(isQuarantinePayload(q), true);
});

test('isQuarantinePayload rejects malformed', () => {
  const bad = { ok: true, count: 1, observationCount: 0, items: [{}], observation: [] };
  assert.equal(isQuarantinePayload(bad), false);
});

