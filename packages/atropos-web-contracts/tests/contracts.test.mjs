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
