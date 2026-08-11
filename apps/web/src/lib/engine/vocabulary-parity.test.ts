/* SPDX-License-Identifier: AGPL-3.0-only */
import { describe, expect, it } from 'vitest';
import {
  STATUS_TERMS,
  COMPLETION_TERMS,
  SIX_ANSWER_KEYS,
  NAV_SPINE,
  DEVELOPER_TOOLS,
  assertVocabularyMatches,
  isSixAnswersPayload,
} from '@atropos/web-contracts';
import { HOE_A02_SPINE_ORDER, navigationSpine } from '@/components/navigation/routes';

/**
 * HOE-C09: "all surfaces consume same types."
 *
 * Until this file existed the contracts package was a declared dependency that
 * nothing imported, which is the same as no contract at all — a shared type
 * nobody reads cannot keep two surfaces in agreement. This is the web side
 * actually consuming it, and asserting that what the app renders matches what
 * the contract promises.
 */
describe('web consumes the shared contracts', () => {
  it('the app spine matches the contract spine, in order', () => {
    expect(navigationSpine.map((i) => i.id)).toEqual(NAV_SPINE.map((i) => i.id));
    expect(HOE_A02_SPINE_ORDER).toEqual(NAV_SPINE.map((i) => i.id));
  });

  it('the contract keeps SpecGraph under hidden Developer Tools', () => {
    expect(DEVELOPER_TOOLS.hiddenByDefault).toBe(true);
    expect(DEVELOPER_TOOLS.tenants[0].path).toBe('/developer/specgraph');
    expect(NAV_SPINE.some((i) => i.id === 'developer')).toBe(false);
  });

  it('carries the nine status terms and the five completion terms separately', () => {
    expect(STATUS_TERMS).toHaveLength(9);
    expect(COMPLETION_TERMS).toHaveLength(5);
    // P20-G09: their collapse is the deficiency, so overlap stays minimal.
    const completion: readonly string[] = COMPLETION_TERMS;
    expect(STATUS_TERMS.filter((t) => completion.includes(t))).toEqual(['blocked']);
  });

  it('names the six answers the engine serves', () => {
    expect(SIX_ANSWER_KEYS).toEqual(['objective', 'doing', 'why', 'progress', 'next', 'evidence']);
  });

  it('detects drift when an engine payload disagrees with the contract', () => {
    expect(() =>
      assertVocabularyMatches({
        status: { terms: [{ term: 'idle' }] },
        completion: { terms: COMPLETION_TERMS.map((term) => ({ term })) },
      }),
    ).toThrow(/vocabulary drift/);
  });

  it('rejects an answers payload that cannot distinguish unreadable from empty', () => {
    const answers = Object.fromEntries(
      SIX_ANSWER_KEYS.map((k) => [k, { value: k, health: 'verified', signal: 'verified' }]),
    );
    expect(isSixAnswersPayload({ answers, queue: { readable: false } })).toBe(true);
    expect(isSixAnswersPayload({ answers, queue: {} })).toBe(false);
  });
});
